package com.monitor.client.collector;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import com.monitor.common.model.DockerMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Docker 指标采集器 —— 通过 docker-java 库与 Docker Engine API 通信。
 * <p>
 * 支持两种连接方式：
 * <ul>
 *   <li>Unix Socket（Linux 默认）—— unix:///var/run/docker.sock</li>
 *   <li>TCP（Windows 或远程）—— tcp://localhost:2375</li>
 * </ul>
 * 采集失败时会将 {@code totalContainers} 和 {@code totalImages} 置为 -1 作为异常标记，
 * 前端可据此识别并展示"采集失败"状态。
 * </p>
 *
 * <h3>采集内容</h3>
 * <ul>
 *   <li>容器概览：总数、运行中、已停止、异常数</li>
 *   <li>容器详情：ID、名称、镜像、状态、运行时长、端口映射</li>
 *   <li>镜像总数</li>
 * </ul>
 *
 * @author csa
 * @see DockerMetrics
 */
@Slf4j
@Component
public class DockerCollector {

    /**
     * 连接 Docker 守护进程并采集容器与镜像信息。
     *
     * @param clientId   客户端唯一标识
     * @param dockerHost Docker 主机地址，如 {@code unix:///var/run/docker.sock} 或 {@code tcp://localhost:2375}
     * @return 包含容器列表和镜像统计的 {@link DockerMetrics}；采集失败时关键字段为 -1
     */
    public DockerMetrics collect(String clientId, String dockerHost) {
        DockerMetrics metrics = new DockerMetrics();
        metrics.setClientId(clientId);
        metrics.setCollectTime(LocalDateTime.now());

        DockerClient dockerClient = null;
        try {
            // 构建客户端配置
            DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                    .withDockerHost(dockerHost).build();

            // 使用 Apache HttpClient5 作为传输层（连接超时 5s，响应超时 10s）
            DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                    .dockerHost(config.getDockerHost())
                    .maxConnections(10)
                    .connectionTimeout(Duration.ofSeconds(5))
                    .responseTimeout(Duration.ofSeconds(10))
                    .build();

            dockerClient = DockerClientImpl.getInstance(config, httpClient);
            dockerClient.pingCmd().exec(); // 验证连接

            // ---- 容器列表（含所有状态） ----
            List<Container> containers = dockerClient.listContainersCmd().withShowAll(true).exec();
            int running = 0, stopped = 0, unhealthy = 0;
            List<DockerMetrics.ContainerInfo> infos = new ArrayList<>();

            for (Container c : containers) {
                DockerMetrics.ContainerInfo info = new DockerMetrics.ContainerInfo();
                info.setContainerId(c.getId() != null ? c.getId().substring(0, Math.min(12, c.getId().length())) : "?");
                info.setName(c.getNames() != null && c.getNames().length > 0 ? c.getNames()[0].replaceFirst("^/", "") : "?");
                info.setImage(c.getImage());
                info.setStatus(c.getState());
                info.setUptime(c.getStatus());

                // 分类统计
                if ("running".equalsIgnoreCase(c.getState())) running++;
                else if ("exited".equalsIgnoreCase(c.getState())) stopped++;
                else unhealthy++;

                // 端口映射
                if (c.getPorts() != null && c.getPorts().length > 0) {
                    StringBuilder sb = new StringBuilder();
                    for (com.github.dockerjava.api.model.ContainerPort p : c.getPorts()) {
                        if (sb.length() > 0) sb.append(", ");
                        sb.append(p.getPublicPort() != null ? p.getPublicPort() + "->" + p.getPrivatePort() : p.getPrivatePort());
                    }
                    info.setPorts(sb.toString());
                }
                infos.add(info);
            }

            metrics.setTotalContainers(containers.size());
            metrics.setRunningContainers(running);
            metrics.setStoppedContainers(stopped);
            metrics.setUnhealthyContainers(unhealthy);
            metrics.setContainers(infos);

            // ---- 镜像总数 ----
            List<Image> images = dockerClient.listImagesCmd().exec();
            metrics.setTotalImages(images.size());

        } catch (RuntimeException e) {
            log.error("Docker 采集失败, dockerHost={}", dockerHost, e);
            metrics.setTotalContainers(-1);  // -1 = 采集失败标记
            metrics.setTotalImages(-1);
        } finally {
            if (dockerClient != null) {
                try {
                    dockerClient.close();
                } catch (Exception e) {
                    log.warn("关闭 Docker 客户端失败", e);
                }
            }
        }
        return metrics;
    }
}
