package com.livingagent.core.model;

import java.util.concurrent.CompletableFuture;

public interface ModelClient {
    
    boolean isConnected();
    
    CompletableFuture<ModelSession> createSession(String sessionId);
    
    CompletableFuture<Boolean> destroySession(String sessionId);
    
    CompletableFuture<ModelResponse> sendRequest(String sessionId, ModelRequest request);
    
    CompletableFuture<ModelResponse> sendControlRequest(ModelRequest request);
    
    CompletableFuture<ModelStatus> getStatus();
    
    void close();
    
    String getDaemonPath();
}
