package com.livingagent.core.evolution.personality;

import java.time.Instant;

public class PersonalityStats {
    
    private int successCount;
    private int failCount;
    private double avgScore;
    private int sampleCount;
    private Instant updatedAt;
    
    public PersonalityStats() {
        this.successCount = 0;
        this.failCount = 0;
        this.avgScore = 0.5;
        this.sampleCount = 0;
        this.updatedAt = Instant.now();
    }
    
    public void recordOutcome(boolean success, double score) {
        if (success) {
            successCount++;
        } else {
            failCount++;
        }
        
        sampleCount++;
        avgScore = avgScore + (score - avgScore) / sampleCount;
        updatedAt = Instant.now();
    }
    
    public double getSuccessRate() {
        int total = successCount + failCount;
        if (total == 0) return 0.5;
        return (double) successCount / total;
    }
    
    public double getLaplaceSmoothedRate() {
        return (successCount + 1.0) / (successCount + failCount + 2.0);
    }
    
    public double getOverallScore() {
        double rate = getLaplaceSmoothedRate();
        double sampleWeight = Math.min(1.0, sampleCount / 8.0);
        return rate * 0.75 + avgScore * 0.25 * sampleWeight;
    }
    
    public int getTotalCount() {
        return successCount + failCount;
    }
    
    public int getSuccessCount() { return successCount; }
    public void setSuccessCount(int successCount) { this.successCount = successCount; }
    
    public int getFailCount() { return failCount; }
    public void setFailCount(int failCount) { this.failCount = failCount; }
    
    public double getAvgScore() { return avgScore; }
    public void setAvgScore(double avgScore) { this.avgScore = avgScore; }
    
    public int getSampleCount() { return sampleCount; }
    public void setSampleCount(int sampleCount) { this.sampleCount = sampleCount; }
    
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
