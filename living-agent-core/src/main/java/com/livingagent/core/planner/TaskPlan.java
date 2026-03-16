package com.livingagent.core.planner;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TaskPlan {
    
    private String planId;
    private String goal;
    private PlanStatus status;
    private List<TaskStep> steps;
    private int currentStepIndex;
    private Map<String, Object> context;
    private Map<String, Object> results;
    private Instant createdAt;
    private Instant updatedAt;
    private double estimatedComplexity;
    private String assignedBrain;
    
    public enum PlanStatus {
        CREATED,
        IN_PROGRESS,
        PAUSED,
        COMPLETED,
        FAILED,
        CANCELLED
    }
    
    public TaskPlan() {
        this.planId = UUID.randomUUID().toString();
        this.status = PlanStatus.CREATED;
        this.steps = new ArrayList<>();
        this.currentStepIndex = 0;
        this.context = new HashMap<>();
        this.results = new HashMap<>();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
    
    public TaskPlan(String goal) {
        this();
        this.goal = goal;
    }
    
    public void addStep(TaskStep step) {
        step.setStepIndex(steps.size());
        steps.add(step);
        updatedAt = Instant.now();
    }
    
    public void addSteps(List<TaskStep> newSteps) {
        for (TaskStep step : newSteps) {
            addStep(step);
        }
    }
    
    public TaskStep getCurrentStep() {
        if (currentStepIndex < steps.size()) {
            return steps.get(currentStepIndex);
        }
        return null;
    }
    
    public TaskStep advanceStep() {
        if (currentStepIndex < steps.size() - 1) {
            currentStepIndex++;
            updatedAt = Instant.now();
            return steps.get(currentStepIndex);
        }
        return null;
    }
    
    public boolean hasNextStep() {
        return currentStepIndex < steps.size() - 1;
    }
    
    public double getProgress() {
        if (steps.isEmpty()) {
            return 0.0;
        }
        long completedSteps = steps.stream()
                .filter(s -> s.getStatus() == TaskStep.StepStatus.COMPLETED)
                .count();
        return (double) completedSteps / steps.size() * 100;
    }
    
    public void markCompleted() {
        this.status = PlanStatus.COMPLETED;
        this.updatedAt = Instant.now();
    }
    
    public void markFailed(String reason) {
        this.status = PlanStatus.FAILED;
        this.context.put("failureReason", reason);
        this.updatedAt = Instant.now();
    }
    
    public void addResult(String key, Object value) {
        results.put(key, value);
        updatedAt = Instant.now();
    }
    
    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }
    
    public String getGoal() { return goal; }
    public void setGoal(String goal) { this.goal = goal; }
    
    public PlanStatus getStatus() { return status; }
    public void setStatus(PlanStatus status) { this.status = status; }
    
    public List<TaskStep> getSteps() { return steps; }
    public void setSteps(List<TaskStep> steps) { this.steps = steps; }
    
    public int getCurrentStepIndex() { return currentStepIndex; }
    public void setCurrentStepIndex(int currentStepIndex) { this.currentStepIndex = currentStepIndex; }
    
    public Map<String, Object> getContext() { return context; }
    public void setContext(Map<String, Object> context) { this.context = context; }
    
    public Map<String, Object> getResults() { return results; }
    public void setResults(Map<String, Object> results) { this.results = results; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    
    public double getEstimatedComplexity() { return estimatedComplexity; }
    public void setEstimatedComplexity(double estimatedComplexity) { this.estimatedComplexity = estimatedComplexity; }
    
    public String getAssignedBrain() { return assignedBrain; }
    public void setAssignedBrain(String assignedBrain) { this.assignedBrain = assignedBrain; }
    
    @Override
    public String toString() {
        return "TaskPlan{planId='" + planId + "', goal='" + goal + "', status=" + status + 
               ", steps=" + steps.size() + ", progress=" + String.format("%.1f%%", getProgress()) + '}';
    }
}
