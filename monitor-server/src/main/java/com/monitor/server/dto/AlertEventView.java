package com.monitor.server.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AlertEventView {
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
}
