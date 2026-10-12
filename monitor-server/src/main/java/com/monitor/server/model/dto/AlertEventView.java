package com.monitor.server.model.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 告警事件展示模型，包含事件详情、生命周期和确认状态。 */
@Data
public class AlertEventView {
    private Long id;
    private Long ruleId;
    private String ruleName;
    private String clientId;
    private String nodeName;
    private String metric;
    private BigDecimal value;
    private BigDecimal threshold;
    private String level;
    private String message;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime lastSeenAt;
    private LocalDateTime resolvedAt;
    private Long durationSeconds;
    private Boolean acknowledged;
    private LocalDateTime acknowledgedAt;
}
