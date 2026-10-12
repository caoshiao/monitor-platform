package com.monitor.client.config;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户端全局配置 —— 绑定 application.yml 中以 {@code monitor.client} 为前缀的配置项。
 * <p>
 * 支持按需启停四种监控模式：
 * <ul>
 *   <li>系统监控 —— CPU/内存/磁盘/网络/JVM（默认开启）</li>
 *   <li>Docker 监控 —— 容器状态与镜像统计（默认关闭，需配置 Docker Host）</li>
 *   <li>外部系统监控 —— TCP 端口与 HTTP 接口双探针（默认关闭，需配置端点列表）</li>
 *   <li>瀚高监控 —— 实例指标、普通日志登录失败和语句统计（默认关闭）</li>
 * </ul>
 * 所有配置均支持命令行参数覆盖，如 {@code --monitor.client.docker-enabled=true}。
 * </p>
 *
 * @author csa
 * @see com.monitor.client.scheduler.CollectScheduler
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "monitor.client")
public class ClientConfig {

    // ==================== 基础配置 ====================

    /** 客户端唯一标识，留空时自动取主机名 */
    private String clientId;

    /** 节点对外展示的 IPv4 地址，留空时由系统自动选择非回环地址。 */
    private String nodeIpAddress;

    /** 服务端 WebSocket 地址，格式 ws://host:port/ws/client */
    private String serverUrl = "ws://localhost:8080/ws/client";

    // ==================== 功能开关 ====================

    /** 是否启用系统指标监控（CPU/内存/磁盘/网络/JVM），默认 true */
    private boolean systemEnabled = true;

    /** 是否启用 Docker 容器状态监控，默认 false */
    private boolean dockerEnabled = false;

    /** Docker 守护进程地址。Linux 默认 unix:///var/run/docker.sock；Windows 需开启 TCP 后填写 tcp://localhost:2375 */
    private String dockerHost = "unix:///var/run/docker.sock";

    /** 是否启用外部系统双探针检查，默认 false。 */
    private boolean microserviceEnabled = false;

    /** 外部系统双探针采集间隔，单位毫秒。 */
    private long microserviceCollectIntervalMs = 10000L;

    /** TCP 端口连接超时，单位毫秒。 */
    private int microserviceConnectTimeoutMs = 3000;

    /** HTTP 接口读取超时，单位毫秒。 */
    private int microserviceReadTimeoutMs = 5000;

    /** 外部系统并行探测线程数。 */
    private int microserviceProbeParallelism = 4;

    /** 接口响应内容最大读取字节数，避免误读大响应占用过多内存。 */
    private int microserviceMaxResponseBytes = 65536;

    /** 是否启用瀚高数据库监控。 */
    private boolean highgoEnabled = false;

    /** 瀚高实时指标采集间隔，单位毫秒。 */
    private long highgoCollectIntervalMs = 5000L;

    /** 不参与监控的数据库名称列表，默认排除安全管理库 highgo。 */
    private List<String> highgoExcludedDatabases = new ArrayList<>(List.of("highgo"));

    private String highgoUrl;
    /** 瀚高安全版 JDBC 驱动类名，默认使用官方驱动入口。 */
    private String highgoDriverClass = "com.highgo.jdbc.Driver";
    private String highgoUsername;
    private String highgoPassword;
    /** 参与大表全表查询预警的最小表大小，单位 MB。 */
    private double highgoLargeTableSizeMb = 50D;
    /** 单次最多返回的大表查询记录数。 */
    private int highgoLargeQueryMaxRows = 100;
    /** 大表查询事件持续上报窗口，单位分钟。 */
    private int highgoLargeQueryWindowMinutes = 60;
    /** pg_stat_statements 单库候选记录数相对于页面最大记录数的倍数。 */
    private int highgoStatementCandidateMultiplier = 5;
    /** pg_stat_statements 采集失败后的重试间隔，单位毫秒。 */
    private long highgoStatementRetryIntervalMs = 60000L;
    /** 瀚高普通数据库文本日志目录。 */
    private String highgoServerLogDirectory;
    /** 普通数据库文本日志文件匹配模式。 */
    private String highgoServerLogPattern = "*.{log,csv}";
    /** 登录失败识别文本，按不区分大小写的包含关系匹配。 */
    private List<String> highgoLoginFailurePatterns = new ArrayList<>(List.of(
            ",28P01,", ",28000,", "password authentication failed", "authentication failed",
            "chances to enter your password before your account get locked",
            "口令认证失败", "密码认证失败", "密码错误"));
    /** 登录失败数量的滚动统计窗口，单位小时。 */
    private int highgoLoginFailureWindowHours = 24;
    /** 单次采集最多读取的普通日志字节数，避免大日志阻塞采集线程。 */
    private long highgoLogMaxReadBytesPerCycle = 1048576L;
    /** 登录失败日志偏移与滚动事件状态文件。 */
    private String highgoCollectorStatePath = "/opt/monitor-platform/data/highgo-collector.state";

    /** 是否校验外部系统 HTTPS 证书，默认 false（内网监控通常跳过证书校验）。 */
    private boolean microserviceSslVerify = false;

    /** 需要监控的外部系统端点列表。 */
    private List<ServiceEndpoint> microservices = new ArrayList<>();

    /**
     * 单个外部系统的双探针配置。
     * <p>
     * 客户端同时检查目标端口和 {@code healthUrl}，并根据两项结果计算总体状态。
     * </p>
     *
     * @author csa
     */
    @Data
    public static class ServiceEndpoint {

        /** 外部系统名称，自定义标识，如“统一身份认证系统”。 */
        private String name;

        /** TCP 探测目标主机；留空时从 healthUrl 自动解析。 */
        private String host;

        /** 接口探测完整 URL，如 "http://localhost:8081/api/status"。 */
        private String healthUrl;

        /** TCP 探测目标端口；小于等于 0 时从 healthUrl 自动解析。 */
        private int port;

        /** HTTP 请求方法，仅允许 GET 或 HEAD。 */
        private String requestMethod = "GET";

        /** 判定接口正常的最小 HTTP 状态码。 */
        private int expectedStatusMin = 200;

        /** 判定接口正常的最大 HTTP 状态码。 */
        private int expectedStatusMax = 399;

        /** 可选的响应内容关键字，配置后必须在响应正文中出现。 */
        private String expectedBodyContains;

        /** 接口请求头，可用于配置令牌等认证信息。 */
        private Map<String, String> requestHeaders = new HashMap<>();
    }
}
