package com.livingagent.core.database.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "employee_execution_receipts", indexes = {
    @Index(name = "idx_receipt_receipt_id", columnList = "receipt_id"),
    @Index(name = "idx_receipt_execution_id", columnList = "execution_id"),
    @Index(name = "idx_receipt_dispatch_id", columnList = "dispatch_id"),
    @Index(name = "idx_receipt_employee_code", columnList = "employee_code"),
    @Index(name = "idx_receipt_status", columnList = "status"),
    @Index(name = "idx_receipt_created_at", columnList = "created_at")
})
public class EmployeeExecutionReceiptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "receipt_id", nullable = false, unique = true, length = 100)
    private String receiptId;

    @Column(name = "execution_id", nullable = false, length = 500)
    private String executionId;

    @Column(name = "dispatch_id", length = 100)
    private String dispatchId;

    @Column(name = "assignment_id", length = 100)
    private String assignmentId;

    @Column(name = "employee_code", length = 100)
    private String employeeCode;

    @Column(name = "employee_neuron_id", length = 200)
    private String employeeNeuronId;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "received_at")
    private Instant receivedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", columnDefinition = "JSONB")
    private String metadataJson;

    @Column(name = "worktree_path", length = 500)
    private String worktreePath;

    @Column(name = "diff_path", length = 500)
    private String diffPath;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public EmployeeExecutionReceiptEntity() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getReceiptId() { return receiptId; }
    public void setReceiptId(String receiptId) { this.receiptId = receiptId; }

    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }

    public String getDispatchId() { return dispatchId; }
    public void setDispatchId(String dispatchId) { this.dispatchId = dispatchId; }

    public String getAssignmentId() { return assignmentId; }
    public void setAssignmentId(String assignmentId) { this.assignmentId = assignmentId; }

    public String getEmployeeCode() { return employeeCode; }
    public void setEmployeeCode(String employeeCode) { this.employeeCode = employeeCode; }

    public String getEmployeeNeuronId() { return employeeNeuronId; }
    public void setEmployeeNeuronId(String employeeNeuronId) { this.employeeNeuronId = employeeNeuronId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }

    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }

    public String getWorktreePath() { return worktreePath; }
    public void setWorktreePath(String worktreePath) { this.worktreePath = worktreePath; }

    public String getDiffPath() { return diffPath; }
    public void setDiffPath(String diffPath) { this.diffPath = diffPath; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
