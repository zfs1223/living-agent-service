package com.livingagent.gateway.service;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.livingagent.core.model.ModelManager;
import com.livingagent.core.model.ModelResponse;
import com.livingagent.core.provider.Provider;
import com.livingagent.core.provider.ProviderRegistry;
import com.livingagent.gateway.dialogue.DialogueSession;
import com.livingagent.gateway.dialogue.DialogueSessionManager;
import com.livingagent.gateway.dialogue.DialogueMessage;
import com.livingagent.gateway.prompt.PromptBuilder;
import com.livingagent.gateway.executor.ToolExecutorService;

@Service
public class DialogueService {
    
    private static final Logger log = LoggerFactory.getLogger(DialogueService.class);
    
    private final ModelManager modelManager;
    private final ProviderRegistry providerRegistry;
    private final DialogueSessionManager sessionManager;
    private final PromptBuilder promptBuilder;
    private final ToolExecutorService toolExecutorService;
    
    public DialogueService(ModelManager modelManager,
                           ProviderRegistry providerRegistry,
                           DialogueSessionManager sessionManager,
                           PromptBuilder promptBuilder,
                           ToolExecutorService toolExecutorService) {
        this.modelManager = modelManager;
        this.providerRegistry = providerRegistry;
        this.sessionManager = sessionManager;
        this.promptBuilder = promptBuilder;
        this.toolExecutorService = toolExecutorService;
    }
    
    public CompletableFuture<Map<String, Object>> processAudio(String sessionId, byte[] audioData, String provider) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String asrProvider = provider != null ? provider : "sherpa";
                ModelResponse response = modelManager.recognizeSpeech(sessionId, 
                    saveTempAudio(audioData), asrProvider).join();
                
                Map<String, Object> result = new HashMap<>();
                if (response.isSuccess()) {
                    result.put("text", response.getText());
                    result.put("provider", asrProvider);
                } else {
                    result.put("error", response.getError());
                }
                return result;
            } catch (Exception e) {
                log.error("Error processing audio", e);
                return Map.of("error", e.getMessage());
            }
        });
    }
    
    public CompletableFuture<Map<String, Object>> generateResponse(String sessionId, String userInput) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                DialogueSession session = sessionManager.getSession(sessionId);
                List<DialogueMessage> history = session != null ? session.getMessages() : new ArrayList<>();
                
                String role = promptBuilder.detectRole(userInput);
                String prompt = promptBuilder.buildPrompt(userInput, history, role);
                
                Provider provider = providerRegistry.getDefault();
                if (provider == null) {
                    return Map.of("error", "No provider available");
                }
                
                String response = provider.simpleChat(prompt, null, 0.7).join();
                
                Map<String, Object> result = new HashMap<>();
                result.put("text", response);
                result.put("model", provider.name());
                
                return result;
            } catch (Exception e) {
                log.error("Error generating response", e);
                return Map.of("error", e.getMessage());
            }
        });
    }
    
    public CompletableFuture<Map<String, Object>> generateResponseWithTools(String sessionId, String userInput) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                DialogueSession session = sessionManager.getSession(sessionId);
                List<DialogueMessage> history = session != null ? session.getMessages() : new ArrayList<>();
                
                String role = promptBuilder.detectRole(userInput);
                
                List<Map<String, Object>> tools = getAvailableTools();
                String prompt = promptBuilder.buildPromptWithTools(userInput, history, role, tools);
                
                Provider provider = providerRegistry.getToolProvider();
                if (provider == null) {
                    provider = providerRegistry.getDefault();
                }
                
                String response = provider.simpleChat(prompt, null, 0.7).join();
                
                Map<String, Object> toolCall = parseToolCall(response);
                
                Map<String, Object> result = new HashMap<>();
                if (toolCall != null) {
                    String toolName = (String) toolCall.get("tool");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> args = (Map<String, Object>) toolCall.get("arguments");
                    
                    var toolResult = toolExecutorService.execute(toolName, args, sessionId, 
                        session != null ? session.getUserId() : "unknown");
                    
                    result.put("toolCall", true);
                    result.put("toolName", toolName);
                    result.put("toolResult", toolResult.getData());
                } else {
                    result.put("toolCall", false);
                    result.put("text", response);
                }
                
                result.put("model", provider.name());
                
                return result;
            } catch (Exception e) {
                log.error("Error generating response with tools", e);
                return Map.of("error", e.getMessage());
            }
        });
    }
    
    public CompletableFuture<Map<String, Object>> synthesizeSpeech(String sessionId, String text) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String outputPath = getTempAudioPath(sessionId);
                ModelResponse response = modelManager.synthesizeSpeech(sessionId, text, "af_bella", 1.0, outputPath, false).join();
                
                Map<String, Object> result = new HashMap<>();
                if (response.isSuccess()) {
                    byte[] audioData = readAudioFile(outputPath);
                    result.put("audio", audioData);
                    result.put("duration", response.getData().get("duration"));
                } else {
                    result.put("error", response.getError());
                }
                return result;
            } catch (Exception e) {
                log.error("Error synthesizing speech", e);
                return Map.of("error", e.getMessage());
            }
        });
    }
    
    private List<Map<String, Object>> getAvailableTools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        
        for (var executor : toolExecutorService.getAllExecutors()) {
            Map<String, Object> tool = new HashMap<>();
            tool.put("name", executor.getName());
            tool.put("description", executor.getDescription());
            tools.add(tool);
        }
        
        return tools;
    }
    
    private Map<String, Object> parseToolCall(String response) {
        if (response == null || response.isEmpty()) {
            return null;
        }
        
        try {
            int jsonStart = response.indexOf('{');
            int jsonEnd = response.lastIndexOf('}');
            
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                String jsonStr = response.substring(jsonStart, jsonEnd + 1);
                
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                @SuppressWarnings("unchecked")
                Map<String, Object> json = mapper.readValue(jsonStr, Map.class);
                
                if (json.containsKey("tool")) {
                    return json;
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse tool call: {}", e.getMessage());
        }
        
        return null;
    }
    
    private String saveTempAudio(byte[] audioData) {
        try {
            String path = System.getProperty("java.io.tmpdir") + "/audio_" + System.currentTimeMillis() + ".wav";
            java.nio.file.Files.write(java.nio.file.Paths.get(path), audioData);
            return path;
        } catch (Exception e) {
            throw new RuntimeException("Failed to save temp audio", e);
        }
    }
    
    private String getTempAudioPath(String sessionId) {
        return System.getProperty("java.io.tmpdir") + "/tts_" + sessionId + "_" + System.currentTimeMillis() + ".wav";
    }
    
    private byte[] readAudioFile(String path) {
        try {
            return java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path));
        } catch (Exception e) {
            log.error("Failed to read audio file: {}", path, e);
            return new byte[0];
        }
    }
}
