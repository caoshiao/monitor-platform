package com.monitor.common.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 瀚高数据库安全版 V4.5 实时运行与审计指标模型。 */
@Data
public class HighgoMetrics {

    private String clientId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime collectTime;

    private long connectionCount;
    private long activeConnectionCount;
    private double load;
    /** 空闲连接数。 */
    private long idleConnectionCount;
    /** 等待锁的会话数。 */
    private long waitingSessionCount;
    /** 长事务数。 */
    private long longTransactionCount;
    /** 数据库容量统计范围。 */
    private String databaseName;
    /** 实例内所有允许连接数据库的总大小，单位 MB。 */
    private double databaseSizeMB;
    /** 缓存命中率，百分比。 */
    private double cacheHitRatio;
    private long loginFailureCount;
    private long repeatedLargeTableQueryCount;
    private List<LargeTableQuery> repeatedLargeTableQueries = new ArrayList<>();

    /** 单次采集窗口内命中的大表全表查询。 */
    @Data
    public static class LargeTableQuery {
        /** SQL 在瀚高数据库日志中记录的实际执行时间。 */
        private String queryTime;
        /** 查询发生的数据库名称。 */
        private String databaseName;
        /** 执行 SQL 的数据库账号。 */
        private String userName;
        /** 执行 SQL 的客户端地址。 */
        private String clientAddress;
        /** 日志中记录的原始 SQL。 */
        private String query;
        /** 当前采集窗口内出现次数。 */
        private long occurrences;
        /** 全表查询涉及的表名。 */
        private String tableName;
        /** 表及其附属对象的总大小，单位 MB。 */
        private double tableSizeMB;
        /** 是否为全字段查询。 */
        private boolean fullTableScan;
    }
}
