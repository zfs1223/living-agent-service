package com.livingagent.gateway.service;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.livingagent.core.model.ModelManager;
import com.livingagent.core.model.ModelStatus;
import com.livingagent.core.neuron.NeuronRegistry;
import com.livingagent.core.channel.ChannelManager;

@Service
public class AgentService {
    
    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    
    private final ModelManager modelManager;
    private final NeuronRegistry neuronRegistry;
    private final ChannelManager channelManager;
    private final ConcurrentHashMap<String, SessionContext> activeSessions;
    
    public AgentService(ModelManager modelManager, NeuronRegistry neuronRegistry, 
                        ChannelManager channelManager) {
        this.modelManager = modelManager;
        this.neuronRegistry = neuronRegistry;
        this.channelManager = channelManager;
        this.activeSessions = new ConcurrentHashMap<>();
    }
    
    public void startSession(String sessionId) {
        SessionContext context = new SessionContext(sessionId);
        activeSessions.put(sessionId, context);
        
        modelManager.createSession(sessionId)
            .thenAccept(session -> {
                context.setModelSession(session);
                log.info("Session started: {}", sessionId);
            })
            .exceptionally(e -> {
                log.error("Failed to start session: {}", sessionId, e);
                return null;
            });
    }
    
    public void endSession(String sessionId) {
        SessionContext context = activeSessions.remove(sessionId);
        if (context != null) {
            modelManager.destroySession(sessionId);
            log.info("Session ended: {}", sessionId);
        }
    }
    
    public CompletableFuture<Map<String, Object>> processTextAsync(String sessionId, String text, String channel) {
        SessionContext context = activeSessions.get(sessionId);
        if (context == null) {
            return CompletableFuture.completedFuture(Map.of(
                "type", "error",
                "message", "Session not found"
            ));
        }
        
        context.incrementMessageCount();
        
        return modelManager.generateText(sessionId, text, null)
            .thenApply(response -> {
                Map<String, Object> result = new HashMap<>();
                result.put("type", "response");
                result.put("sessionId", sessionId);
                result.put("channel", channel);
                
                if (response.isSuccess()) {
                    result.put("text", response.getText());
                    result.put("model", response.getModel());
                } else {
                    result.put("error", response.getError());
                }
                
                return result;
            });
    }
    
    public CompletableFuture<Map<String, Object>> processAudioAsync(String sessionId, String audioData, String format) {
        SessionContext context = activeSessions.get(sessionId);
        if (context == null) {
            return CompletableFuture.completedFuture(Map.of(
                "type", "error",
                "message", "Session not found"
            ));
        }
        
        context.incrementMessageCount();
        
        return modelManager.recognizeSpeech(sessionId, audioData, "sherpa")
            .thenApply(response -> {
                Map<String, Object> result = new HashMap<>();
                result.put("type", "transcription");
                result.put("sessionId", sessionId);
                
                if (response.isSuccess()) {
                    result.put("text", response.getText());
                    result.put("model", response.getModel());
                } else {
                    result.put("error", response.getError());
                }
                
                return result;
            });
    }
    
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        
        status.put("activeSessions", activeSessions.size());
        status.put("neurons", neuronRegistry.getAll().size());
        status.put("channels", channelManager.getAll().size());
        
        try {
            ModelStatus modelStatus = modelManager.getStatus().join();
            status.put("modelsLoaded", modelStatus.getLoadedCount());
            status.put("modelsTotal", modelStatus.getTotalModels());
            status.put("asrAvailable", modelStatus.isAsrAvailable());
            status.put("llmAvailable", modelStatus.isLlmAvailable());
            status.put("ttsAvailable", modelStatus.isTtsAvailable());
        } catch (Exception e) {
            status.put("modelStatusError", e.getMessage());
        }
        
        return status;
    }
    
    public boolean isSessionActive(String sessionId) {
        return activeSessions.containsKey(sessionId);
    }
    
    private static class SessionContext {
        private final String sessionId;
        private final long createdAt;
        private volatile Object modelSession;
        private volatile int messageCount;
        
        public SessionContext(String sessionId) {
            this.sessionId = sessionId;
            this.createdAt = System.currentTimeMillis();
            this.messageCount = 0;
        }
        
        public void setModelSession(Object modelSession) {
            this.modelSession = modelSession;
        }
        
        public Object getModelSession() {
            return modelSession;
        }
        
        public void incrementMessageCount() {
            this.messageCount++;
        }
        
        public int getMessageCount() {
            return messageCount;
        }
    }
}
