package com.livingagent.core.evolution.personality;

import java.time.Instant;

public class PersonalityMutation {
    
    private String mutationId;
    private String param;
    private double delta;
    private String reason;
    private Instant appliedAt;
    
    public PersonalityMutation() {
        this.mutationId = "pm_" + System.currentTimeMillis();
        this.appliedAt = Instant.now();
    }
    
    public PersonalityMutation(String param, double delta, String reason) {
        this();
        this.param = param;
        this.delta = Math.max(-0.2, Math.min(0.2, delta));
        this.reason = reason;
    }
    
    public PersonalityMutation(String param, double delta, String reason, long timestamp) {
        this.mutationId = "pm_" + timestamp;
        this.param = param;
        this.delta = Math.max(-0.2, Math.min(0.2, delta));
        this.reason = reason;
        this.appliedAt = Instant.ofEpochMilli(timestamp);
    }
    
    public static PersonalityMutation increaseRigor(String reason) {
        return new PersonalityMutation("rigor", 0.1, reason);
    }
    
    public static PersonalityMutation increaseCreativity(String reason) {
        return new PersonalityMutation("creativity", 0.1, reason);
    }
    
    public static PersonalityMutation decreaseRisk(String reason) {
        return new PersonalityMutation("riskTolerance", -0.1, reason);
    }
    
    public static PersonalityMutation increaseObedience(String reason) {
        return new PersonalityMutation("obedience", 0.1, reason);
    }
    
    public String getMutationId() { return mutationId; }
    public void setMutationId(String mutationId) { this.mutationId = mutationId; }
    
    public String getParam() { return param; }
    public void setParam(String param) { this.param = param; }
    
    public double getDelta() { return delta; }
    public void setDelta(double delta) { this.delta = delta; }
    
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    
    public Instant getAppliedAt() { return appliedAt; }
    public void setAppliedAt(Instant appliedAt) { this.appliedAt = appliedAt; }
    
    @Override
    public String toString() {
        return String.format("PersonalityMutation{param=%s, delta=%.2f, reason=%s}",
            param, delta, reason);
    }
}
