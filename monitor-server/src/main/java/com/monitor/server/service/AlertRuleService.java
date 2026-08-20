package com.monitor.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.monitor.common.model.ServerMessage;
import com.monitor.common.model.SystemMetrics;
import com.monitor.server.dto.AlertEventView;
import com.monitor.server.dto.AlertRuleRequest;
import com.monitor.server.entity.AlertRule;
import com.monitor.server.entity.AlertEvent;
import com.monitor.server.entity.NodeConfig;
import com.monitor.server.mapper.AlertRuleMapper;
import com.monitor.server.mapper.AlertEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AlertRuleService {

    private final AlertRuleMapper mapper;
    private final AlertEventMapper eventMapper;
    private final MetricsService metricsService;
    private final NodeConfigService nodeConfigService;

    public List<AlertRule> list() {
        return mapper.selectList(new QueryWrapper<AlertRule>().orderByDesc("id"));
    }

    public AlertRule save(AlertRuleRequest request) {
        if (!StringUtils.hasText(request.getName()) || !StringUtils.hasText(request.getMetric())) {
            throw new IllegalArgumentException("告警名称和指标不能为空");
        }
        String metric = request.getMetric().toUpperCase();
        if (!List.of("CPU", "MEMORY", "DISK", "JVM").contains(metric)) {
            throw new IllegalArgumentException("不支持的告警指标: " + metric);
        }
        BigDecimal warning = request.getWarningThreshold() == null ? BigDecimal.valueOf(70) : request.getWarningThreshold();
        BigDecimal critical = request.getCriticalThreshold() == null ? BigDecimal.valueOf(90) : request.getCriticalThreshold();
        if (warning.compareTo(BigDecimal.ZERO) < 0 || critical.compareTo(BigDecimal.valueOf(100)) > 0 || warning.compareTo(critical) >= 0) {
            throw new IllegalArgumentException("告警阈值必须满足 0 <= 预警 < 严重 <= 100");
        }
        AlertRule rule = new AlertRule();
        rule.setName(request.getName()); rule.setNodeClientId(request.getNodeClientId()); rule.setMetric(metric);
        rule.setWarningThreshold(warning); rule.setCriticalThreshold(critical); rule.setEnabled(request.getEnabled() == null || request.getEnabled());
        rule.setCreatedAt(LocalDateTime.now()); rule.setUpdatedAt(LocalDateTime.now());
        mapper.insert(rule);
        return rule;
    }

    public AlertRule update(Long id, AlertRuleRequest request) {
        AlertRule rule = mapper.selectById(id);
        if (rule == null) throw new IllegalArgumentException("告警规则不存在");
        AlertRule created = saveRequest(request, rule);
        mapper.updateById(created);
        return created;
    }

    public void delete(Long id) { mapper.deleteById(id); }

    /** Evaluate current metrics and persist the alert lifecycle before returning active alerts. */
    public synchronized List<AlertEventView> activeAlerts() {
        syncEvents();
        return toViews(eventMapper.selectList(new QueryWrapper<AlertEvent>().eq("status", "ACTIVE").orderByDesc("started_at")), list());
    }

    public synchronized List<AlertEventView> history() {
        List<AlertEvent> events = eventMapper.selectList(new QueryWrapper<AlertEvent>().orderByDesc("started_at").last("LIMIT 200"));
        return toViews(events, list());
    }

    private void syncEvents() {
        Set<String> currentKeys = new HashSet<>();
        Map<String, NodeConfig> nodeMap = nodeConfigService.configMap();
        List<AlertRule> rules = mapper.selectList(new QueryWrapper<AlertRule>().eq("enabled", true));
        List<ServerMessage.ClientSnapshot> snapshots = metricsService.buildAggregatedMessage().getClientSnapshots();
        for (AlertRule rule : rules) {
            for (ServerMessage.ClientSnapshot snapshot : snapshots) {
                if (StringUtils.hasText(rule.getNodeClientId()) && !rule.getNodeClientId().equals(snapshot.getClientId())) continue;
                NodeConfig node = nodeMap.get(snapshot.getClientId());
                if (node != null && Boolean.FALSE.equals(node.getEnabled())) continue;
                BigDecimal value = metricValue(snapshot.getSystemMetrics(), rule.getMetric());
                if (value == null || value.compareTo(rule.getWarningThreshold()) < 0) continue;
                boolean critical = value.compareTo(rule.getCriticalThreshold()) >= 0;
                String level = critical ? "CRITICAL" : "WARNING";
                String nodeName = node != null && StringUtils.hasText(node.getDisplayName()) ? node.getDisplayName() : snapshot.getHostname();
                String key = rule.getId() + "@" + snapshot.getClientId();
                currentKeys.add(key);
                AlertEvent event = eventMapper.selectOne(new QueryWrapper<AlertEvent>().eq("rule_id", rule.getId()).eq("client_id", snapshot.getClientId()).eq("status", "ACTIVE").last("LIMIT 1"));
                LocalDateTime now = LocalDateTime.now();
                if (event == null) {
                    event = new AlertEvent(); event.setRuleId(rule.getId()); event.setClientId(snapshot.getClientId()); event.setNodeName(nodeName); event.setStartedAt(now); event.setStatus("ACTIVE");
                }
                event.setNodeName(nodeName); event.setMetric(rule.getMetric()); event.setValue(value); event.setLevel(level); event.setMessage(nodeName + " 的 " + rule.getMetric() + " 当前为 " + value.stripTrailingZeros().toPlainString() + "%"); event.setLastSeenAt(now); event.setDurationSeconds(java.time.Duration.between(event.getStartedAt(), now).getSeconds()); event.setResolvedAt(null);
                if (event.getId() == null) eventMapper.insert(event); else eventMapper.updateById(event);
            }
        }
        List<AlertEvent> active = eventMapper.selectList(new QueryWrapper<AlertEvent>().eq("status", "ACTIVE"));
        LocalDateTime now = LocalDateTime.now();
        for (AlertEvent event : active) {
            if (!currentKeys.contains(event.getRuleId() + "@" + event.getClientId())) {
                event.setStatus("RESOLVED"); event.setResolvedAt(now); event.setDurationSeconds(java.time.Duration.between(event.getStartedAt(), now).getSeconds()); eventMapper.updateById(event);
            }
        }
    }

    private List<AlertEventView> toViews(List<AlertEvent> events, List<AlertRule> rules) {
        Map<Long, AlertRule> ruleMap = new java.util.HashMap<>(); rules.forEach(rule -> ruleMap.put(rule.getId(), rule));
        List<AlertEventView> result = new ArrayList<>();
        for (AlertEvent event : events) {
            AlertEventView view = new AlertEventView(); AlertRule rule = ruleMap.get(event.getRuleId());
            view.setRuleId(event.getRuleId()); view.setRuleName(rule == null ? "已删除规则" : rule.getName()); view.setClientId(event.getClientId()); view.setNodeName(event.getNodeName()); view.setMetric(event.getMetric()); view.setValue(event.getValue()); view.setLevel(event.getLevel()); view.setStatus(event.getStatus()); view.setMessage(event.getMessage()); view.setStartedAt(event.getStartedAt()); view.setLastSeenAt(event.getLastSeenAt()); view.setResolvedAt(event.getResolvedAt()); view.setDurationSeconds(event.getDurationSeconds());
            if (rule != null) view.setThreshold("CRITICAL".equals(event.getLevel()) ? rule.getCriticalThreshold() : rule.getWarningThreshold());
            result.add(view);
        }
        return result;
    }

    private AlertRule saveRequest(AlertRuleRequest request, AlertRule rule) {
        if (!StringUtils.hasText(request.getName()) || !StringUtils.hasText(request.getMetric())) throw new IllegalArgumentException("告警名称和指标不能为空");
        BigDecimal warning = request.getWarningThreshold() == null ? BigDecimal.valueOf(70) : request.getWarningThreshold();
        BigDecimal critical = request.getCriticalThreshold() == null ? BigDecimal.valueOf(90) : request.getCriticalThreshold();
        if (warning.compareTo(BigDecimal.ZERO) < 0 || critical.compareTo(BigDecimal.valueOf(100)) > 0 || warning.compareTo(critical) >= 0) throw new IllegalArgumentException("告警阈值无效");
        rule.setName(request.getName()); rule.setNodeClientId(request.getNodeClientId()); rule.setMetric(request.getMetric().toUpperCase());
        rule.setWarningThreshold(warning); rule.setCriticalThreshold(critical); rule.setEnabled(request.getEnabled() == null || request.getEnabled()); rule.setUpdatedAt(LocalDateTime.now());
        return rule;
    }

    private BigDecimal metricValue(SystemMetrics metrics, String metric) {
        if (metrics == null) return null;
        switch (metric) {
            case "CPU": return BigDecimal.valueOf(metrics.getCpuUsage());
            case "MEMORY": return BigDecimal.valueOf(metrics.getMemoryUsage());
            case "DISK": return BigDecimal.valueOf(metrics.getDiskUsage());
            case "JVM": return BigDecimal.valueOf(metrics.getJvmHeapUsage());
            default: return null;
        }
    }
}
