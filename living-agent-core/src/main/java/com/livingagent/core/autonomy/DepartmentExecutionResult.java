package com.livingagent.core.autonomy;

import java.util.List;
import java.util.Map;

public record DepartmentExecutionResult(
    String executionId,
    String batchId,
    String department,
    String status,
    List<EmployeeExecutionDispatch> dispatchedAssignments,
    Map<String, Object> metadata
) {
}
