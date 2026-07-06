package com.livingagent.gateway.service;

import com.livingagent.core.diagnosis.HealthAlert;
import com.livingagent.core.diagnosis.HealthIssue;
import com.livingagent.core.diagnosis.HealthMonitor;
import com.livingagent.core.diagnosis.HealthStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MonitoringService {

    private final HealthMonitor healthMonitor;
    private final Map<String, Double> metrics = new ConcurrentHashMap<>();
    private final Map<String, MetricRecord> metricHistory = new ConcurrentHashMap<>();
    private final Instant startTime = Instant.now();

    public MonitoringService(HealthMonitor healthMonitor) {
        this.healthMonitor = healthMonitor;
    }

    public Map<String, Object> getHealthDetailed(org.springframework.boot.info.BuildProperties buildProperties) {
        HealthStatus health = healthMonitor.checkHealth();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", health.getStatus().name().toLowerCase());
        if (buildProperties != null) {
            result.put("version", buildProperties.getVersion());
            result.put("artifact", buildProperties.getArtifact());
        }
        result.put("startedAt", startTime.toString());
        result.put("uptime", java.time.Duration.between(startTime, Instant.now()).toString());
        result.put("components", healthMonitor.getAllComponentStatus());
        result.put("issues", healthMonitor.detectIssues());
        result.put("alerts", healthMonitor.getActiveAlerts());
        return result;
    }

    public HealthStatus getHealth() {
        return healthMonitor.checkHealth();
    }

    public Map<String, HealthStatus> getComponents() {
        return healthMonitor.getAllComponentStatus();
    }

    public List<HealthIssue> getIssues() {
        return healthMonitor.detectIssues();
    }

    public List<HealthAlert> getAlerts() {
        return healthMonitor.getActiveAlerts();
    }

    public void acknowledgeAlert(String alertId) {
        healthMonitor.acknowledgeAlert(alertId);
    }

    public void recordMetric(String key, double value) {
        recordMetric(key, value, Map.of());
    }

    public void recordMetric(String key, double value, Map<String, Object> context) {
        metrics.put(key, value);
        metricHistory.put(key, new MetricRecord(key, value, context == null ? Map.of() : Map.copyOf(context), Instant.now()));
    }

    public Map<String, Double> snapshotMetrics() {
        return Map.copyOf(metrics);
    }

    public List<MetricRecord> recentMetrics() {
        return new ArrayList<>(metricHistory.values());
    }

    public Map<String, Object> summary() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("health", getHealth());
        payload.put("components", getComponents());
        payload.put("issues", getIssues());
        payload.put("alerts", getAlerts());
        payload.put("metrics", snapshotMetrics());
        payload.put("recentMetrics", recentMetrics());
        return payload;
    }

    public record MetricRecord(String key, double value, Map<String, Object> context, Instant recordedAt) {}
}
