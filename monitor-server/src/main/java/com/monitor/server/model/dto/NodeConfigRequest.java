package com.monitor.server.model.dto;

import lombok.Data;

@Data
public class NodeConfigRequest {
    private String clientId;
    private String displayName;
    private String description;
    private String environment;
    private String location;
    private Boolean enabled = true;
    private Boolean displaySystem = true;
    private Boolean displayDocker = true;
    private Boolean displayMicroservice = true;
}
