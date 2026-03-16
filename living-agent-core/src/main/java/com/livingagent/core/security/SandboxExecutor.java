package com.livingagent.core.security;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public interface SandboxExecutor {

    <T> ExecutionResult<T> execute(SandboxConfig config, CallableTask<T> task);

    ExecutionResult<?> executeScript(SandboxConfig config, String script, String language);

    ExecutionResult<?> executeCommand(SandboxConfig config, String command, String[] args);

    boolean isAvailable();

    SandboxStats getStats();

    void cleanup();

    interface CallableTask<T> {
        T call() throws Exception;
    }

    record SandboxConfig(
        long timeoutMs,
        long memoryLimitMB,
        boolean networkAllowed,
        List<String> allowedPaths,
        List<String> deniedPaths,
        Map<String, String> environment,
        boolean allowFileWrite,
        boolean allowFileRead,
        boolean allowProcessExecution,
        String workingDirectory
    ) {
        public static SandboxConfig defaults() {
            return new SandboxConfig(
                30_000,
                512,
                false,
                List.of(),
                List.of("/etc/passwd", "/etc/shadow", "~/.ssh", "~/.gnupg"),
                Map.of(),
                false,
                true,
                false,
                System.getProperty("java.io.tmpdir")
            );
        }

        public static SandboxConfig strict() {
            return new SandboxConfig(
                10_000,
                256,
                false,
                List.of(),
                List.of("/", "~"),
                Map.of(),
                false,
                false,
                false,
                System.getProperty("java.io.tmpdir")
            );
        }

        public static SandboxConfig forScript() {
            return new SandboxConfig(
                60_000,
                1024,
                true,
                List.of("/tmp", System.getProperty("java.io.tmpdir")),
                List.of("/etc/passwd", "/etc/shadow", "~/.ssh"),
                Map.of("SANDBOX", "true"),
                true,
                true,
                false,
                System.getProperty("java.io.tmpdir")
            );
        }
    }

    record ExecutionResult<T>(
        boolean success,
        T result,
        String error,
        long executionTimeMs,
        long memoryUsedMB,
        boolean timedOut,
        boolean memoryExceeded,
        List<String> warnings,
        Map<String, Object> metadata
    ) {
        public static <T> ExecutionResult<T> success(T result, long timeMs) {
            return new ExecutionResult<>(true, result, null, timeMs, 0, false, false, List.of(), Map.of());
        }

        public static <T> ExecutionResult<T> failure(String error) {
            return new ExecutionResult<>(false, null, error, 0, 0, false, false, List.of(), Map.of());
        }

        public static <T> ExecutionResult<T> timeout() {
            return new ExecutionResult<>(false, null, "Execution timed out", 0, 0, true, false, List.of(), Map.of());
        }

        public static <T> ExecutionResult<T> memoryLimitExceeded() {
            return new ExecutionResult<>(false, null, "Memory limit exceeded", 0, 0, false, true, List.of(), Map.of());
        }
    }

    record SandboxStats(
        long totalExecutions,
        long successfulExecutions,
        long failedExecutions,
        long timeoutCount,
        long memoryExceededCount,
        double averageExecutionTimeMs,
        long currentActiveExecutions
    ) {}
}
