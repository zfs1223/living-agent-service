package com.livingagent.core.sandbox;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface SandboxSession {
    
    String getSessionId();
    
    String getContainerId();
    
    String getWorkDir();
    
    SandboxService.SandboxConfig getConfig();
    
    SessionState getState();
    
    Instant getCreatedAt();
    
    Instant getLastActiveAt();
    
    CompletableFuture<ExecutionResult> executeCode(String code, String language);
    
    CompletableFuture<ExecutionResult> executeCommand(String command, List<String> args);
    
    CompletableFuture<ExecutionResult> executeTraeCommand(String action, Map<String, Object> params);
    
    CompletableFuture<Void> writeFile(String path, String content);
    
    CompletableFuture<Optional<String>> readFile(String path);
    
    CompletableFuture<List<String>> listFiles(String path);
    
    CompletableFuture<Void> deleteFile(String path);
    
    void close();
    
    enum SessionState {
        CREATING,
        READY,
        BUSY,
        ERROR,
        CLOSED
    }
}
