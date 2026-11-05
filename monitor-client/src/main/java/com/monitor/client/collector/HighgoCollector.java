package com.monitor.client.collector;

import com.monitor.client.config.ClientConfig;
import com.monitor.client.config.DatabaseMonitorConfig;
import com.monitor.common.model.HighgoMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** PostgreSQL 与瀚高指标采集器，复用兼容协议汇总实例指标和 CSV 日志事件。 */
@Slf4j
@Component
public class HighgoCollector implements DatabaseCollector {

    /** PostgreSQL 兼容方言采集器配置标识。 */
    private static final String COLLECTOR_TYPE = "postgresql";

    /** 查询实例中需要监控的全部非模板数据库。 */
    private static final String DATABASE_LIST_SQL =
            "SELECT datname FROM pg_database WHERE datallowconn AND NOT datistemplate ORDER BY datname";
    /** 查询实例中所有允许连接数据库的总容量。 */
    private static final String DATABASE_SIZE_SQL =
            "SELECT datname, pg_database_size(datname) FROM pg_database "
                    + "WHERE datallowconn AND NOT datistemplate ORDER BY datname";
    /** 实例容量展示名称。 */
    private static final String ALL_DATABASES_LABEL = "全部数据库";
    /** 指标不可用时的统一标记值。 */
    private static final int UNAVAILABLE_METRIC_VALUE = -1;
    private static final double BYTES_PER_MEBIBYTE = 1024D * 1024D;

    private final HighgoQueryLogCollector queryLogCollector = new HighgoQueryLogCollector();

    /** 最近一次命中的大表查询事件，在配置窗口内持续上报。 */
    private final List<HighgoMetrics.LargeTableQuery> recentLargeTableQueries = new ArrayList<>();

    /** 最近大表查询事件的过期时间戳。 */
    private long recentLargeTableQueryExpiresAt;

    /**
     * 采集指定客户端配置的 PostgreSQL 或瀚高实例实时指标。
     *
     * @param clientId 客户端唯一标识
     * @param clientConfig 客户端采集配置
     * @return 本轮数据库指标；不可用的指标返回负值
     */
    @Override
    public HighgoMetrics collect(String clientId, ClientConfig clientConfig) {
        DatabaseMonitorConfig config = clientConfig.resolveDatabaseConfig();
        HighgoMetrics metrics = new HighgoMetrics();
        metrics.setClientId(clientId);
        metrics.setDatabaseType(config.getType());
        metrics.setDatabaseProduct(config.getDisplayName());
        metrics.setCollectTime(LocalDateTime.now());
        // 先标记不可用，连接失败或单项查询失败均不能被误认为正常的零值。
        initializeUnavailableMetrics(metrics);
        try (Connection connection = openConnection(config)) {
            collectConnectionMetrics(connection, config, metrics);
            collectExtendedMetrics(connection, config, metrics);
            collectSecurityMetrics(connection, config, metrics);
            log.debug("数据库实时指标采集完成, clientId={}, 统计范围={}, 数据库大小={}MB, "
                            + "连接数={}, 活跃连接={}, 空闲连接={}, 连接负载={}%, 锁等待={}, 登录失败={}",
                    clientId, metrics.getDatabaseName(), metrics.getDatabaseSizeMB(),
                    metrics.getConnectionCount(), metrics.getActiveConnectionCount(),
                    metrics.getIdleConnectionCount(), metrics.getLoad(), metrics.getWaitingSessionCount(),
                    metrics.getLoginFailureCount());
        } catch (Exception e) {
            log.error("采集数据库指标失败, clientId={}, url={}", clientId, config.getUrl(), e);
            metrics.setConnectionCount(UNAVAILABLE_METRIC_VALUE);
            metrics.setActiveConnectionCount(UNAVAILABLE_METRIC_VALUE);
            metrics.setLoad(UNAVAILABLE_METRIC_VALUE);
            metrics.setLoginFailureCount(UNAVAILABLE_METRIC_VALUE);
            metrics.setRepeatedLargeTableQueryCount(UNAVAILABLE_METRIC_VALUE);
        }
        return metrics;
    }

    /** 将未采集成功的指标统一标记为不可用。 */
    private void initializeUnavailableMetrics(HighgoMetrics metrics) {
        metrics.setConnectionCount(UNAVAILABLE_METRIC_VALUE);
        metrics.setActiveConnectionCount(UNAVAILABLE_METRIC_VALUE);
        metrics.setIdleConnectionCount(UNAVAILABLE_METRIC_VALUE);
        metrics.setLoad(UNAVAILABLE_METRIC_VALUE);
        metrics.setWaitingSessionCount(UNAVAILABLE_METRIC_VALUE);
        metrics.setLongTransactionCount(UNAVAILABLE_METRIC_VALUE);
        metrics.setDatabaseSizeMB(UNAVAILABLE_METRIC_VALUE);
        metrics.setCacheHitRatio(UNAVAILABLE_METRIC_VALUE);
        metrics.setLoginFailureCount(UNAVAILABLE_METRIC_VALUE);
        metrics.setRepeatedLargeTableQueryCount(UNAVAILABLE_METRIC_VALUE);
    }

    /** 显式使用对应产品的 JDBC 驱动，避免同一进程中驱动自动匹配错误。 */
    private Connection openConnection(DatabaseMonitorConfig config) throws Exception {
        return openConnection(config, config.getUrl());
    }

    /** 使用配置的 JDBC 驱动连接指定 JDBC 地址。 */
    private Connection openConnection(DatabaseMonitorConfig config, String url) throws Exception {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("数据库连接地址不能为空");
        }
        config.validate();
        Class<?> driverType = Class.forName(config.getDriverClass());
        Driver driver = (Driver) driverType.getDeclaredConstructor().newInstance();
        Properties properties = new Properties();
        properties.setProperty("user", config.getUsername());
        properties.setProperty("password", config.getPassword());
        properties.setProperty("connectTimeout", String.valueOf(config.getConnectTimeoutSeconds()));
        properties.setProperty("socketTimeout", String.valueOf(config.getQueryTimeoutSeconds()));
        Connection connection = driver.connect(url, properties);
        if (connection == null) {
            throw new IllegalStateException("JDBC 驱动拒绝了连接地址: " + url);
        }
        log.debug("已使用配置的 JDBC 驱动建立连接, type={}, collector={}, url={}",
                config.getType(), config.getCollector(), url);
        return connection;
    }

    /** 从数据库 CSV 日志采集登录失败和大表全表查询安全指标。 */
    private void collectSecurityMetrics(Connection connection, DatabaseMonitorConfig config, HighgoMetrics metrics) throws Exception {
        if (!config.isQueryLogEnabled()) {
            // 未部署 CSV 日志时不进行无意义的目录轮询，指标统一标记为不可用而非误报为零。
            metrics.setLoginFailureCount(UNAVAILABLE_METRIC_VALUE);
            metrics.setRepeatedLargeTableQueryCount(UNAVAILABLE_METRIC_VALUE);
            log.debug("数据库日志事件采集未开启, type={}, collector={}",
                    config.getType(), config.getCollector());
            return;
        }
        List<String> databaseNames = listDatabases(connection, config);
        HighgoQueryLogCollector.CollectionResult result = queryLogCollector.collect(
                databaseNames, config, this::openDatabaseConnection);
        metrics.setLoginFailureCount(result.getLoginFailureCount());
        if (!result.isAvailable()) {
            metrics.setRepeatedLargeTableQueryCount(UNAVAILABLE_METRIC_VALUE);
            return;
        }
        List<HighgoMetrics.LargeTableQuery> windowQueries = updateLargeTableQueryWindow(
                result.getLargeTableQueries(), config.getLargeQueryWindowMinutes());
        metrics.setRepeatedLargeTableQueryCount(windowQueries.stream()
                .mapToLong(HighgoMetrics.LargeTableQuery::getOccurrences).sum());
        metrics.getRepeatedLargeTableQueries().addAll(windowQueries);
    }

    /** 在配置的滚动窗口内保留最近大表查询，避免离散事件只上报一个采集周期。 */
    private synchronized List<HighgoMetrics.LargeTableQuery> updateLargeTableQueryWindow(
            List<HighgoMetrics.LargeTableQuery> collectedQueries, int windowMinutes) {
        long now = System.currentTimeMillis();
        if (!collectedQueries.isEmpty()) {
            recentLargeTableQueries.clear();
            recentLargeTableQueries.addAll(collectedQueries);
            recentLargeTableQueryExpiresAt = now
                    + Duration.ofMinutes(Math.max(1, windowMinutes)).toMillis();
            log.info("数据库大表查询事件进入持续上报窗口, count={}, windowMinutes={}",
                    collectedQueries.size(), windowMinutes);
        } else if (now >= recentLargeTableQueryExpiresAt) {
            recentLargeTableQueries.clear();
        }
        return new ArrayList<>(recentLargeTableQueries);
    }

    /** 查询实例中全部允许连接的非模板数据库。 */
    private List<String> listDatabases(Connection connection, DatabaseMonitorConfig config) throws Exception {
        List<String> databaseNames = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(DATABASE_LIST_SQL);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String databaseName = resultSet.getString(1);
                if (!isExcludedDatabase(databaseName, config)) {
                    databaseNames.add(databaseName);
                }
            }
        }
        log.debug("发现数据库实例数据库, count={}, databases={}", databaseNames.size(), databaseNames);
        return databaseNames;
    }

    /** 判断数据库是否被配置为排除监控。 */
    private boolean isExcludedDatabase(String databaseName, DatabaseMonitorConfig config) {
        return config.getExcludedDatabases().stream()
                .filter(name -> name != null && !name.trim().isEmpty())
                .anyMatch(name -> name.trim().equalsIgnoreCase(databaseName));
    }

    private void collectConnectionMetrics(Connection connection, DatabaseMonitorConfig config,
            HighgoMetrics metrics) throws Exception {
        // PostgreSQL 后台进程不占用客户端连接名额，不能一起计入连接数和负载。
        String backendFilter = config.isClientBackendOnly()
                ? " AND backend_type = 'client backend'" : "";
        try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT count(*) AS total, "
                        + "sum(CASE WHEN state = 'active' THEN 1 ELSE 0 END) AS active, "
                        + "sum(CASE WHEN state = 'idle' THEN 1 ELSE 0 END) AS idle, "
                        + "round(count(*) * 100.0 / NULLIF(current_setting('max_connections')::numeric, 0), 2) AS load "
                        + "FROM pg_stat_activity WHERE pid <> pg_backend_pid()" + backendFilter)) {
            statement.setQueryTimeout(config.getQueryTimeoutSeconds());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    metrics.setConnectionCount(rs.getLong("total"));
                    metrics.setActiveConnectionCount(rs.getLong("active"));
                    metrics.setIdleConnectionCount(rs.getLong("idle"));
                    metrics.setLoad(rs.getDouble("load"));
                }
            }
        }
    }

    /** 独立采集版本相关的扩展指标，单项失败不影响基础连接指标上报。 */
    private void collectExtendedMetrics(Connection connection, DatabaseMonitorConfig config, HighgoMetrics metrics) {
        metrics.setWaitingSessionCount(queryLong(connection,
                "SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock' "
                        + "AND pid <> pg_backend_pid()", "锁等待会话数"));
        metrics.setLongTransactionCount(queryLong(connection,
                "SELECT count(*) FROM pg_stat_activity WHERE xact_start < now() - make_interval(secs => "
                        + config.getLongTransactionSeconds() + ") AND pid <> pg_backend_pid()",
                "长事务数"));
        collectDatabaseSize(connection, config, metrics);
        metrics.setCacheHitRatio(queryDouble(connection,
                "SELECT coalesce(round(sum(s.blks_hit) * 100.0 "
                        + "/ NULLIF(sum(s.blks_hit + s.blks_read), 0), 2), 0) "
                        + "FROM pg_stat_database s JOIN pg_database d ON d.oid = s.datid "
                        + "WHERE d.datallowconn AND NOT d.datistemplate",
                "缓存命中率"));
    }

    /** 汇总实例内所有允许连接数据库的容量，避免只展示当前连接库大小。 */
    private void collectDatabaseSize(Connection connection, DatabaseMonitorConfig config, HighgoMetrics metrics) {
        metrics.setDatabaseName(ALL_DATABASES_LABEL);
        try (PreparedStatement statement = connection.prepareStatement(DATABASE_SIZE_SQL)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                double totalBytes = 0D;
                while (resultSet.next()) {
                    if (!isExcludedDatabase(resultSet.getString(1), config)) {
                        totalBytes += resultSet.getDouble(2);
                    }
                }
                metrics.setDatabaseSizeMB(totalBytes / BYTES_PER_MEBIBYTE);
            }
        } catch (Exception e) {
            metrics.setDatabaseSizeMB(UNAVAILABLE_METRIC_VALUE);
            log.warn("数据库实例数据库总容量采集失败", e);
        }
    }

    /** 执行返回单个整数的实时指标 SQL，失败时记录完整异常并标记不可用。 */
    private long queryLong(Connection connection, String sql, String metricName) {
        try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getLong(1) : 0L;
        } catch (Exception e) {
            log.warn("数据库扩展指标采集失败, 指标={}", metricName, e);
            return UNAVAILABLE_METRIC_VALUE;
        }
    }

    /** 执行返回单个小数的实时指标 SQL，失败时记录完整异常并标记不可用。 */
    private double queryDouble(Connection connection, String sql, String metricName) {
        try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getDouble(1) : 0D;
        } catch (Exception e) {
            log.warn("数据库扩展指标采集失败, 指标={}", metricName, e);
            return UNAVAILABLE_METRIC_VALUE;
        }
    }

    /** 连接实例中的指定数据库，入口 URL 仅用于提供主机、端口及连接参数。 */
    private Connection openDatabaseConnection(DatabaseMonitorConfig config, String databaseName) throws Exception {
        return openConnection(config, replaceDatabaseName(config.getUrl(), databaseName));
    }

    /** 替换 JDBC URL 中的数据库路径并保留原查询参数。 */
    private String replaceDatabaseName(String baseUrl, String databaseName) {
        int queryIndex = baseUrl.indexOf('?');
        String endpoint = queryIndex >= 0 ? baseUrl.substring(0, queryIndex) : baseUrl;
        String parameters = queryIndex >= 0 ? baseUrl.substring(queryIndex) : "";
        int slashIndex = endpoint.lastIndexOf('/');
        int protocolIndex = endpoint.indexOf("//");
        if (slashIndex <= protocolIndex + 1) {
            throw new IllegalArgumentException("JDBC 地址缺少入口数据库名称: " + baseUrl);
        }
        String encodedDatabaseName = URLEncoder.encode(databaseName, StandardCharsets.UTF_8)
                .replace("+", "%20");
        return endpoint.substring(0, slashIndex + 1) + encodedDatabaseName + parameters;
    }

    /**
     * 返回 PostgreSQL 协议兼容数据库的采集器标识。
     *
     * @return 配置使用的采集器标识
     */
    @Override
    public String getCollectorType() {
        return COLLECTOR_TYPE;
    }

}
