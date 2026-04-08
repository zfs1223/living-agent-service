package com.livingagent.core.approval;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ApprovalInstance {
    
    private String instanceId;
    private String workflowId;
    private String businessType;
    private String businessId;
    private String title;
    private String description;
    private ApprovalStatus status;
    private int currentStep;
    private String submitterId;
    private List<ApprovalRecord> records;
    private Map<String, Object> context;
    private Instant createdAt;
    private Instant completedAt;
    
    public enum ApprovalStatus {
        PENDING,
        IN_PROGRESS,
        APPROVED,
        REJECTED,
        RETURNED,
        CANCELLED
    }
    
    public ApprovalInstance() {
        this.instanceId = "appr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        this.status = ApprovalStatus.PENDING;
        this.currentStep = 0;
        this.records = new ArrayList<>();
        this.context = new HashMap<>();
        this.createdAt = Instant.now();
    }
    
    public ApprovalInstance(String workflowId, String businessType, String businessId) {
        this();
        this.workflowId = workflowId;
        this.businessType = businessType;
        this.businessId = businessId;
    }
    
    public void start() {
        this.status = ApprovalStatus.IN_PROGRESS;
    }
    
    public void approve(String stepId, String approverId, String comment) {
        ApprovalRecord record = new ApprovalRecord(
            "rec_" + System.currentTimeMillis(),
            stepId,
            approverId,
            ApprovalRecord.ApprovalDecision.APPROVED,
            comment
        );
        records.add(record);
        currentStep++;
    }
    
    public void reject(String stepId, String approverId, String comment) {
        ApprovalRecord record = new ApprovalRecord(
            "rec_" + System.currentTimeMillis(),
            stepId,
            approverId,
            ApprovalRecord.ApprovalDecision.REJECTED,
            comment
        );
        records.add(record);
        this.status = ApprovalStatus.REJECTED;
        this.completedAt = Instant.now();
    }
    
    public void returnToSubmitter(String stepId, String approverId, String comment) {
        ApprovalRecord record = new ApprovalRecord(
            "rec_" + System.currentTimeMillis(),
            stepId,
            approverId,
            ApprovalRecord.ApprovalDecision.RETURNED,
            comment
        );
        records.add(record);
        this.status = ApprovalStatus.RETURNED;
        this.completedAt = Instant.now();
    }
    
    public void complete() {
        this.status = ApprovalStatus.APPROVED;
        this.completedAt = Instant.now();
    }
    
    public void cancel() {
        this.status = ApprovalStatus.CANCELLED;
        this.completedAt = Instant.now();
    }
    
    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }
    
    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }
    
    public String getBusinessType() { return businessType; }
    public void setBusinessType(String businessType) { this.businessType = businessType; }
    
    public String getBusinessId() { return businessId; }
    public void setBusinessId(String businessId) { this.businessId = businessId; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public ApprovalStatus getStatus() { return status; }
    public void setStatus(ApprovalStatus status) { this.status = status; }
    
    public int getCurrentStep() { return currentStep; }
    public void setCurrentStep(int currentStep) { this.currentStep = currentStep; }
    
    public String getSubmitterId() { return submitterId; }
    public void setSubmitterId(String submitterId) { this.submitterId = submitterId; }
    
    public List<ApprovalRecord> getRecords() { return records; }
    public void setRecords(List<ApprovalRecord> records) { this.records = records; }
    
    public Map<String, Object> getContext() { return context; }
    public void setContext(Map<String, Object> context) { this.context = context; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
