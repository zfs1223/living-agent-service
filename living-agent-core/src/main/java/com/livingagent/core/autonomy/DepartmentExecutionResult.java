package com.livingagent.core.autonomy;

import java.util.List;
import java.util.Map;

public record DepartmentExecutionResult(
    String executionId,
    String batchId,
    String department,
    String sessionId,
    String status,
    List<EmployeeExecutionDispatch> dispatchedAssignments,
    Map<String, Object> metadata
) {
    /**
     * 兼容旧调用方：从 metadata 或 sessionId 字段获取 sessionId。
     */
    public String resolveSessionId() {
        if (sessionId != null && !sessionId.isBlank()) {
            return sessionId;
        }
        if (metadata != null) {
            Object fromMeta = metadata.get("sessionId");
            if (fromMeta != null) {
                return String.valueOf(fromMeta);
            }
        }
        return null;
    }
}
