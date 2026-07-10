package com.livingagent.core.proactive.feedback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * 闭环47-P47-A: 主动服务效果追踪器
 * 追踪主动建议采纳率/行为改变率
 */
public class ProactiveEffectivenessTracker {

    private static final Logger log = LoggerFactory.getLogger(ProactiveEffectivenessTracker.class);

    private final LongAdder totalSuggestions = new LongAdder();
    private final LongAdder acceptedSuggestions = new LongAdder();
    private final LongAdder behaviorChanged = new LongAdder();
    private final Map<String, CategoryMetrics> categoryMetrics = new ConcurrentHashMap<>();

    public void recordSuggestion(String category) {
        totalSuggestions.increment();
        categoryMetrics.computeIfAbsent(category, k -> new CategoryMetrics()).suggested.increment();
    }

    public void recordAccepted(String category, boolean behaviorChanged) {
        acceptedSuggestions.increment();
        if (behaviorChanged) this.behaviorChanged.increment();
        categoryMetrics.computeIfAbsent(category, k -> new CategoryMetrics()).recordAccepted(behaviorChanged);
    }

    public ProactiveEffectivenessReport getReport() {
        long total = totalSuggestions.sum();
        return new ProactiveEffectivenessReport(
            total, acceptedSuggestions.sum(), behaviorChanged.sum(),
            total > 0 ? (double) acceptedSuggestions.sum() / total : 0,
            total > 0 ? (double) behaviorChanged.sum() / total : 0,
            Instant.now()
        );
    }

    public record ProactiveEffectivenessReport(
        long totalSuggestions, long acceptedSuggestions, long behaviorChanged,
        double acceptanceRate, double behaviorChangeRate, Instant capturedAt
    ) {}

    private static class CategoryMetrics {
        final LongAdder suggested = new LongAdder();
        final LongAdder accepted = new LongAdder();
        final LongAdder changed = new LongAdder();
        void recordAccepted(boolean behaviorChanged) {
            accepted.increment();
            if (behaviorChanged) changed.increment();
        }
    }
}
