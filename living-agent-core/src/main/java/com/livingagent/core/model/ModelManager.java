package com.livingagent.core.model;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface ModelManager {
    
    CompletableFuture<ModelResponse> recognizeSpeech(String sessionId, String audioPath, String provider);
    
    CompletableFuture<ModelResponse> recognizeSpeechStreaming(String sessionId, String audioPath, String provider);
    
    CompletableFuture<ModelResponse> generateText(String sessionId, String prompt, List<Map<String, String>> history);
    
    CompletableFuture<ModelResponse> generateTextBitNet(String sessionId, String prompt, int maxTokens, double temperature);
    
    CompletableFuture<ModelResponse> synthesizeSpeech(String sessionId, String text, String voice, 
            double speed, String outputPath, boolean useMeloTTS);
    
    CompletableFuture<ModelResponse> processToolIntent(String sessionId, String userInput, List<String> availableTools);
    
    CompletableFuture<ModelResponse> executeToolCall(String sessionId, String toolName, Map<String, Object> parameters);
    
    CompletableFuture<ModelSession> createSession(String sessionId);
    
    CompletableFuture<Boolean> destroySession(String sessionId);
    
    CompletableFuture<ModelStatus> getStatus();
    
    boolean isAsrAvailable();
    
    boolean isLlmAvailable();
    
    boolean isTtsAvailable();
    
    void shutdown();
    
    String chat(String modelId, String prompt);
    
    String chatWithHistory(String modelId, String prompt, List<Map<String, String>> history);
    
    String chatWithImage(String modelId, String prompt, String imageUrl);
    
    String chatWithImages(String modelId, String prompt, List<String> imageUrls);
    
    CompletableFuture<String> chatAsync(String modelId, String prompt);
    
    CompletableFuture<String> chatWithImageAsync(String modelId, String prompt, String imageUrl);
}
