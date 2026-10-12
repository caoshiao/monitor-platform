package com.monitor.server.controller;

import com.monitor.server.model.dto.NodeConfigRequest;
import com.monitor.server.model.dto.NodeConfigView;
import com.monitor.server.model.entity.NodeConfig;
import com.monitor.server.service.NodeConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 监控节点配置控制器。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/nodes")
public class NodeConfigController {

    private final NodeConfigService service;

    /**
     * 查询全部节点配置及实时在线状态。
     *
     * @return 节点配置视图列表
     */
    @GetMapping
    public List<NodeConfigView> list() {
        return service.listViews();
    }

    /**
     * 创建节点配置。
     *
     * @param request 节点配置请求
     * @return 已创建的节点配置
     */
    @PostMapping
    public NodeConfig create(@RequestBody NodeConfigRequest request) {
        return service.save(request);
    }

    /**
     * 更新节点配置。
     *
     * @param id 节点配置主键
     * @param request 节点配置请求
     * @return 已更新的节点配置
     */
    @PutMapping("/{id}")
    public NodeConfig update(@PathVariable Long id, @RequestBody NodeConfigRequest request) {
        return service.update(id, request);
    }

    /**
     * 删除节点配置。
     *
     * @param id 节点配置主键
     * @return 空响应
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

