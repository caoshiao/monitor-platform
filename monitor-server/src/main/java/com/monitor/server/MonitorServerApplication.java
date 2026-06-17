package com.monitor.server;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 监控服务端 —— 启动入口。
 * <p>
 * 核心职责：
 * <ul>
 *   <li>通过 WebSocket（/ws/client）接收各采集客户端上报的指标数据</li>
 *   <li>汇总并缓存所有在线客户端的指标快照</li>
 *   <li>通过 WebSocket（/ws/frontend）定时推送给浏览器仪表盘</li>
 *   <li>提供静态资源服务（监控仪表盘 HTML 页面）</li>
 * </ul>
 * 默认监听端口 8080，启动后浏览器访问 http://&lt;server-ip&gt;:8080 即可打开仪表盘。
 * </p>
 *
 * @author csa
 * @see com.monitor.server.websocket.FrontendWsHandler
 */
@Slf4j
@SpringBootApplication
@EnableScheduling
public class MonitorServerApplication {

    /**
     * 服务端启动入口。
     *
     * @param args 命令行参数，可通过 {@code --server.port=9090} 覆盖端口
     */
    public static void main(String[] args) {
        log.info("监控服务端正在启动...");
        SpringApplication.run(MonitorServerApplication.class, args);
    }
}
