package com.monitor.server.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.monitor.common.model.ServerMessage;
import com.monitor.server.service.MetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 前端 WebSocket 处理器 —— 管理浏览器仪表盘的 WebSocket 连接，并负责推送聚合后的指标数据。
 * <p>
 * 核心流程：
 * <ol>
 *   <li>浏览器连接时加入 {@code frontendSessions} 集合，并立即推送一次当前数据</li>
 *   <li>{@link com.monitor.server.scheduler.PushScheduler} 每 3 秒调用 {@link #pushToFrontend()} 推送</li>
 *   <li>推送时从 {@link MetricsService} 构建聚合快照，序列化为 JSON 广播给所有前端连接</li>
 *   <li>连接断开时从集合中移除，推送时自动清理已关闭的会话</li>
 * </ol>
 * 使用 {@link CopyOnWriteArraySet} 保证遍历时的线程安全。
 * </p>
 *
 * @author csa
 * @see MetricsService
 * @see com.monitor.server.scheduler.PushScheduler
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FrontendWsHandler extends TextWebSocketHandler {

    /** 指标数据存储服务 */
    private final MetricsService metricsService;

    /** JSON 序列化器 */
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    /** 所有已连接的前端 WebSocket 会话集合（线程安全） */
    private final Set<WebSocketSession> frontendSessions = new CopyOnWriteArraySet<>();

    /**
     * 浏览器连接建立 —— 加入会话集合并立即推送一次当前数据。
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        frontendSessions.add(session);
        log.info("前端连接 — sessionId={}, 当前前端连接数={}", session.getId(), frontendSessions.size());
        pushToFrontend(); // 立即推送当前快照
    }

    /**
     * 浏览器连接断开 —— 从会话集合中移除。
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        frontendSessions.remove(session);
        log.info("前端断开 — sessionId={}, 当前前端连接数={}", session.getId(), frontendSessions.size());
    }

    /**
     * 传输异常处理 —— 移除故障会话。
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable ex) {
        log.error("前端 WebSocket 传输异常: {}", ex.getMessage());
        frontendSessions.remove(session);
    }

    /**
     * 从 {@link MetricsService} 构建聚合快照并广播给所有已连接的前端页面。
     * <p>无前端连接时直接返回，不执行序列化以节省开销。</p>
     */
    public void pushToFrontend() {
        if (frontendSessions.isEmpty()) return;

        ServerMessage msg = metricsService.buildAggregatedMessage();
        String json;
        try {
            json = objectMapper.writeValueAsString(msg);
        } catch (JsonProcessingException e) {
            log.error("聚合消息序列化失败: {}", e.getMessage());
            return;
        }

        TextMessage text = new TextMessage(json);
        for (WebSocketSession session : frontendSessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(text);
                } catch (IOException e) {
                    log.error("推送到前端失败, sessionId={}: {}", session.getId(), e.getMessage());
                }
            } else {
                frontendSessions.remove(session);
            }
        }
    }
}
