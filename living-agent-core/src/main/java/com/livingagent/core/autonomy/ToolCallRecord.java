package com.livingagent.core.autonomy;

import java.time.Instant;
import java.util.Map;

public record ToolCallRecord(
    String callId,
    String toolName,
    Map<String, Object> parameters,
    Object result,
    boolean success,
    String error,
    long durationMs,
    Instant calledAt
) {
    public static ToolCallRecord success(
            String toolName, Map<String, Object> parameters, Object result, long durationMs) {
        return new ToolCallRecord(
            java.util.UUID.randomUUID().toString(),
            toolName, parameters, result, true, null, durationMs, Instant.now()
        );
    }

    public static ToolCallRecord failure(
            String toolName, Map<String, Object> parameters, String error, long durationMs) {
        return new ToolCallRecord(
            java.util.UUID.randomUUID().toString(),
            toolName, parameters, null, false, error, durationMs, Instant.now()
        );
    }
}
