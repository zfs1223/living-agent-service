package com.livingagent.core.model.impl;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.model.*;

public class ModelManagerImpl implements ModelManager {
    
    private static final Logger log = LoggerFactory.getLogger(ModelManagerImpl.class);
    
    private final ModelClient client;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, ModelSession> sessions;
    private final ScheduledExecutorService scheduler;
    private final int sessionTimeoutMinutes;
    
    public ModelManagerImpl() {
        this(new NamedPipeModelClient(), 30);
    }
    
    public ModelManagerImpl(ModelClient client, int sessionTimeoutMinutes) {
        this.client = client;
        this.objectMapper = new ObjectMapper();
        this.sessions = new ConcurrentHashMap<>();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.sessionTimeoutMinutes = sessionTimeoutMinutes;
        
        startSessionCleanup();
    }
    
    private void startSessionCleanup() {
        scheduler.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            long timeoutMs = TimeUnit.MINUTES.toMillis(sessionTimeoutMinutes);
            
            sessions.forEach((sessionId, session) -> {
                long lastAccess = session.getLastAccessedAt().toEpochMilli();
                if (now - lastAccess > timeoutMs) {
                    log.info("Session {} timed out, destroying", sessionId);
                    destroySession(sessionId);
                }
            });
        }, 5, 5, TimeUnit.MINUTES);
    }
    
    @Override
    public CompletableFuture<ModelResponse> recognizeSpeech(String sessionId, String audioPath, String provider) {
        ModelRequest request = ModelRequest.builder()
            .service("asr")
            .param("audio_path", audioPath)
            .param("provider", provider != null ? provider : "sherpa")
            .param("streaming", false)
            .build();
        
        return executeWithSession(sessionId, request);
    }
    
    @Override
    public CompletableFuture<ModelResponse> recognizeSpeechStreaming(String sessionId, String audioPath, String provider) {
        ModelRequest request = ModelRequest.builder()
            .service("asr")
            .param("audio_path", audioPath)
            .param("provider", provider != null ? provider : "sherpa")
            .param("streaming", true)
            .build();
        
        return executeWithSession(sessionId, request);
    }
    
    @Override
    public CompletableFuture<ModelResponse> generateText(String sessionId, String prompt, List<Map<String, String>> history) {
        ModelRequest request = ModelRequest.builder()
            .service("llm")
            .param("prompt", prompt)
            .param("history", history)
            .build();
        
        return executeWithSession(sessionId, request);
    }
    
    @Override
    public CompletableFuture<ModelResponse> generateTextBitNet(String sessionId, String prompt, int maxTokens, double temperature) {
        ModelRequest request = ModelRequest.builder()
            .service("bitnet")
            .param("prompt", prompt)
            .param("max_tokens", maxTokens > 0 ? maxTokens : 1000)
            .param("temperature", temperature > 0 ? temperature : 0.7)
            .build();
        
        return executeWithSession(sessionId, request);
    }
    
    @Override
    public CompletableFuture<ModelResponse> synthesizeSpeech(String sessionId, String text, String voice, 
            double speed, String outputPath, boolean useMeloTTS) {
        ModelRequest request = ModelRequest.builder()
            .service("tts")
            .param("text", text)
            .param("voice", voice != null ? voice : "af_bella")
            .param("speed", speed > 0 ? speed : 1.0)
            .param("output_path", outputPath)
            .param("use_melotts", useMeloTTS)
            .param("use_supertonic", !useMeloTTS)
            .build();
        
        return executeWithSession(sessionId, request);
    }
    
    @Override
    public CompletableFuture<ModelResponse> processToolIntent(String sessionId, String userInput, List<String> availableTools) {
        ModelRequest request = ModelRequest.builder()
            .service("tool_intent")
            .param("user_input", userInput)
            .param("available_tools", availableTools)
            .build();
        
        return executeWithSession(sessionId, request);
    }
    
    @Override
    public CompletableFuture<ModelResponse> executeToolCall(String sessionId, String toolName, Map<String, Object> parameters) {
        ModelRequest request = ModelRequest.builder()
            .service("tool")
            .param("tool", toolName)
            .param("parameters", parameters)
            .build();
        
        return executeWithSession(sessionId, request);
    }
    
    private CompletableFuture<ModelResponse> executeWithSession(String sessionId, ModelRequest request) {
        ModelSession session = sessions.get(sessionId);
        if (session == null) {
            return CompletableFuture.completedFuture(
                ModelResponse.failure("Session not found: " + sessionId)
            );
        }
        
        session.touch();
        return client.sendRequest(sessionId, request);
    }
    
    @Override
    public CompletableFuture<ModelSession> createSession(String sessionId) {
        return client.createSession(sessionId).thenApply(session -> {
            sessions.put(sessionId, session);
            return session;
        });
    }
    
    @Override
    public CompletableFuture<Boolean> destroySession(String sessionId) {
        sessions.remove(sessionId);
        return client.destroySession(sessionId);
    }
    
    @Override
    public CompletableFuture<ModelStatus> getStatus() {
        return client.getStatus();
    }
    
    @Override
    public boolean isAsrAvailable() {
        try {
            ModelStatus status = getStatus().get(5, TimeUnit.SECONDS);
            return status.isAsrAvailable();
        } catch (Exception e) {
            log.warn("Failed to check ASR availability", e);
            return false;
        }
    }
    
    @Override
    public boolean isLlmAvailable() {
        try {
            ModelStatus status = getStatus().get(5, TimeUnit.SECONDS);
            return status.isLlmAvailable();
        } catch (Exception e) {
            log.warn("Failed to check LLM availability", e);
            return false;
        }
    }
    
    @Override
    public boolean isTtsAvailable() {
        try {
            ModelStatus status = getStatus().get(5, TimeUnit.SECONDS);
            return status.isTtsAvailable();
        } catch (Exception e) {
            log.warn("Failed to check TTS availability", e);
            return false;
        }
    }
    
    @Override
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        for (String sessionId : sessions.keySet()) {
            try {
                destroySession(sessionId).get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("Error destroying session {} during shutdown", sessionId, e);
            }
        }
        
        client.close();
        log.info("ModelManager shutdown complete");
    }

    @Override
    public String chat(String modelId, String prompt) {
        try {
            return chatAsync(modelId, prompt).get(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("chat failed for model {}: {}", modelId, e.getMessage());
            return null;
        }
    }

    @Override
    public String chatWithHistory(String modelId, String prompt, List<Map<String, String>> history) {
        try {
            ModelRequest request = ModelRequest.builder()
                .service("llm_chat")
                .param("model_id", modelId)
                .param("prompt", prompt)
                .param("history", history)
                .build();
            ModelResponse response = client.sendControlRequest(request).get(60, TimeUnit.SECONDS);
            if (response.isSuccess()) {
                Object text = response.getData().getOrDefault("response", "");
                return text != null ? text.toString() : null;
            }
            log.warn("chatWithHistory failed: {}", response.getError());
            return null;
        } catch (Exception e) {
            log.error("chatWithHistory failed for model {}: {}", modelId, e.getMessage());
            return null;
        }
    }

    @Override
    public String chatWithImage(String modelId, String prompt, String imageUrl) {
        try {
            return chatWithImageAsync(modelId, prompt, imageUrl).get(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("chatWithImage failed for model {}: {}", modelId, e.getMessage());
            return null;
        }
    }

    @Override
    public String chatWithImages(String modelId, String prompt, List<String> imageUrls) {
        try {
            ModelRequest request = ModelRequest.builder()
                .service("vllm_chat")
                .param("model_id", modelId)
                .param("prompt", prompt)
                .param("image_urls", imageUrls)
                .build();
            ModelResponse response = client.sendControlRequest(request).get(120, TimeUnit.SECONDS);
            if (response.isSuccess()) {
                Object text = response.getData().getOrDefault("response", "");
                return text != null ? text.toString() : null;
            }
            log.warn("chatWithImages failed: {}", response.getError());
            return null;
        } catch (Exception e) {
            log.error("chatWithImages failed for model {}: {}", modelId, e.getMessage());
            return null;
        }
    }

    @Override
    public CompletableFuture<String> chatAsync(String modelId, String prompt) {
        ModelRequest request = ModelRequest.builder()
            .service("llm_chat")
            .param("model_id", modelId)
            .param("prompt", prompt)
            .build();
        return client.sendControlRequest(request)
            .thenApply(response -> {
                if (response.isSuccess()) {
                    Object text = response.getData().getOrDefault("response", "");
                    return text != null ? text.toString() : null;
                }
                log.warn("chatAsync failed: {}", response.getError());
                return null;
            });
    }

    @Override
    public CompletableFuture<String> chatWithImageAsync(String modelId, String prompt, String imageUrl) {
        ModelRequest request = ModelRequest.builder()
            .service("vllm_chat")
            .param("model_id", modelId)
            .param("prompt", prompt)
            .param("image_url", imageUrl)
            .build();
        return client.sendControlRequest(request)
            .thenApply(response -> {
                if (response.isSuccess()) {
                    Object text = response.getData().getOrDefault("response", "");
                    return text != null ? text.toString() : null;
                }
                log.warn("chatWithImageAsync failed: {}", response.getError());
                return null;
            });
    }
}
