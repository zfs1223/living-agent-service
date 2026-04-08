package com.livingagent.core.knowledge;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class KnowledgeEntry {
    
    private String entryId;
    private String key;
    private Object content;
    private String category;
    private KnowledgeType knowledgeType;
    private Importance importance;
    private Validity validity;
    private String brainDomain;
    private String neuronId;
    private float[] vector;
    private Instant expiresAt;
    private double confidence;
    private boolean verified;
    private Map<String, String> tags;
    private KnowledgeMetadata metadata;
    private Instant createdAt;
    private Instant updatedAt;
    private int accessCount;
    private double relevanceScore;
    private double relevance;
    private String source;
    private KnowledgeScope scope;
    private String scopeIdentifier;
    private Instant lastAccessedAt;
    private String promotedFrom;
    
    public KnowledgeEntry() {
        this.entryId = java.util.UUID.randomUUID().toString();
        this.knowledgeType = KnowledgeType.FACT;
        this.importance = Importance.MEDIUM;
        this.validity = Validity.LONG_TERM;
        this.confidence = 0.5;
        this.verified = false;
        this.tags = new HashMap<>();
        this.metadata = new KnowledgeMetadata();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.accessCount = 0;
    }
    
    public KnowledgeEntry(String key, Object content) {
        this();
        this.key = key;
        this.content = content;
    }
    
    public KnowledgeEntry(String key, Object content, KnowledgeType type) {
        this(key, content);
        this.knowledgeType = type;
    }
    
    public void incrementAccess() {
        this.accessCount++;
        this.updatedAt = Instant.now();
    }
    
    public void addTag(String tagName, String value) {
        this.tags.put(tagName, value);
    }
    
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
    
    public double calculateRelevanceScore() {
        double score = 0.0;
        
        score += importance.getWeight() * 0.3;
        
        score += confidence * 0.2;
        
        if (verified) {
            score += 0.2;
        }
        
        double accessBonus = Math.min(0.2, accessCount * 0.02);
        score += accessBonus;
        
        double recencyBonus = 0.1;
        if (updatedAt != null) {
            long daysSinceUpdate = java.time.temporal.ChronoUnit.DAYS.between(updatedAt, Instant.now());
            recencyBonus = Math.max(0, 0.1 - daysSinceUpdate * 0.005);
        }
        score += recencyBonus;
        
        return Math.min(1.0, score);
    }
    
    public String getEntryId() { return entryId; }
    public void setEntryId(String entryId) { this.entryId = entryId; }
    
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    
    public Object getContent() { return content; }
    public void setContent(Object content) { this.content = content; }
    
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    
    public KnowledgeType getKnowledgeType() { return knowledgeType; }
    public void setKnowledgeType(KnowledgeType knowledgeType) { this.knowledgeType = knowledgeType; }
    
    public Importance getImportance() { return importance; }
    public void setImportance(Importance importance) { this.importance = importance; }
    
    public Validity getValidity() { return validity; }
    public void setValidity(Validity validity) { this.validity = validity; }
    
    public String getBrainDomain() { return brainDomain; }
    public void setBrainDomain(String brainDomain) { this.brainDomain = brainDomain; }
    
    public String getNeuronId() { return neuronId; }
    public void setNeuronId(String neuronId) { this.neuronId = neuronId; }
    
    public float[] getVector() { return vector; }
    public void setVector(float[] vector) { this.vector = vector; }
    
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    
    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }
    
    public Map<String, String> getTags() { return tags; }
    public void setTags(Map<String, String> tags) { this.tags = tags; }
    
    public KnowledgeMetadata getMetadata() { return metadata; }
    public void setMetadata(KnowledgeMetadata metadata) { this.metadata = metadata; }
    
    public void setMetadata(Map<String, String> metadata) {
        if (metadata instanceof KnowledgeMetadata) {
            this.metadata = (KnowledgeMetadata) metadata;
        } else if (metadata != null) {
            this.metadata = new KnowledgeMetadata();
            this.metadata.putAll(metadata);
        }
    }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    
    public int getAccessCount() { return accessCount; }
    public void setAccessCount(int accessCount) { this.accessCount = accessCount; }
    
    public double getRelevanceScore() { 
        if (relevanceScore == 0) {
            return calculateRelevanceScore();
        }
        return relevanceScore; 
    }
    public void setRelevanceScore(double relevanceScore) { this.relevanceScore = relevanceScore; }
    
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    
    public double getRelevance() { return relevance; }
    public void setRelevance(double relevance) { this.relevance = relevance; }
    
    public KnowledgeScope getScope() { return scope; }
    public void setScope(KnowledgeScope scope) { this.scope = scope; }
    
    public String getScopeIdentifier() { return scopeIdentifier; }
    public void setScopeIdentifier(String scopeIdentifier) { this.scopeIdentifier = scopeIdentifier; }
    
    public Instant getLastAccessedAt() { return lastAccessedAt; }
    public void setLastAccessedAt(Instant lastAccessedAt) { this.lastAccessedAt = lastAccessedAt; }
    
    public String getPromotedFrom() { return promotedFrom; }
    public void setPromotedFrom(String promotedFrom) { this.promotedFrom = promotedFrom; }
    
    @Override
    public String toString() {
        return String.format("KnowledgeEntry{key='%s', type=%s, importance=%s, brain='%s', accessCount=%d}",
            key, knowledgeType, importance, brainDomain, accessCount);
    }
}
