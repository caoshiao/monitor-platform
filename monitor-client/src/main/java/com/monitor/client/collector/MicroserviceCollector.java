package com.monitor.client.collector;

import com.monitor.client.config.ClientConfig;
import com.monitor.common.model.MicroserviceMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 外部系统双探针采集器，分别检测目标端口连通性和 HTTP 接口可用性。
 *
 * <p>端口与接口均正常时总体状态为 {@code UP}，仅一项正常时为 {@code DEGRADED}，
 * 两项均异常时为 {@code DOWN}。多个外部系统通过受控线程池并行检测，避免单个超时阻塞整轮采集。</p>
 *
 * @author csa
 * @see MicroserviceMetrics
 * @see ClientConfig.ServiceEndpoint
 */
@Slf4j
@Component
public class MicroserviceCollector {

    private static final String STATUS_UP = "UP";
    private static final String STATUS_DOWN = "DOWN";
    private static final String STATUS_DEGRADED = "DEGRADED";
    private static final String METHOD_GET = "GET";
    private static final String METHOD_HEAD = "HEAD";
    private static final int MIN_PARALLELISM = 1;
    private static final int MAX_PARALLELISM = 32;
    private static final int READ_BUFFER_SIZE = 4096;
    private static final int HTTP_STATUS_MIN = 100;
    private static final int HTTP_STATUS_MAX = 599;
    private static final AtomicInteger THREAD_SEQUENCE = new AtomicInteger();
    private static volatile SSLSocketFactory trustAllFactory;
    private final Map<String, String> previousHealthStatuses = new ConcurrentHashMap<>();

    /**
     * 并行采集所有外部系统的双探针指标。
     *
     * @param clientId 客户端唯一标识
     * @param config 客户端配置
     * @return 外部系统探测汇总指标
     */
    public MicroserviceMetrics collect(String clientId, ClientConfig config) {
        List<ClientConfig.ServiceEndpoint> endpoints = config.getMicroservices() == null
                ? new ArrayList<>() : config.getMicroservices();
        log.debug("开始采集外部系统状态, clientId={}, systemCount={}", clientId, endpoints.size());

        ExecutorService executor = createExecutor(config.getMicroserviceProbeParallelism());
        List<MicroserviceMetrics.ServiceInfo> services;
        try {
            List<CompletableFuture<MicroserviceMetrics.ServiceInfo>> tasks = endpoints.stream()
                    .filter(Objects::nonNull)
                    .map(endpoint -> CompletableFuture.supplyAsync(
                                    () -> collectEndpoint(endpoint, config), executor)
                            .exceptionally(exception -> buildUnexpectedFailure(endpoint, exception)))
                    .collect(Collectors.toList());
            services = tasks.stream().map(CompletableFuture::join).collect(Collectors.toList());
        } finally {
            executor.shutdownNow();
        }

        MicroserviceMetrics metrics = buildMetrics(clientId, services);
        recordStatusChanges(clientId, services);
        log.debug("外部系统状态采集完成, clientId={}, total={}, healthy={}, degraded={}, unhealthy={}",
                clientId, metrics.getTotalServices(), metrics.getHealthyServices(),
                metrics.getDegradedServices(), metrics.getUnhealthyServices());
        return metrics;
    }

    private ExecutorService createExecutor(int configuredParallelism) {
        int parallelism = Math.max(MIN_PARALLELISM, Math.min(configuredParallelism, MAX_PARALLELISM));
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable,
                    "external-probe-" + THREAD_SEQUENCE.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newFixedThreadPool(parallelism, threadFactory);
    }

    private MicroserviceMetrics buildMetrics(String clientId,
                                               List<MicroserviceMetrics.ServiceInfo> services) {
        MicroserviceMetrics metrics = new MicroserviceMetrics();
        metrics.setClientId(clientId);
        metrics.setCollectTime(LocalDateTime.now());
        metrics.setServices(services);
        metrics.setTotalServices(services.size());
        metrics.setHealthyServices((int) services.stream()
                .filter(service -> STATUS_UP.equals(service.getHealthStatus())).count());
        metrics.setDegradedServices((int) services.stream()
                .filter(service -> STATUS_DEGRADED.equals(service.getHealthStatus())).count());
        metrics.setUnhealthyServices((int) services.stream()
                .filter(service -> !STATUS_UP.equals(service.getHealthStatus())).count());
        return metrics;
    }

    private MicroserviceMetrics.ServiceInfo collectEndpoint(ClientConfig.ServiceEndpoint endpoint,
                                                             ClientConfig config) {
        MicroserviceMetrics.ServiceInfo info = new MicroserviceMetrics.ServiceInfo();
        info.setServiceName(hasText(endpoint.getName()) ? endpoint.getName() : "未命名外部系统");
        info.setServiceUrl(endpoint.getHealthUrl());
        info.setLastCheckTime(LocalDateTime.now());

        ProbeTarget target = resolveTarget(endpoint);
        info.setHost(target.host);
        info.setPort(target.port);

        ProbeResult networkResult = probeNetwork(target.host, target.port,
                config.getMicroserviceConnectTimeoutMs());
        ProbeResult interfaceResult = probeInterface(endpoint, config);
        info.setNetworkStatus(networkResult.success ? STATUS_UP : STATUS_DOWN);
        info.setInterfaceStatus(interfaceResult.success ? STATUS_UP : STATUS_DOWN);
        info.setNetworkResponseTimeMs(networkResult.durationMs);
        info.setResponseTimeMs(interfaceResult.durationMs);
        info.setHttpStatusCode(interfaceResult.statusCode);
        info.setHealthStatus(resolveHealthStatus(networkResult.success, interfaceResult.success));
        info.setErrorMessage(buildErrorMessage(networkResult, interfaceResult));
        return info;
    }

    private MicroserviceMetrics.ServiceInfo buildUnexpectedFailure(
            ClientConfig.ServiceEndpoint endpoint, Throwable exception) {
        log.error("外部系统探测任务执行失败, system={}", endpoint.getName(), exception);
        MicroserviceMetrics.ServiceInfo info = new MicroserviceMetrics.ServiceInfo();
        info.setServiceName(hasText(endpoint.getName()) ? endpoint.getName() : "未命名外部系统");
        info.setServiceUrl(endpoint.getHealthUrl());
        info.setHost(endpoint.getHost());
        info.setPort(endpoint.getPort());
        info.setNetworkStatus(STATUS_DOWN);
        info.setInterfaceStatus(STATUS_DOWN);
        info.setHealthStatus(STATUS_DOWN);
        info.setNetworkResponseTimeMs(-1L);
        info.setResponseTimeMs(-1L);
        info.setHttpStatusCode(-1);
        info.setLastCheckTime(LocalDateTime.now());
        info.setErrorMessage("探测任务执行失败: " + rootCauseMessage(exception));
        return info;
    }

    private void recordStatusChanges(String clientId,
                                     List<MicroserviceMetrics.ServiceInfo> services) {
        for (MicroserviceMetrics.ServiceInfo service : services) {
            String statusKey = clientId + "\u0000" + service.getServiceName();
            String previousStatus = previousHealthStatuses.put(statusKey, service.getHealthStatus());
            if (!Objects.equals(previousStatus, service.getHealthStatus())) {
                log.info("外部系统状态变化, clientId={}, system={}, previous={}, current={}, tcp={}, interface={}",
                        clientId, service.getServiceName(), previousStatus, service.getHealthStatus(),
                        service.getNetworkStatus(), service.getInterfaceStatus());
            }
        }
    }

    private ProbeTarget resolveTarget(ClientConfig.ServiceEndpoint endpoint) {
        String host = endpoint.getHost();
        int port = endpoint.getPort();
        if ((!hasText(host) || port <= 0) && hasText(endpoint.getHealthUrl())) {
            try {
                URL url = new URL(endpoint.getHealthUrl());
                if (!hasText(host)) {
                    host = url.getHost();
                }
                if (port <= 0) {
                    port = url.getPort() > 0 ? url.getPort() : url.getDefaultPort();
                }
            } catch (IOException exception) {
                log.debug("外部系统接口地址解析失败, system={}, url={}",
                        endpoint.getName(), endpoint.getHealthUrl(), exception);
            }
        }
        return new ProbeTarget(host, port);
    }

    private ProbeResult probeNetwork(String host, int port, int timeoutMs) {
        if (!hasText(host) || port <= 0) {
            return ProbeResult.failed("未配置有效的主机和端口");
        }
        long startTime = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), positiveTimeout(timeoutMs));
            return ProbeResult.succeeded(System.currentTimeMillis() - startTime, -1);
        } catch (IOException exception) {
            log.debug("外部系统端口探测失败, host={}, port={}", host, port, exception);
            return ProbeResult.failed(System.currentTimeMillis() - startTime, exception.getMessage());
        }
    }

    private ProbeResult probeInterface(ClientConfig.ServiceEndpoint endpoint, ClientConfig config) {
        if (!hasText(endpoint.getHealthUrl())) {
            return ProbeResult.failed("未配置接口地址");
        }
        String requestMethod = normalizeRequestMethod(endpoint.getRequestMethod());
        if (requestMethod == null) {
            return ProbeResult.failed("接口请求方法仅支持 GET 或 HEAD");
        }
        if (endpoint.getExpectedStatusMin() < HTTP_STATUS_MIN
                || endpoint.getExpectedStatusMax() > HTTP_STATUS_MAX
                || endpoint.getExpectedStatusMin() > endpoint.getExpectedStatusMax()) {
            return ProbeResult.failed("接口状态码范围配置无效");
        }

        HttpURLConnection connection = null;
        long startTime = System.currentTimeMillis();
        try {
            connection = (HttpURLConnection) new URL(endpoint.getHealthUrl()).openConnection();
            configureConnection(connection, endpoint, config, requestMethod);
            int statusCode = connection.getResponseCode();
            long durationMs = System.currentTimeMillis() - startTime;
            if (statusCode < endpoint.getExpectedStatusMin()
                    || statusCode > endpoint.getExpectedStatusMax()) {
                return ProbeResult.failed(durationMs, statusCode,
                        "接口状态码不符合预期: " + statusCode);
            }
            if (hasText(endpoint.getExpectedBodyContains())) {
                String responseBody = readResponseBody(connection, config.getMicroserviceMaxResponseBytes());
                if (!responseBody.contains(endpoint.getExpectedBodyContains())) {
                    return ProbeResult.failed(durationMs, statusCode, "接口响应内容不符合预期");
                }
            }
            return ProbeResult.succeeded(durationMs, statusCode);
        } catch (IOException | RuntimeException exception) {
            log.debug("外部系统接口探测失败, system={}, url={}",
                    endpoint.getName(), endpoint.getHealthUrl(), exception);
            return ProbeResult.failed(System.currentTimeMillis() - startTime, exception.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void configureConnection(HttpURLConnection connection,
                                     ClientConfig.ServiceEndpoint endpoint,
                                     ClientConfig config,
                                     String requestMethod) throws IOException {
        if (connection instanceof HttpsURLConnection && !config.isMicroserviceSslVerify()) {
            HttpsURLConnection httpsConnection = (HttpsURLConnection) connection;
            httpsConnection.setSSLSocketFactory(getTrustAllFactory());
            httpsConnection.setHostnameVerifier((host, session) -> true);
        }
        connection.setConnectTimeout(positiveTimeout(config.getMicroserviceConnectTimeoutMs()));
        connection.setReadTimeout(positiveTimeout(config.getMicroserviceReadTimeoutMs()));
        connection.setRequestMethod(requestMethod);
        connection.setInstanceFollowRedirects(true);
        if (endpoint.getRequestHeaders() != null) {
            for (Map.Entry<String, String> header : endpoint.getRequestHeaders().entrySet()) {
                if (hasText(header.getKey()) && header.getValue() != null) {
                    connection.setRequestProperty(header.getKey(), header.getValue());
                }
            }
        }
    }

    private String readResponseBody(HttpURLConnection connection, int configuredMaxBytes)
            throws IOException {
        int maxBytes = Math.max(READ_BUFFER_SIZE, configuredMaxBytes);
        try (InputStream inputStream = connection.getInputStream();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[READ_BUFFER_SIZE];
            int totalRead = 0;
            int read;
            while (totalRead < maxBytes && (read = inputStream.read(buffer, 0,
                    Math.min(buffer.length, maxBytes - totalRead))) > 0) {
                outputStream.write(buffer, 0, read);
                totalRead += read;
            }
            return outputStream.toString(StandardCharsets.UTF_8.name());
        }
    }

    private String normalizeRequestMethod(String requestMethod) {
        String normalized = hasText(requestMethod)
                ? requestMethod.trim().toUpperCase(Locale.ROOT) : METHOD_GET;
        return METHOD_GET.equals(normalized) || METHOD_HEAD.equals(normalized) ? normalized : null;
    }

    private String resolveHealthStatus(boolean networkUp, boolean interfaceUp) {
        if (networkUp && interfaceUp) {
            return STATUS_UP;
        }
        if (networkUp || interfaceUp) {
            return STATUS_DEGRADED;
        }
        return STATUS_DOWN;
    }

    private String buildErrorMessage(ProbeResult networkResult, ProbeResult interfaceResult) {
        List<String> errors = new ArrayList<>();
        if (!networkResult.success) {
            errors.add("端口探测: " + networkResult.errorMessage);
        }
        if (!interfaceResult.success) {
            errors.add("接口探测: " + interfaceResult.errorMessage);
        }
        return errors.isEmpty() ? null : String.join("；", errors);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private int positiveTimeout(int configuredTimeoutMs) {
        return Math.max(1, configuredTimeoutMs);
    }

    private String rootCauseMessage(Throwable exception) {
        Throwable rootCause = exception;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        return Objects.toString(rootCause.getMessage(), rootCause.getClass().getSimpleName());
    }

    private static SSLSocketFactory getTrustAllFactory() {
        if (trustAllFactory == null) {
            synchronized (MicroserviceCollector.class) {
                if (trustAllFactory == null) {
                    try {
                        TrustManager[] trustAll = new TrustManager[]{new X509TrustManager() {
                            @Override
                            public void checkClientTrusted(X509Certificate[] certificates, String authType) {
                            }

                            @Override
                            public void checkServerTrusted(X509Certificate[] certificates, String authType) {
                            }

                            @Override
                            public X509Certificate[] getAcceptedIssuers() {
                                return new X509Certificate[0];
                            }
                        }};
                        SSLContext context = SSLContext.getInstance("TLS");
                        context.init(null, trustAll, new java.security.SecureRandom());
                        trustAllFactory = context.getSocketFactory();
                    } catch (NoSuchAlgorithmException | KeyManagementException exception) {
                        log.error("初始化内网 HTTPS 证书信任上下文失败", exception);
                        throw new IllegalStateException("初始化 HTTPS 证书信任上下文失败", exception);
                    }
                }
            }
        }
        return trustAllFactory;
    }

    /** TCP 探测目标。 */
    private static final class ProbeTarget {
        private final String host;
        private final int port;

        private ProbeTarget(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }

    /** 单项探针结果。 */
    private static final class ProbeResult {
        private final boolean success;
        private final long durationMs;
        private final int statusCode;
        private final String errorMessage;

        private ProbeResult(boolean success, long durationMs, int statusCode, String errorMessage) {
            this.success = success;
            this.durationMs = durationMs;
            this.statusCode = statusCode;
            this.errorMessage = errorMessage;
        }

        private static ProbeResult succeeded(long durationMs, int statusCode) {
            return new ProbeResult(true, durationMs, statusCode, null);
        }

        private static ProbeResult failed(String errorMessage) {
            return failed(-1, -1, errorMessage);
        }

        private static ProbeResult failed(long durationMs, String errorMessage) {
            return failed(durationMs, -1, errorMessage);
        }

        private static ProbeResult failed(long durationMs, int statusCode, String errorMessage) {
            return new ProbeResult(false, durationMs, statusCode,
                    Objects.toString(errorMessage, "未知错误"));
        }
    }
}
