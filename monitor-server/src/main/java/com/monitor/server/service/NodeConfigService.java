package com.monitor.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.monitor.common.model.ServerMessage;
import com.monitor.server.model.dto.NodeConfigRequest;
import com.monitor.server.model.dto.NodeConfigView;
import com.monitor.server.model.entity.NodeConfig;
import com.monitor.server.mapper.NodeConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NodeConfigService {

    private final NodeConfigMapper mapper;
    private final MetricsService metricsService;

    public List<NodeConfigView> listViews() {
        List<NodeConfig> configs = mapper.selectList(new QueryWrapper<>());
        Map<String, NodeConfig> byClient = new LinkedHashMap<>();
        configs.forEach(node -> byClient.put(node.getClientId(), node));
        List<NodeConfigView> result = new ArrayList<>();
        for (ServerMessage.ClientSnapshot snapshot : metricsService.buildAggregatedMessage().getClientSnapshots()) {
            NodeConfig node = byClient.get(snapshot.getClientId());
            if (node == null) {
                node = defaults(snapshot.getClientId());
            }
            result.add(toView(node, snapshot.getHostname(), true));
            byClient.remove(snapshot.getClientId());
        }
        byClient.values().forEach(node -> result.add(toView(node, node.getClientId(), false)));
        return result;
    }

    public List<NodeConfig> list() {
        return mapper.selectList(new QueryWrapper<>());
    }

    public NodeConfig getById(Long id) {
        NodeConfig node = mapper.selectById(id);
        if (node == null) {
            throw new IllegalArgumentException("节点不存在");
        }
        return node;
    }

    public NodeConfig save(NodeConfigRequest request) {
        if (!StringUtils.hasText(request.getClientId())) {
            throw new IllegalArgumentException("clientId 不能为空");
        }
        NodeConfig node = mapper.selectOne(new QueryWrapper<NodeConfig>().eq("client_id", request.getClientId()));
        if (node == null) {
            node = new NodeConfig();
            node.setClientId(request.getClientId());
            node.setCreatedAt(LocalDateTime.now());
        }
        node.setDisplayName(request.getDisplayName());
        node.setDescription(request.getDescription());
        node.setEnvironment(request.getEnvironment());
        node.setLocation(request.getLocation());
        node.setEnabled(request.getEnabled() == null || request.getEnabled());
        node.setDisplaySystem(request.getDisplaySystem() == null || request.getDisplaySystem());
        node.setDisplayDocker(request.getDisplayDocker() == null || request.getDisplayDocker());
        node.setDisplayMicroservice(request.getDisplayMicroservice() == null || request.getDisplayMicroservice());
        node.setUpdatedAt(LocalDateTime.now());
        if (node.getId() == null) mapper.insert(node); else mapper.updateById(node);
        return node;
    }

    public void delete(Long id) {
        mapper.deleteById(id);
    }

    public Map<String, NodeConfig> configMap() {
        Map<String, NodeConfig> map = new LinkedHashMap<>();
        list().forEach(node -> map.put(node.getClientId(), node));
        return map;
    }

    private NodeConfig defaults(String clientId) {
        NodeConfig node = new NodeConfig();
        node.setClientId(clientId);
        node.setDisplayName(clientId);
        node.setEnabled(true);
        node.setDisplaySystem(true);
        node.setDisplayDocker(true);
        node.setDisplayMicroservice(true);
        return node;
    }

    private NodeConfigView toView(NodeConfig node, String hostname, boolean online) {
        NodeConfigView view = new NodeConfigView();
        view.setId(node.getId()); view.setClientId(node.getClientId()); view.setHostname(hostname);
        view.setDisplayName(StringUtils.hasText(node.getDisplayName()) ? node.getDisplayName() : hostname);
        view.setDescription(node.getDescription()); view.setEnvironment(node.getEnvironment()); view.setLocation(node.getLocation());
        view.setEnabled(node.getEnabled() == null || node.getEnabled());
        view.setDisplaySystem(node.getDisplaySystem() == null || node.getDisplaySystem());
        view.setDisplayDocker(node.getDisplayDocker() == null || node.getDisplayDocker());
        view.setDisplayMicroservice(node.getDisplayMicroservice() == null || node.getDisplayMicroservice());
        view.setOnline(online);
        return view;
    }
}

