package com.monitor.server.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.monitor.common.model.ClientMessage;
import com.monitor.server.service.MetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端 WebSocket 处理器（服务端视角）—— 接收各采集客户端上报的指标数据并写入 {@link MetricsService}。
 * <p>
 * 每当收到一条客户端消息（JSON 格式）：
 * <ol>
 *   <li>反序列化为 {@link ClientMessage}</li>
 *   <li>根据 type 字段分别写入 systemMap / dockerMap / microserviceMap</li>
 *   <li>标记该客户端在线</li>
 * </ol>
 * 客户端断开时自动从在线列表中移除。
 * </p>
 *
 * @author csa
 * @see MetricsService
 * @see FrontendWsHandler
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClientWsHandler extends TextWebSocketHandler {

    /** 指标存储服务 */
    private final MetricsService metricsService;

    /** JSON 序列化器 */
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    /** sessionId → clientId 映射，用于断连时定位客户端 */
    private final Map<String, String> sessionClientMap = new ConcurrentHashMap<>();

    /**
     * 客户端连接建立。
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("采集客户端连接 — sessionId={}, remote={}", session.getId(), session.getRemoteAddress());
    }

    /**
     * 处理客户端发来的文本消息（JSON）。
     * <p>按 type 字段路由到对应的 MetricsService 更新方法。</p>
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            ClientMessage msg = objectMapper.readValue(message.getPayload(), ClientMessage.class);
            String clientId = msg.getClientId();
            if (clientId == null) {
                log.warn("收到缺少 clientId 的消息，已忽略");
                return;
            }
            sessionClientMap.put(session.getId(), clientId);

            switch (msg.getType()) {
                case "SYSTEM":
                    if (msg.getSystemMetrics() != null) {
                        metricsService.updateSystemMetrics(clientId, msg.getSystemMetrics());
                        metricsService.markOnline(clientId);
                    }
                    break;
                case "DOCKER":
                    if (msg.getDockerMetrics() != null) {
                        metricsService.updateDockerMetrics(clientId, msg.getDockerMetrics());
                        metricsService.markOnline(clientId);
                    }
                    break;
                case "MICROSERVICE":
                    if (msg.getMicroserviceMetrics() != null) {
                        metricsService.updateMicroserviceMetrics(clientId, msg.getMicroserviceMetrics());
                        metricsService.markOnline(clientId);
                    }
                    break;
                case "HIGHGO":
                    if (msg.getHighgoMetrics() != null) {
                        metricsService.updateHighgoMetrics(clientId, msg.getHighgoMetrics());
                        metricsService.markOnline(clientId);
                    }
                    break;
                default:
                    log.warn("未知消息类型: {}", msg.getType());
            }
        } catch (JsonProcessingException e) {
            log.error("客户端消息解析失败: {}", e.getMessage());
        }
    }

    /**
     * 客户端连接关闭 —— 标记离线。
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String clientId = sessionClientMap.remove(session.getId());
        if (clientId != null) metricsService.markOffline(clientId);
        log.warn("采集客户端断开 — sessionId={}, clientId={}", session.getId(), clientId);
    }

    /**
     * 传输异常处理。
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable ex) {
        log.error("客户端 WebSocket 传输异常: {}", ex.getMessage());
    }
}
