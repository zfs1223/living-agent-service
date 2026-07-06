package com.livingagent.core.database.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * 审批实例持久化实体。
 * 将原 ApprovalServiceImpl 中的内存 Map approvalStore 改为 DB 持久化，避免重启丢失。
 * records 字段（List<ApprovalRecord>）序列化为 JSON 存储，避免引入额外表。
 */
@Entity
@Table(name = "approval_instances", indexes = {
    @Index(name = "idx_approval_instance_id", columnList = "instance_id"),
    @Index(name = "idx_approval_submitter", columnList = "submitter_id"),
    @Index(name = "idx_approval_status", columnList = "status"),
    @Index(name = "idx_approval_workflow_id", columnList = "workflow_id"),
    @Index(name = "idx_approval_business", columnList = "business_type, business_id"),
    @Index(name = "idx_approval_created_at", columnList = "created_at")
})
public class ApprovalInstanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instance_id", nullable = false, unique = true, length = 100)
    private String instanceId;

    @Column(name = "workflow_id", length = 100)
    private String workflowId;

    @Column(name = "business_type", length = 64)
    private String businessType;

    @Column(name = "business_id", length = 200)
    private String businessId;

    @Column(name = "title", length = 500)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** PENDING / IN_PROGRESS / APPROVED / REJECTED / RETURNED / CANCELLED */
    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "current_step", nullable = false)
    private int currentStep;

    @Column(name = "submitter_id", length = 200)
    private String submitterId;

    /** List<ApprovalRecord> 序列化为 JSON，避免引入额外的子表 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "records_json", columnDefinition = "JSONB")
    private String recordsJson;

    @Column(name = "context_json", columnDefinition = "TEXT")
    private String contextJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public ApprovalInstanceEntity() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getCurrentStep() { return currentStep; }
    public void setCurrentStep(int currentStep) { this.currentStep = currentStep; }

    public String getSubmitterId() { return submitterId; }
    public void setSubmitterId(String submitterId) { this.submitterId = submitterId; }

    public String getRecordsJson() { return recordsJson; }
    public void setRecordsJson(String recordsJson) { this.recordsJson = recordsJson; }

    public String getContextJson() { return contextJson; }
    public void setContextJson(String contextJson) { this.contextJson = contextJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
