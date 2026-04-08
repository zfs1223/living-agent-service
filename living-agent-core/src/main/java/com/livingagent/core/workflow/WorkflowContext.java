package com.livingagent.core.workflow;

import com.livingagent.core.project.ProjectPhase;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class WorkflowContext {

    private final String projectId;
    private final String projectName;
    private final ProjectPhase currentPhase;
    private final Map<String, Object> projectMetadata;
    private final WorkflowOrchestrator orchestrator;
    private final Map<String, Object> contextData;
    private final Map<String, String> errors;
    private final Instant createdAt;

    public WorkflowContext(String projectId, String projectName, ProjectPhase currentPhase,
                           Map<String, Object> projectMetadata, WorkflowOrchestrator orchestrator) {
        this.projectId = projectId;
        this.projectName = projectName;
        this.currentPhase = currentPhase;
        this.projectMetadata = projectMetadata != null ? projectMetadata : new HashMap<>();
        this.orchestrator = orchestrator;
        this.contextData = new HashMap<>();
        this.errors = new HashMap<>();
        this.createdAt = Instant.now();
    }

    public String getProjectId() { return projectId; }
    public String getProjectName() { return projectName; }
    public ProjectPhase getCurrentPhase() { return currentPhase; }
    public Map<String, Object> getProjectMetadata() { return projectMetadata; }
    public Map<String, Object> getContextData() { return contextData; }
    public Instant getCreatedAt() { return createdAt; }

    public void setData(String key, Object value) {
        contextData.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getData(String key) {
        return (T) contextData.get(key);
    }

    public void addError(String phase, String error) {
        errors.put(phase, error);
    }

    public Map<String, String> getErrors() { return errors; }

    public boolean hasErrors() { return !errors.isEmpty(); }

    public void completePhase(Map<String, Object> result) {
        orchestrator.onPhaseComplete(projectId, currentPhase, result);
    }

    public void requestAdvance() {
        orchestrator.advancePhase(projectId);
    }
}
