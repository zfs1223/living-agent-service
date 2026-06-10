package com.livingagent.core.database.entity;

import com.livingagent.core.operation.performance.PerformanceAssessment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "performance_assessments", indexes = {
        @Index(name = "idx_performance_assessment_id", columnList = "assessment_id"),
        @Index(name = "idx_performance_employee_id", columnList = "employee_id"),
        @Index(name = "idx_performance_period", columnList = "period_type"),
        @Index(name = "idx_performance_assessed_at", columnList = "assessed_at")
})
public class PerformanceAssessmentEntity {

    @jakarta.persistence.Id
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "assessment_id", unique = true, length = 64, nullable = false)
    private String assessmentId;

    @Column(name = "employee_id", length = 64, nullable = false)
    private String employeeId;

    @Column(name = "employee_name", length = 128)
    private String employeeName;

    @Column(name = "period_type", length = 32)
    private String periodType;

    @Column(name = "overall_score")
    private Double overallScore;

    @Column(name = "grade", length = 16)
    private String grade;

    @Column(name = "dimension_scores_json", columnDefinition = "TEXT")
    private String dimensionScoresJson;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "assessed_at")
    private Instant assessedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public PerformanceAssessmentEntity() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.dimensionScoresJson = "{}";
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getAssessmentId() { return assessmentId; }
    public void setAssessmentId(String assessmentId) { this.assessmentId = assessmentId; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

    public String getPeriodType() { return periodType; }
    public void setPeriodType(String periodType) { this.periodType = periodType; }

    public Double getOverallScore() { return overallScore; }
    public void setOverallScore(Double overallScore) { this.overallScore = overallScore; }

    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }

    public String getDimensionScoresJson() { return dimensionScoresJson; }
    public void setDimensionScoresJson(String dimensionScoresJson) { this.dimensionScoresJson = dimensionScoresJson; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public Instant getAssessedAt() { return assessedAt; }
    public void setAssessedAt(Instant assessedAt) { this.assessedAt = assessedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public static PerformanceAssessmentEntity fromDomain(PerformanceAssessment assessment) {
        PerformanceAssessmentEntity entity = new PerformanceAssessmentEntity();
        if (assessment == null) {
            return entity;
        }
        entity.setAssessmentId(assessment.getAssessmentId());
        entity.setEmployeeId(assessment.getEmployeeId());
        entity.setEmployeeName(assessment.getEmployeeName());
        entity.setPeriodType(assessment.getPeriod() != null ? assessment.getPeriod().name() : null);
        entity.setOverallScore(assessment.getOverallScore());
        entity.setGrade(assessment.getGrade());
        entity.setDimensionScoresJson(assessment.getDimensionScores() != null ? assessment.getDimensionScores().toString() : "{}");
        entity.setComment(assessment.getComment());
        entity.setAssessedAt(assessment.getAssessedAt());
        return entity;
    }

    public PerformanceAssessment toDomain() {
        return new PerformanceAssessment() {
            @Override public String getAssessmentId() { return assessmentId; }
            @Override public String getEmployeeId() { return employeeId; }
            @Override public String getEmployeeName() { return employeeName; }
            @Override public AssessmentPeriod getPeriod() { return periodType != null ? AssessmentPeriod.valueOf(periodType) : AssessmentPeriod.MONTHLY; }
            @Override public double getOverallScore() { return overallScore != null ? overallScore : 0.0; }
            @Override public Map<String, Double> getDimensionScores() { return new HashMap<>(); }
            @Override public java.util.List<com.livingagent.core.operation.performance.PerformanceIndicator> getIndicators() { return java.util.List.of(); }
            @Override public String getGrade() { return grade; }
            @Override public String getComment() { return comment; }
            @Override public Instant getAssessedAt() { return assessedAt; }
        };
    }
}
