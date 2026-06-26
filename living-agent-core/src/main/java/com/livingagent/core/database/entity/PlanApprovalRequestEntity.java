package com.livingagent.core.database.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 计划审批请求持久化实体 - P2-3 修复。
 * 对应 InMemoryPlanApprovalService 的 requests 内存 Map。
 */
@Entity
@Table(name = "plan_approval_requests", indexes = {
    @Index(name = "idx_plan_approval_status", columnList = "status"),
    @Index(name = "idx_plan_approval_submitter", columnList = "submitter_neuron_id"),
    @Index(name = "idx_plan_approval_submitted_at", columnList = "submitted_at")
})
public class PlanApprovalRequestEntity {

    @Id
    @Column(name = "request_id", length = 50)
    private String requestId;

    @Column(name = "submitter_neuron_id", length = 255)
    private String submitterNeuronId;

    @Column(name = "plan_text", columnDefinition = "text")
    private String planText;

    @Column(name = "plan_type", length = 32)
    @Enumerated(EnumType.STRING)
    private PlanType planType;

    @Column(name = "status", length = 16)
    @Enumerated(EnumType.STRING)
    private ApprovalStatus status;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "deadline_ms")
    private Long deadlineMs;

    public enum PlanType {
        CODE_CHANGE, ARCHITECTURE_DECISION, DEPLOYMENT_PLAN, DATA_MIGRATION, SECURITY_CHANGE, GENERAL
    }

    public enum ApprovalStatus {
        PENDING, APPROVED, REJECTED, EXPIRED
    }

    public PlanApprovalRequestEntity() {
        this.status = ApprovalStatus.PENDING;
        this.submittedAt = Instant.now();
    }

    // === Getters & Setters ===

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getSubmitterNeuronId() { return submitterNeuronId; }
    public void setSubmitterNeuronId(String submitterNeuronId) { this.submitterNeuronId = submitterNeuronId; }

    public String getPlanText() { return planText; }
    public void setPlanText(String planText) { this.planText = planText; }

    public PlanType getPlanType() { return planType; }
    public void setPlanType(PlanType planType) { this.planType = planType; }

    public ApprovalStatus getStatus() { return status; }
    public void setStatus(ApprovalStatus status) { this.status = status; }

    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }

    public Long getDeadlineMs() { return deadlineMs; }
    public void setDeadlineMs(Long deadlineMs) { this.deadlineMs = deadlineMs; }

    public boolean isExpired() {
        return status == ApprovalStatus.PENDING
            && Instant.now().toEpochMilli() > submittedAt.toEpochMilli() + deadlineMs;
    }

    @Override
    public String toString() {
        return String.format("PlanApprovalRequestEntity{id=%s, submitter=%s, type=%s, status=%s}",
            requestId, submitterNeuronId, planType, status);
    }
}