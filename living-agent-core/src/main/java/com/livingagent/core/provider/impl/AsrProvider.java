package com.livingagent.core.provider.impl;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.livingagent.core.model.ModelManager;
import com.livingagent.core.model.ModelResponse;
import com.livingagent.core.provider.Provider;
import com.livingagent.core.tool.ToolSchema;

public class AsrProvider implements Provider {
    
    private static final Logger log = LoggerFactory.getLogger(AsrProvider.class);
    
    private final ModelManager modelManager;
    private final String sessionId;
    private final String defaultProvider;
    
    public AsrProvider(ModelManager modelManager) {
        this(modelManager, "sherpa");
    }
    
    public AsrProvider(ModelManager modelManager, String defaultProvider) {
        this.modelManager = modelManager;
        this.defaultProvider = defaultProvider;
        this.sessionId = "asr-" + UUID.randomUUID().toString().substring(0, 8);
        
        modelManager.createSession(this.sessionId)
            .thenAccept(session -> log.info("AsrProvider session created: {}", session.getSessionId()))
            .exceptionally(e -> {
                log.error("Failed to create session for AsrProvider", e);
                return null;
            });
    }
    
    @Override
    public String name() {
        return "asr";
    }
    
    @Override
    public ProviderCapabilities capabilities() {
        return ProviderCapabilities.basic();
    }
    
    @Override
    public ToolsPayload convertTools(List<ToolSchema> tools) {
        return new ToolsPayload("", ToolsPayload.ToolsPayloadType.PROMPT_GUIDED);
    }
    
    public CompletableFuture<AsrResult> recognize(String audioPath) {
        return recognize(audioPath, defaultProvider);
    }
    
    public CompletableFuture<AsrResult> recognize(String audioPath, String provider) {
        return modelManager.recognizeSpeech(sessionId, audioPath, provider)
            .thenApply(response -> {
                if (response.isSuccess()) {
                    String text = response.getText();
                    String model = response.getModel();
                    
                    List<AsrSegment> segments = new ArrayList<>();
                    List<Map<String, Object>> results = response.getResults();
                    if (results != null) {
                        for (Map<String, Object> r : results) {
                            segments.add(new AsrSegment(
                                (String) r.get("text"),
                                (Boolean) r.getOrDefault("is_final", true),
                                ((Number) r.getOrDefault("timestamp", 0)).doubleValue()
                            ));
                        }
                    }
                    
                    return new AsrResult(true, text, model, segments, null);
                } else {
                    return new AsrResult(false, null, null, null, response.getError());
                }
            });
    }
    
    public CompletableFuture<AsrResult> recognizeStreaming(String audioPath, String provider) {
        return modelManager.recognizeSpeechStreaming(sessionId, audioPath, provider)
            .thenApply(response -> {
                if (response.isSuccess()) {
                    List<AsrSegment> segments = new ArrayList<>();
                    List<Map<String, Object>> results = response.getResults();
                    if (results != null) {
                        for (Map<String, Object> r : results) {
                            segments.add(new AsrSegment(
                                (String) r.get("text"),
                                (Boolean) r.getOrDefault("is_final", false),
                                ((Number) r.getOrDefault("timestamp", 0)).doubleValue()
                            ));
                        }
                    }
                    
                    String finalText = segments.isEmpty() ? "" : 
                        segments.get(segments.size() - 1).text();
                    
                    return new AsrResult(true, finalText, response.getModel(), segments, null);
                } else {
                    return new AsrResult(false, null, null, null, response.getError());
                }
            });
    }
    
    @Override
    public CompletableFuture<String> simpleChat(String message, String model, double temperature) {
        return CompletableFuture.completedFuture("ASR provider does not support chat");
    }
    
    @Override
    public CompletableFuture<String> chatWithSystem(String systemPrompt, String message, String model, double temperature) {
        return CompletableFuture.completedFuture("ASR provider does not support chat");
    }
    
    @Override
    public CompletableFuture<ChatResponse> chat(ChatRequest request) {
        return CompletableFuture.completedFuture(
            new ChatResponse("ASR provider does not support chat", null, 0, 0, "error")
        );
    }
    
    @Override
    public boolean supportsNativeTools() {
        return false;
    }
    
    @Override
    public boolean supportsVision() {
        return false;
    }
    
    @Override
    public boolean supportsStreaming() {
        return true;
    }
    
    public void close() {
        modelManager.destroySession(sessionId);
    }
    
    public record AsrResult(
        boolean success,
        String text,
        String model,
        List<AsrSegment> segments,
        String error
    ) {
        public boolean hasText() {
            return text != null && !text.isEmpty();
        }
    }
    
    public record AsrSegment(
        String text,
        boolean isFinal,
        double timestamp
    ) {}
}
