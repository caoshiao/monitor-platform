package com.monitor.server.service.impl;

import com.monitor.common.model.HighgoMetrics;
import com.monitor.common.model.ServerMessage;
import com.monitor.common.model.SystemMetrics;
import com.monitor.server.mapper.AlertEventMapper;
import com.monitor.server.mapper.AlertRuleMapper;
import com.monitor.server.model.entity.AlertRule;
import com.monitor.server.model.entity.AlertEvent;
import com.monitor.server.service.MetricsService;
import com.monitor.server.service.NodeConfigService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 瀚高数据库告警文案格式测试。 */
class AlertRuleServiceImplTest {

    private static final int MAX_ALERT_MESSAGE_LENGTH = 500;

    @Test
    void shouldBuildChineseMemoryAlertMessage() {
        String message = AlertRuleServiceImpl.formatSystemMetricAlertMessage(
                "csa", "MEMORY", new BigDecimal("86.07"));

        assertEquals("csa机器的内存使用率当前为86.07%", message);
    }

    @Test
    void shouldBuildLargeTableQueryMessageWithAccountAndExecutionTime() {
        HighgoMetrics.LargeTableQuery query = createQuery("select * from szh_file;");

        String message = AlertRuleServiceImpl.formatLargeTableQueryMessage(query);

        assertEquals("检测到瀚高数据库全表查询：数据库=kcc_main，账号=szh，表=szh_file，"
                + "表大小=84.125MB，SQL=select * from szh_file;，时间：2026-10-09 15:41:06", message);
    }

    @Test
    void shouldKeepExecutionTimeWhenSqlIsTooLong() {
        HighgoMetrics.LargeTableQuery query = createQuery("x".repeat(600));

        String message = AlertRuleServiceImpl.formatLargeTableQueryMessage(query);

        assertEquals(MAX_ALERT_MESSAGE_LENGTH, message.length());
        assertTrue(message.contains("...，时间：2026-10-09 15:41:06"));
    }

    @Test
    void shouldAcknowledgeAlertWithoutDeletingHistory() {
        AlertEventMapper eventMapper = mock(AlertEventMapper.class);
        AlertEvent event = new AlertEvent();
        event.setId(7L);
        event.setClientId("server-node-01");
        event.setMetric("CPU");
        event.setAcknowledged(false);
        when(eventMapper.selectById(7L)).thenReturn(event);
        AlertRuleServiceImpl service = new AlertRuleServiceImpl(mock(AlertRuleMapper.class), eventMapper,
                mock(MetricsService.class), mock(NodeConfigService.class));

        service.acknowledge(7L);

        assertTrue(event.getAcknowledged());
        assertNotNull(event.getAcknowledgedAt());
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        assertTrue(event.getAcknowledgedAt().isAfter(now.minusSeconds(5L)));
        assertTrue(event.getAcknowledgedAt().isBefore(now.plusSeconds(1L)));
        verify(eventMapper).updateById(event);
    }

    @Test
    void shouldCreateNewUnacknowledgedAlertWhenLoginFailureIncreasesAfterAcknowledgement() {
        AlertEventMapper eventMapper = mock(AlertEventMapper.class);
        AlertRuleMapper ruleMapper = mock(AlertRuleMapper.class);
        MetricsService metricsService = mock(MetricsService.class);
        NodeConfigService nodeConfigService = mock(NodeConfigService.class);
        AlertEvent acknowledgedEvent = createAcknowledgedLoginAlert();
        AlertRule rule = createLoginFailureRule();
        when(eventMapper.selectList(argThat(wrapper -> true)))
                .thenReturn(Collections.singletonList(acknowledgedEvent));
        when(ruleMapper.selectList(argThat(wrapper -> true)))
                .thenReturn(Collections.singletonList(rule));
        when(nodeConfigService.configMap()).thenReturn(Collections.emptyMap());
        when(metricsService.buildAggregatedMessage()).thenReturn(createLoginFailureSnapshot(3L));
        AlertRuleServiceImpl service = new AlertRuleServiceImpl(
                ruleMapper, eventMapper, metricsService, nodeConfigService);

        service.evaluateAlerts();

        assertEquals("RESOLVED", acknowledgedEvent.getStatus());
        assertNotNull(acknowledgedEvent.getResolvedAt());
        ArgumentCaptor<AlertEvent> insertedEvent = ArgumentCaptor.forClass(AlertEvent.class);
        verify(eventMapper).insert(insertedEvent.capture());
        assertEquals("ACTIVE", insertedEvent.getValue().getStatus());
        assertEquals(new BigDecimal("3"), insertedEvent.getValue().getValue());
        assertFalse(insertedEvent.getValue().getAcknowledged());
    }

    @Test
    void shouldNotCreateRepeatedLoginAlertWhenFailureCountDoesNotIncrease() {
        AlertEventMapper eventMapper = mock(AlertEventMapper.class);
        AlertRuleMapper ruleMapper = mock(AlertRuleMapper.class);
        MetricsService metricsService = mock(MetricsService.class);
        NodeConfigService nodeConfigService = mock(NodeConfigService.class);
        AlertEvent acknowledgedEvent = createAcknowledgedLoginAlert();
        AlertRule rule = createLoginFailureRule();
        when(eventMapper.selectList(argThat(wrapper -> true)))
                .thenReturn(Collections.singletonList(acknowledgedEvent));
        when(ruleMapper.selectList(argThat(wrapper -> true)))
                .thenReturn(Collections.singletonList(rule));
        when(nodeConfigService.configMap()).thenReturn(Collections.emptyMap());
        when(metricsService.buildAggregatedMessage()).thenReturn(createLoginFailureSnapshot(2L));
        AlertRuleServiceImpl service = new AlertRuleServiceImpl(
                ruleMapper, eventMapper, metricsService, nodeConfigService);

        service.evaluateAlerts();

        assertEquals("ACTIVE", acknowledgedEvent.getStatus());
        verify(eventMapper, org.mockito.Mockito.never()).insert(org.mockito.Mockito.any());
    }

    @Test
    void shouldReturnUnacknowledgedAlertsEvenWhenResolved() {
        AlertEventMapper eventMapper = mock(AlertEventMapper.class);
        AlertRuleMapper ruleMapper = mock(AlertRuleMapper.class);
        when(eventMapper.selectList(argThat(wrapper -> {
            String sqlSegment = wrapper.getSqlSegment();
            return sqlSegment.contains("requires_acknowledgement")
                    && sqlSegment.contains("acknowledged")
                    && !sqlSegment.contains("status");
        }))).thenReturn(Collections.emptyList());
        when(ruleMapper.selectList(argThat(wrapper -> true))).thenReturn(Collections.emptyList());
        AlertRuleServiceImpl service = new AlertRuleServiceImpl(ruleMapper, eventMapper,
                mock(MetricsService.class), mock(NodeConfigService.class));

        assertTrue(service.activeAlerts().isEmpty());

        verify(eventMapper).selectList(argThat(wrapper -> {
            String sqlSegment = wrapper.getSqlSegment();
            return sqlSegment.contains("requires_acknowledgement")
                    && sqlSegment.contains("acknowledged")
                    && !sqlSegment.contains("status");
        }));
    }

    @Test
    void shouldNotResolveAlertWhenClientIsOffline() {
        AlertEvent event = new AlertEvent();
        event.setRuleId(2L);
        event.setClientId("server-node-01");
        event.setMetric("LOGIN_FAILURE");

        boolean shouldResolve = AlertRuleServiceImpl.shouldResolveAlert(
                event, Collections.emptySet(), Collections.emptyMap());

        assertFalse(shouldResolve);
    }

    @Test
    void shouldNotResolveLoginFailureBeforeHighgoMetricsArrive() {
        AlertEvent event = new AlertEvent();
        event.setRuleId(2L);
        event.setClientId("server-node-01");
        event.setMetric("LOGIN_FAILURE");
        ServerMessage.ClientSnapshot snapshot = new ServerMessage.ClientSnapshot();
        snapshot.setClientId("server-node-01");
        snapshot.setSystemMetrics(new SystemMetrics());

        boolean shouldResolve = AlertRuleServiceImpl.shouldResolveAlert(
                event, Collections.emptySet(), Map.of("server-node-01", snapshot));

        assertFalse(shouldResolve);
    }

    @Test
    void shouldResolveLoginFailureAfterValidHighgoMetricsArrive() {
        AlertEvent event = new AlertEvent();
        event.setRuleId(2L);
        event.setClientId("server-node-01");
        event.setMetric("LOGIN_FAILURE");
        ServerMessage.ClientSnapshot snapshot = new ServerMessage.ClientSnapshot();
        snapshot.setClientId("server-node-01");
        HighgoMetrics metrics = new HighgoMetrics();
        metrics.setLoginFailureCount(0L);
        snapshot.setHighgoMetrics(metrics);

        boolean shouldResolve = AlertRuleServiceImpl.shouldResolveAlert(
                event, Collections.emptySet(), Map.of("server-node-01", snapshot));

        assertTrue(shouldResolve);
    }

    @Test
    void shouldNotResolveLoginFailureWhenHighgoMetricIsUnavailable() {
        AlertEvent event = new AlertEvent();
        event.setRuleId(2L);
        event.setClientId("server-node-01");
        event.setMetric("LOGIN_FAILURE");
        ServerMessage.ClientSnapshot snapshot = new ServerMessage.ClientSnapshot();
        snapshot.setClientId("server-node-01");
        HighgoMetrics metrics = new HighgoMetrics();
        metrics.setLoginFailureCount(-1L);
        snapshot.setHighgoMetrics(metrics);

        boolean shouldResolve = AlertRuleServiceImpl.shouldResolveAlert(
                event, Set.of(), Map.of("server-node-01", snapshot));

        assertFalse(shouldResolve);
    }

    @Test
    void shouldMergeContinuousLoginFailureRecordsWithoutDeletingOtherAlerts() {
        AlertEvent newest = createLoginAlert(18L, "2026-10-09T09:12:06", "2026-10-09T09:22:12");
        AlertEvent older = createLoginAlert(17L, "2026-10-09T09:11:28", "2026-10-09T09:11:53");
        AlertEvent cpu = new AlertEvent();
        cpu.setId(16L);
        cpu.setMetric("CPU");

        java.util.List<AlertEvent> merged = AlertRuleServiceImpl.mergeRepeatedLoginAlerts(
                Arrays.asList(newest, older, cpu), Duration.ofHours(24L));

        assertEquals(2, merged.size());
        assertEquals(18L, merged.get(0).getId());
        assertEquals(LocalDateTime.parse("2026-10-09T09:11:28"), merged.get(0).getStartedAt());
        assertEquals(16L, merged.get(1).getId());
    }

    @Test
    void shouldKeepIndependentLoginFailureRecordsOutsideMergeWindow() {
        AlertEvent newest = createLoginAlert(18L, "2026-10-10T10:00:00", "2026-10-10T10:10:00");
        AlertEvent older = createLoginAlert(17L, "2026-10-09T08:00:00", "2026-10-09T08:10:00");

        java.util.List<AlertEvent> merged = AlertRuleServiceImpl.mergeRepeatedLoginAlerts(
                Arrays.asList(newest, older), Duration.ofHours(24L));

        assertEquals(2, merged.size());
    }

    private AlertEvent createLoginAlert(Long id, String startedAt, String lastSeenAt) {
        AlertEvent event = new AlertEvent();
        event.setId(id);
        event.setRuleId(2L);
        event.setClientId("server-node-01");
        event.setMetric("LOGIN_FAILURE");
        event.setValue(BigDecimal.valueOf(2L));
        event.setStartedAt(LocalDateTime.parse(startedAt));
        event.setLastSeenAt(LocalDateTime.parse(lastSeenAt));
        return event;
    }

    private AlertEvent createAcknowledgedLoginAlert() {
        AlertEvent event = createLoginAlert(22L, "2026-10-10T05:41:35", "2026-10-10T07:02:57");
        event.setStatus("ACTIVE");
        event.setAcknowledged(true);
        event.setAcknowledgedAt(LocalDateTime.parse("2026-10-10T07:02:57"));
        return event;
    }

    private AlertRule createLoginFailureRule() {
        AlertRule rule = new AlertRule();
        rule.setId(2L);
        rule.setName("瀚高数据库登录失败");
        rule.setMetric("LOGIN_FAILURE");
        rule.setWarningThreshold(BigDecimal.ONE);
        rule.setCriticalThreshold(BigDecimal.valueOf(5L));
        rule.setEnabled(true);
        return rule;
    }

    private ServerMessage createLoginFailureSnapshot(long loginFailureCount) {
        HighgoMetrics highgoMetrics = new HighgoMetrics();
        highgoMetrics.setLoginFailureCount(loginFailureCount);
        ServerMessage.ClientSnapshot snapshot = new ServerMessage.ClientSnapshot();
        snapshot.setClientId("server-node-01");
        snapshot.setHostname("csa");
        snapshot.setHighgoMetrics(highgoMetrics);
        ServerMessage message = new ServerMessage();
        message.setClientSnapshots(Collections.singletonList(snapshot));
        return message;
    }

    private HighgoMetrics.LargeTableQuery createQuery(String sql) {
        HighgoMetrics.LargeTableQuery query = new HighgoMetrics.LargeTableQuery();
        query.setDatabaseName("kcc_main");
        query.setUserName("szh");
        query.setTableName("szh_file");
        query.setTableSizeMB(84.125D);
        query.setQuery(sql);
        query.setQueryTime("2026-10-09 15:41:06");
        return query;
    }
}
