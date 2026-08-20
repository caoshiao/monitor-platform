package com.monitor.server.web;

import com.monitor.server.dto.AlertRuleRequest;
import com.monitor.server.service.AlertRuleService;
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
@RequestMapping("/api/admin/alerts")
public class AlertController {

    private final AlertRuleService service;

    @GetMapping("/rules")
    public Object rules() { return service.list(); }

    @PostMapping("/rules")
    public Object createRule(@RequestBody AlertRuleRequest request) { return service.save(request); }

    @PutMapping("/rules/{id}")
    public Object updateRule(@PathVariable Long id, @RequestBody AlertRuleRequest request) { return service.update(id, request); }

    @DeleteMapping("/rules/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable Long id) { service.delete(id); return ResponseEntity.noContent().build(); }

    @GetMapping("/active")
    public Object active() { return service.activeAlerts(); }

    @GetMapping("/history")
    public Object history() { return service.history(); }
}
