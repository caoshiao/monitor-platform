package com.monitor.server.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Persisted node metadata and presentation preferences. */
@Data
@TableName("monitor_node")
public class NodeConfig {

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("client_id")
    private String clientId;
    @TableField("display_name")
    private String displayName;
    private String description;
    private String environment;
    private String location;
    private Boolean enabled;
    @TableField("display_system")
    private Boolean displaySystem;
    @TableField("display_docker")
    private Boolean displayDocker;
    @TableField("display_microservice")
    private Boolean displayMicroservice;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}

