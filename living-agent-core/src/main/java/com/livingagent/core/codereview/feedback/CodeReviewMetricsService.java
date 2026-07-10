package com.livingagent.core.codereview.feedback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.LongAdder;

/**
 * 闭环49-P49-A: 代码审查指标服务
 * 追踪审查通过率/返工次数/审查耗时
 *
 * <p>扩展（P49-C）：支持记录 fuck-u-code 基线评分
 */
public class CodeReviewMetricsService {

    private static final Logger log = LoggerFactory.getLogger(CodeReviewMetricsService.class);

    private final LongAdder totalReviews = new LongAdder();
    private final LongAdder approvedReviews = new LongAdder();
    private final LongAdder totalReworkCycles = new LongAdder();
    private final Map<String, ReviewMetrics> reviewerMetrics = new ConcurrentHashMap<>();

    // P49-C: fuck-u-code 基线评分记录
    private final List<BaselineScore> baselineScores = new CopyOnWriteArrayList<>();
    private final LongAdder totalBaselineScore = new LongAdder();
    private final LongAdder baselineCount = new LongAdder();

    public void recordReviewCompleted(String reviewerId, boolean approved, int reworkCycles, long reviewTimeMs) {
        totalReviews.increment();
        if (approved) approvedReviews.increment();
        totalReworkCycles.add(reworkCycles);
        reviewerMetrics.computeIfAbsent(reviewerId, k -> new ReviewMetrics())
            .recordReview(approved, reworkCycles, reviewTimeMs);
    }

    /**
     * P49-C: 记录 fuck-u-code 分析基线评分
     *
     * @param filePath 文件路径
     * @param score 评分 (0-100)
     * @param metrics 详细指标（complexity/duplication/size/structure/error/documentation/naming）
     */
    public void recordBaselineScore(String filePath, int score, Map<String, Double> metrics) {
        BaselineScore baseline = new BaselineScore(filePath, score, metrics, Instant.now());
        baselineScores.add(baseline);
        totalBaselineScore.add(score);
        baselineCount.increment();

        log.info("[P49-C] 记录基线评分: file={}, score={}, metrics={}", filePath, score, metrics.keySet());
    }

    /**
     * P49-C: 批量记录基线评分（来自 analyze 结果）
     */
    public void recordBaselineScores(List<BaselineScore> scores) {
        for (BaselineScore score : scores) {
            baselineScores.add(score);
            totalBaselineScore.add(score.score());
            baselineCount.increment();
        }
        log.info("[P49-C] 批量记录基线评分: count={}", scores.size());
    }

    /**
     * P49-C: 获取基线评分报告
     */
    public BaselineScoreReport getBaselineReport() {
        long count = baselineCount.sum();
        double avgScore = count > 0 ? (double) totalBaselineScore.sum() / count : 0;

        // 计算低于阈值的文件数
        long poorFiles = baselineScores.stream()
            .filter(b -> b.score() < 60)
            .count();

        return new BaselineScoreReport(
            count,
            avgScore,
            poorFiles,
            new ArrayList<>(baselineScores.subList(
                Math.max(0, baselineScores.size() - 100), // 最近100条
                baselineScores.size()
            )),
            Instant.now()
        );
    }

    public CodeReviewMetricsReport getReport() {
        long total = totalReviews.sum();
        return new CodeReviewMetricsReport(
            total, approvedReviews.sum(), totalReworkCycles.sum(),
            total > 0 ? (double) approvedReviews.sum() / total : 0,
            total > 0 ? (double) totalReworkCycles.sum() / total : 0,
            Instant.now()
        );
    }

    // ==================== 数据类 ====================

    public record CodeReviewMetricsReport(
        long totalReviews, long approvedReviews, long totalReworkCycles,
        double approvalRate, double avgReworkCycles, Instant capturedAt
    ) {}

    /**
     * P49-C: 基线评分记录
     */
    public record BaselineScore(
        String filePath,
        int score,
        Map<String, Double> metrics,
        Instant capturedAt
    ) {}

    /**
     * P49-C: 基线评分报告
     */
    public record BaselineScoreReport(
        long totalFiles,
        double avgScore,
        long poorFiles,
        List<BaselineScore> recentScores,
        Instant capturedAt
    ) {}

    private static class ReviewMetrics {
        final LongAdder total = new LongAdder();
        final LongAdder approved = new LongAdder();
        final LongAdder reworkCycles = new LongAdder();
        final LongAdder totalTimeMs = new LongAdder();
        void recordReview(boolean isApproved, int rework, long timeMs) {
            total.increment();
            if (isApproved) approved.increment();
            reworkCycles.add(rework);
            totalTimeMs.add(timeMs);
        }
    }
}
