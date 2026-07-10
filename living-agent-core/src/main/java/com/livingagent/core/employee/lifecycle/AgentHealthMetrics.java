package com.livingagent.core.employee.lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 闭环39-P39-B: Agent级指标采集
 * 采集uptime/errorCount/avgResponseTime，注册到VitalSignsService
 */
public class AgentHealthMetrics {

    private static final Logger log = LoggerFactory.getLogger(AgentHealthMetrics.class);

    private final AgentLifecycleMonitor lifecycleMonitor;
    private final Map<String, ResponseTimeTracker> responseTimeTrackers = new ConcurrentHashMap<>();

    public AgentHealthMetrics(AgentLifecycleMonitor lifecycleMonitor) {
        this.lifecycleMonitor = lifecycleMonitor;
    }

    public void recordResponseTime(String agentId, long responseTimeMs) {
        responseTimeTrackers.computeIfAbsent(agentId, k -> new ResponseTimeTracker())
            .record(responseTimeMs);
    }

    public AgentMetricsSnapshot getMetrics(String agentId) {
        AgentLifecycleMonitor.AgentHealthReport healthReport = lifecycleMonitor.getHealthReport(agentId);
        if (healthReport == null) return null;

        ResponseTimeTracker rtTracker = responseTimeTrackers.get(agentId);
        double avgResponseTimeMs = rtTracker != null ? rtTracker.getAverage() : 0;

        Duration uptime = Duration.between(healthReport.lastHeartbeat(), Instant.now());

        return new AgentMetricsSnapshot(
            agentId,
            healthReport.agentType(),
            uptime.toSeconds(),
            healthReport.errorRate(),
            healthReport.failureCount(),
            avgResponseTimeMs,
            healthReport.totalExecutions(),
            Instant.now()
        );
    }

    public Map<String, AgentMetricsSnapshot> getAllMetrics() {
        Map<String, AgentMetricsSnapshot> all = new ConcurrentHashMap<>();
        for (String agentId : lifecycleMonitor.getAllHealthReports().keySet()) {
            AgentMetricsSnapshot snapshot = getMetrics(agentId);
            if (snapshot != null) {
                all.put(agentId, snapshot);
            }
        }
        return all;
    }

    public String getMetricsSummary() {
        Map<String, AgentMetricsSnapshot> all = getAllMetrics();
        if (all.isEmpty()) return "No agents registered";
        long totalErrors = all.values().stream().mapToLong(AgentMetricsSnapshot::errorCount).sum();
        double avgResponseTime = all.values().stream()
            .mapToDouble(AgentMetricsSnapshot::avgResponseTimeMs)
            .average().orElse(0);
        return String.format("Agents: %d, totalErrors: %d, avgResponseTime: %.0fms",
            all.size(), totalErrors, avgResponseTime);
    }

    public record AgentMetricsSnapshot(
        String agentId, String agentType,
        long uptimeSeconds, double errorRate, long errorCount,
        double avgResponseTimeMs, long totalExecutions,
        Instant capturedAt
    ) {}

    private static class ResponseTimeTracker {
        private long totalMs = 0;
        private long count = 0;

        synchronized void record(long responseTimeMs) {
            totalMs += responseTimeMs;
            count++;
        }

        synchronized double getAverage() {
            return count > 0 ? (double) totalMs / count : 0;
        }
    }
}
