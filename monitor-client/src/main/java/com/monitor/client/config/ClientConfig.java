package com.monitor.client.config;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端全局配置 —— 绑定 application.yml 中以 {@code monitor.client} 为前缀的配置项。
 * <p>
 * 支持按需启停三种监控模式：
 * <ul>
 *   <li>系统监控 —— CPU/内存/磁盘/网络/JVM（默认开启）</li>
 *   <li>Docker 监控 —— 容器状态与镜像统计（默认关闭，需配置 Docker Host）</li>
 *   <li>微服务监控 —— HTTP 健康检查（默认关闭，需配置端点列表）</li>
 * </ul>
 * 所有配置均支持命令行参数覆盖，如 {@code --monitor.client.docker-enabled=true}。
 * </p>
 *
 * @author csa
 * @see com.monitor.client.scheduler.CollectScheduler
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "monitor.client")
public class ClientConfig {

    // ==================== 基础配置 ====================

    /** 客户端唯一标识，留空时自动取主机名 */
    private String clientId;

    /** 服务端 WebSocket 地址，格式 ws://host:port/ws/client */
    private String serverUrl = "ws://localhost:8080/ws/client";

    // ==================== 功能开关 ====================

    /** 是否启用系统指标监控（CPU/内存/磁盘/网络/JVM），默认 true */
    private boolean systemEnabled = true;

    /** 是否启用 Docker 容器状态监控，默认 false */
    private boolean dockerEnabled = false;

    /** Docker 守护进程地址。Linux 默认 unix:///var/run/docker.sock；Windows 需开启 TCP 后填写 tcp://localhost:2375 */
    private String dockerHost = "unix:///var/run/docker.sock";

    /** 是否启用微服务健康检查，默认 false */
    private boolean microserviceEnabled = false;

    /** 是否校验微服务 HTTPS 证书，默认 false（内网监控通常跳过证书校验） */
    private boolean microserviceSslVerify = false;

    /** 需要监控的微服务健康检查端点列表 */
    private List<ServiceEndpoint> microservices = new ArrayList<>();

    /**
     * 单个微服务的健康检查端点配置。
     * <p>
     * 客户端将对 {@code healthUrl} 发起 HTTP GET 请求，
     * 若返回 2xx/3xx 则判定为 UP，否则判定为 DOWN。
     * </p>
     *
     * @author csa
     */
    @Data
    public static class ServiceEndpoint {

        /** 服务名称，自定义标识，如 "user-service" */
        private String name;

        /** 健康检查完整 URL，如 "http://localhost:8081/actuator/health" */
        private String healthUrl;

        /** 服务暴露端口 */
        private int port;
    }
}
