package com.monitor.client.collector;

import com.monitor.client.config.DatabaseMonitorConfig;
import com.monitor.common.model.HighgoMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** PostgreSQL 与瀚高 CSV 日志格式、跨周期读取及断点恢复测试。 */
class HighgoQueryLogCollectorTest {

    private static final long TABLE_SIZE_BYTES = 88_211_456L;
    private static final String ORIGINAL_SQL = "select * from szh_file;";
    private static final String QUERY_LOG = "2026-10-09 15:41:06.633 CST,\"szh\",\"kcc_main\",2612,"
            + "\"127.0.0.1:60818\",session,1,\"SELECT\",2026-10-09 15:41:06 CST,3/1627,0,"
            + "LOG,00000,\"语句: select * from szh_file;\",,,,,,\"select * from szh_file;\"\n";

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldKeepOriginalSqlAccountAndExecutionTime() throws Exception {
        Path logFile = temporaryDirectory.resolve("highgodb_09.csv");
        Files.writeString(logFile, "", StandardCharsets.UTF_8);
        DatabaseMonitorConfig config = createConfig();
        HighgoQueryLogCollector collector = new HighgoQueryLogCollector();

        assertEquals(0, collector.collect(List.of("kcc_main"), config,
                this::openTableSizeConnection).getLargeTableQueries().size());

        Files.writeString(logFile, QUERY_LOG, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        List<HighgoMetrics.LargeTableQuery> queries = collector.collect(List.of("kcc_main"), config,
                this::openTableSizeConnection).getLargeTableQueries();

        assertEquals(1, queries.size());
        HighgoMetrics.LargeTableQuery query = queries.get(0);
        assertEquals("2026-10-09 15:41:06", query.getQueryTime());
        assertEquals("szh", query.getUserName());
        assertEquals("kcc_main", query.getDatabaseName());
        assertEquals("szh_file", query.getTableName());
        assertEquals(84.125D, query.getTableSizeMB());
        assertEquals(ORIGINAL_SQL, query.getQuery());
    }

    @Test
    void shouldReadPostgresqlStatementAndAuthenticationFailureAfterRotation() throws Exception {
        Files.createFile(temporaryDirectory.resolve("postgresql-2026-10-24.csv"));
        DatabaseMonitorConfig config = createConfig();
        HighgoQueryLogCollector collector = new HighgoQueryLogCollector();
        collector.collect(List.of("kcc_main"), config, this::openTableSizeConnection);
        Path rotated = temporaryDirectory.resolve("postgresql-2026-10-25.csv");
        Files.writeString(rotated, postgresRecord("00000", "statement: " + ORIGINAL_SQL)
                + postgresRecord("28P01", "password authentication failed for user \"szh\""));

        HighgoQueryLogCollector.CollectionResult result = collector.collect(
                List.of("kcc_main"), config, this::openTableSizeConnection);
        assertEquals(1L, result.getLoginFailureCount());
        assertEquals(1, result.getLargeTableQueries().size());
        assertEquals(ORIGINAL_SQL, result.getLargeTableQueries().get(0).getQuery());
        // 重启后已提交的完整记录不能重复产生告警。
        HighgoQueryLogCollector.CollectionResult restarted = new HighgoQueryLogCollector().collect(
                List.of("kcc_main"), config, this::openTableSizeConnection);
        assertEquals(1L, restarted.getLoginFailureCount());
        assertEquals(0, restarted.getLargeTableQueries().size());
    }

    @Test
    void shouldKeepMultilineSqlAcrossByteLimitsAndRestart() throws Exception {
        Path file = Files.createFile(temporaryDirectory.resolve("postgresql.csv"));
        DatabaseMonitorConfig config = createConfig();
        config.setLogMaxReadBytesPerCycle(32L);
        HighgoQueryLogCollector collector = new HighgoQueryLogCollector();
        collector.collect(List.of("kcc_main"), config, this::openTableSizeConnection);
        String sql = "select *\nfrom szh_file;";
        String record = postgresRecord("00000", "statement: " + sql);
        Files.writeString(file, record.substring(0, record.length() / 2));
        assertEquals(0, collector.collect(List.of("kcc_main"), config,
                this::openTableSizeConnection).getLargeTableQueries().size());
        collector = new HighgoQueryLogCollector();
        Files.writeString(file, record.substring(record.length() / 2), StandardOpenOption.APPEND);
        List<HighgoMetrics.LargeTableQuery> queries = new ArrayList<>();
        for (int cycle = 0; cycle < record.getBytes(StandardCharsets.UTF_8).length / 32 + 2; cycle++) {
            queries.addAll(collector.collect(List.of("kcc_main"), config,
                    this::openTableSizeConnection).getLargeTableQueries());
        }
        assertEquals(1, queries.size());
        assertEquals(sql, queries.get(0).getQuery());
    }

    @Test
    void shouldSkipOversizedRecordAndContinueWithNextRecord() throws Exception {
        Path file = Files.createFile(temporaryDirectory.resolve("postgresql.csv"));
        DatabaseMonitorConfig config = createConfig();
        config.setLogMaxRecordBytes(512);
        HighgoQueryLogCollector collector = new HighgoQueryLogCollector();
        collector.collect(List.of("kcc_main"), config, this::openTableSizeConnection);
        Files.writeString(file, postgresRecord("00000", "x".repeat(1024))
                + postgresRecord("00000", "statement: " + ORIGINAL_SQL));
        assertEquals(1, collector.collect(List.of("kcc_main"), config,
                this::openTableSizeConnection).getLargeTableQueries().size());
    }

    /** 按 PostgreSQL CSV 列序构造日志，第 19 列为空以覆盖实际正常语句格式。 */
    private String postgresRecord(String sqlState, String message) {
        List<String> columns = new ArrayList<>(Collections.nCopies(23, ""));
        columns.set(0, "2026-10-24 17:00:00.000 CST");
        columns.set(1, "szh");
        columns.set(2, "kcc_main");
        columns.set(4, "127.0.0.1:54321");
        columns.set(12, sqlState);
        columns.set(13, message);
        return columns.stream().map(value -> "\"" + value.replace("\"", "\"\"") + "\"")
                .collect(java.util.stream.Collectors.joining(",")) + "\n";
    }

    private DatabaseMonitorConfig createConfig() {
        DatabaseMonitorConfig config = new DatabaseMonitorConfig();
        config.setServerLogDirectory(temporaryDirectory.toString());
        config.setServerLogPattern("*.csv");
        config.setCollectorStatePath(temporaryDirectory.resolve("collector.state").toString());
        config.setLogMaxReadBytesPerCycle(1024L * 1024L);
        config.setLargeQueryMaxRows(10);
        config.setLargeTableSizeMb(1D);
        return config;
    }

    private Connection openTableSizeConnection(DatabaseMonitorConfig config, String databaseName) {
        ResultSet resultSet = (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {ResultSet.class}, (proxy, method, arguments) -> {
                    if ("next".equals(method.getName())) {
                        return true;
                    }
                    if ("getLong".equals(method.getName())) {
                        return TABLE_SIZE_BYTES;
                    }
                    if ("wasNull".equals(method.getName())) {
                        return false;
                    }
                    return null;
                });
        PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {PreparedStatement.class}, (proxy, method, arguments) ->
                        "executeQuery".equals(method.getName()) ? resultSet : null);
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {Connection.class}, (proxy, method, arguments) ->
                        "prepareStatement".equals(method.getName()) ? statement : null);
    }
}
