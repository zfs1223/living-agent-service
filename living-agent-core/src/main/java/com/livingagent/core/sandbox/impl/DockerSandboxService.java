package com.livingagent.core.sandbox.impl;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.model.*;
import com.livingagent.core.sandbox.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

public class DockerSandboxService implements SandboxService, Closeable {

    private static final Logger log = LoggerFactory.getLogger(DockerSandboxService.class);
    private static final String DEFAULT_IMAGE = "livingagent/trae-sandbox:latest";
    private static final String NETWORK_NAME = "livingagent-internal";
    
    private final DockerClient dockerClient;
    private final ExecutorService executorService;
    private final ConcurrentMap<String, SandboxSessionImpl> sessions;
    private volatile boolean available;

    public DockerSandboxService(DockerClient dockerClient) {
        this.dockerClient = dockerClient;
        this.executorService = Executors.newCachedThreadPool();
        this.sessions = new ConcurrentHashMap<>();
        this.available = checkDockerAvailable();
        
        if (available) {
            ensureNetworkExists();
            log.info("DockerSandboxService initialized successfully");
        } else {
            log.warn("DockerSandboxService initialized but Docker is not available");
        }
    }
    
    private boolean checkDockerAvailable() {
        try {
            dockerClient.pingCmd().exec();
            return true;
        } catch (Exception e) {
            log.error("Docker is not available: {}", e.getMessage());
            return false;
        }
    }
    
    private void ensureNetworkExists() {
        try {
            boolean networkExists = dockerClient.listNetworksCmd()
                .withNameFilter(NETWORK_NAME)
                .exec()
                .stream()
                .anyMatch(n -> NETWORK_NAME.equals(n.getName()));
            
            if (!networkExists) {
                dockerClient.createNetworkCmd()
                    .withName(NETWORK_NAME)
                    .withDriver("bridge")
                    .withInternal(true)
                    .exec();
                log.info("Created internal network: {}", NETWORK_NAME);
            }
        } catch (Exception e) {
            log.warn("Failed to ensure network exists: {}", e.getMessage());
        }
    }

    @Override
    public CompletableFuture<ExecutionResult> executeCode(String code, String language, ExecutionOptions options) {
        if (!available) {
            return CompletableFuture.completedFuture(
                ExecutionResult.error("docker-unavailable", "Docker service is not available")
            );
        }
        
        return CompletableFuture.supplyAsync(() -> {
            String executionId = UUID.randomUUID().toString().substring(0, 8);
            long startTime = System.currentTimeMillis();
            
            try {
                String command = buildCodeExecutionCommand(code, language);
                return executeInContainer(executionId, command, options, startTime);
            } catch (Exception e) {
                log.error("Failed to execute code: {}", e.getMessage());
                return ExecutionResult.error(executionId, e.getMessage());
            }
        }, executorService);
    }

    @Override
    public CompletableFuture<ExecutionResult> executeCommand(String command, List<String> args, ExecutionOptions options) {
        if (!available) {
            return CompletableFuture.completedFuture(
                ExecutionResult.error("docker-unavailable", "Docker service is not available")
            );
        }
        
        return CompletableFuture.supplyAsync(() -> {
            String executionId = UUID.randomUUID().toString().substring(0, 8);
            long startTime = System.currentTimeMillis();
            
            try {
                String fullCommand = buildFullCommand(command, args);
                return executeInContainer(executionId, fullCommand, options, startTime);
            } catch (Exception e) {
                log.error("Failed to execute command: {}", e.getMessage());
                return ExecutionResult.error(executionId, e.getMessage());
            }
        }, executorService);
    }

    @Override
    public CompletableFuture<ExecutionResult> executeTraeCommand(String action, Map<String, Object> params, String workDir) {
        List<String> args = buildTraeArgs(action, params);
        return executeCommand("trae", args, ExecutionOptions.DEVELOPMENT);
    }

    @Override
    public Optional<SandboxSession> createSession(String sessionId, SandboxConfig config) {
        if (!available) {
            log.warn("Cannot create session: Docker not available");
            return Optional.empty();
        }
        
        try {
            String containerId = createContainer(sessionId, config);
            SandboxSessionImpl session = new SandboxSessionImpl(
                sessionId,
                containerId,
                config.workDir(),
                config,
                this
            );
            sessions.put(sessionId, session);
            log.info("Created sandbox session: {} with container: {}", sessionId, containerId);
            return Optional.of(session);
        } catch (Exception e) {
            log.error("Failed to create sandbox session: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void destroySession(String sessionId) {
        SandboxSessionImpl session = sessions.remove(sessionId);
        if (session != null) {
            try {
                dockerClient.removeContainerCmd(session.getContainerId())
                    .withForce(true)
                    .withRemoveVolumes(true)
                    .exec();
                log.info("Destroyed sandbox session: {}", sessionId);
            } catch (Exception e) {
                log.error("Failed to destroy sandbox session: {}", e.getMessage());
            }
        }
    }

    @Override
    public Optional<SandboxSession> getSession(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public String getBackendType() {
        return "docker";
    }
    
    private String createContainer(String sessionId, SandboxConfig config) {
        Map<String, String> labels = new HashMap<>();
        labels.put("livingagent.session", sessionId);
        labels.put("livingagent.type", "sandbox");
        
        HostConfig hostConfig = HostConfig.newHostConfig()
            .withMemory(config != null ? 4L * 1024 * 1024 * 1024 : 4L * 1024 * 1024 * 1024)
            .withCpuCount(2L)
            .withNetworkMode(NETWORK_NAME)
            .withSecurityOpts(Arrays.asList("no-new-privileges"))
            .withReadonlyRootfs(false);
        
        CreateContainerResponse response = dockerClient.createContainerCmd(
            config != null ? config.image() : DEFAULT_IMAGE
        )
            .withName("sandbox-" + sessionId)
            .withHostConfig(hostConfig)
            .withLabels(labels)
            .withTty(true)
            .withStdinOpen(true)
            .withWorkingDir(config != null ? config.workDir() : "/workspace")
            .exec();
        
        dockerClient.startContainerCmd(response.getId()).exec();
        
        return response.getId();
    }
    
    private ExecutionResult executeInContainer(String executionId, String command, ExecutionOptions options, long startTime) {
        try {
            String containerId = createContainer(executionId, new SandboxConfig(
                DEFAULT_IMAGE, "/workspace", Map.of(), List.of()
            ));
            
            try {
                ExecCreateCmdResponse execCreate = dockerClient.execCreateCmd(containerId)
                    .withCmd("sh", "-c", command)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
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
                    .awaitCompletion(options.timeoutSeconds(), TimeUnit.SECONDS);
                
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
            } finally {
                dockerClient.removeContainerCmd(containerId)
                    .withForce(true)
                    .withRemoveVolumes(true)
                    .exec();
            }
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            if (e instanceof TimeoutException || e.getCause() instanceof TimeoutException) {
                return ExecutionResult.timeout(executionId, e.getMessage(), duration);
            }
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
        for (String arg : args) {
            sb.append(" ").append(escapeForShell(arg));
        }
        return sb.toString();
    }
    
    private List<String> buildTraeArgs(String action, Map<String, Object> params) {
        List<String> args = new ArrayList<>();
        args.add(action);
        
        if (params != null) {
            params.forEach((key, value) -> {
                args.add("--" + key);
                args.add(String.valueOf(value));
            });
        }
        
        return args;
    }
    
    private String escapeForShell(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("$", "\\$")
                .replace("`", "\\`");
    }
    
    DockerClient getDockerClient() {
        return dockerClient;
    }
    
    ExecutorService getExecutorService() {
        return executorService;
    }

    @Override
    public void close() {
        sessions.keySet().forEach(this::destroySession);
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("DockerSandboxService closed");
    }
}
