package com.livingagent.core.autonomy;

import java.util.Map;

public record PerformanceCaptureResult(
    boolean success,
    String employeeCode,
    String executionId,
    String contributionType,
    Map<String, Object> metadata
) {
    public static PerformanceCaptureResult success(String employeeCode, String executionId, String contributionType) {
        return new PerformanceCaptureResult(true, employeeCode, executionId, contributionType, Map.of());
    }

    public static PerformanceCaptureResult skipped(String reason) {
        return new PerformanceCaptureResult(false, null, null, reason, Map.of());
    }
}
