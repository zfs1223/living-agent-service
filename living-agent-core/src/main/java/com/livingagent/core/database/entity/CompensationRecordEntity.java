package com.livingagent.core.database.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "compensation_records", indexes = {
        @Index(name = "idx_compensation_record_employee", columnList = "employee_id"),
        @Index(name = "idx_compensation_record_type", columnList = "type"),
        @Index(name = "idx_compensation_record_created_at", columnList = "created_at")
})
public class CompensationRecordEntity {

    @jakarta.persistence.Id
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "record_id", unique = true, length = 64, nullable = false)
    private String recordId;

    @Column(name = "employee_id", length = 64, nullable = false)
    private String employeeId;

    @Column(name = "points")
    private Integer points;

    @Column(name = "type", length = 32)
    private String type;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "source_task_id", length = 64)
    private String sourceTaskId;

    @Column(name = "source_review_id", length = 64)
    private String sourceReviewId;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public CompensationRecordEntity() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getRecordId() { return recordId; }
    public void setRecordId(String recordId) { this.recordId = recordId; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public Integer getPoints() { return points; }
    public void setPoints(Integer points) { this.points = points; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getSourceTaskId() { return sourceTaskId; }
    public void setSourceTaskId(String sourceTaskId) { this.sourceTaskId = sourceTaskId; }

    public String getSourceReviewId() { return sourceReviewId; }
    public void setSourceReviewId(String sourceReviewId) { this.sourceReviewId = sourceReviewId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
