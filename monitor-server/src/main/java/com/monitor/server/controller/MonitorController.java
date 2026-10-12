package com.monitor.server.controller;

import com.monitor.common.model.ServerMessage;
import com.monitor.server.model.dto.NodeConfigView;
import com.monitor.server.service.MetricsService;
import com.monitor.server.service.NodeConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 监控指标查询控制器，为前端提供实时监控只读接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/monitor")
public class MonitorController {

    private final MetricsService metricsService;
    private final NodeConfigService nodeConfigService;

    /**
     * 查询全部在线客户端的实时指标快照。
     *
     * @return 聚合指标快照
     */
    @GetMapping("/snapshot")
    public ServerMessage snapshot() {
        return metricsService.buildAggregatedMessage();
    }

    /**
     * 查询监控首页汇总数据。
     *
     * @return 监控汇总数据
     */
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

    /**
     * 查询监控服务健康状态。
     *
     * @return 服务健康状态
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "monitor-server"));
    }

    /**
     * 查询带实时在线状态的节点配置。
     *
     * @return 节点配置视图列表
     */
    @GetMapping("/node-configs")
    public List<NodeConfigView> nodeConfigs() {
        return nodeConfigService.listViews();
    }
}
