package com.monitor.server.controller;

import com.monitor.common.model.ServerMessage;
import com.monitor.server.service.MetricsService;
import com.monitor.server.service.NodeConfigService;
import com.monitor.server.model.dto.NodeConfigView;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

/** REST read model for the separately deployed Vue frontend. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/monitor")
public class MonitorController {

    private final MetricsService metricsService;
    private final NodeConfigService nodeConfigService;

    @GetMapping("/snapshot")
    public ServerMessage snapshot() {
        return metricsService.buildAggregatedMessage();
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        ServerMessage snapshot = metricsService.buildAggregatedMessage();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timestamp", snapshot.getTimestamp());
        result.put("onlineCount", snapshot.getOnlineClients() == null ? 0 : snapshot.getOnlineClients().size());
        result.put("onlineClients", snapshot.getOnlineClients());
        result.put("clientSnapshots", snapshot.getClientSnapshots());
        return result;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "monitor-server"));
    }

    @GetMapping("/node-configs")
    public List<NodeConfigView> nodeConfigs() {
        return nodeConfigService.listViews();
    }
}
