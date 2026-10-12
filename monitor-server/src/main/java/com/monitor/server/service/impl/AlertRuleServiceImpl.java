package com.monitor.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.monitor.common.model.HighgoMetrics;
import com.monitor.common.model.ServerMessage;
import com.monitor.common.model.SystemMetrics;
import com.monitor.server.model.dto.AlertEventView;
import com.monitor.server.model.dto.AlertRuleRequest;
import com.monitor.server.model.entity.AlertRule;
import com.monitor.server.model.entity.AlertEvent;
import com.monitor.server.model.entity.NodeConfig;
import com.monitor.server.mapper.AlertRuleMapper;
import com.monitor.server.mapper.AlertEventMapper;
import com.monitor.server.service.AlertRuleService;
import com.monitor.server.service.MetricsService;
import com.monitor.server.service.NodeConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 告警规则管理与实时指标告警生命周期业务实现类。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertRuleServiceImpl implements AlertRuleService {

    private static final String LARGE_TABLE_QUERY_METRIC = "LARGE_TABLE_QUERY";
    private static final String LOGIN_FAILURE_METRIC = "LOGIN_FAILURE";
    private static final String CPU_METRIC = "CPU";
    private static final String MEMORY_METRIC = "MEMORY";
    private static final String DISK_METRIC = "DISK";
    private static final String JVM_METRIC = "JVM";
    private static final Map<String, String> SYSTEM_METRIC_NAMES = Map.of(
            CPU_METRIC, "CPU使用率",
            MEMORY_METRIC, "内存使用率",
            DISK_METRIC, "硬盘使用率",
            JVM_METRIC, "JVM堆内存使用率"
    );
    private static final Set<String> SUPPORTED_METRICS =
            Set.of(CPU_METRIC, MEMORY_METRIC, DISK_METRIC, JVM_METRIC,
                    LARGE_TABLE_QUERY_METRIC, LOGIN_FAILURE_METRIC);
    private static final int MAX_ALERT_MESSAGE_LENGTH = 500;
    private static final String TRUNCATION_SUFFIX = "...";

    private final AlertRuleMapper mapper;
    private final AlertEventMapper eventMapper;
    private final MetricsService metricsService;
    private final NodeConfigService nodeConfigService;

    /** 离散安全事件在活动告警中的最短保留时间。 */
    @Value("${monitor.server.discrete-alert-hold-minutes}")
    private long discreteAlertHoldMinutes;

    /** 登录失败告警历史记录的连续事件合并窗口。 */
    @Value("${monitor.server.login-alert-merge-hours}")
    private long loginAlertMergeHours;

    /**
     * 查询全部告警规则。
     *
     * @return 告警规则列表
     */
    @Override
    public List<AlertRule> list() {
        return mapper.selectList(new QueryWrapper<AlertRule>().orderByDesc("id"));
    }

    /**
     * 创建告警规则。
     *
     * @param request 告警规则请求
     * @return 已创建的告警规则
     */
    @Override
    public AlertRule save(AlertRuleRequest request) {
        if (!StringUtils.hasText(request.getName()) || !StringUtils.hasText(request.getMetric())) {
            throw new IllegalArgumentException("告警名称和指标不能为空");
        }
        String metric = request.getMetric().toUpperCase();
        if (!SUPPORTED_METRICS.contains(metric)) {
            throw new IllegalArgumentException("不支持的告警指标: " + metric);
        }
        BigDecimal warning = request.getWarningThreshold() == null ? BigDecimal.valueOf(70) : request.getWarningThreshold();
        BigDecimal critical = request.getCriticalThreshold() == null ? BigDecimal.valueOf(90) : request.getCriticalThreshold();
        validateThreshold(metric, warning, critical);
        AlertRule rule = new AlertRule();
        rule.setName(request.getName()); rule.setNodeClientId(request.getNodeClientId()); rule.setMetric(metric);
        rule.setWarningThreshold(warning); rule.setCriticalThreshold(critical); rule.setEnabled(request.getEnabled() == null || request.getEnabled());
        LocalDateTime now = currentUtcTime();
        rule.setCreatedAt(now); rule.setUpdatedAt(now);
        mapper.insert(rule);
        log.info("告警规则创建完成, ruleId={}, metric={}, warning={}, critical={}",
                rule.getId(), metric, warning, critical);
        return rule;
    }

    /**
     * 更新告警规则。
     *
     * @param id 规则主键
     * @param request 告警规则请求
     * @return 已更新的告警规则
     */
    @Override
    public AlertRule update(Long id, AlertRuleRequest request) {
        AlertRule rule = mapper.selectById(id);
        if (rule == null) throw new IllegalArgumentException("告警规则不存在");
        AlertRule created = saveRequest(request, rule);
        mapper.updateById(created);
        log.info("告警规则更新完成, ruleId={}, metric={}", id, created.getMetric());
        return created;
    }

    /**
     * 删除告警规则。
     *
     * @param id 规则主键
     */
    @Override
    public void delete(Long id) {
        mapper.deleteById(id);
        log.info("告警规则删除完成, ruleId={}", id);
    }

    /**
     * 查询首页尚未人工确认的待处理告警，指标恢复后仍保留到人工确认。
     *
     * @return 待处理告警列表
     */
    @Override
    public List<AlertEventView> activeAlerts() {
        return toViews(eventMapper.selectList(new QueryWrapper<AlertEvent>()
                .eq("requires_acknowledgement", true)
                .eq("acknowledged", false)
                .orderByDesc("started_at")), list());
    }

    /**
     * 查询当前仍超过阈值的全部活动告警，包括已经人工确认的告警。
     *
     * @return 当前活动告警列表
     */
    @Override
    public List<AlertEventView> currentAlerts() {
        return toViews(eventMapper.selectList(new QueryWrapper<AlertEvent>()
                .eq("status", "ACTIVE")
                .orderByDesc("started_at")), list());
    }

    /** 评估实时指标并同步告警生命周期。 */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public synchronized void evaluateAlerts() {
        syncEvents();
    }

    /**
     * 查询最近告警历史。
     *
     * @return 告警历史列表
     */
    @Override
    public synchronized List<AlertEventView> history() {
        List<AlertEvent> events = eventMapper.selectPage(
                new Page<>(1, 200),
                new QueryWrapper<AlertEvent>().orderByDesc("started_at")
        ).getRecords();
        List<AlertEvent> mergedEvents = mergeRepeatedLoginAlerts(events,
                Duration.ofHours(Math.max(1L, loginAlertMergeHours)));
        return toViews(mergedEvents, list());
    }

    /**
     * 人工确认告警，确认只影响首页待处理展示，不删除历史记录。
     *
     * @param id 告警事件主键
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public synchronized void acknowledge(Long id) {
        AlertEvent event = eventMapper.selectById(id);
        if (event == null) {
            throw new IllegalArgumentException("告警记录不存在");
        }
        if (Boolean.TRUE.equals(event.getAcknowledged())) {
            return;
        }
        event.setAcknowledged(true);
        event.setAcknowledgedAt(currentUtcTime());
        eventMapper.updateById(event);
        log.info("告警已人工确认, eventId={}, clientId={}, metric={}",
                event.getId(), event.getClientId(), event.getMetric());
    }

    private void syncEvents() {
        Set<String> currentKeys = new HashSet<>();
        Map<String, AlertEvent> activeEvents = new java.util.HashMap<>();
        eventMapper.selectList(new QueryWrapper<AlertEvent>().eq("status", "ACTIVE"))
                .forEach(event -> activeEvents.put(event.getRuleId() + "@" + event.getClientId(), event));
        Map<String, NodeConfig> nodeMap = nodeConfigService.configMap();
        List<AlertRule> rules = mapper.selectList(new QueryWrapper<AlertRule>().eq("enabled", true));
        List<ServerMessage.ClientSnapshot> snapshots = metricsService.buildAggregatedMessage().getClientSnapshots();
        Map<String, ServerMessage.ClientSnapshot> snapshotMap = snapshots.stream()
                .collect(Collectors.toMap(ServerMessage.ClientSnapshot::getClientId,
                        Function.identity(), (current, ignored) -> current));
        for (AlertRule rule : rules) {
            for (ServerMessage.ClientSnapshot snapshot : snapshots) {
                if (StringUtils.hasText(rule.getNodeClientId()) && !rule.getNodeClientId().equals(snapshot.getClientId())) continue;
                NodeConfig node = nodeMap.get(snapshot.getClientId());
                if (node != null && Boolean.FALSE.equals(node.getEnabled())) continue;
                BigDecimal value = metricValue(snapshot, rule.getMetric());
                if (value == null || value.compareTo(rule.getWarningThreshold()) < 0) continue;
                boolean critical = value.compareTo(rule.getCriticalThreshold()) >= 0;
                String level = critical ? "CRITICAL" : "WARNING";
                String nodeName = node != null && StringUtils.hasText(node.getDisplayName()) ? node.getDisplayName() : snapshot.getHostname();
                String key = rule.getId() + "@" + snapshot.getClientId();
                currentKeys.add(key);
                String eventKey = rule.getId() + "@" + snapshot.getClientId();
                AlertEvent event = activeEvents.get(eventKey);
                LocalDateTime now = currentUtcTime();
                if (shouldCreateNextLoginFailureEvent(event, rule.getMetric(), value)) {
                    resolveAcknowledgedLoginFailureEvent(event, now);
                    event = null;
                }
                if (event == null) {
                    event = createActiveEvent(rule.getId(), snapshot.getClientId(), nodeName, now);
                }
                event.setNodeName(nodeName); event.setMetric(rule.getMetric()); event.setValue(value); event.setLevel(level);
                event.setMessage(buildAlertMessage(snapshot, rule.getMetric(), nodeName, value));
                event.setLastSeenAt(now); event.setDurationSeconds(java.time.Duration.between(event.getStartedAt(), now).getSeconds()); event.setResolvedAt(null);
                if (event.getId() == null) {
                    eventMapper.insert(event);
                    log.warn("告警触发, ruleId={}, clientId={}, metric={}, level={}, message={}",
                            rule.getId(), snapshot.getClientId(), rule.getMetric(), level, event.getMessage());
                } else {
                    eventMapper.updateById(event);
                }
                activeEvents.put(eventKey, event);
            }
        }
        LocalDateTime now = currentUtcTime();
        for (AlertEvent event : activeEvents.values()) {
            if (shouldResolveAlert(event, currentKeys, snapshotMap)) {
                if (shouldRetainDiscreteAlert(event, now)) {
                    continue;
                }
                event.setStatus("RESOLVED"); event.setResolvedAt(now); event.setDurationSeconds(java.time.Duration.between(event.getStartedAt(), now).getSeconds()); eventMapper.updateById(event);
                log.info("告警恢复, ruleId={}, clientId={}, metric={}",
                        event.getRuleId(), event.getClientId(), event.getMetric());
            }
        }
    }

    private List<AlertEventView> toViews(List<AlertEvent> events, List<AlertRule> rules) {
        Map<Long, AlertRule> ruleMap = new java.util.HashMap<>();
        rules.forEach(rule -> ruleMap.put(rule.getId(), rule));
        List<AlertEventView> result = new ArrayList<>();
        for (AlertEvent event : events) {
            AlertRule rule = ruleMap.get(event.getRuleId());
            AlertEventView view = new AlertEventView();
            view.setId(event.getId());
            view.setRuleId(event.getRuleId());
            view.setRuleName(rule == null ? "已删除规则" : rule.getName());
            view.setClientId(event.getClientId());
            view.setNodeName(event.getNodeName());
            view.setMetric(event.getMetric());
            view.setValue(event.getValue());
            view.setLevel(event.getLevel());
            view.setStatus(event.getStatus());
            view.setMessage(event.getMessage());
            view.setStartedAt(event.getStartedAt());
            view.setLastSeenAt(event.getLastSeenAt());
            view.setResolvedAt(event.getResolvedAt());
            view.setDurationSeconds(event.getDurationSeconds());
            view.setAcknowledged(Boolean.TRUE.equals(event.getAcknowledged()));
            view.setAcknowledgedAt(event.getAcknowledgedAt());
            if (rule != null) {
                view.setThreshold("CRITICAL".equals(event.getLevel())
                        ? rule.getCriticalThreshold() : rule.getWarningThreshold());
            }
            result.add(view);
        }
        return result;
    }

    static List<AlertEvent> mergeRepeatedLoginAlerts(List<AlertEvent> events,
            Duration mergeWindow) {
        List<AlertEvent> result = new ArrayList<>();
        Map<String, AlertEvent> latestGroups = new java.util.HashMap<>();
        for (AlertEvent event : events) {
            if (!LOGIN_FAILURE_METRIC.equals(event.getMetric())) {
                result.add(event);
                continue;
            }
            String groupKey = loginAlertGroupKey(event);
            AlertEvent newerEvent = latestGroups.get(groupKey);
            if (newerEvent == null || !isContinuousLoginAlert(event, newerEvent, mergeWindow)) {
                latestGroups.put(groupKey, event);
                result.add(event);
                continue;
            }
            LocalDateTime earlierStartedAt = event.getStartedAt();
            if (earlierStartedAt != null && (newerEvent.getStartedAt() == null
                    || earlierStartedAt.isBefore(newerEvent.getStartedAt()))) {
                newerEvent.setStartedAt(earlierStartedAt);
                LocalDateTime latestTime = newerEvent.getLastSeenAt() == null
                        ? newerEvent.getResolvedAt() : newerEvent.getLastSeenAt();
                if (latestTime != null) {
                    newerEvent.setDurationSeconds(Duration.between(earlierStartedAt, latestTime).getSeconds());
                }
            }
        }
        return result;
    }

    private static String loginAlertGroupKey(AlertEvent event) {
        String value = event.getValue() == null ? "" : event.getValue().stripTrailingZeros().toPlainString();
        return event.getRuleId() + "@" + event.getClientId() + "@" + value;
    }

    private static boolean isContinuousLoginAlert(AlertEvent olderEvent, AlertEvent newerEvent,
            Duration mergeWindow) {
        if (olderEvent.getStartedAt() == null || newerEvent.getStartedAt() == null) {
            return false;
        }
        LocalDateTime olderEnd = olderEvent.getLastSeenAt() != null
                ? olderEvent.getLastSeenAt() : olderEvent.getResolvedAt();
        if (olderEnd == null) {
            olderEnd = olderEvent.getStartedAt();
        }
        Duration gap = Duration.between(olderEnd, newerEvent.getStartedAt());
        return gap.isNegative() || gap.compareTo(mergeWindow) <= 0;
    }

    private AlertRule saveRequest(AlertRuleRequest request, AlertRule rule) {
        if (!StringUtils.hasText(request.getName()) || !StringUtils.hasText(request.getMetric())) throw new IllegalArgumentException("告警名称和指标不能为空");
        String metric = request.getMetric().toUpperCase();
        if (!SUPPORTED_METRICS.contains(metric)) throw new IllegalArgumentException("不支持的告警指标: " + metric);
        BigDecimal warning = request.getWarningThreshold() == null ? BigDecimal.valueOf(70) : request.getWarningThreshold();
        BigDecimal critical = request.getCriticalThreshold() == null ? BigDecimal.valueOf(90) : request.getCriticalThreshold();
        validateThreshold(metric, warning, critical);
        rule.setName(request.getName()); rule.setNodeClientId(request.getNodeClientId()); rule.setMetric(metric);
        rule.setWarningThreshold(warning); rule.setCriticalThreshold(critical); rule.setEnabled(request.getEnabled() == null || request.getEnabled()); rule.setUpdatedAt(currentUtcTime());
        return rule;
    }

    private BigDecimal metricValue(ServerMessage.ClientSnapshot snapshot, String metric) {
        if (LARGE_TABLE_QUERY_METRIC.equals(metric)) {
            HighgoMetrics metrics = snapshot.getHighgoMetrics();
            return metrics == null || metrics.getRepeatedLargeTableQueryCount() < 0
                    ? null : BigDecimal.valueOf(metrics.getRepeatedLargeTableQueryCount());
        }
        if (LOGIN_FAILURE_METRIC.equals(metric)) {
            HighgoMetrics metrics = snapshot.getHighgoMetrics();
            return metrics == null || metrics.getLoginFailureCount() < 0
                    ? null : BigDecimal.valueOf(metrics.getLoginFailureCount());
        }
        SystemMetrics metrics = snapshot.getSystemMetrics();
        if (metrics == null) return null;
        switch (metric) {
            case CPU_METRIC: return BigDecimal.valueOf(metrics.getCpuUsage());
            case MEMORY_METRIC: return BigDecimal.valueOf(metrics.getMemoryUsage());
            case DISK_METRIC: return BigDecimal.valueOf(metrics.getDiskUsage());
            case JVM_METRIC: return BigDecimal.valueOf(metrics.getJvmHeapUsage());
            default: return null;
        }
    }

    private boolean shouldCreateNextLoginFailureEvent(AlertEvent event, String metric,
            BigDecimal currentValue) {
        return LOGIN_FAILURE_METRIC.equals(metric)
                && event != null
                && Boolean.TRUE.equals(event.getAcknowledged())
                && event.getValue() != null
                && currentValue.compareTo(event.getValue()) > 0;
    }

    private void resolveAcknowledgedLoginFailureEvent(AlertEvent event, LocalDateTime now) {
        event.setStatus("RESOLVED");
        event.setResolvedAt(now);
        event.setDurationSeconds(Duration.between(event.getStartedAt(), now).getSeconds());
        eventMapper.updateById(event);
        log.info("已确认登录失败告警检测到新事件，结束旧告警并创建新告警, eventId={}, clientId={}, oldValue={}",
                event.getId(), event.getClientId(), event.getValue());
    }

    private AlertEvent createActiveEvent(Long ruleId, String clientId, String nodeName,
            LocalDateTime now) {
        AlertEvent event = new AlertEvent();
        event.setRuleId(ruleId);
        event.setClientId(clientId);
        event.setNodeName(nodeName);
        event.setStartedAt(now);
        event.setStatus("ACTIVE");
        event.setAcknowledged(false);
        return event;
    }

    static boolean shouldResolveAlert(AlertEvent event, Set<String> currentKeys,
            Map<String, ServerMessage.ClientSnapshot> snapshotMap) {
        String eventKey = event.getRuleId() + "@" + event.getClientId();
        if (currentKeys.contains(eventKey)) {
            return false;
        }
        ServerMessage.ClientSnapshot snapshot = snapshotMap.get(event.getClientId());
        // Server 重启后各类指标分批到达，必须收到当前告警对应的有效指标才能判定恢复。
        return hasValidMetricEvidence(event.getMetric(), snapshot);
    }

    private static boolean hasValidMetricEvidence(String metric,
            ServerMessage.ClientSnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }
        HighgoMetrics highgoMetrics = snapshot.getHighgoMetrics();
        if (LOGIN_FAILURE_METRIC.equals(metric)) {
            return highgoMetrics != null && highgoMetrics.getLoginFailureCount() >= 0;
        }
        if (LARGE_TABLE_QUERY_METRIC.equals(metric)) {
            return highgoMetrics != null
                    && highgoMetrics.getRepeatedLargeTableQueryCount() >= 0;
        }
        return snapshot.getSystemMetrics() != null;
    }

    /** 离散事件在展示窗口内保持活动状态，避免下一采集周期无新事件时立即恢复。 */
    private boolean shouldRetainDiscreteAlert(AlertEvent event, LocalDateTime now) {
        if (!LARGE_TABLE_QUERY_METRIC.equals(event.getMetric()) || event.getStartedAt() == null) {
            return false;
        }
        LocalDateTime retainUntil = event.getStartedAt()
                .plusMinutes(Math.max(1L, discreteAlertHoldMinutes));
        if (retainUntil.isAfter(now)) {
            event.setDurationSeconds(java.time.Duration.between(event.getStartedAt(), now).getSeconds());
            eventMapper.updateById(event);
            return true;
        }
        return false;
    }

    private void validateThreshold(String metric, BigDecimal warning, BigDecimal critical) {
        boolean invalidOrder = warning.compareTo(BigDecimal.ZERO) < 0
                || warning.compareTo(critical) >= 0;
        boolean invalidPercentage = !LARGE_TABLE_QUERY_METRIC.equals(metric)
                && !LOGIN_FAILURE_METRIC.equals(metric)
                && critical.compareTo(BigDecimal.valueOf(100)) > 0;
        if (invalidOrder || invalidPercentage) {
            throw new IllegalArgumentException("告警阈值必须满足 0 <= 预警 < 严重，百分比指标严重阈值不能超过 100");
        }
    }

    private String buildAlertMessage(ServerMessage.ClientSnapshot snapshot, String metric,
            String nodeName, BigDecimal value) {
        if (LOGIN_FAILURE_METRIC.equals(metric)) {
            return nodeName + " 检测到瀚高数据库登录失败 "
                    + value.stripTrailingZeros().toPlainString() + " 次";
        }
        if (!LARGE_TABLE_QUERY_METRIC.equals(metric)) {
            return formatSystemMetricAlertMessage(nodeName, metric, value);
        }
        HighgoMetrics metrics = snapshot.getHighgoMetrics();
        if (metrics == null || metrics.getRepeatedLargeTableQueries().isEmpty()) {
            return "检测到瀚高数据库全表查询：事件详情暂不可用";
        }
        HighgoMetrics.LargeTableQuery query = metrics.getRepeatedLargeTableQueries().get(0);
        return formatLargeTableQueryMessage(query);
    }

    /**
     * 将系统指标告警格式化为面向用户的中文文案。
     *
     * @param nodeName 节点名称
     * @param metric 指标代码
     * @param value 指标值
     * @return 中文告警文案
     */
    static String formatSystemMetricAlertMessage(String nodeName, String metric, BigDecimal value) {
        String metricName = SYSTEM_METRIC_NAMES.getOrDefault(metric, metric);
        return nodeName + "机器的" + metricName + "当前为"
                + value.stripTrailingZeros().toPlainString() + "%";
    }

    static String formatLargeTableQueryMessage(HighgoMetrics.LargeTableQuery query) {
        String prefix = "检测到瀚高数据库全表查询：数据库=" + query.getDatabaseName()
                + "，账号=" + query.getUserName()
                + "，表=" + query.getTableName()
                + "，表大小=" + query.getTableSizeMB() + "MB"
                + "，SQL=";
        String suffix = "，时间：" + query.getQueryTime();
        String sql = query.getQuery() == null ? "" : query.getQuery();
        int maxSqlLength = Math.max(0,
                MAX_ALERT_MESSAGE_LENGTH - prefix.length() - suffix.length());
        if (sql.length() > maxSqlLength) {
            int retainedLength = Math.max(0, maxSqlLength - TRUNCATION_SUFFIX.length());
            sql = sql.substring(0, retainedLength) + TRUNCATION_SUFFIX;
        }
        return prefix + sql + suffix;
    }

    private LocalDateTime currentUtcTime() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}

