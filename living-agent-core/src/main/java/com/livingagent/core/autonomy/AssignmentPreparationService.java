package com.livingagent.core.autonomy;

public interface AssignmentPreparationService {

    PreparedAssignmentBatch prepare(
        String requestId,
        String sessionId,
        String department,
        MainBrainTaskPlan mainBrainTaskPlan,
        DepartmentTaskPlan departmentTaskPlan,
        java.util.List<EmployeeWorkAssignment> assignments
    );
}
