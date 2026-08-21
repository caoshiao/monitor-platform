package com.monitor.server.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Persisted threshold rule for system metrics. */
@Data
@TableName("monitor_alert_rule")
public class AlertRule {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    @TableField("node_client_id")
    private String nodeClientId;
    private String metric;
    @TableField("warning_threshold")
    private BigDecimal warningThreshold;
    @TableField("critical_threshold")
    private BigDecimal criticalThreshold;
    private Boolean enabled;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}

