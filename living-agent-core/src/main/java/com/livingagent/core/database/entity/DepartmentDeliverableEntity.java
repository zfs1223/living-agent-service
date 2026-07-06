package com.livingagent.core.database.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "department_deliverables", indexes = {
    @Index(name = "idx_deliverable_department", columnList = "department"),
    @Index(name = "idx_deliverable_plan_id", columnList = "plan_id")
})
public class DepartmentDeliverableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deliverable_id", nullable = false, unique = true, length = 200)
    private String deliverableId;

    @Column(nullable = false, length = 50)
    private String department;

    @Column(name = "plan_id", length = 200)
    private String planId;

    @Column(columnDefinition = "TEXT")
    private String objective;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(name = "items_json", columnDefinition = "JSONB")
    private String itemsJson;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "issues_json", columnDefinition = "JSONB")
    private String issuesJson;

    @Column(name = "overall_quality_score")
    private Double overallQualityScore;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public DepartmentDeliverableEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDeliverableId() { return deliverableId; }
    public void setDeliverableId(String deliverableId) { this.deliverableId = deliverableId; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }
    public String getObjective() { return objective; }
    public void setObjective(String objective) { this.objective = objective; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getItemsJson() { return itemsJson; }
    public void setItemsJson(String itemsJson) { this.itemsJson = itemsJson; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getIssuesJson() { return issuesJson; }
    public void setIssuesJson(String issuesJson) { this.issuesJson = issuesJson; }
    public Double getOverallQualityScore() { return overallQualityScore; }
    public void setOverallQualityScore(Double overallQualityScore) { this.overallQualityScore = overallQualityScore; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(Instant deliveredAt) { this.deliveredAt = deliveredAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
