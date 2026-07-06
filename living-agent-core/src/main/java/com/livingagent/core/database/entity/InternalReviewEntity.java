package com.livingagent.core.database.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "internal_reviews", indexes = {
    @Index(name = "idx_internal_review_todo_item_id", columnList = "todo_item_id"),
    @Index(name = "idx_internal_review_status", columnList = "status")
})
public class InternalReviewEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "review_id", nullable = false, unique = true, length = 100)
    private String reviewId;

    @Column(name = "todo_item_id", nullable = false, length = 200)
    private String todoItemId;

    @Column(name = "author_code", length = 100)
    private String authorCode;

    @Column(name = "reviewer_code", length = 100)
    private String reviewerCode;

    @Column(name = "execution_id", length = 500)
    private String executionId;

    @Column(name = "review_round")
    private int reviewRound;

    @Column(name = "max_rounds")
    private int maxRounds;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(length = 50)
    private String result;

    @Column(name = "quality_score")
    private Double qualityScore;

    @Column(name = "review_comment", columnDefinition = "TEXT")
    private String reviewComment;

    @Column(name = "revision_notes", columnDefinition = "TEXT")
    private String revisionNotes;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public InternalReviewEntity() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getReviewId() { return reviewId; }
    public void setReviewId(String reviewId) { this.reviewId = reviewId; }

    public String getTodoItemId() { return todoItemId; }
    public void setTodoItemId(String todoItemId) { this.todoItemId = todoItemId; }

    public String getAuthorCode() { return authorCode; }
    public void setAuthorCode(String authorCode) { this.authorCode = authorCode; }

    public String getReviewerCode() { return reviewerCode; }
    public void setReviewerCode(String reviewerCode) { this.reviewerCode = reviewerCode; }

    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }

    public int getReviewRound() { return reviewRound; }
    public void setReviewRound(int reviewRound) { this.reviewRound = reviewRound; }

    public int getMaxRounds() { return maxRounds; }
    public void setMaxRounds(int maxRounds) { this.maxRounds = maxRounds; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public Double getQualityScore() { return qualityScore; }
    public void setQualityScore(Double qualityScore) { this.qualityScore = qualityScore; }

    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }

    public String getRevisionNotes() { return revisionNotes; }
    public void setRevisionNotes(String revisionNotes) { this.revisionNotes = revisionNotes; }

    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }

    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
