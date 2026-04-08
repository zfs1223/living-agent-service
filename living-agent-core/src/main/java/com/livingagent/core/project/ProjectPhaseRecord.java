package com.livingagent.core.project;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class ProjectPhaseRecord {
    
    private String phaseId;
    private ProjectPhase phase;
    private int order;
    private PhaseStatus status;
    private double progress;
    private Instant startedAt;
    private Instant completedAt;
    private Map<String, Object> metadata;
    
    public enum PhaseStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        SKIPPED
    }
    
    public ProjectPhaseRecord() {
        this.phaseId = "phase_" + System.currentTimeMillis();
        this.status = PhaseStatus.PENDING;
        this.progress = 0.0;
        this.metadata = new HashMap<>();
    }
    
    public ProjectPhaseRecord(ProjectPhase phase, int order) {
        this();
        this.phase = phase;
        this.order = order;
    }
    
    public void start() {
        this.status = PhaseStatus.IN_PROGRESS;
        this.startedAt = Instant.now();
    }
    
    public void complete() {
        this.status = PhaseStatus.COMPLETED;
        this.progress = 100.0;
        this.completedAt = Instant.now();
    }
    
    public void skip() {
        this.status = PhaseStatus.SKIPPED;
        this.completedAt = Instant.now();
    }
    
    public String getPhaseId() { return phaseId; }
    public void setPhaseId(String phaseId) { this.phaseId = phaseId; }
    
    public ProjectPhase getPhase() { return phase; }
    public void setPhase(ProjectPhase phase) { this.phase = phase; }
    
    public int getOrder() { return order; }
    public void setOrder(int order) { this.order = order; }
    
    public PhaseStatus getStatus() { return status; }
    public void setStatus(PhaseStatus status) { this.status = status; }
    
    public double getProgress() { return progress; }
    public void setProgress(double progress) { this.progress = Math.min(100.0, Math.max(0.0, progress)); }
    
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
