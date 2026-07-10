package com.livingagent.core.skill.feedback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * 闭环42-P42-A: 技能效果追踪器
 * 追踪技能调用成功率/耗时/用户满意度
 */
public class SkillEffectivenessTracker {

    private static final Logger log = LoggerFactory.getLogger(SkillEffectivenessTracker.class);
    private static final double LOW_SUCCESS_RATE_THRESHOLD = 0.80;

    private final Map<String, SkillMetrics> skillMetrics = new ConcurrentHashMap<>();

    public void recordInvocation(String skillId, boolean success, long executionTimeMs) {
        SkillMetrics metrics = skillMetrics.computeIfAbsent(skillId, k -> new SkillMetrics());
        metrics.invocations.increment();
        if (success) {
            metrics.successCount.increment();
        } else {
            metrics.failureCount.increment();
        }
        metrics.totalExecutionTimeMs.add(executionTimeMs);
        log.debug("[闭环42] 技能调用记录: skill={}, success={}, time={}ms", skillId, success, executionTimeMs);
    }

    public SkillEffectivenessReport getReport(String skillId) {
        SkillMetrics metrics = skillMetrics.get(skillId);
        if (metrics == null) return null;
        long total = metrics.invocations.sum();
        return new SkillEffectivenessReport(
            skillId, total, metrics.successCount.sum(), metrics.failureCount.sum(),
            total > 0 ? (double) metrics.successCount.sum() / total : 0,
            total > 0 ? (double) metrics.totalExecutionTimeMs.sum() / total : 0,
            total > 0 ? (double) metrics.successCount.sum() / total < LOW_SUCCESS_RATE_THRESHOLD : false,
            Instant.now()
        );
    }

    public Map<String, SkillEffectivenessReport> getAllReports() {
        Map<String, SkillEffectivenessReport> reports = new ConcurrentHashMap<>();
        skillMetrics.forEach((id, m) -> {
            long total = m.invocations.sum();
            reports.put(id, new SkillEffectivenessReport(
                id, total, m.successCount.sum(), m.failureCount.sum(),
                total > 0 ? (double) m.successCount.sum() / total : 0,
                total > 0 ? (double) m.totalExecutionTimeMs.sum() / total : 0,
                total > 0 ? (double) m.successCount.sum() / total < LOW_SUCCESS_RATE_THRESHOLD : false,
                Instant.now()
            ));
        });
        return reports;
    }

    public record SkillEffectivenessReport(
        String skillId, long totalInvocations, long successCount, long failureCount,
        double successRate, double avgExecutionTimeMs, boolean isLowEffectiveness,
        Instant capturedAt
    ) {}

    private static class SkillMetrics {
        final LongAdder invocations = new LongAdder();
        final LongAdder successCount = new LongAdder();
        final LongAdder failureCount = new LongAdder();
        final LongAdder totalExecutionTimeMs = new LongAdder();
    }
}
