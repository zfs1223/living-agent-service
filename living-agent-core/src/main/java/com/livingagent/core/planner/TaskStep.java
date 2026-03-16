package com.livingagent.core.planner;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TaskStep {
    
    private String stepId;
    private int stepIndex;
    private String description;
    private String action;
    private StepStatus status;
    private List<String> dependencies;
    private Map<String, Object> parameters;
    private Map<String, Object> result;
    private Instant startedAt;
    private Instant completedAt;
    private Duration estimatedDuration;
    private int retryCount;
    private int maxRetries;
    private String errorMessage;
    private String assignedNeuron;
    
    public enum StepStatus {
        PENDING,
        READY,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        SKIPPED
    }
    
    public TaskStep() {
        this.stepId = UUID.randomUUID().toString();
        this.status = StepStatus.PENDING;
        this.dependencies = new ArrayList<>();
        this.parameters = new HashMap<>();
        this.result = new HashMap<>();
        this.retryCount = 0;
        this.maxRetries = 3;
    }
    
    public TaskStep(String description, String action) {
        this();
        this.description = description;
        this.action = action;
    }
    
    public boolean canExecute(Map<String, TaskStep> completedSteps) {
        if (status == StepStatus.COMPLETED) {
            return false;
        }
        for (String depId : dependencies) {
            TaskStep dep = completedSteps.get(depId);
            if (dep == null || dep.getStatus() != StepStatus.COMPLETED) {
                return false;
            }
        }
        return true;
    }
    
    public void markStarted() {
        this.status = StepStatus.IN_PROGRESS;
        this.startedAt = Instant.now();
    }
    
    public void markCompleted(Map<String, Object> result) {
        this.status = StepStatus.COMPLETED;
        this.result = result;
        this.completedAt = Instant.now();
    }
    
    public void markFailed(String error) {
        this.status = StepStatus.FAILED;
        this.errorMessage = error;
        this.completedAt = Instant.now();
    }
    
    public boolean canRetry() {
        return retryCount < maxRetries;
    }
    
    public void incrementRetry() {
        retryCount++;
        status = StepStatus.PENDING;
    }
    
    public Duration getActualDuration() {
        if (startedAt != null && completedAt != null) {
            return Duration.between(startedAt, completedAt);
        }
        return null;
    }
    
    public String getStepId() { return stepId; }
    public void setStepId(String stepId) { this.stepId = stepId; }
    
    public int getStepIndex() { return stepIndex; }
    public void setStepIndex(int stepIndex) { this.stepIndex = stepIndex; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    
    public StepStatus getStatus() { return status; }
    public void setStatus(StepStatus status) { this.status = status; }
    
    public List<String> getDependencies() { return dependencies; }
    public void setDependencies(List<String> dependencies) { this.dependencies = dependencies; }
    public void addDependency(String stepId) { this.dependencies.add(stepId); }
    
    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
    
    public Map<String, Object> getResult() { return result; }
    public void setResult(Map<String, Object> result) { this.result = result; }
    
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    
    public Duration getEstimatedDuration() { return estimatedDuration; }
    public void setEstimatedDuration(Duration estimatedDuration) { this.estimatedDuration = estimatedDuration; }
    
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    public String getAssignedNeuron() { return assignedNeuron; }
    public void setAssignedNeuron(String assignedNeuron) { this.assignedNeuron = assignedNeuron; }
    
    @Override
    public String toString() {
        return "TaskStep{stepIndex=" + stepIndex + ", description='" + description + "', status=" + status + '}';
    }
}
