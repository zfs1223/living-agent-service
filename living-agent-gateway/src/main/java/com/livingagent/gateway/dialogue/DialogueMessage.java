package com.livingagent.gateway.dialogue;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashMap;

public record DialogueMessage(
    String role,
    String content,
    LocalDateTime timestamp,
    Map<String, Object> metadata
) {
    public DialogueMessage(String role, String content) {
        this(role, content, LocalDateTime.now(), null);
    }
    
    public DialogueMessage(String role, String content, LocalDateTime timestamp) {
        this(role, content, timestamp, null);
    }
    
    public static DialogueMessage system(String content) {
        return new DialogueMessage("system", content);
    }
    
    public static DialogueMessage user(String content) {
        return new DialogueMessage("user", content);
    }
    
    public static DialogueMessage assistant(String content) {
        return new DialogueMessage("assistant", content);
    }
    
    public static DialogueMessage tool(String toolName, String result) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("toolName", toolName);
        return new DialogueMessage("tool", result, LocalDateTime.now(), metadata);
    }
    
    public boolean isSystem() {
        return "system".equals(role);
    }
    
    public boolean isUser() {
        return "user".equals(role);
    }
    
    public boolean isAssistant() {
        return "assistant".equals(role);
    }
    
    public boolean isTool() {
        return "tool".equals(role);
    }
    
    public String getToolName() {
        if (metadata != null && metadata.containsKey("toolName")) {
            return (String) metadata.get("toolName");
        }
        return null;
    }
}
