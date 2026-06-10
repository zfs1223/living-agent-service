package com.livingagent.core.autonomy;

import java.util.List;
import java.util.Map;

public record PreparedAssignmentBatch(
    String batchId,
    String requestId,
    String sessionId,
    String department,
    String taskType,
    String goal,
    List<EmployeeWorkAssignment> assignments,
    Map<String, Object> metadata
) {
}
