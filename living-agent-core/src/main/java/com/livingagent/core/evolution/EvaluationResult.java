package com.livingagent.core.evolution;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EvaluationResult {
    
    private String capabilityId;
    private String capabilityName;
    private double score;
    private EvaluationLevel level;
    private List<String> strengths;
    private List<String> weaknesses;
    private Map<String, Object> metrics;
    private Instant evaluatedAt;
    private String evaluator;
    
    public enum EvaluationLevel {
        EXCELLENT(5),
        GOOD(4),
        SATISFACTORY(3),
        NEEDS_IMPROVEMENT(2),
        CRITICAL(1);
        
        private final int value;
        
        EvaluationLevel(int value) {
            this.value = value;
        }
        
        public int getValue() {
            return value;
        }
        
        public static EvaluationLevel fromScore(double score) {
            if (score >= 0.9) return EXCELLENT;
            if (score >= 0.75) return GOOD;
            if (score >= 0.6) return SATISFACTORY;
            if (score >= 0.4) return NEEDS_IMPROVEMENT;
            return CRITICAL;
        }
    }
    
    public EvaluationResult() {
        this.strengths = new ArrayList<>();
        this.weaknesses = new ArrayList<>();
        this.metrics = new HashMap<>();
        this.evaluatedAt = Instant.now();
    }
    
    public EvaluationResult(String capabilityName, double score) {
        this();
        this.capabilityName = capabilityName;
        this.score = score;
        this.level = EvaluationLevel.fromScore(score);
    }
    
    public void addStrength(String strength) {
        this.strengths.add(strength);
    }
    
    public void addWeakness(String weakness) {
        this.weaknesses.add(weakness);
    }
    
    public void addMetric(String key, Object value) {
        this.metrics.put(key, value);
    }
    
    public String getCapabilityId() { return capabilityId; }
    public void setCapabilityId(String capabilityId) { this.capabilityId = capabilityId; }
    
    public String getCapabilityName() { return capabilityName; }
    public void setCapabilityName(String capabilityName) { this.capabilityName = capabilityName; }
    
    public double getScore() { return score; }
    public void setScore(double score) { 
        this.score = score; 
        this.level = EvaluationLevel.fromScore(score);
    }
    
    public EvaluationLevel getLevel() { return level; }
    public void setLevel(EvaluationLevel level) { this.level = level; }
    
    public List<String> getStrengths() { return strengths; }
    public void setStrengths(List<String> strengths) { this.strengths = strengths; }
    
    public List<String> getWeaknesses() { return weaknesses; }
    public void setWeaknesses(List<String> weaknesses) { this.weaknesses = weaknesses; }
    
    public Map<String, Object> getMetrics() { return metrics; }
    public void setMetrics(Map<String, Object> metrics) { this.metrics = metrics; }
    
    public Instant getEvaluatedAt() { return evaluatedAt; }
    public void setEvaluatedAt(Instant evaluatedAt) { this.evaluatedAt = evaluatedAt; }
    
    public String getEvaluator() { return evaluator; }
    public void setEvaluator(String evaluator) { this.evaluator = evaluator; }
    
    @Override
    public String toString() {
        return "EvaluationResult{capability='" + capabilityName + "', score=" + score + 
               ", level=" + level + "}";
    }
}
