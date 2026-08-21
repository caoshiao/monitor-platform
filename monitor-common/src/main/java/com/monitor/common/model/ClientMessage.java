package com.monitor.common.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户端→服务端 WebSocket 消息体 —— 采集端向服务端上报指标数据的统一封装。
 * <p>
 * 消息类型由 {@code type} 字段区分，对应携带不同类型的指标 payload：
 * <ul>
 *   <li>SYSTEM       —— {@link SystemMetrics}（CPU/内存/磁盘/网络/JVM）</li>
 *   <li>DOCKER       —— {@link DockerMetrics}（容器/镜像状态）</li>
 *   <li>MICROSERVICE —— {@link MicroserviceMetrics}（微服务健康检查）</li>
 * </ul>
 * 创建实例时 {@code timestamp} 自动填充为当前时间。
 * </p>
 *
 * @author csa
 * @see ServerMessage
 */
@Data
public class ClientMessage {

    // ==================== 消息头 ====================

    /**
     * 消息类型标识，取值范围：
     * <ul>
     *   <li>SYSTEM       —— 系统指标消息</li>
     *   <li>DOCKER       —— Docker 指标消息</li>
     *   <li>MICROSERVICE —— 微服务指标消息</li>
     * </ul>
     */
    private String type;

    /** 发送消息的客户端唯一标识 */
    private String clientId;

    /** 消息发送时间，格式 yyyy-MM-dd HH:mm:ss，创建时自动设置为当前时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp = LocalDateTime.now();

    // ==================== 消息体（按类型择一携带） ====================

    /** 系统指标数据（当 type="SYSTEM" 时有效） */
    private SystemMetrics systemMetrics;

    /** Docker 指标数据（当 type="DOCKER" 时有效） */
    private DockerMetrics dockerMetrics;

    /** 微服务指标数据（当 type="MICROSERVICE" 时有效） */
    private MicroserviceMetrics microserviceMetrics;

    /** HighGo/PostgreSQL 数据库指标（当 type="HIGHGO" 时有效） */
    private HighgoMetrics highgoMetrics;
}
