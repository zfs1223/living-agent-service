package com.livingagent.core.autonomy;

public interface DepartmentExecutionCoordinator {

    DepartmentExecutionResult coordinate(
        PreparedAssignmentBatch preparedAssignmentBatch
    );
}
