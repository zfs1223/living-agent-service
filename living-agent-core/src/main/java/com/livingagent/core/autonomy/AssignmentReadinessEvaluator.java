package com.livingagent.core.autonomy;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface AssignmentReadinessEvaluator {

    ReadinessEvaluation evaluate(
        MainBrainTaskPlan mainBrainTaskPlan,
        DepartmentTaskPlan departmentTaskPlan,
        List<EmployeeWorkAssignment> assignments
    );

    enum ReadinessStatus {
        READY,
        BLOCKED,
        NEEDS_CLARIFICATION,
        PARTIALLY_READY
    }

    record ReadinessEvaluation(
        ReadinessStatus status,
        double readinessScore,
        List<String> blockingIssues,
        List<String> clarificationQuestions,
        Map<String, Object> details
    ) {
        public boolean isReady() {
            return status == ReadinessStatus.READY;
        }
    }
}
