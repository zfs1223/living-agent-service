package com.livingagent.core.approval;

import java.time.Instant;

public class ApprovalRecord {
    
    private String recordId;
    private String stepId;
    private String approverId;
    private ApprovalDecision decision;
    private String comment;
    private Instant decidedAt;
    
    public enum ApprovalDecision {
        APPROVED,
        REJECTED,
        RETURNED
    }
    
    public ApprovalRecord() {
        this.decidedAt = Instant.now();
    }
    
    public ApprovalRecord(String recordId, String stepId, String approverId, 
                          ApprovalDecision decision, String comment) {
        this();
        this.recordId = recordId;
        this.stepId = stepId;
        this.approverId = approverId;
        this.decision = decision;
        this.comment = comment;
    }
    
    public String getRecordId() { return recordId; }
    public void setRecordId(String recordId) { this.recordId = recordId; }
    
    public String getStepId() { return stepId; }
    public void setStepId(String stepId) { this.stepId = stepId; }
    
    public String getApproverId() { return approverId; }
    public void setApproverId(String approverId) { this.approverId = approverId; }
    
    public ApprovalDecision getDecision() { return decision; }
    public void setDecision(ApprovalDecision decision) { this.decision = decision; }
    
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
}
