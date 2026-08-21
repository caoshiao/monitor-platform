package com.monitor.common.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 服务端→前端 WebSocket 消息体 —— 汇总所有客户端指标后推送给浏览器仪表盘的统一封装。
 * <p>
 * 消息类型由 {@code type} 字段区分：
 * <ul>
 *   <li>AGGREGATED —— 聚合快照，包含所有在线客户端的 {@link ClientSnapshot} 列表（仪表盘主模式）</li>
 *   <li>SYSTEM / DOCKER / MICROSERVICE —— 单客户端最新指标透传</li>
 * </ul>
 * 创建实例时 {@code timestamp} 自动填充为当前时间。
 * </p>
 *
 * @author csa
 * @see ClientMessage
 */
@Data
public class ServerMessage {

    // ==================== 消息头 ====================

    /**
     * 消息类型：
     * <ul>
     *   <li>AGGREGATED   —— 聚合快照（推荐，前端仪表盘使用）</li>
     *   <li>SYSTEM       —— 系统指标透传</li>
     *   <li>DOCKER       —— Docker 指标透传</li>
     *   <li>MICROSERVICE —— 微服务指标透传</li>
     * </ul>
     */
    private String type;

    /** 消息发送时间，格式 yyyy-MM-dd HH:mm:ss，创建时自动设置为当前时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp = LocalDateTime.now();

    // ==================== 在线状态 ====================

    /** 当前在线的客户端 ID 列表 */
    private List<String> onlineClients;

    // ==================== 单客户端最新指标（透传模式） ====================

    /** 最近一次收到的系统指标 */
    private SystemMetrics latestSystemMetrics;

    /** 最近一次收到的 Docker 指标 */
    private DockerMetrics latestDockerMetrics;

    /** 最近一次收到的微服务指标 */
    private MicroserviceMetrics latestMicroserviceMetrics;

    /** 最近一次收到的 HighGo 数据库指标 */
    private HighgoMetrics latestHighgoMetrics;

    // ==================== 聚合快照（AGGREGATED 模式） ====================

    /** 所有在线客户端的汇总快照列表（当 type="AGGREGATED" 时填充） */
    private List<ClientSnapshot> clientSnapshots;

    /**
     * 单个客户端快照 —— 聚合视图中每个在线客户端的状态摘要。
     * <p>
     * 包含该客户端上报的所有类型指标的最新值，方便仪表盘一次展示全部信息。
     * </p>
     *
     * @author csa
     */
    @Data
    public static class ClientSnapshot {

        /** 客户端唯一标识 */
        private String clientId;

        /** 主机名 */
        private String hostname;

        /**
         * 连接状态：
         * <ul>
         *   <li>ONLINE  —— 当前在线，正常接收数据</li>
         *   <li>OFFLINE —— 已断开或超时未上报</li>
         * </ul>
         */
        private String status;

        /** 该客户端最新的系统指标快照 */
        private SystemMetrics systemMetrics;

        /** 该客户端最新的 Docker 指标快照 */
        private DockerMetrics dockerMetrics;

        /** 该客户端最新的微服务指标快照 */
        private MicroserviceMetrics microserviceMetrics;

        private HighgoMetrics highgoMetrics;
    }
}
