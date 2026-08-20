package com.monitor.server.web;

import com.monitor.server.dto.NodeConfigRequest;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/nodes")
public class NodeConfigController {

    private final NodeConfigService service;

    @GetMapping
    public Object list() { return service.listViews(); }

    @PostMapping
    public Object create(@RequestBody NodeConfigRequest request) { return service.save(request); }

    @PutMapping("/{id}")
    public Object update(@PathVariable Long id, @RequestBody NodeConfigRequest request) {
        if (id == null) throw new IllegalArgumentException("节点 ID 不能为空");
        request.setClientId(request.getClientId());
        com.monitor.server.entity.NodeConfig existing = service.list().stream().filter(n -> id.equals(n.getId())).findFirst().orElseThrow(() -> new IllegalArgumentException("节点不存在"));
        if (request.getClientId() == null) request.setClientId(existing.getClientId());
        return service.save(request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) { service.delete(id); return ResponseEntity.noContent().build(); }
}
