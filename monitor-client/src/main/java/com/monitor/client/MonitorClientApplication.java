package com.monitor.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 监控采集客户端 —— 启动入口。
 * <p>
 * 部署在各台需要监控的服务器上，负责采集本机的：
 * <ul>
 *   <li>系统指标（CPU/内存/磁盘/网络/JVM）</li>
 *   <li>Docker 容器状态与镜像统计</li>
 *   <li>微服务健康检查结果</li>
 * </ul>
 * 采集数据通过 WebSocket 实时上报给监控服务端。
 * </p>
 *
 * <h3>启动参数示例</h3>
 * <pre>
 * java -jar monitor-client.jar \
 *   --monitor.client.server-url=ws://192.168.1.100:8080/ws/client \
 *   --monitor.client.docker-enabled=true \
 *   --monitor.client.microservice-enabled=true
 * </pre>
 *
 * <h3>关键注解</h3>
 * <ul>
 *   <li>{@code @SpringBootApplication} —— 启用自动配置与组件扫描</li>
 *   <li>{@code @EnableScheduling} —— 启用定时任务支撑周期性采集调度</li>
 * </ul>
 *
 * @author csa
 * @see com.monitor.client.scheduler.CollectScheduler
 */
@Slf4j
@SpringBootApplication
@EnableScheduling
public class MonitorClientApplication {

    /**
     * 客户端启动入口。
     *
     * @param args 命令行参数，可通过 {@code --monitor.client.xxx} 覆盖默认配置
     */
    public static void main(String[] args) {
        log.info("监控客户端正在启动...");
        SpringApplication.run(MonitorClientApplication.class, args);
    }
}
