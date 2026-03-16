package com.livingagent.core.diagnosis;

import java.time.Instant;

public class HealthIssue {
    
    public enum Severity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
    
    public enum IssueType {
        PERFORMANCE,
        CONNECTIVITY,
        RESOURCE,
        CONFIGURATION,
        SECURITY,
        LOGIC
    }
    
    private String issueId;
    private String componentName;
    private String title;
    private String description;
    private Severity severity;
    private IssueType type;
    private Instant detectedAt;
    private Instant resolvedAt;
    private boolean resolved;
    private String resolution;
    private String suggestedAction;
    
    public HealthIssue() {
        this.issueId = java.util.UUID.randomUUID().toString();
        this.detectedAt = Instant.now();
        this.resolved = false;
    }
    
    public HealthIssue(String componentName, String title, Severity severity) {
        this();
        this.componentName = componentName;
        this.title = title;
        this.severity = severity;
    }
    
    public void resolve(String resolution) {
        this.resolved = true;
        this.resolution = resolution;
        this.resolvedAt = Instant.now();
    }
    
    public String getIssueId() { return issueId; }
    public void setIssueId(String issueId) { this.issueId = issueId; }
    
    public String getComponentName() { return componentName; }
    public void setComponentName(String componentName) { this.componentName = componentName; }
    
    public String getComponent() { return componentName; }
    
    public String getMessage() { return description != null ? description : title; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }
    
    public IssueType getType() { return type; }
    public void setType(IssueType type) { this.type = type; }
    
    public Instant getDetectedAt() { return detectedAt; }
    public void setDetectedAt(Instant detectedAt) { this.detectedAt = detectedAt; }
    
    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
    
    public boolean isResolved() { return resolved; }
    public void setResolved(boolean resolved) { this.resolved = resolved; }
    
    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
    
    public String getSuggestedAction() { return suggestedAction; }
    public void setSuggestedAction(String suggestedAction) { this.suggestedAction = suggestedAction; }
    
    @Override
    public String toString() {
        return "HealthIssue{title='" + title + "', severity=" + severity + ", resolved=" + resolved + "}";
    }
}
