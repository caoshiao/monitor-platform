package com.monitor.server.model.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AlertRuleRequest {
    private String name;
    private String nodeClientId;
    private String metric;
    private BigDecimal warningThreshold = BigDecimal.valueOf(70);
    private BigDecimal criticalThreshold = BigDecimal.valueOf(90);
    private Boolean enabled = true;
}
