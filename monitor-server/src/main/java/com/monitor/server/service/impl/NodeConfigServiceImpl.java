package com.monitor.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.monitor.common.model.ServerMessage;
import com.monitor.server.mapper.NodeConfigMapper;
import com.monitor.server.model.dto.NodeConfigRequest;
import com.monitor.server.model.dto.NodeConfigView;
import com.monitor.server.model.entity.NodeConfig;
import com.monitor.server.service.MetricsService;
import com.monitor.server.service.NodeConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 监控节点配置业务实现类。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NodeConfigServiceImpl implements NodeConfigService {

    private final NodeConfigMapper mapper;
    private final MetricsService metricsService;

    /**
     * 查询包含实时在线状态的节点配置视图。
     *
     * @return 节点配置视图列表
     */
    @Override
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

    /**
     * 查询全部节点配置。
     *
     * @return 节点配置列表
     */
    @Override
    public List<NodeConfig> list() {
        return mapper.selectList(new QueryWrapper<>());
    }

    /**
     * 根据主键查询节点配置。
     *
     * @param id 节点配置主键
     * @return 节点配置
     */
    @Override
    public NodeConfig getById(Long id) {
        NodeConfig node = mapper.selectById(id);
        if (node == null) {
            throw new IllegalArgumentException("节点不存在");
        }
        return node;
    }

    /**
     * 根据客户端标识新增或更新节点配置。
     *
     * @param request 节点配置请求
     * @return 已保存的节点配置
     */
    @Override
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
        if (node.getId() == null) {
            mapper.insert(node);
            log.info("节点配置创建完成, nodeId={}, clientId={}", node.getId(), node.getClientId());
        } else {
            mapper.updateById(node);
            log.info("节点配置更新完成, nodeId={}, clientId={}", node.getId(), node.getClientId());
        }
        return node;
    }

    /**
     * 根据主键更新节点配置；未传客户端标识时沿用原配置值。
     *
     * @param id 节点配置主键
     * @param request 节点配置请求
     * @return 已更新的节点配置
     */
    @Override
    public NodeConfig update(Long id, NodeConfigRequest request) {
        if (id == null) {
            throw new IllegalArgumentException("节点 ID 不能为空");
        }
        NodeConfig existing = getById(id);
        if (!StringUtils.hasText(request.getClientId())) {
            request.setClientId(existing.getClientId());
        }
        log.debug("开始更新节点配置, nodeId={}, clientId={}", id, request.getClientId());
        return save(request);
    }

    /**
     * 删除节点配置。
     *
     * @param id 节点配置主键
     */
    @Override
    public void delete(Long id) {
        mapper.deleteById(id);
        log.info("节点配置删除完成, nodeId={}", id);
    }

    /**
     * 按客户端标识构建节点配置映射。
     *
     * @return 客户端标识与节点配置映射
     */
    @Override
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

