package com.monitor.client.collector;

import com.monitor.client.config.ClientConfig;
import lombok.extern.slf4j.Slf4j;

import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.StandardOpenOption;
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

/** 瀚高普通文本日志登录失败采集器，负责文件轮转、增量偏移和滚动窗口统计。 */
@Slf4j
class HighgoLoginLogCollector {

    private static final long UNAVAILABLE_METRIC_VALUE = -1L;
    private static final String OFFSET_PREFIX = "offset.";
    private static final String EVENT_TIMES_KEY = "login.failure.times";
    private static final String TEMP_FILE_SUFFIX = ".tmp";

    private final Map<Path, Long> fileOffsets = new HashMap<>();
    private final Deque<Long> failureTimes = new ArrayDeque<>();
    private Path loadedStatePath;
    private boolean initialized;

    synchronized long collect(ClientConfig config) {
        if (!hasText(config.getHighgoServerLogDirectory())) {
            log.warn("未配置瀚高普通数据库日志目录，登录失败指标不可用");
            return UNAVAILABLE_METRIC_VALUE;
        }
        Path logDirectory = Path.of(config.getHighgoServerLogDirectory()).toAbsolutePath().normalize();
        if (!Files.isDirectory(logDirectory) || !Files.isReadable(logDirectory)) {
            log.warn("瀚高普通数据库日志目录不存在或不可读, directory={}", logDirectory);
            return UNAVAILABLE_METRIC_VALUE;
        }

        try {
            loadStateIfNecessary(config);
            List<Path> logFiles = listLogFiles(logDirectory, config.getHighgoServerLogPattern());
            if (logFiles.isEmpty()) {
                log.warn("瀚高普通数据库日志目录中没有匹配文件, directory={}, pattern={}",
                        logDirectory, config.getHighgoServerLogPattern());
                return UNAVAILABLE_METRIC_VALUE;
            }
            if (!initialized) {
                initializeAtFileEnds(logFiles);
                initialized = true;
                saveState(config);
                log.info("瀚高登录失败日志采集基线初始化完成, files={}", logFiles.size());
                return 0L;
            }

            long remainingBytes = Math.max(1L, config.getHighgoLogMaxReadBytesPerCycle());
            long totalReadBytes = 0L;
            Set<Path> existingFiles = new HashSet<>(logFiles);
            for (Path logFile : logFiles) {
                if (remainingBytes <= 0L) {
                    break;
                }
                long readBytes = readIncrement(logFile, remainingBytes, config);
                totalReadBytes += readBytes;
                remainingBytes -= readBytes;
            }
            boolean removedFile = fileOffsets.keySet().removeIf(path -> !existingFiles.contains(path));
            boolean removedEvent = removeExpiredFailures(config);
            if (totalReadBytes > 0L || removedFile || removedEvent) {
                saveState(config);
            }
            return failureTimes.size();
        } catch (Exception e) {
            log.warn("瀚高登录失败日志采集失败, directory={}", logDirectory, e);
            return UNAVAILABLE_METRIC_VALUE;
        }
    }

    private List<Path> listLogFiles(Path directory, String pattern) throws Exception {
        String effectivePattern = hasText(pattern) ? pattern.trim() : "*.log";
        PathMatcher matcher = directory.getFileSystem().getPathMatcher("glob:" + effectivePattern);
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path path : stream) {
                if (Files.isRegularFile(path) && Files.isReadable(path)
                        && matcher.matches(path.getFileName())) {
                    files.add(path.toAbsolutePath().normalize());
                }
            }
        }
        files.sort(Comparator.comparingLong(this::lastModifiedTime));
        return files;
    }

    private long lastModifiedTime(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (Exception e) {
            log.debug("读取瀚高日志修改时间失败, path={}", path, e);
            return 0L;
        }
    }

    private void initializeAtFileEnds(List<Path> logFiles) throws Exception {
        for (Path logFile : logFiles) {
            fileOffsets.putIfAbsent(logFile, Files.size(logFile));
        }
    }

    private long readIncrement(Path path, long maxReadBytes, ClientConfig config) throws Exception {
        long fileSize = Files.size(path);
        long offset = fileOffsets.getOrDefault(path, 0L);
        if (fileSize < offset) {
            log.info("检测到瀚高日志截断，重新从文件开头采集, path={}", path);
            offset = 0L;
        }
        long startOffset = offset;
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            file.seek(offset);
            String line;
            while (file.getFilePointer() - startOffset < maxReadBytes && (line = file.readLine()) != null) {
                String decodedLine = new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                if (isLoginFailure(decodedLine, config.getHighgoLoginFailurePatterns())) {
                    failureTimes.addLast(System.currentTimeMillis());
                    log.info("检测到瀚高数据库登录失败事件, logFile={}", path.getFileName());
                }
            }
            fileOffsets.put(path, file.getFilePointer());
            return file.getFilePointer() - startOffset;
        }
    }

    private boolean isLoginFailure(String line, List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return false;
        }
        String normalizedLine = line.toLowerCase(Locale.ROOT);
        return patterns.stream()
                .filter(this::hasText)
                .map(pattern -> pattern.toLowerCase(Locale.ROOT))
                .anyMatch(normalizedLine::contains);
    }

    private boolean removeExpiredFailures(ClientConfig config) {
        long windowMillis = Duration.ofHours(Math.max(1, config.getHighgoLoginFailureWindowHours())).toMillis();
        long earliestTime = System.currentTimeMillis() - windowMillis;
        boolean removed = false;
        while (!failureTimes.isEmpty() && failureTimes.peekFirst() < earliestTime) {
            failureTimes.removeFirst();
            removed = true;
        }
        return removed;
    }

    private void loadStateIfNecessary(ClientConfig config) {
        Path statePath = resolveStatePath(config);
        if (statePath.equals(loadedStatePath)) {
            return;
        }
        fileOffsets.clear();
        failureTimes.clear();
        loadedStatePath = statePath;
        initialized = false;
        if (!Files.isRegularFile(statePath)) {
            return;
        }
        Properties properties = new Properties();
        try (java.io.Reader reader = Files.newBufferedReader(statePath, StandardCharsets.UTF_8)) {
            properties.load(reader);
            for (String key : properties.stringPropertyNames()) {
                if (key.startsWith(OFFSET_PREFIX)) {
                    String encodedPath = key.substring(OFFSET_PREFIX.length());
                    String decodedPath = new String(Base64.getUrlDecoder().decode(encodedPath), StandardCharsets.UTF_8);
                    fileOffsets.put(Path.of(decodedPath), Long.parseLong(properties.getProperty(key)));
                }
            }
            String times = properties.getProperty(EVENT_TIMES_KEY, "");
            if (hasText(times)) {
                for (String value : times.split(",")) {
                    failureTimes.addLast(Long.parseLong(value));
                }
            }
            initialized = true;
            removeExpiredFailures(config);
            log.info("已恢复瀚高登录失败采集状态, files={}, failures={}",
                    fileOffsets.size(), failureTimes.size());
        } catch (Exception e) {
            fileOffsets.clear();
            failureTimes.clear();
            log.warn("恢复瀚高登录失败采集状态失败，将从当前日志末尾建立新基线, path={}", statePath, e);
        }
    }

    private void saveState(ClientConfig config) {
        Path statePath = resolveStatePath(config);
        Properties properties = new Properties();
        fileOffsets.forEach((path, offset) -> {
            String encodedPath = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(path.toString().getBytes(StandardCharsets.UTF_8));
            properties.setProperty(OFFSET_PREFIX + encodedPath, String.valueOf(offset));
        });
        properties.setProperty(EVENT_TIMES_KEY, joinFailureTimes());
        Path parent = statePath.getParent();
        Path temporaryPath = Path.of(statePath + TEMP_FILE_SUFFIX);
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (java.io.Writer writer = Files.newBufferedWriter(temporaryPath, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                properties.store(writer, "瀚高登录失败采集状态");
            }
            try {
                Files.move(temporaryPath, statePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temporaryPath, statePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            log.warn("保存瀚高登录失败采集状态失败, path={}", statePath, e);
        }
    }

    private String joinFailureTimes() {
        StringBuilder builder = new StringBuilder();
        for (Long failureTime : failureTimes) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(failureTime);
        }
        return builder.toString();
    }

    private Path resolveStatePath(ClientConfig config) {
        return Path.of(config.getHighgoCollectorStatePath()).toAbsolutePath().normalize();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
