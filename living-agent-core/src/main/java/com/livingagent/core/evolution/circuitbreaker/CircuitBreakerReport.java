package com.livingagent.core.evolution.circuitbreaker;

import java.time.Instant;

public class CircuitBreakerReport {
    
    private final String brainDomain;
    private final boolean tripped;
    private final CircuitTripReason tripReason;
    private final int consecutiveRepairs;
    private final int consecutiveFailures;
    private final int emptyCycles;
    private final Instant trippedAt;
    
    public CircuitBreakerReport(String brainDomain, boolean tripped, CircuitTripReason tripReason,
                                int consecutiveRepairs, int consecutiveFailures, int emptyCycles, Instant trippedAt) {
        this.brainDomain = brainDomain;
        this.tripped = tripped;
        this.tripReason = tripReason;
        this.consecutiveRepairs = consecutiveRepairs;
        this.consecutiveFailures = consecutiveFailures;
        this.emptyCycles = emptyCycles;
        this.trippedAt = trippedAt;
    }
    
    public boolean needsAttention() {
        return tripped || consecutiveRepairs >= 2 || consecutiveFailures >= 3;
    }
    
    public String getRecommendation() {
        if (!tripped) {
            if (consecutiveRepairs >= 2) {
                return "接近修复循环阈值，建议准备创新方案";
            }
            if (consecutiveFailures >= 3) {
                return "连续失败增加，建议检查策略";
            }
            return "正常";
        }
        
        switch (tripReason) {
            case REPAIR_LOOP:
                return "检测到修复循环，已强制切换到创新模式";
            case FAILURE_STREAK:
                return "连续失败过多，需要更换进化策略";
            case EMPTY_CYCLE:
                return "进化产出为零，建议进入稳态维护模式";
            case SATURATION:
                return "进化空间饱和，建议降低进化频率";
            default:
                return "未知状态";
        }
    }
    
    public String getBrainDomain() { return brainDomain; }
    public boolean isTripped() { return tripped; }
    public CircuitTripReason getTripReason() { return tripReason; }
    public int getConsecutiveRepairs() { return consecutiveRepairs; }
    public int getConsecutiveFailures() { return consecutiveFailures; }
    public int getEmptyCycles() { return emptyCycles; }
    public Instant getTrippedAt() { return trippedAt; }
    
    @Override
    public String toString() {
        return String.format("CircuitBreakerReport{brain=%s, tripped=%s, reason=%s, repairs=%d, failures=%d}",
            brainDomain, tripped, tripReason, consecutiveRepairs, consecutiveFailures);
    }
}
