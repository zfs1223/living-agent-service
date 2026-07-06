package com.livingagent.gateway.controller;

import com.livingagent.gateway.controller.common.ApiResponse;
import com.livingagent.gateway.service.MonitoringService;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/monitoring")
public class MonitoringController {

    private final MonitoringService monitoringService;
    private final BuildProperties buildProperties;

    public MonitoringController(MonitoringService monitoringService,
                                org.springframework.context.ApplicationContext context) {
        this.monitoringService = monitoringService;
        // Spring Boot 3.x: getIfAvailable() 无参版本，不存在时返回 null
        this.buildProperties = context.getAutowireCapableBeanFactory().getBeanProvider(
            org.springframework.boot.info.BuildProperties.class).getIfAvailable();
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(monitoringService.getHealthDetailed(buildProperties));
    }

    @GetMapping("/components")
    public ResponseEntity<?> components() {
        return ResponseEntity.ok(monitoringService.getComponents());
    }

    @GetMapping("/issues")
    public ResponseEntity<?> issues() {
        return ResponseEntity.ok(monitoringService.getIssues());
    }

    @GetMapping("/alerts")
    public ResponseEntity<?> alerts() {
        return ResponseEntity.ok(monitoringService.getAlerts());
    }

    @PostMapping("/alerts/{alertId}/ack")
    public ResponseEntity<?> acknowledge(@PathVariable String alertId,
                                         @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId == null || employeeId.isBlank()) {
            return ResponseEntity.status(401).body(ApiResponse.err("unauthorized", "Not authenticated"));
        }
        monitoringService.acknowledgeAlert(alertId);
        return ResponseEntity.ok(Map.of("alertId", alertId, "acknowledged", true));
    }
}
