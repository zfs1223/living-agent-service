package com.livingagent.core.tool.feedback;

import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

@Component
public class WindowsAutomationImprovementTracker {

    private static final Logger log = LoggerFactory.getLogger(WindowsAutomationImprovementTracker.class);

    private final CrossLoopEventBus eventBus;
    private final LongAdder totalOperations = new LongAdder();
    private final LongAdder failedOperations = new LongAdder();
    private final LongAdder highRiskBlocked = new LongAdder();
    private final Map<String, OperationStats> operationStats = new ConcurrentHashMap<>();
    private volatile double failureRateThreshold = 0.30;

    public WindowsAutomationImprovementTracker(CrossLoopEventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void recordOperation(String operation, boolean success, long latencyMs) {
        totalOperations.increment();
        if (!success) failedOperations.increment();
        operationStats.computeIfAbsent(operation, k -> new OperationStats())
            .record(success, latencyMs);
    }

    public void recordHighRiskBlocked(String operation) {
        highRiskBlocked.increment();
    }

    @Scheduled(fixedRate = 30 * 60 * 1000)
    public void evaluateAutomationQuality() {
        long total = totalOperations.sum();
        if (total < 10) return;

        double failureRate = (double) failedOperations.sum() / total;

        if (failureRate > failureRateThreshold) {
            failureRateThreshold = Math.min(0.50, failureRateThreshold + 0.05);
            log.info("[闭环6] Windows自动化失败率{}%，提高阈值至{}%",
                String.format("%.0f", failureRate * 100),
                String.format("%.0f", failureRateThreshold * 100));
            eventBus.publish(6, "automation_quality_degraded",
                CrossLoopEvent.EventPriority.SELF_HEALING,
                Map.of("failureRate", failureRate,
                    "action", "increase_threshold",
                    "newThreshold", failureRateThreshold));
        } else if (failureRate < 0.10 && failureRateThreshold > 0.20) {
            failureRateThreshold = Math.max(0.20, failureRateThreshold - 0.02);
            log.info("[闭环6] Windows自动化失败率低，降低阈值至{}%",
                String.format("%.0f", failureRateThreshold * 100));
        }

        for (Map.Entry<String, OperationStats> entry : operationStats.entrySet()) {
            String op = entry.getKey();
            OperationStats stats = entry.getValue();
            if (stats.total.sum() > 5 && stats.getFailureRate() > 0.5) {
                log.warn("[闭环6] 操作{}失败率{}%，建议禁用", op, String.format("%.0f", stats.getFailureRate() * 100));
                eventBus.publish(6, "operation_high_failure",
                    CrossLoopEvent.EventPriority.SECURITY,
                    Map.of("operation", op, "failureRate", stats.getFailureRate(),
                        "action", "suggest_disable"));
            }
        }
    }

    public double getFailureRateThreshold() { return failureRateThreshold; }

    private static class OperationStats {
        final LongAdder total = new LongAdder();
        final LongAdder failures = new LongAdder();
        final LongAdder totalLatencyMs = new LongAdder();
        void record(boolean success, long latencyMs) {
            total.increment();
            if (!success) failures.increment();
            totalLatencyMs.add(latencyMs);
        }
        double getFailureRate() {
            long t = total.sum();
            return t > 0 ? (double) failures.sum() / t : 0;
        }
    }
}
