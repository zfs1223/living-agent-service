package com.livingagent.core.autonomy;

import java.time.Instant;
import java.util.Map;

public record EmployeeExecutionDispatch(
    String dispatchId,
    String assignmentId,
    String employeeCode,
    String employeeNeuronId,
    String targetChannel,
    String status,
    Instant dispatchedAt,
    Map<String, Object> metadata
) {
}
