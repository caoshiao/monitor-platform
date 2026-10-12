package com.monitor.client.collector;

import com.monitor.client.config.ClientConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 瀚高登录日志增量、轮转及状态恢复测试。 */
class HighgoLoginLogCollectorTest {

    private static final String NORMAL_LOG = "2026-10-09 09:00:00 LOG: connection received\n";
    private static final String FAILURE_LOG =
            "2026-10-09 09:01:00 FATAL: password authentication failed for user test_user\n";
    private static final String HIGHGO_CSV_FAILURE_LOG =
            "2026-10-09 10:59:49.818 CST,\"sysdba\",\"kcc_main\",2612,\"[local]\",session,1,"
                    + "\"authentication\",2026-10-09 10:59:49 CST,3/34,0,致命错误,28P01,"
                    + "\"You still have \"\"4\"\" chances to enter your password before your account get locked.\"\n";
    private static final String HIGHGO_UNKNOWN_ROLE_CSV_LOG =
            "2026-10-09 11:11:00.710 CST,\"monitor_probe_nonexistent\",\"kcc_main\",3055,"
                    + "\"127.0.0.1:60818\",session,1,\"authentication\",2026-10-09 11:11:00 CST,"
                    + "3/1627,0,致命错误,28000,\"角色 \"\"monitor_probe_nonexistent\"\" 不存在\"\n";

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldReadOnlyAppendedContentAndNewDailyFile() throws Exception {
        Path firstLog = temporaryDirectory.resolve("highgo-2026-10-09.log");
        Files.writeString(firstLog, NORMAL_LOG, StandardCharsets.UTF_8);
        ClientConfig config = createConfig();
        HighgoLoginLogCollector collector = new HighgoLoginLogCollector();

        assertEquals(0L, collector.collect(config));

        Files.writeString(firstLog, FAILURE_LOG, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        assertEquals(1L, collector.collect(config));

        Path nextLog = temporaryDirectory.resolve("highgo-2026-10-10.log");
        Files.writeString(nextLog, FAILURE_LOG, StandardCharsets.UTF_8);
        assertEquals(2L, collector.collect(config));
        assertEquals(2L, collector.collect(config));
    }

    @Test
    void shouldRestoreOffsetsAndRollingCountAfterRestart() throws Exception {
        Path logFile = temporaryDirectory.resolve("highgo-2026-10-09.log");
        Files.writeString(logFile, NORMAL_LOG, StandardCharsets.UTF_8);
        ClientConfig config = createConfig();
        HighgoLoginLogCollector firstCollector = new HighgoLoginLogCollector();
        assertEquals(0L, firstCollector.collect(config));

        Files.writeString(logFile, FAILURE_LOG, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        assertEquals(1L, firstCollector.collect(config));

        HighgoLoginLogCollector restartedCollector = new HighgoLoginLogCollector();
        assertEquals(1L, restartedCollector.collect(config));
        Files.writeString(logFile, FAILURE_LOG, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        assertEquals(2L, restartedCollector.collect(config));
    }

    @Test
    void shouldCollectHighgoCsvAuthenticationFailure() throws Exception {
        Path csvLog = temporaryDirectory.resolve("highgodb_09.csv");
        Files.writeString(csvLog, NORMAL_LOG, StandardCharsets.UTF_8);
        ClientConfig config = createConfig();
        config.setHighgoServerLogPattern("*.{log,csv}");
        HighgoLoginLogCollector collector = new HighgoLoginLogCollector();

        assertEquals(0L, collector.collect(config));

        Files.writeString(csvLog, HIGHGO_CSV_FAILURE_LOG, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        assertEquals(1L, collector.collect(config));

        Files.writeString(csvLog, HIGHGO_UNKNOWN_ROLE_CSV_LOG,
                StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        assertEquals(2L, collector.collect(config));
    }

    private ClientConfig createConfig() {
        ClientConfig config = new ClientConfig();
        config.setHighgoServerLogDirectory(temporaryDirectory.toString());
        config.setHighgoServerLogPattern("highgo-*.log");
        config.setHighgoCollectorStatePath(temporaryDirectory.resolve("collector.state").toString());
        config.setHighgoLogMaxReadBytesPerCycle(1024L * 1024L);
        return config;
    }
}
