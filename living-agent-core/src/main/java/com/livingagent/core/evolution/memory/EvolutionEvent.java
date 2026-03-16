package com.livingagent.core.evolution.memory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EvolutionEvent {
    
    private String eventId;
    private EventType kind;
    private Instant timestamp;
    private String brainDomain;
    private String neuronId;
    private SignalSnapshot signal;
    private Hypothesis hypothesis;
    private EvolutionAction action;
    private EvolutionOutcome outcome;
    private Map<String, Object> metadata;
    
    public enum EventType {
        SIGNAL,
        HYPOTHESIS,
        ATTEMPT,
        OUTCOME,
        CONFIDENCE_EDGE,
        EXTERNAL_CANDIDATE
    }
    
    public EvolutionEvent() {
        this.eventId = "evt_" + System.currentTimeMillis() + "_" + Integer.toHexString(hashCode());
        this.timestamp = Instant.now();
        this.metadata = new HashMap<>();
    }
    
    public EvolutionEvent(EventType kind) {
        this();
        this.kind = kind;
    }
    
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    
    public EventType getKind() { return kind; }
    public void setKind(EventType kind) { this.kind = kind; }
    
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    
    public String getBrainDomain() { return brainDomain; }
    public void setBrainDomain(String brainDomain) { this.brainDomain = brainDomain; }
    
    public String getNeuronId() { return neuronId; }
    public void setNeuronId(String neuronId) { this.neuronId = neuronId; }
    
    public SignalSnapshot getSignal() { return signal; }
    public void setSignal(SignalSnapshot signal) { this.signal = signal; }
    
    public Hypothesis getHypothesis() { return hypothesis; }
    public void setHypothesis(Hypothesis hypothesis) { this.hypothesis = hypothesis; }
    
    public EvolutionAction getAction() { return action; }
    public void setAction(EvolutionAction action) { this.action = action; }
    
    public EvolutionOutcome getOutcome() { return outcome; }
    public void setOutcome(EvolutionOutcome outcome) { this.outcome = outcome; }
    
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    
    public static class SignalSnapshot {
        private String key;
        private List<String> signals;
        private String errorSignature;
        
        public SignalSnapshot() {
            this.signals = new ArrayList<>();
        }
        
        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        
        public List<String> getSignals() { return signals; }
        public void setSignals(List<String> signals) { this.signals = signals; }
        
        public String getErrorSignature() { return errorSignature; }
        public void setErrorSignature(String errorSignature) { this.errorSignature = errorSignature; }
    }
    
    public static class Hypothesis {
        private String hypothesisId;
        private String text;
        private Map<String, Object> predictedOutcome;
        
        public Hypothesis() {
            this.predictedOutcome = new HashMap<>();
        }
        
        public String getHypothesisId() { return hypothesisId; }
        public void setHypothesisId(String hypothesisId) { this.hypothesisId = hypothesisId; }
        
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        
        public Map<String, Object> getPredictedOutcome() { return predictedOutcome; }
        public void setPredictedOutcome(Map<String, Object> predictedOutcome) { this.predictedOutcome = predictedOutcome; }
    }
    
    public static class EvolutionAction {
        private String actionId;
        private String skillId;
        private String toolId;
        private String mutationCategory;
        private double driftIntensity;
        private String selectedBy;
        
        public String getActionId() { return actionId; }
        public void setActionId(String actionId) { this.actionId = actionId; }
        
        public String getSkillId() { return skillId; }
        public void setSkillId(String skillId) { this.skillId = skillId; }
        
        public String getToolId() { return toolId; }
        public void setToolId(String toolId) { this.toolId = toolId; }
        
        public String getMutationCategory() { return mutationCategory; }
        public void setMutationCategory(String mutationCategory) { this.mutationCategory = mutationCategory; }
        
        public double getDriftIntensity() { return driftIntensity; }
        public void setDriftIntensity(double driftIntensity) { this.driftIntensity = driftIntensity; }
        
        public String getSelectedBy() { return selectedBy; }
        public void setSelectedBy(String selectedBy) { this.selectedBy = selectedBy; }
    }
    
    public static class EvolutionOutcome {
        private String status;
        private double score;
        private String note;
        private Instant completedAt;
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public double getScore() { return score; }
        public void setScore(double score) { this.score = score; }
        
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
        
        public Instant getCompletedAt() { return completedAt; }
        public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    }
}
