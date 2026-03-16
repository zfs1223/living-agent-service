package com.livingagent.core.provider.impl;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.livingagent.core.model.ModelManager;
import com.livingagent.core.model.ModelResponse;
import com.livingagent.core.provider.Provider;
import com.livingagent.core.tool.ToolSchema;

public class TtsProvider implements Provider {
    
    private static final Logger log = LoggerFactory.getLogger(TtsProvider.class);
    
    private final ModelManager modelManager;
    private final String sessionId;
    private final String defaultVoice;
    private final double defaultSpeed;
    
    public TtsProvider(ModelManager modelManager) {
        this(modelManager, "af_bella", 1.0);
    }
    
    public TtsProvider(ModelManager modelManager, String defaultVoice, double defaultSpeed) {
        this.modelManager = modelManager;
        this.defaultVoice = defaultVoice;
        this.defaultSpeed = defaultSpeed;
        this.sessionId = "tts-" + UUID.randomUUID().toString().substring(0, 8);
        
        modelManager.createSession(this.sessionId)
            .thenAccept(session -> log.info("TtsProvider session created: {}", session.getSessionId()))
            .exceptionally(e -> {
                log.error("Failed to create session for TtsProvider", e);
                return null;
            });
    }
    
    @Override
    public String name() {
        return "tts";
    }
    
    @Override
    public ProviderCapabilities capabilities() {
        return ProviderCapabilities.basic();
    }
    
    @Override
    public ToolsPayload convertTools(List<ToolSchema> tools) {
        return new ToolsPayload("", ToolsPayload.ToolsPayloadType.PROMPT_GUIDED);
    }
    
    public CompletableFuture<TtsResult> synthesize(String text, String outputPath) {
        return synthesize(text, defaultVoice, defaultSpeed, outputPath, false);
    }
    
    public CompletableFuture<TtsResult> synthesize(String text, String voice, double speed, String outputPath, boolean useMeloTTS) {
        String actualVoice = voice != null ? voice : defaultVoice;
        double actualSpeed = speed > 0 ? speed : defaultSpeed;
        
        return modelManager.synthesizeSpeech(sessionId, text, actualVoice, actualSpeed, outputPath, useMeloTTS)
            .thenApply(response -> {
                if (response.isSuccess()) {
                    Double duration = (Double) response.get("duration");
                    Integer sampleRate = (Integer) response.get("sample_rate");
                    String model = response.getModel();
                    String langCode = (String) response.get("lang_code");
                    
                    return new TtsResult(
                        true,
                        outputPath,
                        duration != null ? duration : 0.0,
                        sampleRate != null ? sampleRate : 16000,
                        model,
                        langCode,
                        null
                    );
                } else {
                    return new TtsResult(false, null, 0, 0, null, null, response.getError());
                }
            });
    }
    
    public CompletableFuture<TtsResult> synthesizeWithMeloTTS(String text, String outputPath) {
        return synthesize(text, "ZH", 1.0, outputPath, true);
    }
    
    public CompletableFuture<TtsResult> synthesizeWithSupertonic(String text, String voice, String outputPath) {
        return synthesize(text, voice, 1.0, outputPath, false);
    }
    
    @Override
    public CompletableFuture<String> simpleChat(String message, String model, double temperature) {
        return CompletableFuture.completedFuture("TTS provider does not support chat");
    }
    
    @Override
    public CompletableFuture<String> chatWithSystem(String systemPrompt, String message, String model, double temperature) {
        return CompletableFuture.completedFuture("TTS provider does not support chat");
    }
    
    @Override
    public CompletableFuture<ChatResponse> chat(ChatRequest request) {
        return CompletableFuture.completedFuture(
            new ChatResponse("TTS provider does not support chat", null, 0, 0, "error")
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
        return false;
    }
    
    public void close() {
        modelManager.destroySession(sessionId);
    }
    
    public record TtsResult(
        boolean success,
        String outputPath,
        double duration,
        int sampleRate,
        String model,
        String langCode,
        String error
    ) {
        public boolean hasOutput() {
            return outputPath != null && !outputPath.isEmpty();
        }
    }
}
