package com.livingagent.core.codereview.feedback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.HashMap;

/**
 * 闭环49-P49-B: 代码审查质量优化器
 * 基于指标优化审查规则和预检清单
 *
 * <p>扩展（P49-C）：基于 fuck-u-code 基线评分优化阈值
 */
public class CodeReviewQualityOptimizer {

    private static final Logger log = LoggerFactory.getLogger(CodeReviewQualityOptimizer.class);
    private static final double HIGH_REWORK_THRESHOLD = 2.0;
    private static final int QUALITY_THRESHOLD_DEFAULT = 60;
    private static final int MIN_BASELINE_SAMPLES = 10;

    private final CodeReviewMetricsService metricsService;
    private volatile int qualityThreshold = QUALITY_THRESHOLD_DEFAULT;
    private volatile double avgBaselineScore = 0;

    public CodeReviewQualityOptimizer(CodeReviewMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    public void optimize() {
        CodeReviewMetricsService.CodeReviewMetricsReport report = metricsService.getReport();
        if (report.totalReviews() < 5) return;

        boolean strategyChanged = false;

        if (report.avgReworkCycles() > HIGH_REWORK_THRESHOLD) {
            qualityThreshold = Math.max(40, qualityThreshold - 5);
            log.info("[闭环49] 平均返工次数{}次偏高，降低质量阈值至{}以减少阻断",
                String.format("%.1f", report.avgReworkCycles()), qualityThreshold);
            strategyChanged = true;
        }

        if (report.approvalRate() < 0.50) {
            qualityThreshold = Math.max(40, qualityThreshold - 5);
            log.info("[闭环49] 审查通过率{}%偏低，降低质量阈值至{}以减少返工",
                String.format("%.0f", report.approvalRate() * 100), qualityThreshold);
            strategyChanged = true;
        } else if (report.approvalRate() > 0.85 && report.avgReworkCycles() < 1.5) {
            qualityThreshold = Math.min(80, qualityThreshold + 3);
            log.info("[闭环49] 审查通过率{}%良好，适当提高质量阈值至{}",
                String.format("%.0f", report.approvalRate() * 100), qualityThreshold);
            strategyChanged = true;
        }

        if (strategyChanged) {
            adjustThresholdBasedOnBaseline();
        }
    }

    /**
     * P49-C: 基于基线评分优化质量阈值
     *
     * <p>策略：
     * <ul>
     *   <li>平均分 > 80: 阈值提高到 70（高标准）</li>
     *   <li>平均分 60-80: 阈值保持 60（标准）</li>
     *   <li>平均分 < 60: 阈值降低到 50（宽松，避免过多阻断）</li>
     * </ul>
     *
     * @return 调整后的质量阈值
     */
    public int adjustThresholdBasedOnBaseline() {
        CodeReviewMetricsService.BaselineScoreReport baselineReport = metricsService.getBaselineReport();

        if (baselineReport.totalFiles() < MIN_BASELINE_SAMPLES) {
            log.debug("[P49-C] 基线样本不足（{}），保持默认阈值", baselineReport.totalFiles());
            return qualityThreshold;
        }

        avgBaselineScore = baselineReport.avgScore();

        int oldThreshold = qualityThreshold;
        if (avgBaselineScore > 80) {
            qualityThreshold = 70;
        } else if (avgBaselineScore < 60) {
            qualityThreshold = 50;
        } else {
            qualityThreshold = QUALITY_THRESHOLD_DEFAULT;
        }

        if (qualityThreshold != oldThreshold) {
            log.info("[P49-C] 质量阈值调整: {} → {}（平均基线分={:.1f}，样本数={}）",
                oldThreshold, qualityThreshold, avgBaselineScore, baselineReport.totalFiles());
        }

        return qualityThreshold;
    }

    /**
     * P49-C: 基于基线评分生成优化建议
     */
    public Map<String, Object> generateOptimizationSuggestions() {
        Map<String, Object> suggestions = new HashMap<>();

        CodeReviewMetricsService.BaselineScoreReport baselineReport = metricsService.getBaselineReport();
        CodeReviewMetricsService.CodeReviewMetricsReport reviewReport = metricsService.getReport();

        suggestions.put("currentThreshold", qualityThreshold);
        suggestions.put("avgBaselineScore", avgBaselineScore);
        suggestions.put("totalFilesAnalyzed", baselineReport.totalFiles());
        suggestions.put("poorFilesCount", baselineReport.poorFiles());

        // 阈值建议
        if (baselineReport.poorFiles() > baselineReport.totalFiles() * 0.3) {
            suggestions.put("thresholdRecommendation", "考虑降低阈值或加强代码培训");
        } else if (baselineReport.avgScore() > 85) {
            suggestions.put("thresholdRecommendation", "可考虑提高阈值以追求更高质量");
        } else {
            suggestions.put("thresholdRecommendation", "当前阈值合理");
        }

        // 返工建议
        if (reviewReport.avgReworkCycles() > HIGH_REWORK_THRESHOLD) {
            suggestions.put("reworkRecommendation", "建议加强代码预检清单");
        }

        return suggestions;
    }

    /**
     * P49-C: 获取当前质量阈值
     */
    public int getQualityThreshold() {
        return qualityThreshold;
    }

    /**
     * P49-C: 获取平均基线评分
     */
    public double getAvgBaselineScore() {
        return avgBaselineScore;
    }
}
