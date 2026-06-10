package com.livingagent.core.autonomy;

import java.util.List;

/**
 * 执行能力解析结果。
 * 输出：executionCapability + artifactType + executionMode + confidence + reason
 */
public record ExecutionCapabilityResolution(
    ExecutionCapability executionCapability,
    ArtifactType artifactType,
    ExecutionMode executionMode,
    double confidence,
    String reason,
    boolean requiresClarification,
    List<String> clarificationQuestions,
    boolean requiresHumanReview
) {
    public static ExecutionCapabilityResolution resolved(
            ExecutionCapability capability, ArtifactType artifactType, ExecutionMode mode,
            double confidence, String reason) {
        return new ExecutionCapabilityResolution(capability, artifactType, mode, confidence, reason,
            false, List.of(), false);
    }

    public static ExecutionCapabilityResolution needsClarification(
            List<String> clarificationQuestions, String reason) {
        return new ExecutionCapabilityResolution(null, null, ExecutionMode.NO_EXECUTION, 0.0, reason,
            true, clarificationQuestions != null ? List.copyOf(clarificationQuestions) : List.of(), false);
    }

    public static ExecutionCapabilityResolution needsHumanReview(String reason) {
        return new ExecutionCapabilityResolution(null, null, ExecutionMode.HUMAN_REVIEW_REQUIRED, 0.0, reason,
            false, List.of(), true);
    }
}
