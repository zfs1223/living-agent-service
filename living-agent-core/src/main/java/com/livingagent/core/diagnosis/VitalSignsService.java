package com.livingagent.core.diagnosis;

import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.IntSupplier;
import java.util.function.BooleanSupplier;

/**
 * P32-A: 生命体征服务。
 * 聚合 HealthMonitor + 连接状态 + 应用模式 + JVM内存，
 * 提供实时生命体征快照和历史趋势。
 * 
 * P32: 预警主动推送 + 驱动改进闭环
 */
public class VitalSignsService {

    private static final Logger log = LoggerFactory.getLogger(VitalSignsService.class);
    
    // P32: 预警阈值
    private static final double CRITICAL_HEALTH_SCORE = 0.3;
    private static final double WARNING_HEALTH_SCORE = 0.6;
    private static final double CRITICAL_MEMORY_USAGE = 90.0;

    private final HealthMonitor healthMonitor;
    private final IntSupplier connectionCountSupplier;
    private final BooleanSupplier degradedModeSupplier;
    private final ApplicationEventPublisher eventPublisher; // P32: 主动推送
    private final CrossLoopEventBus crossLoopEventBus; // P32: 驱动改进闭环

    private final ConcurrentLinkedDeque<VitalSnapshot> history = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<VitalAlert> recentAlerts = new ConcurrentLinkedDeque<>(); // P32: 预警历史
    private static final int MAX_HISTORY = 360;
    private static final int MAX_ALERTS = 50;

    public VitalSignsService(HealthMonitor healthMonitor,
                             IntSupplier connectionCountSupplier,
                             BooleanSupplier degradedModeSupplier,
                             ApplicationEventPublisher eventPublisher,
                             CrossLoopEventBus crossLoopEventBus) {
        this.healthMonitor = healthMonitor;
        this.connectionCountSupplier = connectionCountSupplier;
        this.degradedModeSupplier = degradedModeSupplier;
        this.eventPublisher = eventPublisher;
        this.crossLoopEventBus = crossLoopEventBus;
    }

    public VitalSnapshot getCurrentVitals() {
        // 使用缓存的健康状态，避免重复调用checkHealth()导致双重健康检查
        // HealthMonitorImpl每60秒自动执行checkHealth()，这里只获取缓存结果
        Map<String, HealthStatus> componentStatus = healthMonitor.getAllComponentStatus();
        // 根据组件状态计算整体健康分数
        HealthStatus overallHealth = computeOverallHealth(componentStatus);
        int activeConnections = connectionCountSupplier.getAsInt();
        boolean degradedMode = degradedModeSupplier.getAsBoolean();

        VitalSnapshot snapshot = new VitalSnapshot(
            Instant.now(),
            overallHealth.getStatus().name(),
            overallHealth.getScore(),
            componentStatus.size(),
            activeConnections,
            degradedMode,
            Runtime.getRuntime().freeMemory(),
            Runtime.getRuntime().totalMemory(),
            Runtime.getRuntime().maxMemory()
        );

        recordSnapshot(snapshot);
        return snapshot;
    }

    public List<VitalSnapshot> getVitalHistory(Duration duration) {
        Instant cutoff = Instant.now().minus(duration);
        List<VitalSnapshot> result = new ArrayList<>();
        for (VitalSnapshot s : history) {
            if (s.timestamp().isAfter(cutoff)) {
                result.add(s);
            }
        }
        return result;
    }

    private void recordSnapshot(VitalSnapshot snapshot) {
        history.addFirst(snapshot);
        while (history.size() > MAX_HISTORY) {
            history.removeLast();
        }
    }

    /**
     * 根据组件状态计算整体健康状态（使用缓存结果，避免重复检查）
     */
    private HealthStatus computeOverallHealth(Map<String, HealthStatus> componentStatus) {
        if (componentStatus.isEmpty()) {
            return HealthStatus.healthy("system");
        }

        double totalScore = 0.0;
        int unhealthyCount = 0;
        int degradedCount = 0;

        for (HealthStatus status : componentStatus.values()) {
            totalScore += status.getScore();
            if (status.getStatus() == HealthStatus.Status.UNHEALTHY) {
                unhealthyCount++;
            } else if (status.getStatus() == HealthStatus.Status.DEGRADED) {
                degradedCount++;
            }
        }

        double avgScore = componentStatus.isEmpty() ? 100.0 : totalScore / componentStatus.size();

        if (unhealthyCount > 0) {
            return HealthStatus.unhealthy("system",
                String.format("%d unhealthy components out of %d", unhealthyCount, componentStatus.size()));
        } else if (degradedCount > 0) {
            return HealthStatus.degraded("system",
                String.format("%d degraded components out of %d", degradedCount, componentStatus.size()));
        } else {
            return HealthStatus.healthy("system");
        }
    }

    public record VitalSnapshot(
        Instant timestamp,
        String healthStatus,
        double healthScore,
        int componentCount,
        int activeConnections,
        boolean degradedMode,
        long freeMemoryBytes,
        long totalMemoryBytes,
        long maxMemoryBytes
    ) {
        public double memoryUsagePercent() {
            if (maxMemoryBytes <= 0) return 0;
            return (double) (totalMemoryBytes - freeMemoryBytes) / maxMemoryBytes * 100;
        }

        public boolean isHealthy() {
            return "HEALTHY".equals(healthStatus) && !degradedMode;
        }
    }
    
    // P32: 预警记录
    public record VitalAlert(
        Instant timestamp,
        String alertType,
        String severity,
        double value,
        double threshold,
        String message,
        boolean pushed,
        boolean triggeredImprovement
    ) {}
    
    /**
     * P32: 周期性预警检测（每30秒）。
     */
    @Scheduled(fixedRate = 30000)
    public void checkAndPushAlerts() {
        VitalSnapshot current = getCurrentVitals();
        
        // 检查健康分数
        if (current.healthScore() < CRITICAL_HEALTH_SCORE) {
            pushAlertAndTriggerImprovement("health_critical", "CRITICAL", 
                current.healthScore(), CRITICAL_HEALTH_SCORE,
                "Health score critical: " + String.format("%.2f", current.healthScore()));
        } else if (current.healthScore() < WARNING_HEALTH_SCORE) {
            pushAlert("health_warning", "WARNING",
                current.healthScore(), WARNING_HEALTH_SCORE,
                "Health score warning: " + String.format("%.2f", current.healthScore()));
        }
        
        // 检查内存使用
        if (current.memoryUsagePercent() > CRITICAL_MEMORY_USAGE) {
            pushAlertAndTriggerImprovement("memory_critical", "CRITICAL",
                current.memoryUsagePercent(), CRITICAL_MEMORY_USAGE,
                "Memory usage critical: " + String.format("%.1f%%", current.memoryUsagePercent()));
        }
        
        // 检查降级模式
        if (current.degradedMode() && recentAlerts.stream()
            .noneMatch(a -> a.alertType().equals("degraded_mode") && a.timestamp().isAfter(Instant.now().minusSeconds(300)))) {
            pushAlert("degraded_mode", "WARNING", 1.0, 0.0, "System in degraded mode");
        }
    }
    
    /**
     * P32: 主动推送预警（通过事件发布）。
     */
    private void pushAlert(String alertType, String severity, double value, double threshold, String message) {
        VitalAlert alert = new VitalAlert(Instant.now(), alertType, severity, value, threshold, message, true, false);
        recentAlerts.addFirst(alert);
        if (recentAlerts.size() > MAX_ALERTS) recentAlerts.removeLast();
        
        // 主动推送：发布事件
        if (eventPublisher != null) {
            eventPublisher.publishEvent(alert);
            log.info("P32: Alert pushed: type={}, severity={}, value={}", alertType, severity, String.format("%.2f", value));
        }
    }
    
    /**
     * P32: 推送预警并驱动改进闭环。
     */
    private void pushAlertAndTriggerImprovement(String alertType, String severity, double value, double threshold, String message) {
        VitalAlert alert = new VitalAlert(Instant.now(), alertType, severity, value, threshold, message, true, true);
        recentAlerts.addFirst(alert);
        if (recentAlerts.size() > MAX_ALERTS) recentAlerts.removeLast();
        
        // 主动推送
        if (eventPublisher != null) {
            eventPublisher.publishEvent(alert);
        }
        
        // 驱动改进闭环：发布 CrossLoopEvent
        if (crossLoopEventBus != null) {
            crossLoopEventBus.publish(32, alertType,
                CrossLoopEvent.EventPriority.SELF_HEALING,
                Map.of("alertType", alertType, "severity", severity, "value", value, "threshold", threshold),
                60);
            log.warn("P32: Improvement triggered: type={}, severity={}, value={}", alertType, severity, String.format("%.2f", value));
        }
    }
    
    /**
     * P32: 获取最近预警列表。
     */
    public List<VitalAlert> getRecentAlerts(Duration duration) {
        Instant cutoff = Instant.now().minus(duration);
        List<VitalAlert> result = new ArrayList<>();
        for (VitalAlert a : recentAlerts) {
            if (a.timestamp().isAfter(cutoff)) {
                result.add(a);
            }
        }
        return result;
    }
}
