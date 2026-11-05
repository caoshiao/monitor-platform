package com.monitor.client.config;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** 数据库实例采集配置；采集器、驱动和方言行为均由配置决定。 */
@Getter
@Setter
public class DatabaseMonitorConfig {

    private boolean enabled;
    /** 数据库产品标识，例如 hgdb、postgres；仅用于标识和展示，不参与代码分支。 */
    private String type = "postgres";
    /** 采集器标识，例如 postgresql；由 Spring 中注册的采集器实现提供。 */
    private String collector = "postgresql";
    /** 前端和告警展示的数据库产品名称。 */
    private String displayName = "PostgreSQL";
    /** JDBC 地址必须匹配的前缀，避免使用错误驱动连接目标实例。 */
    private String urlPrefix = "jdbc:postgresql:";
    /** JDBC 驱动类，由部署配置指定。 */
    private String driverClass;
    private String url;
    private String username;
    private String password;
    private long collectIntervalMs = 5000L;
    private List<String> excludedDatabases = new ArrayList<>();
    private int connectTimeoutSeconds = 5;
    private int queryTimeoutSeconds = 10;
    private int longTransactionSeconds = 300;
    /** 是否只统计 PostgreSQL client backend 类型连接。 */
    private boolean clientBackendOnly;
    /** 是否从数据库 CSV 日志采集登录失败和大表查询事件。 */
    private boolean queryLogEnabled;
    private String serverLogDirectory;
    private String serverLogPattern = "*.csv";
    private List<String> loginFailurePatterns = new ArrayList<>(List.of(
            ",28P01,", ",28000,", "password authentication failed", "authentication failed",
            "chances to enter your password before your account get locked", "口令认证失败", "密码认证失败", "密码错误"));
    private int loginFailureWindowHours = 24;
    private long logMaxReadBytesPerCycle = 1048576L;
    /** 单条 CSV 记录内存上限；超限丢弃该记录并记录告警日志。 */
    private int logMaxRecordBytes = 1048576;
    private String collectorStatePath = "/opt/monitor-platform/data/postgresql-collector.state";
    private double largeTableSizeMb = 50D;
    private int largeQueryMaxRows = 100;
    private int largeQueryWindowMinutes = 60;

    /**
     * 校验产品、连接参数及限额，避免错误驱动和无界日志读取。
     *
     * @throws IllegalArgumentException 配置缺失或超出范围时抛出
     */
    public void validate() {
        if (isBlank(type) || isBlank(collector) || isBlank(displayName) || isBlank(urlPrefix)
                || isBlank(driverClass) || url == null || !url.startsWith(urlPrefix)
                || username == null || username.trim().isEmpty() || password == null) {
            throw new IllegalArgumentException("数据库类型、采集器、驱动、JDBC 地址或监控账号配置不正确");
        }
        if (collectIntervalMs <= 0 || connectTimeoutSeconds <= 0 || queryTimeoutSeconds <= 0
                || longTransactionSeconds <= 0 || logMaxReadBytesPerCycle <= 0 || logMaxRecordBytes <= 0
                || largeQueryMaxRows <= 0 || largeQueryWindowMinutes <= 0 || loginFailureWindowHours <= 0
                || largeTableSizeMb < 0 || collectorStatePath == null || collectorStatePath.trim().isEmpty()) {
            throw new IllegalArgumentException("数据库采集周期、超时和读取限额必须为正数，状态路径不能为空");
        }
    }

    /** 判断字符串配置是否为空。 */
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
