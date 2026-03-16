package com.livingagent.core.evolution.circuitbreaker;

import java.time.Duration;
import java.time.Instant;

public class CircuitState {
    
    private final String brainDomain;
    private CircuitTripReason tripReason;
    private Instant trippedAt;
    private int consecutiveFailures;
    private int emptyCycleCount;
    private boolean forceInnovation;
    private boolean forceStrategyChange;
    
    public CircuitState(String brainDomain) {
        this.brainDomain = brainDomain;
        this.consecutiveFailures = 0;
        this.emptyCycleCount = 0;
        this.forceInnovation = false;
        this.forceStrategyChange = false;
    }
    
    public boolean isTripped() {
        return tripReason != null;
    }
    
    public Duration getTimeSinceTripped() {
        if (trippedAt == null) {
            return Duration.ZERO;
        }
        return Duration.between(trippedAt, Instant.now());
    }
    
    public void recordSuccess() {
        consecutiveFailures = 0;
        emptyCycleCount = 0;
        if (!isTripped()) {
            forceInnovation = false;
            forceStrategyChange = false;
        }
    }
    
    public void recordFailure() {
        consecutiveFailures++;
    }
    
    public void incrementEmptyCycles() {
        emptyCycleCount++;
    }
    
    public void reset() {
        tripReason = null;
        trippedAt = null;
        consecutiveFailures = 0;
        emptyCycleCount = 0;
        forceInnovation = false;
        forceStrategyChange = false;
    }
    
    public String getBrainDomain() { return brainDomain; }
    
    public CircuitTripReason getTripReason() { return tripReason; }
    public void setTripReason(CircuitTripReason tripReason) { this.tripReason = tripReason; }
    
    public Instant getTrippedAt() { return trippedAt; }
    public void setTrippedAt(Instant trippedAt) { this.trippedAt = trippedAt; }
    
    public int getConsecutiveFailures() { return consecutiveFailures; }
    
    public int getEmptyCycleCount() { return emptyCycleCount; }
    
    public boolean isForceInnovation() { return forceInnovation; }
    public void setForceInnovation(boolean forceInnovation) { this.forceInnovation = forceInnovation; }
    
    public boolean isForceStrategyChange() { return forceStrategyChange; }
    public void setForceStrategyChange(boolean forceStrategyChange) { this.forceStrategyChange = forceStrategyChange; }
}
