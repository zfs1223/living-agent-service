package com.livingagent.core.intervention.feedback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 闭环41-P41-A: 干预效果追踪器
 * 追踪干预决策执行效果(成功/误报/漏报/响应时间)
 */
public class InterventionEffectivenessTracker {

    private static final Logger log = LoggerFactory.getLogger(InterventionEffectivenessTracker.class);

    private final LongAdder totalInterventions = new LongAdder();
    private final LongAdder successfulInterventions = new LongAdder();
    private final LongAdder falsePositives = new LongAdder();
    private final LongAdder missedDetections = new LongAdder();
    private final Map<String, RuleEffectiveness> ruleEffectiveness = new ConcurrentHashMap<>();
    private final AtomicLong totalResponseTimeMs = new AtomicLong(0);

    /**
     * 记录干预执行结果
     */
    public void recordIntervention(String ruleId, InterventionOutcome outcome, long responseTimeMs) {
        totalInterventions.increment();
        totalResponseTimeMs.addAndGet(responseTimeMs);

        RuleEffectiveness effectiveness = ruleEffectiveness.computeIfAbsent(ruleId,
            k -> new RuleEffectiveness());

        switch (outcome) {
            case SUCCESS -> {
                successfulInterventions.increment();
                effectiveness.recordSuccess(responseTimeMs);
            }
            case FALSE_POSITIVE -> {
                falsePositives.increment();
                effectiveness.recordFalsePositive();
            }
            case MISSED_DETECTION -> {
                missedDetections.increment();
                effectiveness.recordMissedDetection();
            }
        }

        log.debug("[闭环41] 干预结果记录: rule={}, outcome={}, responseTime={}ms",
            ruleId, outcome, responseTimeMs);
    }

    /**
     * 获取干预效果统计
     */
    public InterventionEffectivenessReport getReport() {
        long total = totalInterventions.sum();
        long success = successfulInterventions.sum();
        long fp = falsePositives.sum();
        long missed = missedDetections.sum();
        long avgResponseTime = total > 0 ? totalResponseTimeMs.get() / total : 0;

        Map<String, RuleEffectivenessSnapshot> ruleSnapshots = new ConcurrentHashMap<>();
        ruleEffectiveness.forEach((k, v) -> ruleSnapshots.put(k, v.toSnapshot()));

        return new InterventionEffectivenessReport(
            total, success, fp, missed,
            total > 0 ? (double) success / total : 0,
            total > 0 ? (double) fp / total : 0,
            avgResponseTime,
            ruleSnapshots,
            Instant.now()
        );
    }

    public String getEffectivenessSummary() {
        InterventionEffectivenessReport report = getReport();
        if (report.totalInterventions() == 0) return "No interventions recorded";
        return String.format("Interventions: total=%d, success=%.0f%%, falsePositive=%.0f%%, avgResponse=%dms",
            report.totalInterventions(),
            report.successRate() * 100,
            report.falsePositiveRate() * 100,
            report.avgResponseTimeMs()
        );
    }

    public enum InterventionOutcome {
        SUCCESS, FALSE_POSITIVE, MISSED_DETECTION
    }

    public record InterventionEffectivenessReport(
        long totalInterventions, long successfulInterventions,
        long falsePositives, long missedDetections,
        double successRate, double falsePositiveRate,
        long avgResponseTimeMs,
        Map<String, RuleEffectivenessSnapshot> ruleEffectiveness,
        Instant capturedAt
    ) {}

    public record RuleEffectivenessSnapshot(
        long triggerCount, long successCount, long falsePositiveCount,
        long missedDetectionCount, double successRate, double falsePositiveRate,
        long avgResponseTimeMs
    ) {}

    private static class RuleEffectiveness {
        private final LongAdder triggerCount = new LongAdder();
        private final LongAdder successCount = new LongAdder();
        private final LongAdder falsePositiveCount = new LongAdder();
        private final LongAdder missedDetectionCount = new LongAdder();
        private final AtomicLong totalResponseTimeMs = new AtomicLong(0);

        void recordSuccess(long responseTimeMs) {
            triggerCount.increment();
            successCount.increment();
            totalResponseTimeMs.addAndGet(responseTimeMs);
        }
        void recordFalsePositive() {
            triggerCount.increment();
            falsePositiveCount.increment();
        }
        void recordMissedDetection() {
            missedDetectionCount.increment();
        }

        RuleEffectivenessSnapshot toSnapshot() {
            long total = triggerCount.sum();
            return new RuleEffectivenessSnapshot(
                total, successCount.sum(), falsePositiveCount.sum(), missedDetectionCount.sum(),
                total > 0 ? (double) successCount.sum() / total : 0,
                total > 0 ? (double) falsePositiveCount.sum() / total : 0,
                total > 0 ? totalResponseTimeMs.get() / total : 0
            );
        }
    }
}
