package com.livingagent.core.knowledge;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class KnowledgeMetadata extends HashMap<String, String> {
    
    private static final String KEY_SOURCE = "source";
    private static final String KEY_BRAIN_DOMAIN = "brainDomain";
    private static final String KEY_NEURON_ID = "neuronId";
    private static final String KEY_CONFIDENCE = "confidence";
    private static final String KEY_ACCESS_COUNT = "accessCount";
    private static final String KEY_CREATED_AT = "createdAt";
    private static final String KEY_UPDATED_AT = "updatedAt";
    private static final String KEY_LAST_ACCESSED_AT = "lastAccessedAt";
    private static final String KEY_VERIFIED_AT = "verifiedAt";
    private static final String KEY_VERIFIED_BY = "verifiedBy";
    
    public KnowledgeMetadata() {
        put(KEY_CONFIDENCE, "0.5");
        put(KEY_ACCESS_COUNT, "0");
        put(KEY_CREATED_AT, Instant.now().toString());
        put(KEY_UPDATED_AT, Instant.now().toString());
    }
    
    public void incrementAccess() {
        int count = getAccessCount() + 1;
        put(KEY_ACCESS_COUNT, String.valueOf(count));
        put(KEY_LAST_ACCESSED_AT, Instant.now().toString());
    }
    
    public void markVerified(String verifiedBy) {
        put(KEY_VERIFIED_AT, Instant.now().toString());
        put(KEY_VERIFIED_BY, verifiedBy);
    }
    
    public String getSource() { return get(KEY_SOURCE); }
    public void setSource(String source) { put(KEY_SOURCE, source); }
    
    public String getBrainDomain() { return get(KEY_BRAIN_DOMAIN); }
    public void setBrainDomain(String brainDomain) { put(KEY_BRAIN_DOMAIN, brainDomain); }
    
    public String getNeuronId() { return get(KEY_NEURON_ID); }
    public void setNeuronId(String neuronId) { put(KEY_NEURON_ID, neuronId); }
    
    public double getConfidence() { 
        String val = get(KEY_CONFIDENCE);
        return val != null ? Double.parseDouble(val) : 0.5;
    }
    public void setConfidence(double confidence) { put(KEY_CONFIDENCE, String.valueOf(confidence)); }
    
    public int getAccessCount() { 
        String val = get(KEY_ACCESS_COUNT);
        return val != null ? Integer.parseInt(val) : 0;
    }
    public void setAccessCount(int accessCount) { put(KEY_ACCESS_COUNT, String.valueOf(accessCount)); }
    
    public Instant getCreatedAt() { 
        String val = get(KEY_CREATED_AT);
        return val != null ? Instant.parse(val) : null;
    }
    public void setCreatedAt(Instant createdAt) { 
        if (createdAt != null) put(KEY_CREATED_AT, createdAt.toString()); 
    }
    
    public Instant getUpdatedAt() { 
        String val = get(KEY_UPDATED_AT);
        return val != null ? Instant.parse(val) : null;
    }
    public void setUpdatedAt(Instant updatedAt) { 
        if (updatedAt != null) put(KEY_UPDATED_AT, updatedAt.toString()); 
    }
    
    public Instant getLastAccessedAt() { 
        String val = get(KEY_LAST_ACCESSED_AT);
        return val != null ? Instant.parse(val) : null;
    }
    public void setLastAccessedAt(Instant lastAccessedAt) { 
        if (lastAccessedAt != null) put(KEY_LAST_ACCESSED_AT, lastAccessedAt.toString()); 
    }
    
    public Instant getVerifiedAt() { 
        String val = get(KEY_VERIFIED_AT);
        return val != null ? Instant.parse(val) : null;
    }
    public void setVerifiedAt(Instant verifiedAt) { 
        if (verifiedAt != null) put(KEY_VERIFIED_AT, verifiedAt.toString()); 
    }
    
    public String getVerifiedBy() { return get(KEY_VERIFIED_BY); }
    public void setVerifiedBy(String verifiedBy) { put(KEY_VERIFIED_BY, verifiedBy); }
    
    public void addExtra(String key, Object value) {
        if (value != null) {
            put(key, value.toString());
        }
    }
    
    public Object getExtra(String key) {
        return get(key);
    }
}
