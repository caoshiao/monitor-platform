package com.monitor.client.collector;

import com.monitor.client.config.ClientConfig;
import com.monitor.common.model.HighgoMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Collects HighGo metrics through the PostgreSQL-compatible JDBC interface. */
@Slf4j
@Component
public class HighgoCollector {

    private long auditOffset;
    private final Map<String, Long> queryCounts = new HashMap<>();
    private static final Pattern PID_PATTERN = Pattern.compile("\\[(\\d+)\\]");
    private static final Pattern TABLE_PATTERN = Pattern.compile("(?i)\\b(?:from|join|update|into)\\s+([a-zA-Z_][\\w$]*(?:\\.[a-zA-Z_][\\w$]*)?)");

    public HighgoMetrics collect(String clientId, ClientConfig config) {
        HighgoMetrics metrics = new HighgoMetrics();
        metrics.setClientId(clientId);
        metrics.setCollectTime(LocalDateTime.now());
        try (Connection connection = DriverManager.getConnection(config.getHighgoUrl(), config.getHighgoUsername(), config.getHighgoPassword())) {
            collectConnectionMetrics(connection, metrics);
            collectConfiguredAudit(connection, config, metrics);
        } catch (Exception e) {
            log.error("采集瀚高数据库指标失败, clientId={}, url={}", clientId, config.getHighgoUrl(), e);
            metrics.setConnectionCount(-1);
            metrics.setActiveConnectionCount(-1);
            metrics.setLoad(-1);
            metrics.setLoginFailureCount(-1);
            metrics.setRepeatedLargeTableQueryCount(-1);
        }
        return metrics;
    }

    private void collectConfiguredAudit(Connection connection, ClientConfig config, HighgoMetrics metrics) throws Exception {
        if (config.getHighgoAuditLogPath() != null && !config.getHighgoAuditLogPath().trim().isEmpty()) {
            readAuditLog(config.getHighgoAuditLogPath(), config, metrics);
            return;
        }
        collectLoginFailures(connection, config, metrics);
        collectLargeQueries(connection, config, metrics);
    }

    private synchronized void readAuditLog(String fileName, ClientConfig config, HighgoMetrics metrics) {
        Path path = Path.of(fileName);
        if (!Files.isRegularFile(path)) return;
        try {
            long length = Files.size(path);
            if (length < auditOffset) {
                auditOffset = 0;
                queryCounts.clear();
            }
            try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
                file.seek(auditOffset);
                String line;
                while ((line = file.readLine()) != null) {
                    String decoded = new String(line.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1), java.nio.charset.StandardCharsets.UTF_8);
                    if (decoded.contains("password authentication failed") || decoded.contains("authentication failed")) {
                        metrics.setLoginFailureCount(metrics.getLoginFailureCount() + 1);
                    }
                    Matcher pid = PID_PATTERN.matcher(decoded);
                    Matcher table = TABLE_PATTERN.matcher(decoded);
                    if (!pid.find() || !table.find()) continue;
                    String key = pid.group(1) + "@" + table.group(1).toLowerCase();
                    long count = queryCounts.merge(key, 1L, Long::sum);
                    if (count >= config.getHighgoLargeQueryMinOccurrences()) {
                        HighgoMetrics.LargeTableQuery query = new HighgoMetrics.LargeTableQuery();
                        query.setClientAddress(pid.group(1));
                        query.setQuery(decoded);
                        query.setOccurrences(count);
                        metrics.getRepeatedLargeTableQueries().add(query);
                    }
                }
                auditOffset = file.getFilePointer();
            }
            metrics.setRepeatedLargeTableQueryCount(metrics.getRepeatedLargeTableQueries().size());
        } catch (Exception e) {
            log.warn("读取瀚高审计日志失败, path={}", fileName, e);
        }
    }

    private void collectConnectionMetrics(Connection connection, HighgoMetrics metrics) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT count(*) AS total, count(*) FILTER (WHERE state = 'active') AS active, "
                        + "round(count(*) * 100.0 / NULLIF(current_setting('max_connections')::numeric, 0), 2) AS load "
                        + "FROM pg_stat_activity")) {
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    metrics.setConnectionCount(rs.getLong("total"));
                    metrics.setActiveConnectionCount(rs.getLong("active"));
                    metrics.setLoad(rs.getDouble("load"));
                }
            }
        }
    }

    private void collectLoginFailures(Connection connection, ClientConfig config, HighgoMetrics metrics) throws Exception {
        if (config.getHighgoLoginFailureSql() == null || config.getHighgoLoginFailureSql().trim().isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(config.getHighgoLoginFailureSql()); ResultSet rs = statement.executeQuery()) {
            if (rs.next()) metrics.setLoginFailureCount(rs.getLong(1));
        }
    }

    private void collectLargeQueries(Connection connection, ClientConfig config, HighgoMetrics metrics) throws Exception {
        String sql = config.getHighgoLargeQuerySql();
        if (sql == null || sql.trim().isEmpty()) return;
        try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                HighgoMetrics.LargeTableQuery query = new HighgoMetrics.LargeTableQuery();
                query.setUserName(rs.getString(1));
                query.setClientAddress(rs.getString(2));
                query.setQuery(rs.getString(3));
                query.setOccurrences(rs.getLong(4));
                metrics.getRepeatedLargeTableQueries().add(query);
                metrics.setRepeatedLargeTableQueryCount(metrics.getRepeatedLargeTableQueryCount() + 1);
            }
        }
    }
}
