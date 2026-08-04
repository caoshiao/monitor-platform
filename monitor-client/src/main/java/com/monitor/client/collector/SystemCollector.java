package com.monitor.client.collector;

import com.monitor.common.model.SystemMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统指标采集器 —— 基于 OSHI 库获取底层硬件与操作系统运行指标。
 * <p>
 * 采集维度：
 * <ul>
 *   <li>CPU —— 通过两次 ticks 差值计算真实使用率，获取逻辑核心数</li>
 *   <li>内存 —— 总量/已用(GB)、使用率百分比</li>
 *   <li>磁盘 —— 遍历所有挂载分区，累加总量与已用量</li>
 *   <li>网络 —— 基于两次采集字节差值 ÷ 时间间隔，计算 KB/s 速率</li>
 *   <li>系统负载 —— 运行时间(分钟)、1 分钟平均负载（Linux 取自 /proc/loadavg）</li>
 *   <li>JVM —— 堆内存使用率（used / max）</li>
 * </ul>
 * 首次调用 {@link #collect(String)} 时，网络速率可能不准确（基线刚初始化）；
 * 后续调用将基于实际间隔计算真实速率。
 * </p>
 *
 * @author csa
 * @see oshi.SystemInfo
 */
@Slf4j
@Component
public class SystemCollector {

    /** OSHI 系统信息入口 */
    private final SystemInfo systemInfo;

    /** 硬件抽象层（CPU、内存、网络设备） */
    private final HardwareAbstractionLayer hardware;

    /** 操作系统抽象层（磁盘、运行时间） */
    private final OperatingSystem os;

    /** 上一次采集的 CPU ticks 快照，用于计算使用率增量 */
    private long[] prevTicks;

    /** 上一次网络字节快照（接收） */
    private long prevRxBytes;

    /** 上一次网络字节快照（发送） */
    private long prevTxBytes;

    /** 上一次网络采集的时间戳(ms)，用于计算速率分母 */
    private long prevCollectTime;

    /**
     * 构造采集器并捕获初始基准快照。
     * 初始化网络字节计数与 CPU ticks，确保后续采集可以计算差分。
     */
    public SystemCollector() {
        this.systemInfo = new SystemInfo();
        this.hardware = systemInfo.getHardware();
        this.os = systemInfo.getOperatingSystem();
        this.prevTicks = hardware.getProcessor().getSystemCpuLoadTicks();
        initNetworkBaseline();
    }

    /**
     * 初始化网络字节基线 —— 遍历所有网络接口累加收发字节数，记录当前时间戳。
     */
    private void initNetworkBaseline() {
        List<NetworkIF> networks = hardware.getNetworkIFs();
        long totalRx = 0, totalTx = 0;
        for (NetworkIF net : networks) {
            totalRx += net.getBytesRecv();
            totalTx += net.getBytesSent();
        }
        this.prevRxBytes = totalRx;
        this.prevTxBytes = totalTx;
        this.prevCollectTime = System.currentTimeMillis();
    }

    /**
     * 执行一次完整的系统指标采集。
     *
     * @param clientId 客户端唯一标识，会写入返回的 {@link SystemMetrics} 中
     * @return 填充了当前时刻所有系统指标的 {@link SystemMetrics} 对象
     */
    public SystemMetrics collect(String clientId) {
        SystemMetrics metrics = new SystemMetrics();
        metrics.setClientId(clientId);
        metrics.setCollectTime(LocalDateTime.now());

        // IP 地址（失败降级为 "unknown"）
        try {
            metrics.setHostname(InetAddress.getLocalHost().getHostAddress());
        } catch (UnknownHostException e) {
            metrics.setHostname("unknown");
            log.debug("获取 IP 地址失败", e);
        }

        // === CPU ===
        CentralProcessor processor = hardware.getProcessor();
        int cpuCores = processor.getLogicalProcessorCount();
        metrics.setCpuCores(cpuCores);
        double cpuUsage = processor.getSystemCpuLoadBetweenTicks(prevTicks);
        prevTicks = processor.getSystemCpuLoadTicks();  // 更新基线
        metrics.setCpuUsage(Math.round(cpuUsage * 10000.0) / 100.0);

        // === 内存 ===
        GlobalMemory memory = hardware.getMemory();
        long totalMem = memory.getTotal();
        long availableMem = memory.getAvailable();
        long usedMem = totalMem - availableMem;
        metrics.setTotalMemoryGB(Math.round(totalMem / (1024.0 * 1024.0 * 1024.0) * 100.0) / 100.0);
        metrics.setUsedMemoryGB(Math.round(usedMem / (1024.0 * 1024.0 * 1024.0) * 100.0) / 100.0);
        metrics.setMemoryUsage(Math.round((double) usedMem / totalMem * 10000.0) / 100.0);

        // === 磁盘 ===
        FileSystem fileSystem = os.getFileSystem();
        List<OSFileStore> fileStores = fileSystem.getFileStores();
        long totalDisk = 0, usedDisk = 0;
        for (OSFileStore store : fileStores) {
            totalDisk += store.getTotalSpace();
            usedDisk += (store.getTotalSpace() - store.getUsableSpace());
        }
        metrics.setTotalDiskGB(Math.round(totalDisk / (1024.0 * 1024.0 * 1024.0) * 100.0) / 100.0);
        metrics.setUsedDiskGB(Math.round(usedDisk / (1024.0 * 1024.0 * 1024.0) * 100.0) / 100.0);
        metrics.setDiskUsage(Math.round((double) usedDisk / totalDisk * 10000.0) / 100.0);

        // === 网络速率（增量计算） ===
        collectNetworkMetrics(metrics);

        // === 系统负载 ===
        metrics.setUptimeMinutes(os.getSystemUptime() / 60);
        double[] loadAvgs = processor.getSystemLoadAverage(3);
        double loadAvg = (loadAvgs != null && loadAvgs.length > 0) ? loadAvgs[0] : -1;
        if (loadAvg < 0) {
            loadAvg = hardware.getProcessor().getSystemCpuLoadBetweenTicks(prevTicks) * cpuCores;
        }
        metrics.setLoadAverage(Math.round(loadAvg * 100.0) / 100.0);

        // === JVM ===
        Runtime runtime = Runtime.getRuntime();
        long jvmMax = runtime.maxMemory();
        long jvmUsed = runtime.totalMemory() - runtime.freeMemory();
        metrics.setJvmHeapUsage(Math.round((double) jvmUsed / jvmMax * 10000.0) / 100.0);
        metrics.setJvmNonHeapUsage(0);

        return metrics;
    }

    /**
     * 计算网络收发速率（KB/s），基于两次采集间的字节增量。
     *
     * @param metrics 待填充网络速率的目标对象
     */
    private void collectNetworkMetrics(SystemMetrics metrics) {
        long totalRx = 0, totalTx = 0;
        for (NetworkIF net : hardware.getNetworkIFs()) {
            totalRx += net.getBytesRecv();
            totalTx += net.getBytesSent();
        }
        long now = System.currentTimeMillis();
        long elapsed = Math.max((now - prevCollectTime) / 1000, 1);

        metrics.setNetworkRxKBs(Math.round((totalRx - prevRxBytes) / 1024.0 / elapsed * 100.0) / 100.0);
        metrics.setNetworkTxKBs(Math.round((totalTx - prevTxBytes) / 1024.0 / elapsed * 100.0) / 100.0);

        // 更新快照
        this.prevRxBytes = totalRx;
        this.prevTxBytes = totalTx;
        this.prevCollectTime = now;
    }
}
