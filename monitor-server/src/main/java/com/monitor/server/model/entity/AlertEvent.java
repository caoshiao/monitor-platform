package com.monitor.server.model.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 告警事件持久化实体，记录生命周期及人工确认状态。 */
@Data
@TableName("monitor_alert_event")
public class AlertEvent {
    @TableId(type = IdType.AUTO) private Long id;
    @TableField("rule_id") private Long ruleId;
    @TableField("client_id") private String clientId;
    @TableField("node_name") private String nodeName;
    private String metric;
    private BigDecimal value;
    private String level;
    private String status;
    private String message;
    @TableField("started_at") private LocalDateTime startedAt;
    @TableField("last_seen_at") private LocalDateTime lastSeenAt;
    @TableField("resolved_at") private LocalDateTime resolvedAt;
    @TableField("duration_seconds") private Long durationSeconds;
    @TableField(value = "requires_acknowledgement", insertStrategy = FieldStrategy.NEVER,
            updateStrategy = FieldStrategy.NEVER)
    private Boolean requiresAcknowledgement;
    private Boolean acknowledged;
    @TableField("acknowledged_at") private LocalDateTime acknowledgedAt;
}

