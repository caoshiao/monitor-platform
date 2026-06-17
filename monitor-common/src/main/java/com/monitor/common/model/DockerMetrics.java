package com.monitor.common.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Docker 指标数据模型 —— 封装 Docker 守护进程中容器与镜像的运行状态。
 * <p>
 * 通过 docker-java 库与 Docker Engine API 通信，获取容器列表（含所有状态）、
 * 镜像总数以及每个容器的端口映射信息。
 * 当采集失败时，{@code totalContainers} 会被设置为 -1 作为标记。
 * </p>
 *
 * <h3>主要指标</h3>
 * <ul>
 *   <li>概览：容器总数、运行中、已停止、异常、镜像总数</li>
 *   <li>详情：每个容器的 ID、名称、镜像、状态、CPU/内存、端口映射</li>
 * </ul>
 *
 * @author csa
 * @see SystemMetrics
 * @see MicroserviceMetrics
 */
@Data
public class DockerMetrics {

    // ==================== 基础标识字段 ====================

    /** 采集客户端唯一标识 */
    private String clientId;

    /** 指标采集时间，格式 yyyy-MM-dd HH:mm:ss */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime collectTime;

    // ==================== 概览指标 ====================

    /** 容器总数（含所有状态），取值为 -1 表示采集失败 */
    private int totalContainers = 0;

    /** 当前处于 running 状态的容器数量 */
    private int runningContainers = 0;

    /** 当前处于 exited 状态的容器数量 */
    private int stoppedContainers = 0;

    /** 当前处于非 running/exited 状态（异常）的容器数量 */
    private int unhealthyContainers = 0;

    /** Docker 镜像总数，取值为 -1 表示采集失败 */
    private int totalImages = 0;

    // ==================== 容器详情列表 ====================

    /** 所有容器的详细信息列表 */
    private List<ContainerInfo> containers;

    /**
     * 单个 Docker 容器信息 —— 描述一个容器的基本属性与资源占用。
     *
     * @author csa
     */
    @Data
    public static class ContainerInfo {

        /** 容器 ID（截取前 12 位显示） */
        private String containerId;

        /** 容器名称（去掉前缀 "/"） */
        private String name;

        /** 容器使用的镜像名称（含标签） */
        private String image;

        /** 容器状态：running / exited / 其他异常状态 */
        private String status;

        /** 容器运行时长描述（例如 "Up 2 hours"） */
        private String uptime;

        /** CPU 使用率，范围 [0, 100]，仅在 stats 可用时填充，否则为 0 */
        private double cpuUsage = 0.0;

        /** 内存使用量，单位 MB，仅在 stats 可用时填充，否则为 0 */
        private double memoryUsageMB = 0.0;

        /** 端口映射描述（例如 "8080->8080, 3306->3306"） */
        private String ports;
    }
}
