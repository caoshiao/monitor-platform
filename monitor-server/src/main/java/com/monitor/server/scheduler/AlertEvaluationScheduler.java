package com.monitor.server.scheduler;

import com.monitor.server.service.AlertRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Persists alert start, duration and recovery even when no browser is open. */
@Component
@RequiredArgsConstructor
public class AlertEvaluationScheduler {
    private final AlertRuleService alertRuleService;

    @Scheduled(fixedDelayString = "${monitor.server.alert-interval:5000}")
    public void evaluate() {
        alertRuleService.evaluateAlerts();
    }
}
