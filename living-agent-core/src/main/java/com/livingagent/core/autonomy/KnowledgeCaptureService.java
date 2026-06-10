package com.livingagent.core.autonomy;

public interface KnowledgeCaptureService {
    KnowledgeCaptureResult captureFromExecution(
        String executionId,
        String department,
        String taskType,
        String goal,
        String resultSummary,
        java.util.List<String> employeeCodes
    );
}
