package com.livingagent.core.sandbox.impl;

import com.livingagent.core.sandbox.ExecutionResult;
import com.livingagent.core.sandbox.SandboxService;
import com.livingagent.core.sandbox.SandboxSession;
import com.livingagent.core.security.SandboxExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 统一沙箱入口（双后端协同）：
 * - 本地安全沙箱（SandboxExecutor）用于低风险、短时执行
 * - DockerSandboxService 用于重型/开发型任务
 */
public class HybridSandboxService implements SandboxService {

    private static final Logger log = LoggerFactory.getLogger(HybridSandboxService.class);

    private final SandboxExecutor sandboxExecutor;
    private final DockerSandboxService dockerSandboxService;

    public HybridSandboxService(SandboxExecutor sandboxExecutor, DockerSandboxService dockerSandboxService) {
        this.sandboxExecutor = sandboxExecutor;
        this.dockerSandboxService = dockerSandboxService;
    }

    @Override
    public CompletableFuture<ExecutionResult> executeCode(String code, String language, ExecutionOptions options) {
        if (shouldUseDocker(options) && dockerAvailable()) {
            return dockerSandboxService.executeCode(code, language, options);
        }

        return CompletableFuture.supplyAsync(() -> {
            String executionId = shortId();
            SandboxExecutor.SandboxConfig cfg = toExecutorConfig(options, false);
            SandboxExecutor.ExecutionResult<?> result = sandboxExecutor.executeScript(cfg, code, language);
            return toExecutionResult(executionId, result);
        });
    }

    @Override
    public CompletableFuture<ExecutionResult> executeCommand(String command, List<String> args, ExecutionOptions options) {
        if (shouldUseDocker(options) && dockerAvailable()) {
            return dockerSandboxService.executeCommand(command, args, options);
        }

        return CompletableFuture.supplyAsync(() -> {
            String executionId = shortId();
            SandboxExecutor.SandboxConfig cfg = toExecutorConfig(options, true);
            String[] argArray = args == null ? new String[0] : args.toArray(new String[0]);
            SandboxExecutor.ExecutionResult<?> result = sandboxExecutor.executeCommand(cfg, command, argArray);
            return toExecutionResult(executionId, result);
        });
    }

    @Override
    public CompletableFuture<ExecutionResult> executeTraeCommand(String action, Map<String, Object> params, String workDir) {
        if (!dockerAvailable()) {
            return CompletableFuture.completedFuture(
                ExecutionResult.error(shortId(), "Trae command requires docker sandbox backend")
            );
        }
        return dockerSandboxService.executeTraeCommand(action, params, workDir);
    }

    @Override
    public Optional<SandboxSession> createSession(String sessionId, SandboxConfig config) {
        if (!dockerAvailable()) {
            return Optional.empty();
        }
        return dockerSandboxService.createSession(sessionId, config);
    }

    @Override
    public void destroySession(String sessionId) {
        if (dockerAvailable()) {
            dockerSandboxService.destroySession(sessionId);
        }
    }

    @Override
    public Optional<SandboxSession> getSession(String sessionId) {
        if (!dockerAvailable()) {
            return Optional.empty();
        }
        return dockerSandboxService.getSession(sessionId);
    }

    @Override
    public boolean isAvailable() {
        return (sandboxExecutor != null && sandboxExecutor.isAvailable()) || dockerAvailable();
    }

    @Override
    public String getBackendType() {
        if (dockerAvailable() && sandboxExecutor != null && sandboxExecutor.isAvailable()) {
            return "hybrid(local+docker)";
        }
        if (dockerAvailable()) {
            return "docker";
        }
        return "local";
    }

    private boolean dockerAvailable() {
        return dockerSandboxService != null && dockerSandboxService.isAvailable();
    }

    private boolean shouldUseDocker(ExecutionOptions options) {
        if (options == null) {
            return false;
        }
        return options.maxCpuCores() > 2
            || options.maxMemoryMB() > 4096
            || options.timeoutSeconds() > 300
            || (options.allowedNetworks() != null && !options.allowedNetworks().isEmpty());
    }

    private SandboxExecutor.SandboxConfig toExecutorConfig(ExecutionOptions options, boolean allowProcessExec) {
        ExecutionOptions effective = options == null ? ExecutionOptions.DEFAULT : options;

        return new SandboxExecutor.SandboxConfig(
            effective.timeoutSeconds() * 1000L,
            effective.maxMemoryMB(),
            effective.allowedNetworks() != null && !effective.allowedNetworks().isEmpty(),
            List.of(),
            List.of("/etc/passwd", "/etc/shadow", "~/.ssh", "~/.gnupg"),
            effective.env() == null ? Map.of() : effective.env(),
            true,
            true,
            allowProcessExec,
            System.getProperty("java.io.tmpdir")
        );
    }

    private ExecutionResult toExecutionResult(String executionId, SandboxExecutor.ExecutionResult<?> result) {
        if (result == null) {
            return ExecutionResult.error(executionId, "Sandbox returned null result");
        }

        long durationMs = result.executionTimeMs();
        if (result.success()) {
            return new ExecutionResult(
                executionId,
                true,
                0,
                result.result() == null ? "" : String.valueOf(result.result()),
                "",
                durationMs,
                result.metadata() == null ? Map.of() : result.metadata(),
                Instant.now()
            );
        }

        if (result.timedOut()) {
            return ExecutionResult.timeout(executionId, result.error(), durationMs);
        }

        return ExecutionResult.failure(executionId, -1, result.error(), durationMs);
    }

    private String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
