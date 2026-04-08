package com.livingagent.core.sandbox.impl;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import com.livingagent.core.sandbox.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class SandboxSessionImpl implements SandboxSession {

    private static final Logger log = LoggerFactory.getLogger(SandboxSessionImpl.class);
    private static final int DEFAULT_TIMEOUT_SECONDS = 300;

    private final String sessionId;
    private final String containerId;
    private final String workDir;
    private final SandboxService.SandboxConfig config;
    private final DockerSandboxService sandboxService;
    
    private volatile SessionState state;
    private final Instant createdAt;
    private volatile Instant lastActiveAt;

    public SandboxSessionImpl(
            String sessionId,
            String containerId,
            String workDir,
            SandboxService.SandboxConfig config,
            DockerSandboxService sandboxService) {
        this.sessionId = sessionId;
        this.containerId = containerId;
        this.workDir = workDir;
        this.config = config;
        this.sandboxService = sandboxService;
        this.state = SessionState.READY;
        this.createdAt = Instant.now();
        this.lastActiveAt = Instant.now();
    }

    @Override
    public String getSessionId() {
        return sessionId;
    }

    @Override
    public String getContainerId() {
        return containerId;
    }

    @Override
    public String getWorkDir() {
        return workDir;
    }

    @Override
    public SandboxService.SandboxConfig getConfig() {
        return config;
    }

    @Override
    public SessionState getState() {
        return state;
    }

    @Override
    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public Instant getLastActiveAt() {
        return lastActiveAt;
    }

    @Override
    public CompletableFuture<ExecutionResult> executeCode(String code, String language) {
        return CompletableFuture.supplyAsync(() -> {
            state = SessionState.BUSY;
            lastActiveAt = Instant.now();
            
            try {
                String command = buildCodeExecutionCommand(code, language);
                return executeCommandInternal(command, DEFAULT_TIMEOUT_SECONDS);
            } finally {
                state = SessionState.READY;
                lastActiveAt = Instant.now();
            }
        }, sandboxService.getExecutorService());
    }

    @Override
    public CompletableFuture<ExecutionResult> executeCommand(String command, List<String> args) {
        return CompletableFuture.supplyAsync(() -> {
            state = SessionState.BUSY;
            lastActiveAt = Instant.now();
            
            try {
                String fullCommand = buildFullCommand(command, args);
                return executeCommandInternal(fullCommand, DEFAULT_TIMEOUT_SECONDS);
            } finally {
                state = SessionState.READY;
                lastActiveAt = Instant.now();
            }
        }, sandboxService.getExecutorService());
    }

    @Override
    public CompletableFuture<ExecutionResult> executeTraeCommand(String action, Map<String, Object> params) {
        List<String> args = new ArrayList<>();
        args.add(action);
        
        if (params != null) {
            params.forEach((key, value) -> {
                args.add("--" + key);
                args.add(String.valueOf(value));
            });
        }
        
        return executeCommand("trae", args);
    }

    @Override
    public CompletableFuture<Void> writeFile(String path, String content) {
        return CompletableFuture.runAsync(() -> {
            state = SessionState.BUSY;
            try {
                String command = String.format("mkdir -p '%s' && cat > '%s' << 'EOFMARKER'\n%s\nEOFMARKER",
                    path.substring(0, path.lastIndexOf('/')),
                    path,
                    content.replace("'", "'\\''"));
                
                executeCommandInternal(command, 60);
            } finally {
                state = SessionState.READY;
                lastActiveAt = Instant.now();
            }
        }, sandboxService.getExecutorService());
    }

    @Override
    public CompletableFuture<Optional<String>> readFile(String path) {
        return CompletableFuture.supplyAsync(() -> {
            state = SessionState.BUSY;
            try {
                ExecutionResult result = executeCommandInternal("cat '" + path + "'", 60);
                if (result.success()) {
                    return Optional.of(result.stdout());
                }
                return Optional.empty();
            } finally {
                state = SessionState.READY;
                lastActiveAt = Instant.now();
            }
        }, sandboxService.getExecutorService());
    }

    @Override
    public CompletableFuture<List<String>> listFiles(String path) {
        return CompletableFuture.supplyAsync(() -> {
            state = SessionState.BUSY;
            try {
                ExecutionResult result = executeCommandInternal("ls -1 '" + path + "'", 60);
                if (result.success() && !result.stdout().isEmpty()) {
                    return Arrays.asList(result.stdout().split("\n"));
                }
                return Collections.emptyList();
            } finally {
                state = SessionState.READY;
                lastActiveAt = Instant.now();
            }
        }, sandboxService.getExecutorService());
    }

    @Override
    public CompletableFuture<Void> deleteFile(String path) {
        return CompletableFuture.runAsync(() -> {
            state = SessionState.BUSY;
            try {
                executeCommandInternal("rm -rf '" + path + "'", 60);
            } finally {
                state = SessionState.READY;
                lastActiveAt = Instant.now();
            }
        }, sandboxService.getExecutorService());
    }

    @Override
    public void close() {
        state = SessionState.CLOSED;
        sandboxService.destroySession(sessionId);
    }
    
    private ExecutionResult executeCommandInternal(String command, int timeoutSeconds) {
        String executionId = UUID.randomUUID().toString().substring(0, 8);
        long startTime = System.currentTimeMillis();
        
        try {
            DockerClient dockerClient = sandboxService.getDockerClient();
            
            ExecCreateCmdResponse execCreate = dockerClient.execCreateCmd(containerId)
                .withCmd("sh", "-c", command)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withWorkingDir(workDir)
                .exec();
            
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            
            dockerClient.execStartCmd(execCreate.getId())
                .exec(new com.github.dockerjava.api.async.ResultCallback.Adapter<Frame>() {
                    @Override
                    public void onNext(Frame frame) {
                        if (frame.getStreamType() == StreamType.STDOUT) {
                            stdout.writeBytes(frame.getPayload());
                        } else if (frame.getStreamType() == StreamType.STDERR) {
                            stderr.writeBytes(frame.getPayload());
                        }
                    }
                })
                .awaitCompletion(timeoutSeconds, TimeUnit.SECONDS);
            
            Long exitCode = dockerClient.inspectExecCmd(execCreate.getId())
                .exec()
                .getExitCodeLong();
            
            long duration = System.currentTimeMillis() - startTime;
            
            if (exitCode == null || exitCode == 0) {
                return new ExecutionResult(
                    executionId,
                    true,
                    0,
                    stdout.toString(StandardCharsets.UTF_8),
                    stderr.toString(StandardCharsets.UTF_8),
                    duration,
                    Map.of(),
                    Instant.now()
                );
            } else {
                return ExecutionResult.failure(
                    executionId,
                    exitCode.intValue(),
                    stderr.toString(StandardCharsets.UTF_8),
                    duration
                );
            }
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Failed to execute command in sandbox: {}", e.getMessage());
            return ExecutionResult.error(executionId, e.getMessage());
        }
    }
    
    private String buildCodeExecutionCommand(String code, String language) {
        return switch (language.toLowerCase()) {
            case "python", "python3" -> "python3 -c \"" + escapeForShell(code) + "\"";
            case "javascript", "js", "node" -> "node -e \"" + escapeForShell(code) + "\"";
            case "bash", "shell", "sh" -> code;
            case "java" -> "echo '" + escapeForShell(code) + "' > /tmp/Main.java && javac /tmp/Main.java && java -cp /tmp Main";
            case "rust" -> "echo '" + escapeForShell(code) + "' > /tmp/main.rs && rustc /tmp/main.rs -o /tmp/main && /tmp/main";
            default -> throw new IllegalArgumentException("Unsupported language: " + language);
        };
    }
    
    private String buildFullCommand(String command, List<String> args) {
        StringBuilder sb = new StringBuilder(command);
        if (args != null) {
            for (String arg : args) {
                sb.append(" ").append(escapeForShell(arg));
            }
        }
        return sb.toString();
    }
    
    private String escapeForShell(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("$", "\\$")
                .replace("`", "\\`");
    }
}
