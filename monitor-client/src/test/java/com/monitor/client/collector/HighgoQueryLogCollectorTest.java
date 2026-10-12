package com.monitor.client.collector;

import com.monitor.client.config.ClientConfig;
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

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 瀚高 CSV 日志大表全表查询采集测试。 */
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
        ClientConfig config = createConfig();
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

    private ClientConfig createConfig() {
        ClientConfig config = new ClientConfig();
        config.setHighgoServerLogDirectory(temporaryDirectory.toString());
        config.setHighgoServerLogPattern("*.csv");
        config.setHighgoCollectorStatePath(temporaryDirectory.resolve("collector.state").toString());
        config.setHighgoLogMaxReadBytesPerCycle(1024L * 1024L);
        config.setHighgoLargeQueryMaxRows(10);
        config.setHighgoLargeTableSizeMb(1D);
        return config;
    }

    private Connection openTableSizeConnection(ClientConfig config, String databaseName) {
        ResultSet resultSet = (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {ResultSet.class}, (proxy, method, arguments) -> {
                    if ("next".equals(method.getName())) {
                        return true;
                    }
                    if ("getLong".equals(method.getName())) {
                        return TABLE_SIZE_BYTES;
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
