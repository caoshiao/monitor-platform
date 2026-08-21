package com.monitor.client.collector;

import com.monitor.client.config.ClientConfig;
import com.monitor.common.model.MicroserviceMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 微服务健康检测采集器 —— 通过 HTTP GET 请求各微服务的健康检查端点。
 * <p>
 * 每个微服务支持自定义健康检查 URL（如 Spring Boot Actuator 的 {@code /actuator/health}）。
 * HTTP 返回 2xx/3xx 即判定为 UP，其他（含超时、连接被拒等 IO 异常）判定为 DOWN。
 * 同时记录每次检查的响应时间（毫秒）和失败时的错误信息。
 * </p>
 *
 * <h3>HTTPS 证书校验</h3>
 * 通过配置 {@code monitor.client.microservice-ssl-verify} 控制是否校验 HTTPS 证书：
 * <ul>
 *   <li>{@code false}（默认）—— 跳过证书校验，适用于内网自签名证书场景</li>
 *   <li>{@code true} —— 使用 JVM 默认 trustStore 校验</li>
 * </ul>
 *
 * <h3>超时设置</h3>
 * <ul>
 *   <li>连接超时：5 秒</li>
 *   <li>读取超时：5 秒</li>
 *   <li>单服务检查最多阻塞 10 秒</li>
 * </ul>
 *
 * @author csa
 * @see MicroserviceMetrics
 * @see ClientConfig.ServiceEndpoint
 */
@Slf4j
@Component
public class MicroserviceCollector {

    /** 信任所有证书的 SSLContext（懒加载，仅在 sslVerify=false 时初始化一次） */
    private static volatile SSLSocketFactory trustAllFactory;

    /**
     * 遍历配置的所有微服务端点，逐一执行 HTTP 健康检查并汇总。
     *
     * @param clientId 客户端唯一标识
     * @param config   客户端配置，从中获取微服务端点列表和 SSL 校验开关
     * @return 包含所有服务健康检查结果的 {@link MicroserviceMetrics}
     */
    public MicroserviceMetrics collect(String clientId, ClientConfig config) {
        MicroserviceMetrics metrics = new MicroserviceMetrics();
        metrics.setClientId(clientId);
        metrics.setCollectTime(LocalDateTime.now());

        List<ClientConfig.ServiceEndpoint> endpoints = config.getMicroservices();
        List<MicroserviceMetrics.ServiceInfo> services = new ArrayList<>();
        int healthy = 0, unhealthy = 0;

        for (ClientConfig.ServiceEndpoint ep : endpoints) {
            MicroserviceMetrics.ServiceInfo info = new MicroserviceMetrics.ServiceInfo();
            info.setServiceName(ep.getName());
            info.setServiceUrl(ep.getHealthUrl());
            info.setPort(ep.getPort());
            info.setLastCheckTime(LocalDateTime.now());

            // 发起 HTTP/HTTPS 健康检查
            HealthCheckResult r = doHealthCheck(ep.getHealthUrl(), config.isMicroserviceSslVerify());
            info.setHealthStatus(r.up ? "UP" : "DOWN");
            info.setResponseTimeMs(r.responseMs);
            info.setErrorMessage(r.error);

            if (r.up) healthy++; else unhealthy++;
            services.add(info);
        }

        metrics.setTotalServices(endpoints.size());
        metrics.setHealthyServices(healthy);
        metrics.setUnhealthyServices(unhealthy);
        metrics.setServices(services);

        return metrics;
    }

    /**
     * 对单个 URL 发起 HTTP GET 请求以判断服务健康状态。
     * <p>
     * 对于 HTTPS URL，根据 {@code sslVerify} 参数决定是否跳过证书校验。
     * </p>
     *
     * @param url       健康检查 URL
     * @param sslVerify 是否校验 SSL 证书
     * @return 检查结果（是否健康、耗时、错误信息）
     */
    private HealthCheckResult doHealthCheck(String url, boolean sslVerify) {
        HttpURLConnection conn = null;
        try {
            long start = System.currentTimeMillis();
            conn = (HttpURLConnection) new URL(url).openConnection();

            // HTTPS 不校验证书（内网自签名场景）
            if (url.startsWith("https://") && !sslVerify && conn instanceof HttpsURLConnection) {
                HttpsURLConnection httpsConn = (HttpsURLConnection) conn;
                httpsConn.setSSLSocketFactory(getTrustAllFactory());
                httpsConn.setHostnameVerifier((host, session) -> true);
            }

            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            return new HealthCheckResult(code >= 200 && code < 400, System.currentTimeMillis() - start, null);
        } catch (IOException e) {
            log.debug("微服务健康检查失败 [{}]: {}", url, e.getMessage());
            return new HealthCheckResult(false, -1, e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * 获取信任所有证书的 SSLSocketFactory（双重检查锁，懒加载）。
     * <p>仅在 {@code microserviceSslVerify=false} 时调用，创建后全局复用。</p>
     */
    private static SSLSocketFactory getTrustAllFactory() {
        if (trustAllFactory == null) {
            synchronized (MicroserviceCollector.class) {
                if (trustAllFactory == null) {
                    try {
                        TrustManager[] trustAll = new TrustManager[] {
                            new X509TrustManager() {
                                public void checkClientTrusted(X509Certificate[] c, String a) {}
                                public void checkServerTrusted(X509Certificate[] c, String a) {}
                                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                            }
                        };
                        SSLContext ctx = SSLContext.getInstance("TLS");
                        ctx.init(null, trustAll, new java.security.SecureRandom());
                        trustAllFactory = ctx.getSocketFactory();
                    } catch (NoSuchAlgorithmException | KeyManagementException e) {
                        log.error("初始化 trust-all SSLContext 失败", e);
                    }
                }
            }
        }
        return trustAllFactory;
    }

    /**
     * 健康检查结果 —— 内部 POJO，封装一次 HTTP 检查的三个关键信息。
     */
    private static class HealthCheckResult {
        final boolean up;
        final long responseMs;
        final String error;

        HealthCheckResult(boolean up, long responseMs, String error) {
            this.up = up;
            this.responseMs = responseMs;
            this.error = error;
        }
    }
}
