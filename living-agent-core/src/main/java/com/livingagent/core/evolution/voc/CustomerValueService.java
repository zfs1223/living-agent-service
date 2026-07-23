package com.livingagent.core.evolution.voc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * P12: DBS 客户价值指标服务（VOC 闭环扩展）。
 *
 * P12-1: 闭环66 — 客户价值指标
 *   - 从 VOC 信号聚合客户价值指标
 *   - Net Value Score = 加权(NEED×1.0 + PAIN×1.5 + PRAISE×0.5) / 总量
 *   - 客户价值指标驱动闭环4进化调整
 *
 * P12-2: 闭环54 关联客户价值
 *   - 将客户价值指标与大脑能力评估关联
 *   - 闭环54（能力演进）的输入维度增加 VOC 反馈
 *
 * 关联闭环：
 * - 闭环46（对话质量）→ VOC 信号来源
 * - 闭环4（进化调整）→ 客户价值驱动进化
 * - 闭环54（能力演进）→ 客户价值关联能力
 */
public interface CustomerValueService {

    /**
     * 记录一个 VOC 信号。
     */
    void recordVOC(String brainDomain, String vocType, double confidence, String content);

    /**
     * 获取指定大脑域的客户价值指标。
     */
    CustomerValueMetrics getMetrics(String brainDomain);

    /**
     * 获取所有大脑域的客户价值概览。
     */
    Map<String, CustomerValueMetrics> getAllMetrics();

    /**
     * 计算全局 Net Value Score。
     */
    double getNetValueScore();

    /**
     * 获取闭环54关联：能力价值矩阵。
     */
    CapabilityValueMatrix getCapabilityValueMatrix();

    /**
     * 客户价值指标。
     */
    record CustomerValueMetrics(
        String brainDomain,
        double needScore,      // USER_NEED 加权得分
        double painScore,      // USER_PAIN 加权得分（权重1.5）
        double praiseScore,    // USER_PRAISE 加权得分（权重0.5）
        double netValueScore,  // 净价值得分
        int totalVOCCount,     // VOC 信号总数
        int needCount,
        int painCount,
        int praiseCount,
        Instant lastUpdated
    ) {
        /**
         * 价值趋势：PRAISE > PAIN 为正向，反之为负向。
         */
        public boolean isPositiveTrend() {
            return praiseScore > painScore;
        }
    }

    /**
     * 闭环54 能力价值矩阵。
     */
    record CapabilityValueMatrix(
        Map<String, Double> capabilityScores,  // 能力 → 评分
        Map<String, Double> valueContributions, // 能力 → 客户价值贡献
        List<String> highValueLowCapability,    // 高价值低能力（需优先提升）
        List<String> lowValueHighCapability,    // 低价值高能力（可降级）
        Instant generatedAt
    ) {}
}
