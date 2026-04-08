package com.livingagent.core.project;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Project {
    
    private String projectId;
    private String name;
    private String description;
    private ProjectStatus status;
    private ProjectPhase currentPhase;
    private String ownerDepartment;
    private String managerId;
    private Instant startDate;
    private Instant endDate;
    private double progress;
    private List<ProjectPhaseRecord> phases;
    private Map<String, Object> metadata;
    private Instant createdAt;
    private Instant updatedAt;
    
    public Project() {
        this.projectId = "proj_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        this.status = ProjectStatus.PLANNING;
        this.currentPhase = ProjectPhase.MARKET_ANALYSIS;
        this.progress = 0.0;
        this.phases = new ArrayList<>();
        this.metadata = new HashMap<>();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        initializePhases();
    }
    
    public Project(String name, String ownerDepartment) {
        this();
        this.name = name;
        this.ownerDepartment = ownerDepartment;
    }
    
    private void initializePhases() {
        int order = 0;
        for (ProjectPhase phase : ProjectPhase.values()) {
            phases.add(new ProjectPhaseRecord(phase, order++));
        }
    }
    
    public void start() {
        this.status = ProjectStatus.IN_PROGRESS;
        this.startDate = Instant.now();
        this.updatedAt = Instant.now();
    }
    
    public void complete() {
        this.status = ProjectStatus.COMPLETED;
        this.progress = 100.0;
        this.endDate = Instant.now();
        this.updatedAt = Instant.now();
        for (ProjectPhaseRecord phase : phases) {
            phase.complete();
        }
    }
    
    public void hold() {
        this.status = ProjectStatus.ON_HOLD;
        this.updatedAt = Instant.now();
    }
    
    public void cancel() {
        this.status = ProjectStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }
    
    public void advancePhase() {
        int currentIndex = currentPhase.ordinal();
        if (currentIndex < ProjectPhase.values().length - 1) {
            phases.get(currentIndex).complete();
            currentPhase = ProjectPhase.values()[currentIndex + 1];
            phases.get(currentIndex + 1).start();
            recalculateProgress();
            this.updatedAt = Instant.now();
        }
    }
    
    public void setPhaseProgress(ProjectPhase phase, double phaseProgress) {
        ProjectPhaseRecord record = phases.stream()
            .filter(p -> p.getPhase() == phase)
            .findFirst()
            .orElse(null);
        if (record != null) {
            record.setProgress(phaseProgress);
            recalculateProgress();
            this.updatedAt = Instant.now();
        }
    }
    
    private void recalculateProgress() {
        double totalProgress = 0.0;
        int phaseCount = phases.size();
        double phaseWeight = 100.0 / phaseCount;
        
        for (ProjectPhaseRecord phase : phases) {
            totalProgress += (phase.getProgress() / 100.0) * phaseWeight;
        }
        
        this.progress = totalProgress;
    }
    
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; this.updatedAt = Instant.now(); }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; this.updatedAt = Instant.now(); }
    
    public ProjectStatus getStatus() { return status; }
    public void setStatus(ProjectStatus status) { this.status = status; this.updatedAt = Instant.now(); }
    
    public ProjectPhase getCurrentPhase() { return currentPhase; }
    public void setCurrentPhase(ProjectPhase currentPhase) { this.currentPhase = currentPhase; this.updatedAt = Instant.now(); }
    
    public String getOwnerDepartment() { return ownerDepartment; }
    public void setOwnerDepartment(String ownerDepartment) { this.ownerDepartment = ownerDepartment; this.updatedAt = Instant.now(); }
    
    public String getManagerId() { return managerId; }
    public void setManagerId(String managerId) { this.managerId = managerId; this.updatedAt = Instant.now(); }
    
    public Instant getStartDate() { return startDate; }
    public void setStartDate(Instant startDate) { this.startDate = startDate; }
    
    public Instant getEndDate() { return endDate; }
    public void setEndDate(Instant endDate) { this.endDate = endDate; }
    
    public double getProgress() { return progress; }
    public void setProgress(double progress) { this.progress = progress; this.updatedAt = Instant.now(); }
    
    public List<ProjectPhaseRecord> getPhases() { return phases; }
    public void setPhases(List<ProjectPhaseRecord> phases) { this.phases = phases; }
    
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
