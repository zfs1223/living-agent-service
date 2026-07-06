package com.livingagent.core.database.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "department_execution_results", indexes = {
    @Index(name = "idx_der_execution_id", columnList = "execution_id"),
    @Index(name = "idx_der_batch_id", columnList = "batch_id"),
    @Index(name = "idx_der_department", columnList = "department"),
    @Index(name = "idx_der_status", columnList = "status")
})
public class DepartmentExecutionResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "execution_id", nullable = false, length = 100)
    private String executionId;

    @Column(name = "batch_id", length = 100)
    private String batchId;

    @Column(name = "department", length = 64)
    private String department;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "dispatched_assignments", columnDefinition = "TEXT")
    private String dispatchedAssignments;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public DepartmentExecutionResultEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }

    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDispatchedAssignments() { return dispatchedAssignments; }
    public void setDispatchedAssignments(String dispatchedAssignments) { this.dispatchedAssignments = dispatchedAssignments; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
