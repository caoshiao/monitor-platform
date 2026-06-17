package com.monitor.common.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统指标数据模型 —— 封装一次采集周期内服务器的运行状态。
 * <p>
 * 覆盖维度：CPU、内存、磁盘、网络、系统负载、JVM 堆内存。
 * 所有百分比字段取值范围为 [0, 100]，保留两位小数精度。
 * </p>
 *
 * <h3>字段分组</h3>
 * <ul>
 *   <li>CPU：使用率（两次 ticks 差值）、逻辑核心数</li>
 *   <li>内存：总量/已用(GB)、使用率</li>
 *   <li>磁盘：所有分区累计总量/已用(GB)、使用率</li>
 *   <li>网络：基于两次采集差值计算的接收/发送速率(KB/s)</li>
 *   <li>系统负载：运行时间(分钟)、1 分钟平均负载</li>
 *   <li>JVM：堆内存使用率、非堆内存使用率</li>
 * </ul>
 *
 * @author csa
 * @see DockerMetrics
 * @see MicroserviceMetrics
 */
@Data
public class SystemMetrics {

    // ==================== 基础标识字段 ====================

    /** 采集客户端唯一标识（通常为主机名或自定义名称） */
    private String clientId;

    /** 主机名，由采集端通过 {@code InetAddress.getLocalHost().getHostName()} 获取 */
    private String hostname;

    /** 指标采集时间，格式 yyyy-MM-dd HH:mm:ss */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime collectTime;

    // ==================== CPU 指标 ====================

    /** CPU 总体使用率，范围 [0, 100]（通过相邻两次 tick 差值计算，避免瞬时抖动） */
    private double cpuUsage = 0.0;

    /** CPU 逻辑核心数 */
    private int cpuCores = 0;

    // ==================== 内存指标 ====================

    /** 物理内存总量，单位 GB */
    private double totalMemoryGB = 0.0;

    /** 已使用的物理内存，单位 GB */
    private double usedMemoryGB = 0.0;

    /** 物理内存使用率（已用/总量），范围 [0, 100] */
    private double memoryUsage = 0.0;

    // ==================== 磁盘指标 ====================

    /** 磁盘总容量（所有分区累加），单位 GB */
    private double totalDiskGB = 0.0;

    /** 已用磁盘容量（所有分区累加），单位 GB */
    private double usedDiskGB = 0.0;

    /** 磁盘使用率（已用/总量），范围 [0, 100] */
    private double diskUsage = 0.0;

    // ==================== 网络指标 ====================

    /** 网络接收速率，单位 KB/s（基于两次采集间的字节差值 ÷ 时间间隔） */
    private double networkRxKBs = 0.0;

    /** 网络发送速率，单位 KB/s（基于两次采集间的字节差值 ÷ 时间间隔） */
    private double networkTxKBs = 0.0;

    // ==================== 系统负载指标 ====================

    /** 系统持续运行时间，单位分钟 */
    private long uptimeMinutes = 0;

    /** 系统 1 分钟平均负载（Linux 下为 /proc/loadavg，Windows 下为估算值） */
    private double loadAverage = 0.0;

    // ==================== JVM 指标 ====================

    /** 当前 JVM 堆内存使用率，范围 [0, 100]（used / max） */
    private double jvmHeapUsage = 0.0;

    /** 当前 JVM 非堆内存使用率（预留字段，OSHI 暂不直接提供非堆数据，默认 0） */
    private double jvmNonHeapUsage = 0.0;
}
