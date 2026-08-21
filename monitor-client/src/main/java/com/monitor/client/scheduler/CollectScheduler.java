package com.monitor.client.scheduler;

import com.monitor.client.collector.DockerCollector;
import com.monitor.client.collector.MicroserviceCollector;
import com.monitor.client.collector.SystemCollector;
import com.monitor.client.config.ClientConfig;
import com.monitor.client.websocket.ClientWebSocketHandler;
import com.monitor.common.model.ClientMessage;
import com.monitor.common.model.DockerMetrics;
import com.monitor.common.model.MicroserviceMetrics;
import com.monitor.common.model.SystemMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 采集调度器 —— 客户端的核心控制器，在应用就绪后建立 WebSocket 连接并周期性调度三类采集器。
 * <p>
 * 初始化流程（{@link ApplicationReadyEvent} 回调）：
 * <ol>
 *   <li>确定客户端标识（优先级：配置 clientId &gt; 主机名 &gt; 随机 ID）</li>
 *   <li>建立到监控服务端的 WebSocket 长连接</li>
 * </ol>
 * 随后按固定间隔执行采集与上报：
 * <ul>
 *   <li>系统指标 —— 每 5 秒</li>
 *   <li>Docker 指标 —— 每 15 秒</li>
 *   <li>微服务指标 —— 每 10 秒</li>
 * </ul>
 * 每次采集前校验功能开关及 WebSocket 连接状态，避免无效调用。
 * </p>
 *
 * @author csa
 * @see SystemCollector
 * @see DockerCollector
 * @see MicroserviceCollector
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollectScheduler {

    /** 客户端全局配置 */
    private final ClientConfig config;

    /** 系统指标采集器（OSHI） */
    private final SystemCollector systemCollector;

    /** Docker 指标采集器（docker-java） */
    private final DockerCollector dockerCollector;

    /** 微服务健康检测采集器（HTTP GET） */
    private final MicroserviceCollector microserviceCollector;

    /** WebSocket 客户端，用于向服务端发送消息 */
    private final ClientWebSocketHandler wsHandler;

    /** 最终确定的客户端唯一标识（由 initClientId 设置） */
    private String clientId;

    /**
     * Spring 容器就绪后回调：初始化 clientId 并连接服务端。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        initClientId();
        wsHandler.connect(config.getServerUrl());
        log.info("监控客户端启动完成 — clientId={}, serverUrl={}", clientId, config.getServerUrl());
    }

    /**
     * 确定客户端唯一标识。优先级：配置值 &gt; 本机主机名 &gt; "unknown-{timestamp}"。
     */
    private void initClientId() {
        this.clientId = config.getClientId();
        if (clientId == null || clientId.isEmpty()) {
            try {
                clientId = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException e) {
                clientId = "unknown-" + System.currentTimeMillis();
            }
        }
    }

    /**
     * 定时采集系统指标并上报（每 5 秒）。
     * <p>前置条件：systemEnabled=true 且 WebSocket 已连接。</p>
     */
    @Scheduled(fixedDelay = 5000)
    public void collectSystemMetrics() {
        if (!config.isSystemEnabled() || !wsHandler.isConnected()) return;
        try {
            SystemMetrics m = systemCollector.collect(clientId);
            ClientMessage msg = new ClientMessage();
            msg.setType("SYSTEM");
            msg.setClientId(clientId);
            msg.setSystemMetrics(m);
            wsHandler.sendMessage(msg);
        } catch (RuntimeException e) {
            log.error("采集系统指标失败, clientId={}", clientId, e);
        }
    }

    /**
     * 定时采集 Docker 指标并上报（每 15 秒）。
     * <p>前置条件：dockerEnabled=true 且 WebSocket 已连接。</p>
     */
    @Scheduled(fixedDelay = 15000)
    public void collectDockerMetrics() {
        if (!config.isDockerEnabled() || !wsHandler.isConnected()) return;
        try {
            DockerMetrics m = dockerCollector.collect(clientId, config.getDockerHost());
            ClientMessage msg = new ClientMessage();
            msg.setType("DOCKER");
            msg.setClientId(clientId);
            msg.setDockerMetrics(m);
            wsHandler.sendMessage(msg);
        } catch (RuntimeException e) {
            log.error("采集 Docker 指标失败, clientId={}, dockerHost={}", clientId, config.getDockerHost(), e);
        }
    }

    /**
     * 定时采集微服务指标并上报（每 10 秒）。
     * <p>前置条件：microserviceEnabled=true 且 WebSocket 已连接。</p>
     */
    @Scheduled(fixedDelay = 10000)
    public void collectMicroserviceMetrics() {
        if (!config.isMicroserviceEnabled() || !wsHandler.isConnected()) return;
        try {
            MicroserviceMetrics m = microserviceCollector.collect(clientId, config);
            ClientMessage msg = new ClientMessage();
            msg.setType("MICROSERVICE");
            msg.setClientId(clientId);
            msg.setMicroserviceMetrics(m);
            wsHandler.sendMessage(msg);
        } catch (RuntimeException e) {
            log.error("采集微服务指标失败, clientId={}", clientId, e);
        }
    }
}
