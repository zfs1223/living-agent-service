package com.livingagent.core.workflow;

import com.livingagent.core.project.ProjectPhase;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class WorkflowExecution {

    private final String projectId;
    private ProjectPhase currentPhase;
    private WorkflowState state;
    private final Instant startedAt;
    private Instant completedAt;
    private final Map<String, Map<String, Object>> phaseResults;
    private final Map<String, Object> metadata;

    public WorkflowExecution(String projectId, ProjectPhase initialPhase) {
        this.projectId = projectId;
        this.currentPhase = initialPhase;
        this.state = WorkflowState.RUNNING;
        this.startedAt = Instant.now();
        this.phaseResults = new HashMap<>();
        this.metadata = new HashMap<>();
    }

    public String getProjectId() { return projectId; }
    public ProjectPhase getCurrentPhase() { return currentPhase; }
    public WorkflowState getState() { return state; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Map<String, Map<String, Object>> getPhaseResults() { return phaseResults; }
    public Map<String, Object> getMetadata() { return metadata; }

    public void setCurrentPhase(ProjectPhase phase) {
        this.currentPhase = phase;
    }

    public void addPhaseResult(String phaseCode, Map<String, Object> result) {
        phaseResults.put(phaseCode, result);
    }

    public Map<String, Object> getPhaseResult(String phaseCode) {
        return phaseResults.get(phaseCode);
    }

    public void pause() {
        this.state = WorkflowState.PAUSED;
    }

    public void resume() {
        this.state = WorkflowState.RUNNING;
    }

    public void complete() {
        this.state = WorkflowState.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void cancel() {
        this.state = WorkflowState.CANCELLED;
        this.completedAt = Instant.now();
    }

    public boolean isRunning() { return state == WorkflowState.RUNNING; }
    public boolean isPaused() { return state == WorkflowState.PAUSED; }
    public boolean isCompleted() { return state == WorkflowState.COMPLETED; }
    public boolean isCancelled() { return state == WorkflowState.CANCELLED; }

    public enum WorkflowState {
        RUNNING,
        PAUSED,
        COMPLETED,
        CANCELLED
    }
}
