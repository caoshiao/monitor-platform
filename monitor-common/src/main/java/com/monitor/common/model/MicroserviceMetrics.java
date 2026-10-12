package com.monitor.common.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 外部系统指标数据模型 —— 封装一轮 TCP 与接口双探针检查结果。
 * <p>
 * 客户端分别检测目标主机端口连通性和 HTTP 接口可用性，再计算外部系统总体状态。
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

    /** 被监控的外部系统总数。 */
    private int totalServices = 0;

    /** 两项探针均正常的系统数量。 */
    private int healthyServices = 0;

    /** 至少一项探针异常的系统数量。 */
    private int unhealthyServices = 0;

    /** 仅一项探针异常的系统数量。 */
    private int degradedServices = 0;

    // ==================== 服务详情列表 ====================

    /** 所有外部系统的双探针详情列表。 */
    private List<ServiceInfo> services;

    /**
     * 单个外部系统健康信息，描述一次双探针检查的完整结果。
     *
     * @author csa
     */
    @Data
    public static class ServiceInfo {

        /** 外部系统名称（自定义标识，如“统一身份认证系统”）。 */
        private String serviceName;

        /** 外部系统接口探测地址（完整 URL）。 */
        private String serviceUrl;

        /** TCP 探测目标主机。 */
        private String host;

        /**
         * 健康状态：
         * <ul>
         *   <li>UP —— TCP 与接口探针均正常</li>
         *   <li>DEGRADED —— 仅一项探针正常</li>
         *   <li>DOWN —— 两项探针均异常</li>
         * </ul>
         */
        private String healthStatus;

        /** TCP 端口探针状态：UP 或 DOWN。 */
        private String networkStatus;

        /** 接口探针状态：UP 或 DOWN。 */
        private String interfaceStatus;

        /** TCP 连接耗时，单位毫秒。 */
        private long networkResponseTimeMs = -1;

        /** 接口返回的 HTTP 状态码；未获得响应时为 -1。 */
        private int httpStatusCode = -1;

        /** 健康检查请求的响应时间，单位毫秒。连接失败时为 -1 */
        private long responseTimeMs = 0;

        /** 外部系统 TCP 探测端口号。 */
        private int port = 0;

        /** 最后一次健康检查的时间 */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime lastCheckTime;

        /** 健康检查失败时的错误信息（连接超时、连接被拒等），正常时为 null */
        private String errorMessage;
    }
}
