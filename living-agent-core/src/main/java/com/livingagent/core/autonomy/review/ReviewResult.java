package com.livingagent.core.autonomy.review;

import java.util.List;

/**
 * 审查结果。
 *
 * @param reviewerCode  审查员工代码
 * @param decision      审查决定
 * @param qualityScore  质量评分 (0.0-1.0)
 * @param issues        发现的问题列表
 * @param suggestions   改进建议列表
 * @param completionTag 完成标记：仅 APPROVED 时为 true
 * @param reviewRound   当前审查轮次
 */
public record ReviewResult(
    String reviewerCode,
    ReviewDecision decision,
    double qualityScore,
    List<String> issues,
    List<String> suggestions,
    boolean completionTag,
    int reviewRound
) {
    public static ReviewResult approved(String reviewerCode, double qualityScore, int reviewRound) {
        return new ReviewResult(reviewerCode, ReviewDecision.APPROVED, qualityScore,
            List.of(), List.of(), true, reviewRound);
    }

    public static ReviewResult revisionNeeded(String reviewerCode, double qualityScore,
                                               List<String> issues, List<String> suggestions, int reviewRound) {
        return new ReviewResult(reviewerCode, ReviewDecision.REVISION_NEEDED, qualityScore,
            issues, suggestions, false, reviewRound);
    }

    public static ReviewResult rejected(String reviewerCode, List<String> issues, int reviewRound) {
        return new ReviewResult(reviewerCode, ReviewDecision.REJECTED, 0.0,
            issues, List.of(), false, reviewRound);
    }

    public static ReviewResult escalated(String reviewerCode, String reason, int reviewRound) {
        return new ReviewResult(reviewerCode, ReviewDecision.ESCALATE_TO_BRAIN, 0.0,
            List.of(reason), List.of(), false, reviewRound);
    }
}
