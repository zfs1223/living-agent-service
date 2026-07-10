package com.livingagent.core.compliance.feedback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * 闭环45-P45-A: 合规违规追踪器
 * 追踪违规频率/整改完成率/重复违规
 */
public class ComplianceViolationTracker {

    private static final Logger log = LoggerFactory.getLogger(ComplianceViolationTracker.class);

    private final LongAdder totalViolations = new LongAdder();
    private final LongAdder resolvedViolations = new LongAdder();
    private final LongAdder repeatViolations = new LongAdder();
    private final Map<String, RuleViolationMetrics> ruleMetrics = new ConcurrentHashMap<>();

    public void recordViolation(String ruleId, String violatedBy, boolean isRepeat) {
        totalViolations.increment();
        if (isRepeat) repeatViolations.increment();
        ruleMetrics.computeIfAbsent(ruleId, k -> new RuleViolationMetrics())
            .recordViolation(isRepeat);
        log.debug("[闭环45] 合规违规记录: rule={}, by={}, repeat={}", ruleId, violatedBy, isRepeat);
    }

    public void recordResolution(String ruleId) {
        resolvedViolations.increment();
        ruleMetrics.computeIfAbsent(ruleId, k -> new RuleViolationMetrics()).recordResolution();
    }

    public ComplianceReport getReport() {
        long total = totalViolations.sum();
        Map<String, RuleViolationSnapshot> snapshots = new ConcurrentHashMap<>();
        ruleMetrics.forEach((k, v) -> snapshots.put(k, v.toSnapshot()));
        return new ComplianceReport(total, resolvedViolations.sum(), repeatViolations.sum(),
            total > 0 ? (double) resolvedViolations.sum() / total : 0,
            total > 0 ? (double) repeatViolations.sum() / total : 0,
            snapshots, Instant.now());
    }

    public record ComplianceReport(
        long totalViolations, long resolvedViolations, long repeatViolations,
        double resolutionRate, double repeatRate,
        Map<String, RuleViolationSnapshot> ruleMetrics, Instant capturedAt
    ) {}
    public record RuleViolationSnapshot(long violations, long resolved, long repeats, double resolutionRate, double repeatRate) {}

    private static class RuleViolationMetrics {
        final LongAdder violations = new LongAdder();
        final LongAdder resolved = new LongAdder();
        final LongAdder repeats = new LongAdder();
        void recordViolation(boolean isRepeat) { violations.increment(); if (isRepeat) repeats.increment(); }
        void recordResolution() { resolved.increment(); }
        RuleViolationSnapshot toSnapshot() {
            long v = violations.sum();
            return new RuleViolationSnapshot(v, resolved.sum(), repeats.sum(),
                v > 0 ? (double) resolved.sum() / v : 0,
                v > 0 ? (double) repeats.sum() / v : 0);
        }
    }
}
