package com.livingagent.core.database.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 16.6: 审批专用审计日志。
 * 记录审批全生命周期操作（创建/通过/拒绝/退回/取消），支持审计追溯。
 */
@Entity
@Table(name = "approval_audit_log", indexes = {
    @Index(name = "idx_approval_audit_instance", columnList = "instance_id"),
    @Index(name = "idx_approval_audit_operator", columnList = "operator_id"),
    @Index(name = "idx_approval_audit_action", columnList = "action"),
    @Index(name = "idx_approval_audit_created", columnList = "created_at")
})
public class ApprovalAuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instance_id", nullable = false, length = 100)
    private String instanceId;

    @Column(name = "workflow_id", length = 100)
    private String workflowId;

    @Column(name = "business_type", length = 64)
    private String businessType;

    @Column(name = "business_id", length = 200)
    private String businessId;

    /** CREATE / APPROVE / REJECT / RETURN / CANCEL */
    @Column(name = "action", nullable = false, length = 20)
    private String action;

    @Column(name = "step_id", length = 100)
    private String stepId;

    @Column(name = "operator_id", length = 200)
    private String operatorId;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "result_status", length = 30)
    private String resultStatus;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public ApprovalAuditLogEntity() {
        this.createdAt = Instant.now();
    }

    public static ApprovalAuditLogEntity of(String instanceId, String workflowId,
            String businessType, String businessId, String action,
            String stepId, String operatorId, String comment, String resultStatus) {
        ApprovalAuditLogEntity e = new ApprovalAuditLogEntity();
        e.instanceId = instanceId;
        e.workflowId = workflowId;
        e.businessType = businessType;
        e.businessId = businessId;
        e.action = action;
        e.stepId = stepId;
        e.operatorId = operatorId;
        e.comment = comment;
        e.resultStatus = resultStatus;
        return e;
    }

    public Long getId() { return id; }
    public String getInstanceId() { return instanceId; }
    public String getWorkflowId() { return workflowId; }
    public String getBusinessType() { return businessType; }
    public String getBusinessId() { return businessId; }
    public String getAction() { return action; }
    public String getStepId() { return stepId; }
    public String getOperatorId() { return operatorId; }
    public String getComment() { return comment; }
    public String getResultStatus() { return resultStatus; }
    public Instant getCreatedAt() { return createdAt; }
}
