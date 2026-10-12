package com.monitor.server.service;

import com.monitor.server.model.dto.AlertEventView;
import com.monitor.server.model.dto.AlertRuleRequest;
import com.monitor.server.model.entity.AlertRule;

import java.util.List;

/**
 * 告警规则与告警事件业务接口。
 */
public interface AlertRuleService {

    /**
     * 查询全部告警规则。
     *
     * @return 告警规则列表
     */
    List<AlertRule> list();

    /**
     * 创建告警规则。
     *
     * @param request 告警规则请求
     * @return 已创建的告警规则
     */
    AlertRule save(AlertRuleRequest request);

    /**
     * 更新告警规则。
     *
     * @param id 告警规则主键
     * @param request 告警规则请求
     * @return 已更新的告警规则
     */
    AlertRule update(Long id, AlertRuleRequest request);

    /**
     * 删除告警规则。
     *
     * @param id 告警规则主键
     */
    void delete(Long id);

    /**
     * 查询首页尚未人工确认的待处理告警，包含指标已经恢复的记录。
     *
     * @return 待处理告警列表
     */
    List<AlertEventView> activeAlerts();

    /**
     * 查询当前仍超过阈值的全部活动告警，包括已经人工确认的告警。
     *
     * @return 当前活动告警列表
     */
    List<AlertEventView> currentAlerts();

    /**
     * 根据最新监控指标评估告警生命周期。
     */
    void evaluateAlerts();

    /**
     * 查询最近告警历史。
     *
     * @return 告警历史列表
     */
    List<AlertEventView> history();

    /**
     * 人工确认告警。
     *
     * @param id 告警事件主键
     */
    void acknowledge(Long id);
}
