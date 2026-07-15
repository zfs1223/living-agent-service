package com.livingagent.core.skill.feedback;

import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SkillImprovementExecutor {

    private static final Logger log = LoggerFactory.getLogger(SkillImprovementExecutor.class);

    private final SkillEffectivenessTracker effectivenessTracker;
    private final SkillRecommendationEngine recommendationEngine;
    private final CrossLoopEventBus eventBus;

    private volatile double effectivenessThreshold = 0.80;

    @Autowired(required = false)
    private com.livingagent.core.skill.SkillRegistry skillRegistry;

    public SkillImprovementExecutor(SkillEffectivenessTracker effectivenessTracker,
                                     SkillRecommendationEngine recommendationEngine,
                                     CrossLoopEventBus eventBus) {
        this.effectivenessTracker = effectivenessTracker;
        this.recommendationEngine = recommendationEngine;
        this.eventBus = eventBus;
    }

    @Scheduled(fixedRate = 30 * 60 * 1000)
    public void evaluateAndExecute() {
        List<SkillRecommendationEngine.SkillRecommendation> recommendations =
            recommendationEngine.generateRecommendations();

        if (recommendations.isEmpty()) return;

        for (SkillRecommendationEngine.SkillRecommendation rec : recommendations) {
            log.info("[闭环42] 执行技能改进: skill={}, action={}", rec.skillId(), rec.action());

            if ("REPLACE".equals(rec.action())) {
                eventBus.publish(42, "skill_replacement_needed",
                    CrossLoopEvent.EventPriority.DEGRADATION,
                    Map.of("skillId", rec.skillId(),
                        "action", "disable_and_replace",
                        "successRate", rec.successRate(),
                        "reason", rec.reason()));
            } else if ("OPTIMIZE".equals(rec.action())) {
                eventBus.publish(42, "skill_optimization_needed",
                    CrossLoopEvent.EventPriority.ECONOMY,
                    Map.of("skillId", rec.skillId(),
                        "action", "optimize_execution",
                        "successRate", rec.successRate(),
                        "reason", rec.reason()));
            }
        }

        Map<String, SkillEffectivenessTracker.SkillEffectivenessReport> reports =
            effectivenessTracker.getAllReports();
        double lowEffectivenessRate = (double) reports.values().stream()
            .filter(r -> r.isLowEffectiveness()).count() / Math.max(1, reports.size());

        if (lowEffectivenessRate > 0.3) {
            effectivenessThreshold = Math.max(0.60, effectivenessThreshold - 0.05);
            log.info("[闭环42] 低效技能率{}%，降低有效性阈值至{}%",
                String.format("%.0f", lowEffectivenessRate * 100),
                String.format("%.0f", effectivenessThreshold * 100));
        } else if (lowEffectivenessRate < 0.1 && effectivenessThreshold < 0.90) {
            effectivenessThreshold = Math.min(0.90, effectivenessThreshold + 0.02);
        }
    }

    public double getEffectivenessThreshold() {
        return effectivenessThreshold;
    }
}
