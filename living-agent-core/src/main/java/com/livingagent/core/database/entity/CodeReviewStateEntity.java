package com.livingagent.core.database.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnTransformer;

import java.time.Instant;

@Entity
@Table(name = "code_review_states")
public class CodeReviewStateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false, unique = true, length = 100)
    private String taskId;

    @Column(name = "project_id", length = 100)
    private String projectId;

    @Column(name = "execution_id", length = 500)
    private String executionId;

    @Column(name = "stage", nullable = false, length = 50)
    private String stage;

    @Column(name = "review_round")
    private Integer reviewRound;

    @Column(name = "developer_employee_code", length = 100)
    private String developerEmployeeCode;

    @Column(name = "reviewer_employee_code", length = 100)
    private String reviewerEmployeeCode;

    @Column(name = "worktree_path", length = 500)
    private String worktreePath;

    @Column(name = "diff_path", length = 500)
    private String diffPath;

    @Column(name = "review_report_path", length = 500)
    private String reviewReportPath;

    @Column(name = "final_summary_path", length = 500)
    private String finalSummaryPath;

    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "review_findings_json", columnDefinition = "JSONB")
    private String reviewFindingsJson;

    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "metadata_json", columnDefinition = "JSONB")
    private String metadataJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public CodeReviewStateEntity() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }

    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }

    public Integer getReviewRound() { return reviewRound; }
    public void setReviewRound(Integer reviewRound) { this.reviewRound = reviewRound; }

    public String getDeveloperEmployeeCode() { return developerEmployeeCode; }
    public void setDeveloperEmployeeCode(String developerEmployeeCode) { this.developerEmployeeCode = developerEmployeeCode; }

    public String getReviewerEmployeeCode() { return reviewerEmployeeCode; }
    public void setReviewerEmployeeCode(String reviewerEmployeeCode) { this.reviewerEmployeeCode = reviewerEmployeeCode; }

    public String getWorktreePath() { return worktreePath; }
    public void setWorktreePath(String worktreePath) { this.worktreePath = worktreePath; }

    public String getDiffPath() { return diffPath; }
    public void setDiffPath(String diffPath) { this.diffPath = diffPath; }

    public String getReviewReportPath() { return reviewReportPath; }
    public void setReviewReportPath(String reviewReportPath) { this.reviewReportPath = reviewReportPath; }

    public String getFinalSummaryPath() { return finalSummaryPath; }
    public void setFinalSummaryPath(String finalSummaryPath) { this.finalSummaryPath = finalSummaryPath; }

    public String getReviewFindingsJson() { return reviewFindingsJson; }
    public void setReviewFindingsJson(String reviewFindingsJson) { this.reviewFindingsJson = reviewFindingsJson; }

    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
