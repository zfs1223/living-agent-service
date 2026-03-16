package com.livingagent.core.provider.impl;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.livingagent.core.model.ModelManager;
import com.livingagent.core.model.ModelResponse;
import com.livingagent.core.provider.Provider;
import com.livingagent.core.tool.ToolSchema;

public class QwenProvider implements Provider {
    
    private static final Logger log = LoggerFactory.getLogger(QwenProvider.class);
    
    private final ModelManager modelManager;
    private final String sessionId;
    private final String modelName;
    
    public QwenProvider(ModelManager modelManager) {
        this(modelManager, "qwen3-0.6b");
    }
    
    public QwenProvider(ModelManager modelManager, String modelName) {
        this.modelManager = modelManager;
        this.modelName = modelName;
        this.sessionId = "qwen-" + UUID.randomUUID().toString().substring(0, 8);
        
        modelManager.createSession(this.sessionId)
            .thenAccept(session -> log.info("QwenProvider session created: {}", session.getSessionId()))
            .exceptionally(e -> {
                log.error("Failed to create session for QwenProvider", e);
                return null;
            });
    }
    
    @Override
    public String name() {
        return "qwen";
    }
    
    @Override
    public ProviderCapabilities capabilities() {
        return ProviderCapabilities.basic();
    }
    
    @Override
    public ToolsPayload convertTools(List<ToolSchema> tools) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Available Tools\n\n");
        sb.append("To use a tool, respond with JSON in this format:\n");
        sb.append("```json\n{\"tool\": \"tool_name\", \"arguments\": {\"param\": \"value\"}}\n```\n\n");
        
        for (ToolSchema tool : tools) {
            sb.append("### ").append(tool.name()).append("\n");
            sb.append(tool.description()).append("\n\n");
            if (tool.properties() != null && !tool.properties().isEmpty()) {
                sb.append("Parameters: ").append(tool.properties().keySet()).append("\n\n");
            }
        }
        
        return new ToolsPayload(sb.toString(), ToolsPayload.ToolsPayloadType.PROMPT_GUIDED);
    }
    
    @Override
    public CompletableFuture<String> simpleChat(String message, String model, double temperature) {
        return chatWithSystem(null, message, model, temperature);
    }
    
    @Override
    public CompletableFuture<String> chatWithSystem(String systemPrompt, String message, String model, double temperature) {
        StringBuilder fullPrompt = new StringBuilder();
        
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            fullPrompt.append(systemPrompt).append("\n\n");
        }
        
        fullPrompt.append(message);
        
        return modelManager.generateText(sessionId, fullPrompt.toString(), null)
            .thenApply(response -> {
                if (response.isSuccess()) {
                    return response.getText();
                } else {
                    throw new RuntimeException("Qwen generation failed: " + response.getError());
                }
            });
    }
    
    @Override
    public CompletableFuture<ChatResponse> chat(ChatRequest request) {
        StringBuilder prompt = new StringBuilder();
        List<Map<String, String>> history = new ArrayList<>();
        
        for (ChatMessage msg : request.messages()) {
            String role = msg.role();
            String content = msg.content();
            
            if ("system".equals(role)) {
                prompt.insert(0, content + "\n\n");
            } else if ("user".equals(role)) {
                history.add(Map.of("role", "user", "content", content));
            } else if ("assistant".equals(role)) {
                history.add(Map.of("role", "assistant", "content", content != null ? content : ""));
            } else if ("tool".equals(role) && msg.toolResults() != null) {
                for (ToolResultData result : msg.toolResults()) {
                    history.add(Map.of("role", "tool", "content", result.content()));
                }
            }
        }
        
        if (!history.isEmpty()) {
            ChatMessage lastMsg = request.messages().get(request.messages().size() - 1);
            if ("user".equals(lastMsg.role())) {
                prompt.append(lastMsg.content());
            }
        }
        
        return modelManager.generateText(sessionId, prompt.toString(), history)
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
                    throw new RuntimeException("Qwen chat failed: " + response.getError());
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
                
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                @SuppressWarnings("unchecked")
                Map<String, Object> json = mapper.readValue(jsonStr, Map.class);
                
                if (json.containsKey("tool")) {
                    String toolName = (String) json.get("tool");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> args = (Map<String, Object>) json.get("arguments");
                    String argsJson = args != null ? mapper.writeValueAsString(args) : "{}";
                    
                    calls.add(new ToolCallData(
                        UUID.randomUUID().toString(),
                        toolName,
                        argsJson
                    ));
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse tool calls from response: {}", e.getMessage());
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
}
