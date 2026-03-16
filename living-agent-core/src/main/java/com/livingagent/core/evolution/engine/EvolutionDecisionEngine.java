package com.livingagent.core.evolution.engine;

import com.livingagent.core.evolution.circuitbreaker.CircuitBreakerReport;
import com.livingagent.core.evolution.memory.EvolutionMemoryGraph;
import com.livingagent.core.evolution.personality.BrainPersonality;
import com.livingagent.core.evolution.signal.EvolutionSignal;

import java.util.List;
import java.util.Map;

public interface EvolutionDecisionEngine {
    
    EvolutionDecision decide(EvolutionSignal signal);
    
    EvolutionDecision decideWithConstraints(EvolutionSignal signal, 
                                            EvolutionConstraints constraints);
    
    List<EvolutionDecision> batchDecide(List<EvolutionSignal> signals);
    
    boolean shouldTriggerEvolution(String brainDomain);
    
    EvolutionPriority getPriority(EvolutionSignal signal);
    
    EvolutionConstraints getDefaultConstraints(String brainDomain);
    
    class EvolutionDecision {
        private String decisionId;
        private EvolutionSignal signal;
        private EvolutionStrategy strategy;
        private String targetSkillId;
        private String targetToolId;
        private double confidence;
        private List<String> reasons;
        private Map<String, Object> parameters;
        private BrainPersonality personality;
        private CircuitBreakerReport circuitBreakerStatus;
        
        public EvolutionDecision() {
            this.decisionId = "dec_" + System.currentTimeMillis();
            this.reasons = new java.util.ArrayList<>();
            this.parameters = new java.util.HashMap<>();
            this.confidence = 0.5;
        }
        
        public static EvolutionDecision skip(EvolutionSignal signal, String reason) {
            EvolutionDecision decision = new EvolutionDecision();
            decision.setSignal(signal);
            decision.setStrategy(EvolutionStrategy.SKIP);
            decision.getReasons().add(reason);
            return decision;
        }
        
        public static EvolutionDecision repair(EvolutionSignal signal, String targetId, double confidence) {
            EvolutionDecision decision = new EvolutionDecision();
            decision.setSignal(signal);
            decision.setStrategy(EvolutionStrategy.REPAIR);
            decision.setTargetSkillId(targetId);
            decision.setConfidence(confidence);
            return decision;
        }
        
        public static EvolutionDecision optimize(EvolutionSignal signal, String targetId, double confidence) {
            EvolutionDecision decision = new EvolutionDecision();
            decision.setSignal(signal);
            decision.setStrategy(EvolutionStrategy.OPTIMIZE);
            decision.setTargetSkillId(targetId);
            decision.setConfidence(confidence);
            return decision;
        }
        
        public static EvolutionDecision innovate(EvolutionSignal signal, String description, double confidence) {
            EvolutionDecision decision = new EvolutionDecision();
            decision.setSignal(signal);
            decision.setStrategy(EvolutionStrategy.INNOVATE);
            decision.getParameters().put("description", description);
            decision.setConfidence(confidence);
            return decision;
        }
        
        public boolean shouldExecute() {
            return strategy != null && strategy != EvolutionStrategy.SKIP && confidence >= 0.3;
        }
        
        public String getDecisionId() { return decisionId; }
        public void setDecisionId(String decisionId) { this.decisionId = decisionId; }
        
        public EvolutionSignal getSignal() { return signal; }
        public void setSignal(EvolutionSignal signal) { this.signal = signal; }
        
        public EvolutionStrategy getStrategy() { return strategy; }
        public void setStrategy(EvolutionStrategy strategy) { this.strategy = strategy; }
        
        public String getTargetSkillId() { return targetSkillId; }
        public void setTargetSkillId(String targetSkillId) { this.targetSkillId = targetSkillId; }
        
        public String getTargetToolId() { return targetToolId; }
        public void setTargetToolId(String targetToolId) { this.targetToolId = targetToolId; }
        
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
        
        public List<String> getReasons() { return reasons; }
        public void setReasons(List<String> reasons) { this.reasons = reasons; }
        
        public Map<String, Object> getParameters() { return parameters; }
        public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
        
        public BrainPersonality getPersonality() { return personality; }
        public void setPersonality(BrainPersonality personality) { this.personality = personality; }
        
        public CircuitBreakerReport getCircuitBreakerStatus() { return circuitBreakerStatus; }
        public void setCircuitBreakerStatus(CircuitBreakerReport circuitBreakerStatus) { this.circuitBreakerStatus = circuitBreakerStatus; }
        
        @Override
        public String toString() {
            return String.format("EvolutionDecision{id=%s, strategy=%s, confidence=%.2f, skill=%s}",
                decisionId, strategy, confidence, targetSkillId);
        }
    }
    
    enum EvolutionStrategy {
        SKIP,
        REPAIR,
        OPTIMIZE,
        INNOVATE,
        DEFER,
        ESCALATE
    }
    
    class EvolutionPriority {
        private int level;
        private String label;
        private double weight;
        
        public static final EvolutionPriority CRITICAL = new EvolutionPriority(1, "critical", 1.0);
        public static final EvolutionPriority HIGH = new EvolutionPriority(2, "high", 0.8);
        public static final EvolutionPriority MEDIUM = new EvolutionPriority(3, "medium", 0.6);
        public static final EvolutionPriority LOW = new EvolutionPriority(4, "low", 0.4);
        public static final EvolutionPriority DEFERRED = new EvolutionPriority(5, "deferred", 0.2);
        
        public EvolutionPriority(int level, String label, double weight) {
            this.level = level;
            this.label = label;
            this.weight = weight;
        }
        
        public int getLevel() { return level; }
        public String getLabel() { return label; }
        public double getWeight() { return weight; }
        
        public boolean isHigherThan(EvolutionPriority other) {
            return this.level < other.level;
        }
    }
    
    class EvolutionConstraints {
        private int maxRepairAttempts;
        private int maxInnovationsPerDay;
        private double minConfidenceThreshold;
        private boolean allowRiskyOperations;
        private boolean requireApproval;
        private List<String> bannedSkills;
        private List<String> requiredSkills;
        
        public EvolutionConstraints() {
            this.maxRepairAttempts = 3;
            this.maxInnovationsPerDay = 5;
            this.minConfidenceThreshold = 0.3;
            this.allowRiskyOperations = false;
            this.requireApproval = false;
            this.bannedSkills = new java.util.ArrayList<>();
            this.requiredSkills = new java.util.ArrayList<>();
        }
        
        public static EvolutionConstraints strict() {
            EvolutionConstraints c = new EvolutionConstraints();
            c.setMaxRepairAttempts(1);
            c.setMaxInnovationsPerDay(2);
            c.setMinConfidenceThreshold(0.7);
            c.setAllowRiskyOperations(false);
            c.setRequireApproval(true);
            return c;
        }
        
        public static EvolutionConstraints relaxed() {
            EvolutionConstraints c = new EvolutionConstraints();
            c.setMaxRepairAttempts(5);
            c.setMaxInnovationsPerDay(10);
            c.setMinConfidenceThreshold(0.2);
            c.setAllowRiskyOperations(true);
            c.setRequireApproval(false);
            return c;
        }
        
        public int getMaxRepairAttempts() { return maxRepairAttempts; }
        public void setMaxRepairAttempts(int maxRepairAttempts) { this.maxRepairAttempts = maxRepairAttempts; }
        
        public int getMaxInnovationsPerDay() { return maxInnovationsPerDay; }
        public void setMaxInnovationsPerDay(int maxInnovationsPerDay) { this.maxInnovationsPerDay = maxInnovationsPerDay; }
        
        public double getMinConfidenceThreshold() { return minConfidenceThreshold; }
        public void setMinConfidenceThreshold(double minConfidenceThreshold) { this.minConfidenceThreshold = minConfidenceThreshold; }
        
        public boolean isAllowRiskyOperations() { return allowRiskyOperations; }
        public void setAllowRiskyOperations(boolean allowRiskyOperations) { this.allowRiskyOperations = allowRiskyOperations; }
        
        public boolean isRequireApproval() { return requireApproval; }
        public void setRequireApproval(boolean requireApproval) { this.requireApproval = requireApproval; }
        
        public List<String> getBannedSkills() { return bannedSkills; }
        public void setBannedSkills(List<String> bannedSkills) { this.bannedSkills = bannedSkills; }
        
        public List<String> getRequiredSkills() { return requiredSkills; }
        public void setRequiredSkills(List<String> requiredSkills) { this.requiredSkills = requiredSkills; }
    }
}
