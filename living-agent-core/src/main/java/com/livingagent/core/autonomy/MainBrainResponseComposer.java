package com.livingagent.core.autonomy;

import java.util.List;

public interface MainBrainResponseComposer {

    String composeUserResponse(
        String requestId,
        String department,
        MainBrainTaskPlan mainBrainTaskPlan,
        List<EmployeeWorkAssignment> employeeAssignments,
        String brainRawResponse,
        DepartmentExecutionResult executionResult
    );

    String composeProgressResponse(
        String requestId,
        String department,
        MainBrainTaskPlan mainBrainTaskPlan,
        List<EmployeeWorkAssignment> employeeAssignments,
        DepartmentExecutionResult executionResult
    );
}
