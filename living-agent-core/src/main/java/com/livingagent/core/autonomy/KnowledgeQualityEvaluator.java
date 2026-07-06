package com.livingagent.core.autonomy;

import com.livingagent.core.knowledge.KnowledgeEntry;
import java.util.List;

/**
 * NP2-1: 知识质量评估服务。
 * 对知识条目进行质量评估，生成质量评分和晋升/降级建议。
 */
public interface KnowledgeQualityEvaluator {

    /**
     * 评估单条知识的质量。
     */
    QualityAssessment assess(KnowledgeEntry entry);

    /**
     * 批量评估知识列表，返回评估结果。
     */
    List<QualityAssessment> assessAll(List<KnowledgeEntry> entries);

    /**
     * 计算知识条目的晋升就绪度（0.0-1.0）。
     * 综合考虑置信度、访问次数、验证状态、相关性评分等。
     */
    double calculatePromotionReadiness(KnowledgeEntry entry);

    /**
     * 单条知识质量评估结果。
     */
    record QualityAssessment(
        String entryId,
        String key,
        double qualityScore,
        double promotionReadiness,
        QualityLevel level,
        List<String> recommendations
    ) {}

    /**
     * 质量等级。
     */
    enum QualityLevel {
        LOW,       // qualityScore < 0.3
        MEDIUM,    // 0.3 <= qualityScore < 0.6
        HIGH,      // 0.6 <= qualityScore < 0.8
        EXCELLENT  // qualityScore >= 0.8
    }
}
