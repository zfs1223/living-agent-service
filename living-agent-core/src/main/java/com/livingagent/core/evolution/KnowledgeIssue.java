package com.livingagent.core.evolution;

import java.time.Instant;

public class KnowledgeIssue {
    
    private String issueId;
    private String type;
    private String description;
    private String severity;
    private String suggestion;
    private String knowledgeId;
    private long detectedAt;
    
    public KnowledgeIssue() {}
    
    public KnowledgeIssue(String type, String description, String severity, String suggestion) {
        this.issueId = java.util.UUID.randomUUID().toString();
        this.type = type;
        this.description = description;
        this.severity = severity;
        this.suggestion = suggestion;
        this.detectedAt = System.currentTimeMillis();
    }
    
    public KnowledgeIssue(String type, String description, String severity, String suggestion, String knowledgeId) {
        this(type, description, severity, suggestion);
        this.knowledgeId = knowledgeId;
    }
    
    public static KnowledgeIssue expired(String knowledgeId, Instant expiredAt) {
        KnowledgeIssue issue = new KnowledgeIssue();
        issue.issueId = java.util.UUID.randomUUID().toString();
        issue.type = "EXPIRED";
        issue.description = "Knowledge expired at " + expiredAt;
        issue.severity = "MEDIUM";
        issue.suggestion = "Consider updating or removing this knowledge";
        issue.knowledgeId = knowledgeId;
        issue.detectedAt = System.currentTimeMillis();
        return issue;
    }
    
    public static KnowledgeIssue lowConfidence(String knowledgeId, double confidence) {
        KnowledgeIssue issue = new KnowledgeIssue();
        issue.issueId = java.util.UUID.randomUUID().toString();
        issue.type = "LOW_CONFIDENCE";
        issue.description = "Knowledge has low confidence score: " + confidence;
        issue.severity = "HIGH";
        issue.suggestion = "Review and validate this knowledge";
        issue.knowledgeId = knowledgeId;
        issue.detectedAt = System.currentTimeMillis();
        return issue;
    }
    
    public String getIssueId() { return issueId; }
    public void setIssueId(String issueId) { this.issueId = issueId; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    
    public String getSuggestion() { return suggestion; }
    public void setSuggestion(String suggestion) { this.suggestion = suggestion; }
    
    public String getKnowledgeId() { return knowledgeId; }
    public void setKnowledgeId(String knowledgeId) { this.knowledgeId = knowledgeId; }
    
    public long getDetectedAt() { return detectedAt; }
    public void setDetectedAt(long detectedAt) { this.detectedAt = detectedAt; }
    
    @Override
    public String toString() {
        return "KnowledgeIssue{" +
                "type='" + type + '\'' +
                ", severity='" + severity + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}
