package com.livingagent.core.provider.impl;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.model.ModelManager;
import com.livingagent.core.model.ModelResponse;
import com.livingagent.core.provider.Provider;
import com.livingagent.core.tool.ToolSchema;

public class BitNetProvider implements Provider {
    
    private static final Logger log = LoggerFactory.getLogger(BitNetProvider.class);
    
    private final ModelManager modelManager;
    private final String sessionId;
    private final ObjectMapper objectMapper;
    
    public BitNetProvider(ModelManager modelManager) {
        this.modelManager = modelManager;
        this.objectMapper = new ObjectMapper();
        this.sessionId = "bitnet-" + UUID.randomUUID().toString().substring(0, 8);
        
        modelManager.createSession(this.sessionId)
            .thenAccept(session -> log.info("BitNetProvider session created: {}", session.getSessionId()))
            .exceptionally(e -> {
                log.error("Failed to create session for BitNetProvider", e);
                return null;
            });
    }
    
    @Override
    public String name() {
        return "bitnet";
    }
    
    @Override
    public ProviderCapabilities capabilities() {
        return ProviderCapabilities.basic();
    }
    
    @Override
    public ToolsPayload convertTools(List<ToolSchema> tools) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Available Tools\n\n");
        sb.append("Analyze user input and determine if a tool call is needed.\n");
        sb.append("If a tool call is needed, respond in JSON format:\n");
        sb.append("{\"tool_call\": true, \"tool\": \"tool_name\", \"parameters\": {\"param\": \"value\"}}\n\n");
        sb.append("If no tool call is needed, respond:\n");
        sb.append("{\"tool_call\": false, \"response\": \"your response\"}\n\n");
        
        for (ToolSchema tool : tools) {
            sb.append("- ").append(tool.name()).append(": ");
            sb.append(tool.description()).append("\n");
        }
        
        return new ToolsPayload(sb.toString(), ToolsPayload.ToolsPayloadType.PROMPT_GUIDED);
    }
    
    @Override
    public CompletableFuture<String> simpleChat(String message, String model, double temperature) {
        return modelManager.generateTextBitNet(sessionId, message, 500, temperature)
            .thenApply(response -> {
                if (response.isSuccess()) {
                    return response.getText();
                } else {
                    throw new RuntimeException("BitNet generation failed: " + response.getError());
                }
            });
    }
    
    @Override
    public CompletableFuture<String> chatWithSystem(String systemPrompt, String message, String model, double temperature) {
        String fullPrompt = (systemPrompt != null && !systemPrompt.isEmpty()) 
            ? systemPrompt + "\n\n" + message 
            : message;
        
        return simpleChat(fullPrompt, model, temperature);
    }
    
    @Override
    public CompletableFuture<ChatResponse> chat(ChatRequest request) {
        StringBuilder prompt = new StringBuilder();
        
        for (ChatMessage msg : request.messages()) {
            String role = msg.role();
            String content = msg.content();
            
            if ("system".equals(role)) {
                prompt.append("System: ").append(content).append("\n\n");
            } else if ("user".equals(role)) {
                prompt.append("User: ").append(content).append("\n");
            } else if ("assistant".equals(role)) {
                prompt.append("Assistant: ").append(content).append("\n");
            }
        }
        
        return modelManager.generateTextBitNet(sessionId, prompt.toString(), request.maxTokens(), request.temperature())
            .thenApply(response -> {
                if (response.isSuccess()) {
                    String text = response.getText();
                    List<ToolCallData> toolCalls = parseToolCalls(text);
                    
                    return new ChatResponse(
                        text,
                        toolCalls,
                        0,
                        0,
                        toolCalls.isEmpty() ? "stop" : "tool_calls"
                    );
                } else {
                    throw new RuntimeException("BitNet chat failed: " + response.getError());
                }
            });
    }
    
    public CompletableFuture<ToolIntentResult> processToolIntent(String userInput, List<String> availableTools) {
        return modelManager.processToolIntent(sessionId, userInput, availableTools)
            .thenApply(response -> {
                if (response.isSuccess()) {
                    Boolean toolCall = (Boolean) response.get("tool_call");
                    if (toolCall != null && toolCall) {
                        String tool = (String) response.get("tool");
                        @SuppressWarnings("unchecked")
                        Map<String, Object> params = (Map<String, Object>) response.get("parameters");
                        
                        return new ToolIntentResult(true, tool, params, null);
                    } else {
                        String resp = (String) response.get("response");
                        return new ToolIntentResult(false, null, null, resp);
                    }
                } else {
                    return new ToolIntentResult(false, null, null, "Error: " + response.getError());
                }
            });
    }
    
    private List<ToolCallData> parseToolCalls(String text) {
        List<ToolCallData> calls = new ArrayList<>();
        
        if (text == null || text.isEmpty()) {
            return calls;
        }
        
        try {
            int jsonStart = text.indexOf('{');
            int jsonEnd = text.lastIndexOf('}');
            
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                String jsonStr = text.substring(jsonStart, jsonEnd + 1);
                
                @SuppressWarnings("unchecked")
                Map<String, Object> json = objectMapper.readValue(jsonStr, Map.class);
                
                Boolean toolCall = (Boolean) json.get("tool_call");
                if (toolCall != null && toolCall) {
                    String toolName = (String) json.get("tool");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> args = (Map<String, Object>) json.get("parameters");
                    String argsJson = args != null ? objectMapper.writeValueAsString(args) : "{}";
                    
                    calls.add(new ToolCallData(
                        UUID.randomUUID().toString(),
                        toolName,
                        argsJson
                    ));
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse tool calls from BitNet response: {}", e.getMessage());
        }
        
        return calls;
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
    
    public record ToolIntentResult(
        boolean isToolCall,
        String toolName,
        Map<String, Object> parameters,
        String response
    ) {}
}
