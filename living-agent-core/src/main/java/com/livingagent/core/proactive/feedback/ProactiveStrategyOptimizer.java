package com.livingagent.core.proactive.feedback;

import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Map;

/**
 * 闭环47-P47-B: 主动策略优化器
 * 基于采纳率优化建议频率和内容策略
 */
public class ProactiveStrategyOptimizer {

    private static final Logger log = LoggerFactory.getLogger(ProactiveStrategyOptimizer.class);
    private static final double LOW_ACCEPTANCE_RATE = 0.20;

    private final ProactiveEffectivenessTracker tracker;
    private final CrossLoopEventBus eventBus;

    private volatile double pushFrequencyMultiplier = 1.0;
    private volatile double contentQualityThreshold = 0.5;

    public ProactiveStrategyOptimizer(ProactiveEffectivenessTracker tracker) {
        this(tracker, null);
    }

    public ProactiveStrategyOptimizer(ProactiveEffectivenessTracker tracker, CrossLoopEventBus eventBus) {
        this.tracker = tracker;
        this.eventBus = eventBus;
    }

    @Scheduled(fixedRate = 30 * 60 * 1000)
    public void scheduledOptimize() {
        optimize();
    }

    public void optimize() {
        ProactiveEffectivenessTracker.ProactiveEffectivenessReport report = tracker.getReport();
        if (report.totalSuggestions() < 10) return;

        boolean strategyChanged = false;

        if (report.acceptanceRate() < LOW_ACCEPTANCE_RATE) {
            pushFrequencyMultiplier = Math.max(0.3, pushFrequencyMultiplier * 0.7);
            contentQualityThreshold = Math.min(0.9, contentQualityThreshold + 0.1);
            log.info("[闭环47] 采纳率{}%偏低，降低推送频率至{}x，提高内容阈值至{}",
                String.format("%.0f", report.acceptanceRate() * 100),
                String.format("%.1f", pushFrequencyMultiplier),
                String.format("%.2f", contentQualityThreshold));
            strategyChanged = true;
        } else if (report.acceptanceRate() > 0.60) {
            pushFrequencyMultiplier = Math.min(2.0, pushFrequencyMultiplier * 1.2);
            contentQualityThreshold = Math.max(0.3, contentQualityThreshold - 0.05);
            log.info("[闭环47] 采纳率{}%正常，恢复推送频率至{}x",
                String.format("%.0f", report.acceptanceRate() * 100),
                String.format("%.1f", pushFrequencyMultiplier));
            strategyChanged = true;
        }

        if (report.behaviorChangeRate() < 0.10) {
            contentQualityThreshold = Math.min(0.9, contentQualityThreshold + 0.15);
            log.info("[闭环47] 行为改变率{}%偏低，提高内容阈值至{}",
                String.format("%.0f", report.behaviorChangeRate() * 100),
                String.format("%.2f", contentQualityThreshold));
            strategyChanged = true;
        }

        if (strategyChanged && eventBus != null) {
            eventBus.publish(47, "strategy_optimized",
                CrossLoopEvent.EventPriority.DEGRADATION,
                Map.of("pushFrequencyMultiplier", pushFrequencyMultiplier,
                       "contentQualityThreshold", contentQualityThreshold,
                       "acceptanceRate", report.acceptanceRate()),
                300);
        }
    }

    public double getPushFrequencyMultiplier() {
        return pushFrequencyMultiplier;
    }

    public double getContentQualityThreshold() {
        return contentQualityThreshold;
    }
}
