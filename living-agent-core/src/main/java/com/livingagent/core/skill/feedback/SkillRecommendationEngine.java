package com.livingagent.core.skill.feedback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 闭环42-P42-B: 技能推荐引擎
 * 基于部门需求推荐技能，低效技能建议替换
 */
public class SkillRecommendationEngine {

    private static final Logger log = LoggerFactory.getLogger(SkillRecommendationEngine.class);

    private final SkillEffectivenessTracker effectivenessTracker;

    public SkillRecommendationEngine(SkillEffectivenessTracker effectivenessTracker) {
        this.effectivenessTracker = effectivenessTracker;
    }

    public List<SkillRecommendation> generateRecommendations() {
        List<SkillRecommendation> recommendations = new ArrayList<>();
        Map<String, SkillEffectivenessTracker.SkillEffectivenessReport> reports =
            effectivenessTracker.getAllReports();

        for (SkillEffectivenessTracker.SkillEffectivenessReport report : reports.values()) {
            if (report.isLowEffectiveness()) {
                recommendations.add(new SkillRecommendation(
                    report.skillId(),
                    "REPLACE",
                    String.format("技能%s成功率%.0f%%低于阈值%.0f%%，建议卸载或更新",
                        report.skillId(), report.successRate() * 100, 80.0),
                    report.successRate()
                ));
            } else if (report.avgExecutionTimeMs() > 5000) {
                recommendations.add(new SkillRecommendation(
                    report.skillId(),
                    "OPTIMIZE",
                    String.format("技能%s平均耗时%.0fms过高，建议优化或替换",
                        report.skillId(), report.avgExecutionTimeMs()),
                    report.successRate()
                ));
            }
        }

        log.info("[闭环42] 技能推荐生成: {}条建议", recommendations.size());
        return recommendations;
    }

    public record SkillRecommendation(
        String skillId, String action, String reason, double successRate
    ) {}
}
