package com.livingagent.gateway.controller;

import com.livingagent.core.diagnosis.HealthMonitor;
import com.livingagent.core.diagnosis.HealthStatus;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * P12-B: 轻量健康检查端点。
 * /api/health — 基础健康状态（桌面端/K8s liveness）
 * /api/health/live — liveness 探针（JVM 存活）
 * /api/health/ready — readiness 探针（DB + model_daemon 连通性）
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final HealthMonitor healthMonitor;
    private final DataSource dataSource;
    private final BuildProperties buildProperties;
    private final Instant startTime = Instant.now();

    public HealthController(HealthMonitor healthMonitor, DataSource dataSource,
                            org.springframework.context.ApplicationContext context) {
        this.healthMonitor = healthMonitor;
        this.dataSource = dataSource;
        // Spring Boot 3.x: getIfAvailable() 无参版本，不存在时返回 null
        this.buildProperties = context.getAutowireCapableBeanFactory().getBeanProvider(
            org.springframework.boot.info.BuildProperties.class).getIfAvailable();
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        HealthStatus status = healthMonitor.checkHealth();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.getStatus().name().toLowerCase());
        body.put("timestamp", Instant.now().toString());
        if (buildProperties != null) {
            body.put("version", buildProperties.getVersion());
        }
        body.put("uptime", java.time.Duration.between(startTime, Instant.now()).toString());
        boolean isOk = status.getStatus() != HealthStatus.Status.UNHEALTHY;
        return ResponseEntity.status(isOk ? 200 : 503).body(body);
    }

    @GetMapping("/live")
    public ResponseEntity<Map<String, Object>> liveness() {
        Map<String, Object> body = Map.of("status", "alive", "timestamp", Instant.now().toString());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> readiness() {
        Map<String, Object> checks = new LinkedHashMap<>();
        boolean allReady = true;

        // DB connectivity
        try (Connection conn = dataSource.getConnection()) {
            boolean dbOk = conn.isValid(3);
            checks.put("database", Map.of("status", dbOk ? "ready" : "not_ready"));
            if (!dbOk) allReady = false;
        } catch (Exception e) {
            checks.put("database", Map.of("status", "not_ready", "error", e.getMessage()));
            allReady = false;
        }

        // model_daemon health
        try {
            HealthStatus daemonStatus = healthMonitor.checkComponent("model_daemon");
            if (daemonStatus != null) {
                boolean daemonOk = daemonStatus.getStatus() != HealthStatus.Status.UNHEALTHY;
                checks.put("model_daemon", Map.of("status", daemonOk ? "ready" : "degraded"));
                if (!daemonOk) allReady = false;
            } else {
                checks.put("model_daemon", Map.of("status", "not_checked"));
            }
        } catch (Exception e) {
            checks.put("model_daemon", Map.of("status", "error", "error", e.getMessage()));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", allReady ? "ready" : "not_ready");
        body.put("checks", checks);
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(allReady ? 200 : 503).body(body);
    }
}
