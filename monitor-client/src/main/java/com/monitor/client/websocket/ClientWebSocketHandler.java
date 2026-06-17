package com.monitor.client.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.monitor.common.model.ClientMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 客户端 WebSocket 处理器 —— 与服务端建立长连接，负责序列化并发送采集到的指标消息。
 * <p>
 * 核心能力：
 * <ul>
 *   <li>连接管理 —— 启动时建立连接，断开后 10 秒自动重连</li>
 *   <li>消息发送 —— 将 {@link ClientMessage} 序列化为 JSON 通过 WebSocket 发送</li>
 *   <li>线程安全 —— sendMessage 使用 synchronized 保证并发安全</li>
 *   <li>资源释放 —— 应用关闭时通过 @PreDestroy 停止重连线程和连接</li>
 * </ul>
 * </p>
 *
 * @author csa
 * @see com.monitor.client.scheduler.CollectScheduler
 */
@Slf4j
@Component
public class ClientWebSocketHandler implements WebSocketHandler {

    /** JSON 序列化器，注册了 Java 8 时间模块以支持 LocalDateTime */
    private final ObjectMapper objectMapper;

    /** 当前活跃的 WebSocket 会话 */
    private WebSocketSession session;

    /** Spring WebSocket 连接管理器 */
    private WebSocketConnectionManager connectionManager;

    /** 断线重连调度线程池（单线程、守护） */
    private final ScheduledExecutorService reconnectExecutor;

    /** 当前目标服务端 URL */
    private String serverUrl;

    /** 连接状态标记 */
    private volatile boolean connected = false;

    /** 应用关闭中标记，停止重连调度 */
    private volatile boolean shuttingDown = false;

    /**
     * 构造处理器，初始化 JSON 序列化器和重连线程池。
     */
    public ClientWebSocketHandler() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ws-reconnect");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 连接到指定的服务端 WebSocket 地址（幂等：相同 URL 且已连接则跳过）。
     *
     * @param serverUrl 服务端地址，如 ws://192.168.1.100:8080/ws/client
     */
    public synchronized void connect(String serverUrl) {
        if (this.serverUrl != null && this.serverUrl.equals(serverUrl) && connected) return;
        this.serverUrl = serverUrl;
        doConnect();
    }

    /** 执行实际的连接操作：先断开旧连接，再创建新连接 */
    private void doConnect() {
        if (connectionManager != null) {
            try { connectionManager.stop(); } catch (Exception ignored) {}
        }
        StandardWebSocketClient client = new StandardWebSocketClient();
        connectionManager = new WebSocketConnectionManager(client, this, serverUrl);
        connectionManager.setAutoStartup(true);
        try {
            connectionManager.start();
        } catch (Exception e) {
            log.error("连接服务端失败 [{}]: {}", serverUrl, e.getMessage());
            scheduleReconnect();
        }
    }

    /** 10 秒后自动重连（应用关闭中则跳过） */
    private void scheduleReconnect() {
        if (shuttingDown || reconnectExecutor.isShutdown() || reconnectExecutor.isTerminated()) {
            return;
        }
        try {
            reconnectExecutor.schedule(() -> {
                if (!connected && !shuttingDown) {
                    log.info("尝试重连服务端: {}", serverUrl);
                    doConnect();
                }
            }, 10, TimeUnit.SECONDS);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            log.debug("重连调度被拒绝（应用关闭中），忽略");
        }
    }

    /**
     * 发送采集消息到服务端（JSON 序列化后发送）。
     * 连接断开时直接丢弃消息，由采集调度器自行判断下次是否发送。
     *
     * @param message 待发送的客户端消息
     */
    public synchronized void sendMessage(ClientMessage message) {
        if (session == null || !session.isOpen()) {
            log.warn("WebSocket 未连接，消息丢弃");
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(json));
        } catch (JsonProcessingException e) {
            log.error("消息序列化失败: {}", e.getMessage());
        } catch (IOException e) {
            log.error("消息发送失败: {}", e.getMessage());
        }
    }

    /** 查询当前是否已连接 */
    public boolean isConnected() {
        return connected && session != null && session.isOpen();
    }

    // ========== WebSocketHandler 回调 ==========

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("已连接到服务端: {}, sessionId={}", serverUrl, session.getId());
        this.session = session;
        this.connected = true;
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
        if (message instanceof TextMessage) {
            log.debug("收到服务端消息: {}", ((TextMessage) message).getPayload());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable ex) {
        log.error("WebSocket 传输异常: {}", ex.getMessage());
        this.connected = false;
        scheduleReconnect();
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.warn("WebSocket 连接关闭 — code={}, reason={}", status.getCode(), status.getReason());
        this.connected = false;
        this.session = null;
        scheduleReconnect();
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    /**
     * 应用关闭时释放资源。
     * 先标记关闭状态防止重连，再断开连接，最后关闭线程池。
     */
    @PreDestroy
    public void destroy() {
        shuttingDown = true;
        this.connected = false;
        if (connectionManager != null) {
            try { connectionManager.stop(); } catch (Exception ignored) {}
        }
        reconnectExecutor.shutdown();
        try { reconnectExecutor.awaitTermination(2, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
    }
}
