package com.monitor.common.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 微服务指标数据模型 —— 封装一轮微服务健康检查的结果。
 * <p>
 * 客户端通过 HTTP GET 请求各微服务的健康检查端点（如 {@code /actuator/health}），
 * 根据 HTTP 状态码判断服务是否健康（2xx/3xx 为 UP，其余为 DOWN）。
 * 同时记录每次检查的响应时间（毫秒），用于监控服务品质。
 * </p>
 *
 * <h3>主要指标</h3>
 * <ul>
 *   <li>概览：服务总数、健康数、异常数</li>
 *   <li>详情：每个服务的名称、地址、端口、健康状态、响应时间、错误信息</li>
 * </ul>
 *
 * @author csa
 * @see SystemMetrics
 * @see DockerMetrics
 */
@Data
public class MicroserviceMetrics {

    // ==================== 基础标识字段 ====================

    /** 采集客户端唯一标识 */
    private String clientId;

    /** 指标采集时间，格式 yyyy-MM-dd HH:mm:ss */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime collectTime;

    // ==================== 概览指标 ====================

    /** 被监控的微服务总数 */
    private int totalServices = 0;

    /** 健康服务数量（HTTP 状态码 2xx/3xx） */
    private int healthyServices = 0;

    /** 异常服务数量（连接超时/HTTP 错误/IO 异常） */
    private int unhealthyServices = 0;

    // ==================== 服务详情列表 ====================

    /** 所有微服务的健康检查详情列表 */
    private List<ServiceInfo> services;

    /**
     * 单个微服务健康信息 —— 描述一次健康检查的完整结果。
     *
     * @author csa
     */
    @Data
    public static class ServiceInfo {

        /** 微服务名称（自定义标识，如 "user-service"） */
        private String serviceName;

        /** 微服务健康检查地址（完整 URL） */
        private String serviceUrl;

        /**
         * 健康状态：
         * <ul>
         *   <li>UP    —— 服务正常，HTTP 返回 2xx/3xx</li>
         *   <li>DOWN  —— 服务不可达或返回 4xx/5xx</li>
         * </ul>
         */
        private String healthStatus;

        /** 健康检查请求的响应时间，单位毫秒。连接失败时为 -1 */
        private long responseTimeMs = 0;

        /** 微服务暴露的端口号 */
        private int port = 0;

        /** 最后一次健康检查的时间 */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime lastCheckTime;

        /** 健康检查失败时的错误信息（连接超时、连接被拒等），正常时为 null */
        private String errorMessage;
    }
}
