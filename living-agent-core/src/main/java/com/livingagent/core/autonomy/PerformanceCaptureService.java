package com.livingagent.core.autonomy;

import java.util.List;

public interface PerformanceCaptureService {
    List<PerformanceCaptureResult> captureFromExecution(
        String executionId,
        String department,
        String taskType,
        String goal,
        List<String> employeeCodes,
        String resultStatus
    );
}
