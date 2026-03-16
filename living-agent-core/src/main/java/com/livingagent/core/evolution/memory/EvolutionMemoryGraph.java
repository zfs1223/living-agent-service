package com.livingagent.core.evolution.memory;

import com.livingagent.core.evolution.signal.EvolutionSignal;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface EvolutionMemoryGraph {
    
    void recordSignal(EvolutionSignal signal);
    
    String recordHypothesis(EvolutionSignal signal, String skillId, String mutationCategory);
    
    String recordAttempt(EvolutionSignal signal, String hypothesisId, String skillId);
    
    void recordOutcome(String attemptId, String status, double score, String note);
    
    List<EvolutionEvent> getRecentEvents(String brainDomain, int limit);
    
    List<EvolutionEvent> getEventsBySignal(String signalKey);
    
    Optional<EvolutionEvent> getLastOutcome(String brainDomain);
    
    MemoryAdvice getMemoryAdvice(EvolutionSignal signal);
    
    Map<String, Double> getSkillSuccessRates(String brainDomain);
    
    int getConsecutiveRepairCount(String brainDomain);
    
    int getConsecutiveFailureCount(String brainDomain);
    
    boolean hasRepairLoop(String brainDomain, int threshold);
    
    void cleanupOldEvents(int daysToKeep);
    
    class MemoryAdvice {
        private String preferredSkillId;
        private List<String> bannedSkillIds;
        private List<String> explanations;
        private double confidence;
        
        public MemoryAdvice() {
            this.bannedSkillIds = new java.util.ArrayList<>();
            this.explanations = new java.util.ArrayList<>();
            this.confidence = 0.5;
        }
        
        public String getPreferredSkillId() { return preferredSkillId; }
        public void setPreferredSkillId(String preferredSkillId) { this.preferredSkillId = preferredSkillId; }
        
        public List<String> getBannedSkillIds() { return bannedSkillIds; }
        public void setBannedSkillIds(List<String> bannedSkillIds) { this.bannedSkillIds = bannedSkillIds; }
        
        public List<String> getExplanations() { return explanations; }
        public void setExplanations(List<String> explanations) { this.explanations = explanations; }
        
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
        
        public void addExplanation(String explanation) {
            this.explanations.add(explanation);
        }
        
        public void banSkill(String skillId, String reason) {
            this.bannedSkillIds.add(skillId);
            this.explanations.add("banned:" + skillId + " - " + reason);
        }
    }
}
