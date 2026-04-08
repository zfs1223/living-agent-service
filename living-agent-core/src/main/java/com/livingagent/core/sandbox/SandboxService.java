package com.livingagent.core.sandbox;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface SandboxService {
    
    CompletableFuture<ExecutionResult> executeCode(
        String code,
        String language,
        ExecutionOptions options
    );
    
    CompletableFuture<ExecutionResult> executeCommand(
        String command,
        List<String> args,
        ExecutionOptions options
    );
    
    CompletableFuture<ExecutionResult> executeTraeCommand(
        String action,
        Map<String, Object> params,
        String workDir
    );
    
    Optional<SandboxSession> createSession(String sessionId, SandboxConfig config);
    
    void destroySession(String sessionId);
    
    Optional<SandboxSession> getSession(String sessionId);
    
    boolean isAvailable();
    
    String getBackendType();
    
    record ExecutionOptions(
        int timeoutSeconds,
        int maxCpuCores,
        long maxMemoryMB,
        long maxDiskMB,
        Map<String, String> env,
        List<String> allowedNetworks
    ) {
        public static ExecutionOptions DEFAULT = new ExecutionOptions(
            300, 2, 4096, 10240, Map.of(), List.of("internal")
        );
        
        public static ExecutionOptions QUICK = new ExecutionOptions(
            60, 1, 2048, 5120, Map.of(), List.of()
        );
        
        public static ExecutionOptions DEVELOPMENT = new ExecutionOptions(
            1800, 4, 8192, 20480, Map.of(), List.of("internal", "github.com", "npmjs.org")
        );
    }
    
    record SandboxConfig(
        String image,
        String workDir,
        Map<String, String> volumeMounts,
        List<String> preInstalledTools
    ) {
        public static SandboxConfig TRAE_DEFAULT = new SandboxConfig(
            "livingagent/trae-sandbox:latest",
            "/workspace",
            Map.of(),
            List.of("trae", "node", "python3", "git", "docker")
        );
    }
}
