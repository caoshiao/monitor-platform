package com.monitor.server.controller;

import com.monitor.server.model.dto.NodeConfigRequest;
import com.monitor.server.model.entity.NodeConfig;
import com.monitor.server.service.NodeConfigService;
import com.monitor.server.model.dto.NodeConfigView;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/nodes")
public class NodeConfigController {

    private final NodeConfigService service;

    @GetMapping
    public List<NodeConfigView> list() { return service.listViews(); }

    @PostMapping
    public NodeConfig create(@RequestBody NodeConfigRequest request) { return service.save(request); }

    @PutMapping("/{id}")
    public NodeConfig update(@PathVariable Long id, @RequestBody NodeConfigRequest request) {
        if (id == null) throw new IllegalArgumentException("节点 ID 不能为空");
        NodeConfig existing = service.getById(id);
        if (request.getClientId() == null || request.getClientId().isBlank()) {
            request.setClientId(existing.getClientId());
        }
        return service.save(request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) { service.delete(id); return ResponseEntity.noContent().build(); }
}

