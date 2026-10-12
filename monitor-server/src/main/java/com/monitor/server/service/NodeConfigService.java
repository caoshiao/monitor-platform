package com.monitor.server.service;

import com.monitor.server.model.dto.NodeConfigRequest;
import com.monitor.server.model.dto.NodeConfigView;
import com.monitor.server.model.entity.NodeConfig;

import java.util.List;
import java.util.Map;

/**
 * 监控节点配置业务接口。
 */
public interface NodeConfigService {

    /**
     * 查询包含在线状态的节点配置视图。
     *
     * @return 节点配置视图列表
     */
    List<NodeConfigView> listViews();

    /**
     * 查询全部节点配置。
     *
     * @return 节点配置列表
     */
    List<NodeConfig> list();

    /**
     * 根据主键查询节点配置。
     *
     * @param id 节点配置主键
     * @return 节点配置
     */
    NodeConfig getById(Long id);

    /**
     * 新增或更新节点配置。
     *
     * @param request 节点配置请求
     * @return 已保存的节点配置
     */
    NodeConfig save(NodeConfigRequest request);

    /**
     * 根据主键更新节点配置。
     *
     * @param id 节点配置主键
     * @param request 节点配置请求
     * @return 已更新的节点配置
     */
    NodeConfig update(Long id, NodeConfigRequest request);

    /**
     * 删除节点配置。
     *
     * @param id 节点配置主键
     */
    void delete(Long id);

    /**
     * 按客户端标识构建节点配置映射。
     *
     * @return 客户端标识与节点配置映射
     */
    Map<String, NodeConfig> configMap();
}
