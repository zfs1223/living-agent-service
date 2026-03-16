package com.livingagent.core.evolution.signal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EvolutionSignal {
    
    private String signalId;
    private SignalType type;
    private SignalCategory category;
    private String source;
    private String content;
    private String brainDomain;
    private String neuronId;
    private double confidence;
    private Instant detectedAt;
    private Map<String, Object> metadata;
    private List<String> tags;
    
    public enum SignalType {
        ERROR,
        OPPORTUNITY,
        STABILITY,
        DRIFT,
        CAPABILITY_GAP,
        PERFORMANCE,
        USER_REQUEST,
        SYSTEM_EVENT
    }
    
    public enum SignalCategory {
        REPAIR,
        OPTIMIZE,
        INNOVATE
    }
    
    public EvolutionSignal() {
        this.signalId = java.util.UUID.randomUUID().toString();
        this.detectedAt = Instant.now();
        this.metadata = new HashMap<>();
        this.tags = new ArrayList<>();
        this.confidence = 0.5;
    }
    
    public EvolutionSignal(SignalType type, String content) {
        this();
        this.type = type;
        this.content = content;
        this.category = inferCategory(type);
    }
    
    private SignalCategory inferCategory(SignalType type) {
        switch (type) {
            case ERROR:
            case CAPABILITY_GAP:
                return SignalCategory.REPAIR;
            case PERFORMANCE:
            case DRIFT:
                return SignalCategory.OPTIMIZE;
            case OPPORTUNITY:
            case USER_REQUEST:
            case STABILITY:
                return SignalCategory.INNOVATE;
            default:
                return SignalCategory.OPTIMIZE;
        }
    }
    
    public void addTag(String tag) {
        this.tags.add(tag);
    }
    
    public void addMetadata(String key, Object value) {
        this.metadata.put(key, value);
    }
    
    public boolean isRepairSignal() {
        return category == SignalCategory.REPAIR;
    }
    
    public boolean isOptimizeSignal() {
        return category == SignalCategory.OPTIMIZE;
    }
    
    public boolean isInnovateSignal() {
        return category == SignalCategory.INNOVATE;
    }
    
    public static EvolutionSignal error(String signalId, String source, String content, String brainDomain) {
        EvolutionSignal signal = new EvolutionSignal(SignalType.ERROR, content);
        if (signalId != null) {
            signal.setSignalId(signalId);
        }
        signal.setSource(source);
        signal.setBrainDomain(brainDomain);
        signal.setConfidence(0.9);
        return signal;
    }
    
    public static EvolutionSignal opportunity(String content, String brainDomain, double confidence) {
        EvolutionSignal signal = new EvolutionSignal(SignalType.OPPORTUNITY, content);
        signal.setBrainDomain(brainDomain);
        signal.setConfidence(confidence);
        return signal;
    }
    
    public static EvolutionSignal performance(String content, String brainDomain, double confidence) {
        EvolutionSignal signal = new EvolutionSignal(SignalType.PERFORMANCE, content);
        signal.setBrainDomain(brainDomain);
        signal.setConfidence(confidence);
        return signal;
    }
    
    public static EvolutionSignal capabilityGap(String content, String brainDomain) {
        EvolutionSignal signal = new EvolutionSignal(SignalType.CAPABILITY_GAP, content);
        signal.setBrainDomain(brainDomain);
        signal.setConfidence(0.7);
        return signal;
    }
    
    public String getSignalId() { return signalId; }
    public void setSignalId(String signalId) { this.signalId = signalId; }
    
    public SignalType getType() { return type; }
    public void setType(SignalType type) { this.type = type; }
    
    public SignalCategory getCategory() { return category; }
    public void setCategory(SignalCategory category) { this.category = category; }
    
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    
    public String getBrainDomain() { return brainDomain; }
    public void setBrainDomain(String brainDomain) { this.brainDomain = brainDomain; }
    
    public String getNeuronId() { return neuronId; }
    public void setNeuronId(String neuronId) { this.neuronId = neuronId; }
    
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    
    public Instant getDetectedAt() { return detectedAt; }
    public void setDetectedAt(Instant detectedAt) { this.detectedAt = detectedAt; }
    
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    
    @Override
    public String toString() {
        return String.format("EvolutionSignal{id=%s, type=%s, category=%s, brain=%s, confidence=%.2f}",
            signalId, type, category, brainDomain, confidence);
    }
}
