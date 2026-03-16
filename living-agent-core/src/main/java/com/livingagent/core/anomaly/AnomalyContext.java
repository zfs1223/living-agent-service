package com.livingagent.core.anomaly;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class AnomalyContext {
    
    private String sessionId;
    private String userId;
    private String source;
    private Instant timestamp;
    private Map<String, Object> metrics;
    private Map<String, Object> metadata;
    
    public AnomalyContext() {
        this.timestamp = Instant.now();
        this.metrics = new HashMap<>();
        this.metadata = new HashMap<>();
    }
    
    public static AnomalyContext create(String source) {
        AnomalyContext context = new AnomalyContext();
        context.source = source;
        return context;
    }
    
    public AnomalyContext sessionId(String sessionId) {
        this.sessionId = sessionId;
        return this;
    }
    
    public AnomalyContext userId(String userId) {
        this.userId = userId;
        return this;
    }
    
    public AnomalyContext metric(String key, Object value) {
        this.metrics.put(key, value);
        return this;
    }
    
    public AnomalyContext metadata(String key, Object value) {
        this.metadata.put(key, value);
        return this;
    }
    
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    
    public Map<String, Object> getMetrics() { return metrics; }
    public void setMetrics(Map<String, Object> metrics) { this.metrics = metrics; }
    
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
