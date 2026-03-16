package com.livingagent.core.knowledge;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class KnowledgeMetadata {
    
    private String source;
    private String brainDomain;
    private String neuronId;
    private double confidence;
    private int accessCount;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastAccessedAt;
    private Instant verifiedAt;
    private String verifiedBy;
    private Map<String, Object> extra;
    
    public KnowledgeMetadata() {
        this.confidence = 0.5;
        this.accessCount = 0;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.extra = new HashMap<>();
    }
    
    public void incrementAccess() {
        this.accessCount++;
        this.lastAccessedAt = Instant.now();
    }
    
    public void markVerified(String verifiedBy) {
        this.verifiedAt = Instant.now();
        this.verifiedBy = verifiedBy;
    }
    
    public void addExtra(String key, Object value) {
        this.extra.put(key, value);
    }
    
    public Object getExtra(String key) {
        return this.extra.get(key);
    }
    
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    
    public String getBrainDomain() { return brainDomain; }
    public void setBrainDomain(String brainDomain) { this.brainDomain = brainDomain; }
    
    public String getNeuronId() { return neuronId; }
    public void setNeuronId(String neuronId) { this.neuronId = neuronId; }
    
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    
    public int getAccessCount() { return accessCount; }
    public void setAccessCount(int accessCount) { this.accessCount = accessCount; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    
    public Instant getLastAccessedAt() { return lastAccessedAt; }
    public void setLastAccessedAt(Instant lastAccessedAt) { this.lastAccessedAt = lastAccessedAt; }
    
    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }
    
    public String getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(String verifiedBy) { this.verifiedBy = verifiedBy; }
    
    public Map<String, Object> getExtra() { return extra; }
    public void setExtra(Map<String, Object> extra) { this.extra = extra; }
}
