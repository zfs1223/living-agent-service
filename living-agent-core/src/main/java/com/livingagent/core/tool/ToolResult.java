package com.livingagent.core.tool;

import java.time.Duration;
import java.time.Instant;

public record ToolResult(
    String callId,
    String toolName,
    boolean success,
    Object data,
    String error,
    Duration duration,
    Instant timestamp
) {
    public static ToolResult success(String callId, String toolName, Object data) {
        return new ToolResult(callId, toolName, true, data, null, Duration.ZERO, Instant.now());
    }

    public static ToolResult success(String callId, String toolName, Object data, Duration duration) {
        return new ToolResult(callId, toolName, true, data, null, duration, Instant.now());
    }

    public static ToolResult success(Object data) {
        return new ToolResult(null, null, true, data, null, Duration.ZERO, Instant.now());
    }

    public static ToolResult failure(String callId, String toolName, String error) {
        return new ToolResult(callId, toolName, false, null, error, Duration.ZERO, Instant.now());
    }

    public static ToolResult failure(String callId, String toolName, String error, Duration duration) {
        return new ToolResult(callId, toolName, false, null, error, duration, Instant.now());
    }

    public static ToolResult failure(String error) {
        return new ToolResult(null, null, false, null, error, Duration.ZERO, Instant.now());
    }

    @SuppressWarnings("unchecked")
    public <T> T getData() {
        return (T) data;
    }
}
