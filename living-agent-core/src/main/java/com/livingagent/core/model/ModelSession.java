package com.livingagent.core.model;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ModelSession {
    
    private final String sessionId;
    private final Instant createdAt;
    private Instant lastAccessedAt;
    private SessionStatus status;
    private final Map<String, Object> attributes;
    
    public enum SessionStatus {
        CREATED,
        ACTIVE,
        IDLE,
        CLOSED
    }
    
    public ModelSession(String sessionId) {
        this.sessionId = sessionId;
        this.createdAt = Instant.now();
        this.lastAccessedAt = this.createdAt;
        this.status = SessionStatus.CREATED;
        this.attributes = new ConcurrentHashMap<>();
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }
    
    public void touch() {
        this.lastAccessedAt = Instant.now();
        if (this.status == SessionStatus.CREATED) {
            this.status = SessionStatus.ACTIVE;
        }
    }
    
    public SessionStatus getStatus() {
        return status;
    }
    
    public void setStatus(SessionStatus status) {
        this.status = status;
    }
    
    public Map<String, Object> getAttributes() {
        return attributes;
    }
    
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }
    
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }
    
    public void close() {
        this.status = SessionStatus.CLOSED;
        this.attributes.clear();
    }
    
    public boolean isActive() {
        return status == SessionStatus.ACTIVE || status == SessionStatus.IDLE;
    }
}
