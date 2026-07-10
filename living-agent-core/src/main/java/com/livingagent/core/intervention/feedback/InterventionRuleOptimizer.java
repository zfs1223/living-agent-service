package com.livingagent.core.intervention.feedback;

import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 闭环41-P41-B/C: 干预规则优化器
 * 基于效果数据自动调整规则阈值和优先级
 * 干预效果→EvolutionSignal→规则自动优化闭环(P41-C)
 */
public class InterventionRuleOptimizer {

    private static final Logger log = LoggerFactory.getLogger(InterventionRuleOptimizer.class);

    private static final double HIGH_FALSE_POSITIVE_RATE = 0.40;
    private static final double LOW_SUCCESS_RATE = 0.50;

    private final InterventionEffectivenessTracker effectivenessTracker;
    private final CrossLoopEventBus eventBus;
    private final Map<String, RuleAdjustment> pendingAdjustments = new ConcurrentHashMap<>();

    public InterventionRuleOptimizer(InterventionEffectivenessTracker effectivenessTracker,
                                      CrossLoopEventBus eventBus) {
        this.effectivenessTracker = effectivenessTracker;
        this.eventBus = eventBus;
    }

    /**
     * 分析效果数据并建议规则调整
     */
    public OptimizationResult optimize() {
        InterventionEffectivenessTracker.InterventionEffectivenessReport report =
            effectivenessTracker.getReport();

        int adjustedCount = 0;
        int totalRules = report.ruleEffectiveness().size();

        for (Map.Entry<String, InterventionEffectivenessTracker.RuleEffectivenessSnapshot> entry :
                report.ruleEffectiveness().entrySet()) {
            String ruleId = entry.getKey();
            InterventionEffectivenessTracker.RuleEffectivenessSnapshot snapshot = entry.getValue();

            if (snapshot.triggerCount() < 3) continue;

            RuleAdjustment adjustment = null;

            // 高误报率 → 提高阈值
            if (snapshot.falsePositiveRate() > HIGH_FALSE_POSITIVE_RATE) {
                adjustment = new RuleAdjustment(
                    ruleId, "INCREASE_THRESHOLD",
                    String.format("规则%s误报率%.1f%%超过阈值%.0f%%，建议提高触发阈值",
                        ruleId, snapshot.falsePositiveRate() * 100, HIGH_FALSE_POSITIVE_RATE * 100),
                    snapshot.falsePositiveRate(), snapshot.successRate()
                );
            }
            // 低成功率 → 降低优先级或禁用
            else if (snapshot.successRate() < LOW_SUCCESS_RATE) {
                adjustment = new RuleAdjustment(
                    ruleId, "DECREASE_PRIORITY",
                    String.format("规则%s成功率%.1f%%低于阈值%.0f%%，建议降低优先级或审查规则逻辑",
                        ruleId, snapshot.successRate() * 100, LOW_SUCCESS_RATE * 100),
                    snapshot.falsePositiveRate(), snapshot.successRate()
                );
            }

            if (adjustment != null) {
                pendingAdjustments.put(ruleId, adjustment);
                adjustedCount++;
                publishOptimizationSignal(adjustment);
            }
        }

        log.info("[闭环41] 规则优化完成: 分析{}条规则，{}条需调整", totalRules, adjustedCount);
        return new OptimizationResult(totalRules, adjustedCount, Map.copyOf(pendingAdjustments));
    }

    private void publishOptimizationSignal(RuleAdjustment adjustment) {
        if (eventBus != null) {
            eventBus.publish(41, "intervention_optimization", CrossLoopEvent.EventPriority.DEGRADATION,
                Map.of("content", String.format("干预规则优化: %s - %s", adjustment.ruleId(), adjustment.action()),
                    "ruleId", adjustment.ruleId(), "action", adjustment.action(),
                    "reason", adjustment.reason(), "successRate", adjustment.successRate()));
        }
        log.info("[闭环41] 规则调整建议: rule={}, action={}, reason={}",
            adjustment.ruleId(), adjustment.action(), adjustment.reason());
    }

    public Map<String, RuleAdjustment> getPendingAdjustments() {
        return Map.copyOf(pendingAdjustments);
    }

    public void clearAdjustment(String ruleId) {
        pendingAdjustments.remove(ruleId);
    }

    public record RuleAdjustment(
        String ruleId, String action, String reason,
        double falsePositiveRate, double successRate
    ) {}

    public record OptimizationResult(
        int totalRulesAnalyzed, int adjustedCount,
        Map<String, RuleAdjustment> adjustments
    ) {}
}
