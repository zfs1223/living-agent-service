package com.livingagent.core.database.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * 审批流程定义持久化实体。
 * 将原 ApprovalServiceImpl 中的内存 Map workflowStore 改为 DB 持久化，避免重启丢失。
 * steps 字段（List<ApprovalStep>）序列化为 JSON 存储，避免引入额外子表。
 */
@Entity
@Table(name = "approval_workflows", indexes = {
    @Index(name = "idx_approval_workflow_id", columnList = "workflow_id"),
    @Index(name = "idx_approval_workflow_enabled", columnList = "enabled")
})
public class ApprovalWorkflowEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_id", nullable = false, unique = true, length = 100)
    private String workflowId;

    @Column(name = "name", length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** List<ApprovalStep> 序列化为 JSON */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "steps_json", columnDefinition = "JSONB")
    private String stepsJson;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public ApprovalWorkflowEntity() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStepsJson() { return stepsJson; }
    public void setStepsJson(String stepsJson) { this.stepsJson = stepsJson; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
