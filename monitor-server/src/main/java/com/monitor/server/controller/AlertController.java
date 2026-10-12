package com.monitor.server.controller;

import com.monitor.server.model.dto.AlertEventView;
import com.monitor.server.model.dto.AlertRuleRequest;
import com.monitor.server.model.entity.AlertRule;
import com.monitor.server.service.AlertRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 告警规则、活动告警、历史记录及人工确认接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/alerts")
public class AlertController {

    private final AlertRuleService service;

    /**
     * 查询全部告警规则。
     *
     * @return 告警规则列表
    */
    @GetMapping("/rules")
    public List<AlertRule> rules() {
        return service.list();
    }

    /**
     * 新增告警规则。
     *
     * @param request 告警规则请求
     * @return 已创建规则
    */
    @PostMapping("/rules")
    public AlertRule createRule(@RequestBody AlertRuleRequest request) {
        return service.save(request);
    }

    /**
     * 更新指定告警规则。
     *
     * @param id 规则主键
     * @param request 告警规则请求
     * @return 已更新规则
    */
    @PutMapping("/rules/{id}")
    public AlertRule updateRule(@PathVariable Long id, @RequestBody AlertRuleRequest request) {
        return service.update(id, request);
    }

    /**
     * 删除指定告警规则。
     *
     * @param id 规则主键
     * @return 空响应
    */
    @DeleteMapping("/rules/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 查询尚未人工确认的告警，指标恢复后仍在首页保留。
     *
     * @return 首页待处理告警列表
     */
    @GetMapping("/active")
    public List<AlertEventView> active() {
        return service.activeAlerts();
    }

    /**
     * 查询全部告警历史记录。
     *
     * @return 告警历史列表
    */
    @GetMapping("/history")
    public List<AlertEventView> history() {
        return service.history();
    }

    /**
     * 确认指定告警，告警记录仍保留在历史列表。
     *
     * @param id 告警事件主键
     * @return 空响应
     */
    @PutMapping("/events/{id}/acknowledge")
    public ResponseEntity<Void> acknowledge(@PathVariable Long id) {
        service.acknowledge(id);
        return ResponseEntity.noContent().build();
    }
}

