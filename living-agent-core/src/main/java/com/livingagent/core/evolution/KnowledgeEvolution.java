package com.livingagent.core.evolution;

import java.time.Instant;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

public class KnowledgeEvolution {
    
    public enum EvolutionType {
        CREATE,
        UPDATE,
        MERGE,
        SPLIT,
        DELETE,
        PROPAGATE,
        REFINE,
        PROMOTE,
        ENHANCE,
        DEPRECATE,
        MAINTAIN,
        SIMILARITY,
        EXTRACT
    }
    
    private String evolutionId;
    private String knowledgeId;
    private EvolutionType evolutionType;
    private String sourceId;
    private String beforeState;
    private String afterState;
    private String trigger;
    private double confidence;
    private String source;
    private Map<String, Object> metadata;
    private Instant timestamp;
    private String agentId;
    private double relevanceDelta;
    private boolean validated;
    private String validationError;
    
    public KnowledgeEvolution() {
        this.evolutionId = UUID.randomUUID().toString();
        this.metadata = new HashMap<>();
        this.timestamp = Instant.now();
        this.confidence = 0.0;
        this.relevanceDelta = 0.0;
        this.validated = false;
    }
    
    public KnowledgeEvolution(String knowledgeId, EvolutionType evolutionType) {
        this();
        this.knowledgeId = knowledgeId;
        this.evolutionType = evolutionType;
    }
    
    public KnowledgeEvolution(String knowledgeId, EvolutionType evolutionType, String trigger) {
        this(knowledgeId, evolutionType);
        this.trigger = trigger;
    }
    
    public String getEvolutionId() { return evolutionId; }
    public void setEvolutionId(String evolutionId) { this.evolutionId = evolutionId; }
    
    public String getKnowledgeId() { return knowledgeId; }
    public void setKnowledgeId(String knowledgeId) { this.knowledgeId = knowledgeId; }
    
    public EvolutionType getEvolutionType() { return evolutionType; }
    public void setEvolutionType(EvolutionType evolutionType) { this.evolutionType = evolutionType; }
    
    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }
    
    public String getBeforeState() { return beforeState; }
    public void setBeforeState(String beforeState) { this.beforeState = beforeState; }
    
    public String getAfterState() { return afterState; }
    public void setAfterState(String afterState) { this.afterState = afterState; }
    
    public String getTrigger() { return trigger; }
    public void setTrigger(String trigger) { this.trigger = trigger; }
    
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    
    public double getRelevanceDelta() { return relevanceDelta; }
    public void setRelevanceDelta(double relevanceDelta) { this.relevanceDelta = relevanceDelta; }
    
    public boolean isValidated() { return validated; }
    public void setValidated(boolean validated) { this.validated = validated; }
    
    public String getValidationError() { return validationError; }
    public void setValidationError(String validationError) { this.validationError = validationError; }
    
    @Override
    public String toString() {
        return "KnowledgeEvolution{" +
                "evolutionId='" + evolutionId + '\'' +
                ", knowledgeId='" + knowledgeId + '\'' +
                ", evolutionType='" + evolutionType + '\'' +
                ", confidence=" + confidence +
                ", validated=" + validated +
                '}';
    }
}
