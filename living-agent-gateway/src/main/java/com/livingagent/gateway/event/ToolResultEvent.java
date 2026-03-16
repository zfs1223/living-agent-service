package com.livingagent.gateway.event;

import java.time.LocalDateTime;
import java.util.Map;

public class ToolResultEvent {
    
    private final String eventId;
    private final String sessionId;
    private final String toolName;
    private final Map<String, Object> result;
    private final boolean success;
    private final String error;
    private final LocalDateTime timestamp;
    
    public ToolResultEvent(String sessionId, String toolName, Map<String, Object> result, boolean success, String error) {
        this.eventId = java.util.UUID.randomUUID().toString();
        this.sessionId = sessionId;
        this.toolName = toolName;
        this.result = result;
        this.success = success;
        this.error = error;
        this.timestamp = LocalDateTime.now();
    }
    
    public static ToolResultEvent success(String sessionId, String toolName, Map<String, Object> result) {
        return new ToolResultEvent(sessionId, toolName, result, true, null);
    }
    
    public static ToolResultEvent failure(String sessionId, String toolName, String error) {
        return new ToolResultEvent(sessionId, toolName, null, false, error);
    }
    
    public String getEventId() {
        return eventId;
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public String getToolName() {
        return toolName;
    }
    
    public Map<String, Object> getResult() {
        return result;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public String getError() {
        return error;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}
