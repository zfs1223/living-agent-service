package com.livingagent.core.evolution;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ImprovementSuggestion {
    
    private String suggestionId;
    private String capabilityId;
    private String title;
    private String description;
    private ImprovementPriority priority;
    private ImprovementType type;
    private List<String> actionItems;
    private String expectedOutcome;
    private double estimatedEffort;
    private Instant createdAt;
    private ImprovementStatus status;
    
    public enum ImprovementPriority {
        CRITICAL,
        HIGH,
        MEDIUM,
        LOW
    }
    
    public enum ImprovementType {
        SKILL_ADDITION,
        SKILL_REFINEMENT,
        TOOL_CREATION,
        PROCESS_OPTIMIZATION,
        KNOWLEDGE_UPDATE,
        PERFORMANCE_TUNING
    }
    
    public enum ImprovementStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        REJECTED
    }
    
    public ImprovementSuggestion() {
        this.suggestionId = java.util.UUID.randomUUID().toString();
        this.actionItems = new ArrayList<>();
        this.createdAt = Instant.now();
        this.status = ImprovementStatus.PENDING;
    }
    
    public ImprovementSuggestion(String title, String description, ImprovementPriority priority) {
        this();
        this.title = title;
        this.description = description;
        this.priority = priority;
    }
    
    public void addActionItem(String action) {
        this.actionItems.add(action);
    }
    
    public String getSuggestionId() { return suggestionId; }
    public void setSuggestionId(String suggestionId) { this.suggestionId = suggestionId; }
    
    public String getCapabilityId() { return capabilityId; }
    public void setCapabilityId(String capabilityId) { this.capabilityId = capabilityId; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public ImprovementPriority getPriority() { return priority; }
    public void setPriority(ImprovementPriority priority) { this.priority = priority; }
    
    public ImprovementType getType() { return type; }
    public void setType(ImprovementType type) { this.type = type; }
    
    public List<String> getActionItems() { return actionItems; }
    public void setActionItems(List<String> actionItems) { this.actionItems = actionItems; }
    
    public String getExpectedOutcome() { return expectedOutcome; }
    public void setExpectedOutcome(String expectedOutcome) { this.expectedOutcome = expectedOutcome; }
    
    public double getEstimatedEffort() { return estimatedEffort; }
    public void setEstimatedEffort(double estimatedEffort) { this.estimatedEffort = estimatedEffort; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    public ImprovementStatus getStatus() { return status; }
    public void setStatus(ImprovementStatus status) { this.status = status; }
    
    @Override
    public String toString() {
        return "ImprovementSuggestion{title='" + title + "', priority=" + priority + 
               ", status=" + status + "}";
    }
}
