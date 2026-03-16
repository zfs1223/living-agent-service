package com.livingagent.core.evolution.personality;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BrainPersonality {
    
    private String brainDomain;
    private double rigor;
    private double creativity;
    private double verbosity;
    private double riskTolerance;
    private double obedience;
    private Instant updatedAt;
    private List<PersonalityMutation> recentMutations;
    private Map<String, PersonalityStats> stats;
    
    public static final Map<String, BrainPersonality> DEFAULT_PERSONALITIES = new HashMap<>();
    
    static {
        DEFAULT_PERSONALITIES.put("TechBrain", new BrainPersonality("TechBrain", 0.8, 0.6, 0.3, 0.5, 0.7));
        DEFAULT_PERSONALITIES.put("AdminBrain", new BrainPersonality("AdminBrain", 0.7, 0.4, 0.4, 0.3, 0.9));
        DEFAULT_PERSONALITIES.put("SalesBrain", new BrainPersonality("SalesBrain", 0.5, 0.7, 0.5, 0.6, 0.6));
        DEFAULT_PERSONALITIES.put("HrBrain", new BrainPersonality("HrBrain", 0.6, 0.5, 0.4, 0.4, 0.8));
        DEFAULT_PERSONALITIES.put("FinanceBrain", new BrainPersonality("FinanceBrain", 0.9, 0.3, 0.3, 0.2, 0.95));
        DEFAULT_PERSONALITIES.put("CsBrain", new BrainPersonality("CsBrain", 0.6, 0.5, 0.6, 0.4, 0.7));
        DEFAULT_PERSONALITIES.put("LegalBrain", new BrainPersonality("LegalBrain", 0.95, 0.2, 0.3, 0.1, 0.98));
        DEFAULT_PERSONALITIES.put("OpsBrain", new BrainPersonality("OpsBrain", 0.7, 0.5, 0.4, 0.5, 0.7));
        DEFAULT_PERSONALITIES.put("MainBrain", new BrainPersonality("MainBrain", 0.7, 0.5, 0.3, 0.4, 0.85));
    }
    
    public BrainPersonality() {
        this.updatedAt = Instant.now();
        this.recentMutations = new ArrayList<>();
        this.stats = new HashMap<>();
    }
    
    public BrainPersonality(String brainDomain, double rigor, double creativity, 
                           double verbosity, double riskTolerance, double obedience) {
        this();
        this.brainDomain = brainDomain;
        this.rigor = clamp01(rigor);
        this.creativity = clamp01(creativity);
        this.verbosity = clamp01(verbosity);
        this.riskTolerance = clamp01(riskTolerance);
        this.obedience = clamp01(obedience);
    }
    
    public static BrainPersonality getDefaultForBrain(String brainDomain) {
        return DEFAULT_PERSONALITIES.getOrDefault(brainDomain, 
            new BrainPersonality(brainDomain, 0.7, 0.5, 0.3, 0.4, 0.85));
    }
    
    private static double clamp01(double value) {
        if (!Double.isFinite(value)) return 0.5;
        return Math.max(0, Math.min(1, value));
    }
    
    public void applyMutation(PersonalityMutation mutation) {
        if (mutation == null) return;
        
        switch (mutation.getParam()) {
            case "rigor":
                this.rigor = clamp01(this.rigor + mutation.getDelta());
                break;
            case "creativity":
                this.creativity = clamp01(this.creativity + mutation.getDelta());
                break;
            case "verbosity":
                this.verbosity = clamp01(this.verbosity + mutation.getDelta());
                break;
            case "riskTolerance":
                this.riskTolerance = clamp01(this.riskTolerance + mutation.getDelta());
                break;
            case "obedience":
                this.obedience = clamp01(this.obedience + mutation.getDelta());
                break;
        }
        
        this.recentMutations.add(mutation);
        if (this.recentMutations.size() > 20) {
            this.recentMutations.remove(0);
        }
        this.updatedAt = Instant.now();
    }
    
    public String toKey() {
        return String.format("rigor=%.1f|creativity=%.1f|verbosity=%.1f|riskTolerance=%.1f|obedience=%.1f",
            rigor, creativity, verbosity, riskTolerance, obedience);
    }
    
    public boolean isHighRigor() {
        return rigor >= 0.8;
    }
    
    public boolean isHighCreativity() {
        return creativity >= 0.7;
    }
    
    public boolean isLowRisk() {
        return riskTolerance <= 0.3;
    }
    
    public boolean shouldForceInnovation() {
        return creativity >= 0.7 && riskTolerance >= 0.5;
    }
    
    public String getBrainDomain() { return brainDomain; }
    public void setBrainDomain(String brainDomain) { this.brainDomain = brainDomain; }
    
    public double getRigor() { return rigor; }
    public void setRigor(double rigor) { this.rigor = clamp01(rigor); }
    
    public double getCreativity() { return creativity; }
    public void setCreativity(double creativity) { this.creativity = clamp01(creativity); }
    
    public double getVerbosity() { return verbosity; }
    public void setVerbosity(double verbosity) { this.verbosity = clamp01(verbosity); }
    
    public double getRiskTolerance() { return riskTolerance; }
    public void setRiskTolerance(double riskTolerance) { this.riskTolerance = clamp01(riskTolerance); }
    
    public double getObedience() { return obedience; }
    public void setObedience(double obedience) { this.obedience = clamp01(obedience); }
    
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    
    public List<PersonalityMutation> getRecentMutations() { return recentMutations; }
    
    public Map<String, PersonalityStats> getStats() { return stats; }
    
    @Override
    public String toString() {
        return String.format("BrainPersonality{brain=%s, rigor=%.2f, creativity=%.2f, risk=%.2f}",
            brainDomain, rigor, creativity, riskTolerance);
    }
}
