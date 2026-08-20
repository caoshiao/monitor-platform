package com.monitor.server.dto;

import lombok.Data;

@Data
public class NodeConfigView {
    private Long id;
    private String clientId;
    private String hostname;
    private String displayName;
    private String description;
    private String environment;
    private String location;
    private Boolean enabled;
    private Boolean displaySystem;
    private Boolean displayDocker;
    private Boolean displayMicroservice;
    private Boolean online;
}
