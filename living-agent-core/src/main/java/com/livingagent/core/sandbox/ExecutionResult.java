package com.livingagent.core.sandbox;

import java.time.Instant;
import java.util.Map;

public record ExecutionResult(
    String executionId,
    boolean success,
    int exitCode,
    String stdout,
    String stderr,
    long durationMs,
    Map<String, Object> metrics,
    Instant executedAt
) {
    public static ExecutionResult success(String executionId, String stdout, long durationMs) {
        return new ExecutionResult(
            executionId,
            true,
            0,
            stdout,
            "",
            durationMs,
            Map.of(),
            Instant.now()
        );
    }
    
    public static ExecutionResult failure(String executionId, int exitCode, String stderr, long durationMs) {
        return new ExecutionResult(
            executionId,
            false,
            exitCode,
            "",
            stderr,
            durationMs,
            Map.of(),
            Instant.now()
        );
    }
    
    public static ExecutionResult timeout(String executionId, String message, long durationMs) {
        return new ExecutionResult(
            executionId,
            false,
            -1,
            "",
            "Execution timeout: " + message,
            durationMs,
            Map.of("timeout", true),
            Instant.now()
        );
    }
    
    public static ExecutionResult error(String executionId, String error) {
        return new ExecutionResult(
            executionId,
            false,
            -1,
            "",
            error,
            0,
            Map.of(),
            Instant.now()
        );
    }
    
    public String getOutput() {
        if (stdout != null && !stdout.isEmpty()) {
            return stdout;
        }
        return stderr;
    }
    
    public String getCombinedOutput() {
        StringBuilder sb = new StringBuilder();
        if (stdout != null && !stdout.isEmpty()) {
            sb.append(stdout);
        }
        if (stderr != null && !stderr.isEmpty()) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append("STDERR:\n").append(stderr);
        }
        return sb.toString();
    }
}
