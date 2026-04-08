package com.livingagent.core.approval;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class ApprovalWorkflow {
    
    private String workflowId;
    private String name;
    private String description;
    private List<ApprovalStep> steps;
    private boolean enabled;
    private Instant createdAt;
    
    public ApprovalWorkflow() {
        this.enabled = true;
        this.createdAt = Instant.now();
    }
    
    public ApprovalWorkflow(String workflowId, String name) {
        this();
        this.workflowId = workflowId;
        this.name = name;
    }
    
    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public List<ApprovalStep> getSteps() { return steps; }
    public void setSteps(List<ApprovalStep> steps) { this.steps = steps; }
    
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
