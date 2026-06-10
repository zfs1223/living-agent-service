package com.livingagent.core.autonomy.impl;

import com.livingagent.core.autonomy.ArtifactRecord;
import com.livingagent.core.autonomy.ArtifactRecordService;
import com.livingagent.core.autonomy.CodeReviewWorkflowService;
import com.livingagent.core.autonomy.TaskMetadataKeys;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryCodeReviewWorkflowService implements CodeReviewWorkflowService {

    private final Map<String, ReviewState> byTaskId = new ConcurrentHashMap<>();
    private final Map<String, ReviewState> byExecutionId = new ConcurrentHashMap<>();
    private final ArtifactRecordService artifactRecordService;

    public InMemoryCodeReviewWorkflowService(ArtifactRecordService artifactRecordService) {
        this.artifactRecordService = artifactRecordService;
    }

    @Override
    public ReviewState createOrUpdate(String taskId, String projectId, String executionId, ReviewStage stage,
                                      String developerEmployeeCode, String reviewerEmployeeCode,
                                      String worktreePath, String diffPath, String reviewReportPath,
                                      String finalSummaryPath, List<String> reviewFindings,
                                      Map<String, Object> metadata) {
        ReviewState state = new ReviewState(
            taskId,
            projectId,
            executionId,
            stage,
            metadata != null && metadata.containsKey(TaskMetadataKeys.REVIEW_ROUND) ? ((Number) metadata.get(TaskMetadataKeys.REVIEW_ROUND)).intValue() : 0,
            developerEmployeeCode,
            reviewerEmployeeCode,
            worktreePath,
            diffPath,
            reviewReportPath,
            finalSummaryPath,
            reviewFindings != null ? List.copyOf(reviewFindings) : List.of(),
            metadata != null ? new LinkedHashMap<>(metadata) : Map.of()
        );
        byTaskId.put(taskId, state);
        if (executionId != null && !executionId.isBlank()) {
            byExecutionId.put(executionId, state);
        }
        return state;
    }

    @Override
    public Optional<ReviewState> getByTaskId(String taskId) {
        return Optional.ofNullable(byTaskId.get(taskId));
    }

    @Override
    public Optional<ReviewState> getByExecutionId(String executionId) {
        return Optional.ofNullable(byExecutionId.get(executionId));
    }

    @Override
    public ReviewState advanceStage(String taskId, ReviewStage nextStage, Map<String, Object> metadata) {
        ReviewState current = byTaskId.get(taskId);
        if (current == null) {
            throw new IllegalArgumentException("Review state not found for taskId=" + taskId);
        }
        ReviewState next = new ReviewState(
            current.taskId(), current.projectId(), current.executionId(), nextStage,
            current.reviewRound(), current.developerEmployeeCode(), current.reviewerEmployeeCode(),
            current.worktreePath(), current.diffPath(), current.reviewReportPath(), current.finalSummaryPath(),
            current.reviewFindings(), mergeMetadata(current.metadata(), metadata)
        );
        byTaskId.put(taskId, next);
        if (current.executionId() != null) {
            byExecutionId.put(current.executionId(), next);
        }
        return next;
    }

    @Override
    public ReviewState requestChanges(String taskId, List<String> findings, Map<String, Object> metadata) {
        ReviewState current = byTaskId.get(taskId);
        if (current == null) {
            throw new IllegalArgumentException("Review state not found for taskId=" + taskId);
        }
        Map<String, Object> merged = mergeMetadata(current.metadata(), metadata);
        merged.put(TaskMetadataKeys.REVIEW_ROUND, current.reviewRound() + 1);
        merged.put(TaskMetadataKeys.LAST_REVIEW_AT, Instant.now().toString());
        ReviewState next = new ReviewState(
            current.taskId(), current.projectId(), current.executionId(), ReviewStage.REVIEW_CHANGES_REQUESTED,
            current.reviewRound() + 1, current.developerEmployeeCode(), current.reviewerEmployeeCode(),
            current.worktreePath(), current.diffPath(), current.reviewReportPath(), current.finalSummaryPath(),
            findings != null ? List.copyOf(findings) : List.of(), merged
        );
        byTaskId.put(taskId, next);
        if (current.executionId() != null) {
            byExecutionId.put(current.executionId(), next);
        }
        return next;
    }

    @Override
    public ReviewState approve(String taskId, Map<String, Object> metadata) {
        ReviewState current = byTaskId.get(taskId);
        if (current == null) {
            throw new IllegalArgumentException("Review state not found for taskId=" + taskId);
        }
        ReviewState next = new ReviewState(
            current.taskId(), current.projectId(), current.executionId(), ReviewStage.REVIEW_APPROVED,
            current.reviewRound(), current.developerEmployeeCode(), current.reviewerEmployeeCode(),
            current.worktreePath(), current.diffPath(), current.reviewReportPath(), current.finalSummaryPath(),
            current.reviewFindings(), mergeMetadata(current.metadata(), metadata)
        );
        byTaskId.put(taskId, next);
        if (current.executionId() != null) {
            byExecutionId.put(current.executionId(), next);
        }
        return next;
    }

    @Override
    public ReviewState escalate(String taskId, String reason, Map<String, Object> metadata) {
        ReviewState current = byTaskId.get(taskId);
        if (current == null) {
            throw new IllegalArgumentException("Review state not found for taskId=" + taskId);
        }
        Map<String, Object> merged = mergeMetadata(current.metadata(), metadata);
        merged.put(TaskMetadataKeys.ESCALATION_REASON, reason);
        ReviewState next = new ReviewState(
            current.taskId(), current.projectId(), current.executionId(), ReviewStage.ESCALATED,
            current.reviewRound(), current.developerEmployeeCode(), current.reviewerEmployeeCode(),
            current.worktreePath(), current.diffPath(), current.reviewReportPath(), current.finalSummaryPath(),
            current.reviewFindings(), merged
        );
        byTaskId.put(taskId, next);
        if (current.executionId() != null) {
            byExecutionId.put(current.executionId(), next);
        }
        return next;
    }

    @Override
    public ArtifactRecord registerWorktreeArtifact(ArtifactRecord artifact) {
        return persist(artifact);
    }

    @Override
    public ArtifactRecord registerDiffArtifact(ArtifactRecord artifact) {
        return persist(artifact);
    }

    @Override
    public ArtifactRecord registerReviewReportArtifact(ArtifactRecord artifact) {
        return persist(artifact);
    }

    @Override
    public ArtifactRecord registerFinalSummaryArtifact(ArtifactRecord artifact) {
        return persist(artifact);
    }

    @Override
    public List<ArtifactRecord> getArtifactsByTaskId(String taskId) {
        return artifactRecordService != null ? artifactRecordService.getByTaskId(taskId) : List.of();
    }

    private ArtifactRecord persist(ArtifactRecord artifact) {
        return artifactRecordService != null ? artifactRecordService.recordArtifact(artifact) : artifact;
    }

    private Map<String, Object> mergeMetadata(Map<String, Object> base, Map<String, Object> extra) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (base != null) merged.putAll(base);
        if (extra != null) merged.putAll(extra);
        return merged;
    }
}
