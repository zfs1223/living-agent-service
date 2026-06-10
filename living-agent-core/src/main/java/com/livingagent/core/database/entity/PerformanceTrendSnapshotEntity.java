package com.livingagent.core.database.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "performance_trend_snapshots", indexes = {
        @Index(name = "idx_performance_trend_employee", columnList = "employee_id"),
        @Index(name = "idx_performance_trend_date", columnList = "date"),
        @Index(name = "idx_performance_trend_period", columnList = "period")
})
public class PerformanceTrendSnapshotEntity {

    @jakarta.persistence.Id
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "employee_id", length = 64, nullable = false)
    private String employeeId;

    @Column(name = "date")
    private LocalDate date;

    @Column(name = "score")
    private Double score;

    @Column(name = "grade", length = 16)
    private String grade;

    @Column(name = "period", length = 32)
    private String period;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public PerformanceTrendSnapshotEntity() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }

    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }

    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
