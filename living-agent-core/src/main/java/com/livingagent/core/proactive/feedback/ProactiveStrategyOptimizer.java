package com.livingagent.core.proactive.feedback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 闭环47-P47-B: 主动策略优化器
 * 基于采纳率优化建议频率和内容策略
 */
public class ProactiveStrategyOptimizer {

    private static final Logger log = LoggerFactory.getLogger(ProactiveStrategyOptimizer.class);
    private static final double LOW_ACCEPTANCE_RATE = 0.20;

    private final ProactiveEffectivenessTracker tracker;

    public ProactiveStrategyOptimizer(ProactiveEffectivenessTracker tracker) {
        this.tracker = tracker;
    }

    public void optimize() {
        ProactiveEffectivenessTracker.ProactiveEffectivenessReport report = tracker.getReport();
        if (report.totalSuggestions() < 10) return;

        if (report.acceptanceRate() < LOW_ACCEPTANCE_RATE) {
            log.info("[闭环47] 主动建议采纳率{}%偏低，建议降低推送频率或优化内容",
                String.format("%.0f%%", report.acceptanceRate() * 100));
        }

        if (report.behaviorChangeRate() < 0.10) {
            log.info("[闭环47] 行为改变率{}%偏低，建议调整建议策略",
                String.format("%.0f%%", report.behaviorChangeRate() * 100));
        }
    }
}
