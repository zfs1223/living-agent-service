package com.livingagent.core.autonomy.impl;

import com.livingagent.core.autonomy.KnowledgeQualityEvaluator;
import com.livingagent.core.knowledge.KnowledgeEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * NP2-1: 默认知识质量评估实现。
 * 基于 KnowledgeEntry 的 confidence、accessCount、verified、relevanceScore 等字段计算质量评分。
 */
@Component
public class DefaultKnowledgeQualityEvaluator implements KnowledgeQualityEvaluator {

    private static final Logger log = LoggerFactory.getLogger(DefaultKnowledgeQualityEvaluator.class);

    @Override
    public QualityAssessment assess(KnowledgeEntry entry) {
        if (entry == null) {
            return new QualityAssessment("", "", 0.0, 0.0, QualityLevel.LOW, List.of("条目为空"));
        }

        double score = calculateQualityScore(entry);
        double promotionReadiness = calculatePromotionReadiness(entry);
        QualityLevel level = resolveLevel(score);
        List<String> recommendations = generateRecommendations(entry, score, promotionReadiness);

        return new QualityAssessment(
            entry.getEntryId(),
            entry.getKey(),
            score,
            promotionReadiness,
            level,
            recommendations
        );
    }

    @Override
    public List<QualityAssessment> assessAll(List<KnowledgeEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        return entries.stream().map(this::assess).toList();
    }

    @Override
    public double calculatePromotionReadiness(KnowledgeEntry entry) {
        if (entry == null) return 0.0;

        double readiness = 0.0;

        // 置信度贡献（0-0.3）
        readiness += Math.min(entry.getConfidence(), 1.0) * 0.3;

        // 访问次数贡献（0-0.2，10次以上满分）
        readiness += Math.min(entry.getAccessCount() / 10.0, 1.0) * 0.2;

        // 验证状态贡献（0-0.2）
        if (entry.isVerified()) readiness += 0.2;

        // 相关性评分贡献（0-0.15）
        readiness += Math.min(entry.getRelevanceScore(), 1.0) * 0.15;

        // 重要性贡献（0-0.15）
        readiness += switch (entry.getImportance()) {
            case HIGH -> 0.15;
            case MEDIUM -> 0.10;
            case LOW -> 0.05;
        };

        return Math.min(readiness, 1.0);
    }

    private double calculateQualityScore(KnowledgeEntry entry) {
        double score = 0.0;

        // 置信度（0-0.3）
        score += Math.min(entry.getConfidence(), 1.0) * 0.3;

        // 验证状态（0-0.2）
        if (entry.isVerified()) score += 0.2;

        // 相关性评分（0-0.2）
        score += Math.min(entry.getRelevanceScore(), 1.0) * 0.2;

        // 访问活跃度（0-0.15，5次以上满分）
        score += Math.min(entry.getAccessCount() / 5.0, 1.0) * 0.15;

        // 重要性（0-0.15）
        score += switch (entry.getImportance()) {
            case HIGH -> 0.15;
            case MEDIUM -> 0.10;
            case LOW -> 0.05;
        };

        return Math.min(score, 1.0);
    }

    private QualityLevel resolveLevel(double score) {
        if (score >= 0.8) return QualityLevel.EXCELLENT;
        if (score >= 0.6) return QualityLevel.HIGH;
        if (score >= 0.3) return QualityLevel.MEDIUM;
        return QualityLevel.LOW;
    }

    private List<String> generateRecommendations(KnowledgeEntry entry, double score, double promotionReadiness) {
        List<String> recs = new ArrayList<>();

        if (entry.getConfidence() < 0.5) {
            recs.add("置信度较低（" + String.format("%.2f", entry.getConfidence()) + "），建议补充验证");
        }
        if (!entry.isVerified()) {
            recs.add("未验证，建议人工审核确认");
        }
        if (entry.getAccessCount() < 3) {
            recs.add("访问次数少（" + entry.getAccessCount() + "），可能需要更多使用验证");
        }
        if (promotionReadiness >= 0.7) {
            recs.add("晋升就绪度高（" + String.format("%.2f", promotionReadiness) + "），可考虑晋升到更高层级");
        }
        if (entry.isExpired()) {
            recs.add("知识已过期，建议更新或归档");
        }

        return recs;
    }
}
