package com.monitor.client.collector;

import com.monitor.client.config.ClientConfig;
import com.monitor.common.model.HighgoMetrics;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 瀚高 CSV 日志采集器，识别认证失败和大表全表查询并保存增量状态。 */
@Slf4j
class HighgoQueryLogCollector {

    private static final String OFFSET_PREFIX = "query.offset.";
    private static final String LOGIN_TIMES_KEY = "query.login.failure.times";
    private static final String TEMP_FILE_SUFFIX = ".tmp";
    private static final int LOG_TIME_COLUMN = 0;
    private static final int DATABASE_COLUMN = 2;
    private static final int USER_COLUMN = 1;
    private static final int CLIENT_ADDRESS_COLUMN = 4;
    private static final int SQL_STATE_COLUMN = 12;
    private static final int MESSAGE_COLUMN = 13;
    private static final int QUERY_COLUMN = 19;
    private static final int LOG_TIME_SECOND_END_INDEX = 19;
    private static final Pattern TABLE_PATTERN = Pattern.compile(
            "(?is)^\\s*select\\s+\\*\\s+from\\s+"
                    + "((?:\"[^\"]+\"|[a-zA-Z_][a-zA-Z0-9_$]*)"
                    + "(?:\\s*\\.\\s*(?:\"[^\"]+\"|[a-zA-Z_][a-zA-Z0-9_$]*))?)");
    private static final String TABLE_SIZE_SQL = "SELECT pg_total_relation_size(to_regclass(?))";
    private static final String CLIENT_ADDRESS_UNAVAILABLE = "日志未提供";

    private final Map<Path, Long> fileOffsets = new HashMap<>();
    private final Deque<Long> loginFailureTimes = new ArrayDeque<>();
    private Path loadedStatePath;
    private boolean initialized;

    /** 按数据库名称创建连接的函数，用于查询日志命中的表实际大小。 */
    @FunctionalInterface
    interface DatabaseConnectionFactory {
        Connection open(ClientConfig config, String databaseName) throws Exception;
    }

    /** 增量采集 CSV 日志并返回本轮安全事件。 */
    CollectionResult collect(List<String> databaseNames, ClientConfig config,
            DatabaseConnectionFactory connectionFactory) {
        if (!hasText(config.getHighgoServerLogDirectory())) {
            log.warn("未配置瀚高日志目录，CSV 日志事件不可用");
            return CollectionResult.unavailable();
        }
        Path directory = Path.of(config.getHighgoServerLogDirectory()).toAbsolutePath().normalize();
        if (!Files.isDirectory(directory) || !Files.isReadable(directory)) {
            log.warn("瀚高日志目录不存在或不可读, directory={}", directory);
            return CollectionResult.unavailable();
        }
        try {
            loadStateIfNecessary(config);
            List<Path> files = listFiles(directory, config.getHighgoServerLogPattern());
            if (files.isEmpty()) {
                log.warn("瀚高日志目录没有匹配文件, directory={}, pattern={}", directory,
                        config.getHighgoServerLogPattern());
                return CollectionResult.unavailable();
            }
            if (!initialized) {
                initializeAtFileEnds(files);
                initialized = true;
                saveState(config);
                log.info("瀚高 SQL 日志采集基线初始化完成, files={}", files.size());
                return CollectionResult.empty(loginFailureTimes.size());
            }
            List<HighgoMetrics.LargeTableQuery> queries = new ArrayList<>();
            Map<String, Double> tableSizeCache = new HashMap<>();
            int maxQueries = Math.max(1, config.getHighgoLargeQueryMaxRows());
            long remaining = Math.max(1L, config.getHighgoLogMaxReadBytesPerCycle());
            for (Path file : files) {
                if (remaining <= 0L || queries.size() >= maxQueries) {
                    break;
                }
                ReadResult result = readFile(file, remaining, databaseNames, config, connectionFactory,
                        tableSizeCache, maxQueries - queries.size());
                remaining -= result.bytesRead;
                queries.addAll(result.queries);
            }
            fileOffsets.keySet().removeIf(path -> !new HashSet<>(files).contains(path));
            removeExpiredLoginFailures(config);
            saveState(config);
            return new CollectionResult(loginFailureTimes.size(), queries, true);
        } catch (Exception e) {
            log.warn("瀚高 CSV 日志采集失败, directory={}", directory, e);
            return CollectionResult.unavailable();
        }
    }

    private ReadResult readFile(Path path, long maxBytes, List<String> databaseNames,
            ClientConfig config, DatabaseConnectionFactory factory,
            Map<String, Double> tableSizeCache, int maxQueries) throws Exception {
        long size = Files.size(path);
        long offset = fileOffsets.getOrDefault(path, 0L);
        if (size < offset) {
            offset = 0L;
        }
        long start = offset;
        List<HighgoMetrics.LargeTableQuery> queries = new ArrayList<>();
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            file.seek(offset);
            String line;
            while (file.getFilePointer() - start < maxBytes && queries.size() < maxQueries
                    && (line = file.readLine()) != null) {
                List<String> columns = parseCsv(new String(line.getBytes(StandardCharsets.ISO_8859_1),
                        StandardCharsets.UTF_8));
                if (isLoginFailure(columns, config.getHighgoLoginFailurePatterns())) {
                    loginFailureTimes.addLast(System.currentTimeMillis());
                    log.info("检测到瀚高数据库登录失败事件, logFile={}", path.getFileName());
                }
                if (columns.size() <= QUERY_COLUMN) {
                    continue;
                }
                String databaseName = value(columns, DATABASE_COLUMN);
                String queryText = extractQuery(columns);
                String tableName = extractTableName(queryText);
                if (tableName == null || !databaseNames.contains(databaseName)) {
                    continue;
                }
                String tableKey = databaseName + "\u0000" + tableName;
                double tableSizeMb = tableSizeCache.computeIfAbsent(tableKey,
                        key -> queryTableSize(factory, config, databaseName, tableName));
                if (tableSizeMb >= Math.max(0D, config.getHighgoLargeTableSizeMb())) {
                    HighgoMetrics.LargeTableQuery query = new HighgoMetrics.LargeTableQuery();
                    query.setQueryTime(normalizeLogTime(value(columns, LOG_TIME_COLUMN)));
                    query.setDatabaseName(databaseName);
                    query.setUserName(value(columns, USER_COLUMN));
                    query.setClientAddress(value(columns, CLIENT_ADDRESS_COLUMN));
                    query.setQuery(queryText);
                    query.setOccurrences(1L);
                    query.setTableName(tableName);
                    query.setTableSizeMB(tableSizeMb);
                    query.setFullTableScan(true);
                    queries.add(query);
                    log.info("检测到瀚高大表全表查询, database={}, table={}", databaseName, tableName);
                }
            }
            fileOffsets.put(path, file.getFilePointer());
            return new ReadResult(file.getFilePointer() - start, queries);
        }
    }

    private String normalizeLogTime(String logTime) {
        if (!hasText(logTime)) {
            return "";
        }
        String normalized = logTime.trim();
        return normalized.length() > LOG_TIME_SECOND_END_INDEX
                ? normalized.substring(0, LOG_TIME_SECOND_END_INDEX) : normalized;
    }

    private double queryTableSize(DatabaseConnectionFactory factory,
            ClientConfig config, String databaseName, String tableName) {
        try (Connection connection = factory.open(config, databaseName);
                PreparedStatement statement = connection.prepareStatement(TABLE_SIZE_SQL)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) / 1024D / 1024D : 0D;
            }
        } catch (Exception e) {
            log.warn("查询瀚高表大小失败, database={}, table={}", databaseName, tableName, e);
            return 0D;
        }
    }

    private boolean isLoginFailure(List<String> columns, List<String> patterns) {
        String line = String.join(",", columns).toLowerCase(Locale.ROOT);
        return patterns != null && patterns.stream().filter(this::hasText)
                .map(pattern -> pattern.toLowerCase(Locale.ROOT)).anyMatch(line::contains);
    }

    private String extractTableName(String query) {
        if (query == null) {
            return null;
        }
        Matcher matcher = TABLE_PATTERN.matcher(query);
        return matcher.find() ? matcher.group(1).replaceAll("\\s*\\.\\s*", ".") : null;
    }

    private String extractQuery(List<String> columns) {
        String query = value(columns, QUERY_COLUMN);
        if (hasText(query)) {
            return query;
        }
        String message = value(columns, MESSAGE_COLUMN);
        String prefix = "语句:";
        int prefixIndex = message.indexOf(prefix);
        return prefixIndex >= 0 ? message.substring(prefixIndex + prefix.length()).trim() : "";
    }

    private List<String> parseCsv(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if (current == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    value.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (current == ',' && !quoted) {
                values.add(value.toString());
                value.setLength(0);
            } else {
                value.append(current);
            }
        }
        values.add(value.toString());
        return values;
    }

    private List<Path> listFiles(Path directory, String pattern) throws Exception {
        PathMatcher matcher = directory.getFileSystem().getPathMatcher("glob:" +
                (hasText(pattern) ? pattern : "*.*"));
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path path : stream) {
                if (Files.isRegularFile(path) && Files.isReadable(path) && matcher.matches(path.getFileName())) {
                    files.add(path.toAbsolutePath().normalize());
                }
            }
        }
        files.sort(Comparator.comparing(Path::toString));
        return files;
    }

    private void initializeAtFileEnds(List<Path> files) throws Exception {
        for (Path file : files) {
            fileOffsets.putIfAbsent(file, Files.size(file));
        }
    }

    private void loadStateIfNecessary(ClientConfig config) {
        Path statePath = Path.of(config.getHighgoCollectorStatePath()).toAbsolutePath().normalize();
        if (statePath.equals(loadedStatePath)) {
            return;
        }
        loadedStatePath = statePath;
        fileOffsets.clear();
        loginFailureTimes.clear();
        initialized = false;
        if (!Files.isRegularFile(statePath)) {
            return;
        }
        try (java.io.Reader reader = Files.newBufferedReader(statePath, StandardCharsets.UTF_8)) {
            Properties properties = new Properties();
            properties.load(reader);
            for (String key : properties.stringPropertyNames()) {
                if (key.startsWith(OFFSET_PREFIX)) {
                    String path = new String(Base64.getUrlDecoder().decode(key.substring(OFFSET_PREFIX.length())),
                            StandardCharsets.UTF_8);
                    fileOffsets.put(Path.of(path), Long.parseLong(properties.getProperty(key)));
                }
            }
            String times = properties.getProperty(LOGIN_TIMES_KEY, "");
            if (hasText(times)) {
                for (String value : times.split(",")) {
                    loginFailureTimes.addLast(Long.parseLong(value));
                }
            }
            initialized = !fileOffsets.isEmpty();
            removeExpiredLoginFailures(config);
        } catch (Exception e) {
            log.warn("恢复瀚高 SQL 日志采集状态失败, path={}", statePath, e);
            fileOffsets.clear();
            loginFailureTimes.clear();
        }
    }

    private void saveState(ClientConfig config) {
        Path statePath = Path.of(config.getHighgoCollectorStatePath()).toAbsolutePath().normalize();
        Properties properties = new Properties();
        fileOffsets.forEach((path, offset) -> properties.setProperty(OFFSET_PREFIX
                + Base64.getUrlEncoder().withoutPadding().encodeToString(path.toString().getBytes(StandardCharsets.UTF_8)),
                String.valueOf(offset)));
        properties.setProperty(LOGIN_TIMES_KEY, loginFailureTimes.stream().map(String::valueOf)
                .reduce((left, right) -> left + "," + right).orElse(""));
        Path temp = Path.of(statePath + TEMP_FILE_SUFFIX);
        try {
            Files.createDirectories(statePath.getParent());
            try (java.io.Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                properties.store(writer, "瀚高 SQL 日志采集状态");
            }
            Files.move(temp, statePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            log.warn("保存瀚高 SQL 日志采集状态失败, path={}", statePath, e);
        }
    }

    private void removeExpiredLoginFailures(ClientConfig config) {
        long earliest = System.currentTimeMillis()
                - Duration.ofHours(Math.max(1, config.getHighgoLoginFailureWindowHours())).toMillis();
        while (!loginFailureTimes.isEmpty() && loginFailureTimes.peekFirst() < earliest) {
            loginFailureTimes.removeFirst();
        }
    }

    private String value(List<String> columns, int index) {
        return index < columns.size() ? columns.get(index) : "";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static class ReadResult {
        private final long bytesRead;
        private final List<HighgoMetrics.LargeTableQuery> queries;

        private ReadResult(long bytesRead, List<HighgoMetrics.LargeTableQuery> queries) {
            this.bytesRead = bytesRead;
            this.queries = queries;
        }
    }

    /** 单轮日志采集结果。 */
    @Getter
    static class CollectionResult {
        private final long loginFailureCount;
        private final List<HighgoMetrics.LargeTableQuery> largeTableQueries;
        private final boolean available;

        private CollectionResult(long loginFailureCount, List<HighgoMetrics.LargeTableQuery> largeTableQueries,
                boolean available) {
            this.loginFailureCount = loginFailureCount;
            this.largeTableQueries = largeTableQueries;
            this.available = available;
        }

        static CollectionResult empty(long loginFailureCount) {
            return new CollectionResult(loginFailureCount, new ArrayList<>(), true);
        }

        static CollectionResult unavailable() {
            return new CollectionResult(-1L, new ArrayList<>(), false);
        }
    }
}
