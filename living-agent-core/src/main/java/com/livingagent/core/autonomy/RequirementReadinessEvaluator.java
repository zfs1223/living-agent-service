package com.livingagent.core.autonomy;

import java.util.List;

/**
 * 需求就绪评估器。
 * 职责：在主脑规划和员工分派之前，判断用户需求是否足够明确。
 *
 * 设计原则：
 * - 需求不明确时，不应进入任务规划和员工分派，而应先澄清
 * - 澄清由主脑统一负责，不直接派给员工
 * - 评估结果为 SUFFICIENT 时才进入规划流程
 */
public interface RequirementReadinessEvaluator {

    /**
     * 评估需求就绪状态。
     */
    RequirementReadinessResult evaluate(String userMessage, String department, String sessionId);

    /**
     * 需求就绪评估结果。
     */
    record RequirementReadinessResult(
        ReadinessLevel level,
        double confidence,
        List<String> missingElements,
        List<String> clarificationQuestions,
        String reason
    ) {
        public static RequirementReadinessResult sufficient(double confidence, String reason) {
            return new RequirementReadinessResult(ReadinessLevel.SUFFICIENT, confidence, List.of(), List.of(), reason);
        }

        public static RequirementReadinessResult partiallySufficient(double confidence,
                List<String> missingElements, List<String> clarificationQuestions, String reason) {
            return new RequirementReadinessResult(ReadinessLevel.PARTIALLY_SUFFICIENT, confidence,
                missingElements, clarificationQuestions, reason);
        }

        public static RequirementReadinessResult insufficient(List<String> clarificationQuestions, String reason) {
            return new RequirementReadinessResult(ReadinessLevel.INSUFFICIENT, 0.0, List.of(), clarificationQuestions, reason);
        }

        public boolean isReady() {
            return level == ReadinessLevel.SUFFICIENT;
        }

        public boolean needsClarification() {
            return level != ReadinessLevel.SUFFICIENT;
        }
    }

    enum ReadinessLevel {
        /** 需求明确，可以进入规划 */
        SUFFICIENT,
        /** 需求部分明确，可规划但建议先澄清 */
        PARTIALLY_SUFFICIENT,
        /** 需求不明确，必须先澄清 */
        INSUFFICIENT
    }
}
