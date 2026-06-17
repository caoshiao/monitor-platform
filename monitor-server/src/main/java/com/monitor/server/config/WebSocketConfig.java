package com.monitor.server.config;

import com.monitor.server.websocket.ClientWsHandler;
import com.monitor.server.websocket.FrontendWsHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 端点配置 —— 注册两个 WebSocket 路由。
 * <p>
 * <ul>
 *   <li>{@code /ws/client}    —— 采集客户端连接端点，由 {@link ClientWsHandler} 处理</li>
 *   <li>{@code /ws/frontend}  —— 浏览器仪表盘连接端点，由 {@link FrontendWsHandler} 处理</li>
 * </ul>
 * 允许跨域访问（生产环境建议收紧 allowedOrigins）。
 * </p>
 *
 * @author csa
 * @see ClientWsHandler
 * @see FrontendWsHandler
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    /** 采集客户端 WebSocket 处理器 */
    private final ClientWsHandler clientWsHandler;

    /** 前端页面 WebSocket 处理器 */
    private final FrontendWsHandler frontendWsHandler;

    /**
     * 注册 WebSocket 端点。
     *
     * @param registry Spring WebSocket 处理器注册表
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(clientWsHandler, "/ws/client")
                .setAllowedOrigins("*");
        registry.addHandler(frontendWsHandler, "/ws/frontend")
                .setAllowedOrigins("*");
    }
}
