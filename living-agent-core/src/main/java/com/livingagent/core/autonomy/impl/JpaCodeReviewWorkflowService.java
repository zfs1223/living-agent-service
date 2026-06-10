package com.livingagent.core.autonomy.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.autonomy.ArtifactRecord;
import com.livingagent.core.autonomy.ArtifactRecordService;
import com.livingagent.core.autonomy.CodeReviewWorkflowService;
import com.livingagent.core.autonomy.TaskMetadataKeys;
import com.livingagent.core.database.entity.CodeReviewStateEntity;
import com.livingagent.core.database.repository.CodeReviewStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JPA 持久化实现的代码审查工作流服务。
 * 审查状态存储在 PostgreSQL，重启不丢失。
 */
public class JpaCodeReviewWorkflowService implements CodeReviewWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(JpaCodeReviewWorkflowService.class);

    private final CodeReviewStateRepository repository;
    private final ArtifactRecordService artifactRecordService;
    private final ObjectMapper objectMapper;

    public JpaCodeReviewWorkflowService(CodeReviewStateRepository repository,
                                        ArtifactRecordService artifactRecordService) {
        this.repository = repository;
        this.artifactRecordService = artifactRecordService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public ReviewState createOrUpdate(String taskId, String projectId, String executionId, ReviewStage stage,
                                      String developerEmployeeCode, String reviewerEmployeeCode,
                                      String worktreePath, String diffPath, String reviewReportPath,
                                      String finalSummaryPath, List<String> reviewFindings,
                                      Map<String, Object> metadata) {
        CodeReviewStateEntity entity = repository.findByTaskId(taskId)
            .orElseGet(CodeReviewStateEntity::new);

        entity.setTaskId(taskId);
        entity.setProjectId(projectId);
        entity.setExecutionId(executionId);
        entity.setStage(stage.name());
        entity.setReviewRound(metadata != null && metadata.containsKey(TaskMetadataKeys.REVIEW_ROUND)
            ? ((Number) metadata.get(TaskMetadataKeys.REVIEW_ROUND)).intValue() : 0);
        entity.setDeveloperEmployeeCode(developerEmployeeCode);
        entity.setReviewerEmployeeCode(reviewerEmployeeCode);
        entity.setWorktreePath(worktreePath);
        entity.setDiffPath(diffPath);
        entity.setReviewReportPath(reviewReportPath);
        entity.setFinalSummaryPath(finalSummaryPath);
        entity.setReviewFindingsJson(serializeList(reviewFindings));
        entity.setMetadataJson(serializeMap(metadata));
        entity.setUpdatedAt(Instant.now());

        entity = repository.save(entity);
        log.info("Persisted review state: taskId={}, stage={}", taskId, stage);
        return toReviewState(entity);
    }

    @Override
    public Optional<ReviewState> getByTaskId(String taskId) {
        return repository.findByTaskId(taskId).map(this::toReviewState);
    }

    @Override
    public Optional<ReviewState> getByExecutionId(String executionId) {
        return repository.findByExecutionId(executionId).map(this::toReviewState);
    }

    @Override
    public ReviewState advanceStage(String taskId, ReviewStage nextStage, Map<String, Object> metadata) {
        CodeReviewStateEntity entity = repository.findByTaskId(taskId)
            .orElseThrow(() -> new IllegalArgumentException("Review state not found for taskId=" + taskId));

        ReviewStage currentStage = ReviewStage.valueOf(entity.getStage());
        if (!CodeReviewWorkflowService.canTransition(currentStage, nextStage)) {
            throw new IllegalStateException(
                String.format("Invalid stage transition: %s -> %s (taskId=%s)", currentStage, nextStage, taskId));
        }
        if (entity.getReviewRound() >= CodeReviewWorkflowService.MAX_REVIEW_ROUNDS && nextStage == ReviewStage.ASSIGN_REVIEWER) {
            throw new IllegalStateException(
                String.format("Exceeded max review rounds (%d), must escalate (taskId=%s)", CodeReviewWorkflowService.MAX_REVIEW_ROUNDS, taskId));
        }

        Map<String, Object> merged = mergeMetadata(deserializeMap(entity.getMetadataJson()), metadata);

        entity.setStage(nextStage.name());
        entity.setMetadataJson(serializeMap(merged));
        entity.setUpdatedAt(Instant.now());

        entity = repository.save(entity);
        log.info("Advanced review stage: taskId={} {} -> {}", taskId, currentStage, nextStage);
        return toReviewState(entity);
    }

    @Override
    public ReviewState requestChanges(String taskId, List<String> findings, Map<String, Object> metadata) {
        CodeReviewStateEntity entity = repository.findByTaskId(taskId)
            .orElseThrow(() -> new IllegalArgumentException("Review state not found for taskId=" + taskId));

        int newRound = (entity.getReviewRound() != null ? entity.getReviewRound() : 0) + 1;
        Map<String, Object> merged = mergeMetadata(deserializeMap(entity.getMetadataJson()), metadata);
        merged.put(TaskMetadataKeys.REVIEW_ROUND, newRound);
        merged.put(TaskMetadataKeys.LAST_REVIEW_AT, Instant.now().toString());

        entity.setStage(ReviewStage.REVIEW_CHANGES_REQUESTED.name());
        entity.setReviewRound(newRound);
        entity.setReviewFindingsJson(serializeList(findings));
        entity.setMetadataJson(serializeMap(merged));
        entity.setUpdatedAt(Instant.now());

        entity = repository.save(entity);
        log.info("Requested changes: taskId={}, round={}", taskId, newRound);
        return toReviewState(entity);
    }

    @Override
    public ReviewState approve(String taskId, Map<String, Object> metadata) {
        CodeReviewStateEntity entity = repository.findByTaskId(taskId)
            .orElseThrow(() -> new IllegalArgumentException("Review state not found for taskId=" + taskId));

        Map<String, Object> merged = mergeMetadata(deserializeMap(entity.getMetadataJson()), metadata);

        entity.setStage(ReviewStage.REVIEW_APPROVED.name());
        entity.setMetadataJson(serializeMap(merged));
        entity.setUpdatedAt(Instant.now());

        entity = repository.save(entity);
        log.info("Approved review: taskId={}, rounds={}", taskId, entity.getReviewRound());
        return toReviewState(entity);
    }

    @Override
    public ReviewState escalate(String taskId, String reason, Map<String, Object> metadata) {
        CodeReviewStateEntity entity = repository.findByTaskId(taskId)
            .orElseThrow(() -> new IllegalArgumentException("Review state not found for taskId=" + taskId));

        Map<String, Object> merged = mergeMetadata(deserializeMap(entity.getMetadataJson()), metadata);
        merged.put(TaskMetadataKeys.ESCALATION_REASON, reason);

        entity.setStage(ReviewStage.ESCALATED.name());
        entity.setMetadataJson(serializeMap(merged));
        entity.setUpdatedAt(Instant.now());

        entity = repository.save(entity);
        log.warn("Escalated review: taskId={}, reason={}", taskId, reason);
        return toReviewState(entity);
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

    // ========== Entity <-> Record 转换 ==========

    private ReviewState toReviewState(CodeReviewStateEntity entity) {
        return new ReviewState(
            entity.getTaskId(),
            entity.getProjectId(),
            entity.getExecutionId(),
            ReviewStage.valueOf(entity.getStage()),
            entity.getReviewRound() != null ? entity.getReviewRound() : 0,
            entity.getDeveloperEmployeeCode(),
            entity.getReviewerEmployeeCode(),
            entity.getWorktreePath(),
            entity.getDiffPath(),
            entity.getReviewReportPath(),
            entity.getFinalSummaryPath(),
            deserializeList(entity.getReviewFindingsJson()),
            deserializeMap(entity.getMetadataJson())
        );
    }

    // ========== JSON 序列化/反序列化 ==========

    private String serializeList(List<String> list) {
        if (list == null || list.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize list: {}", e.getMessage());
            return null;
        }
    }

    private List<String> deserializeList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize list: {}", e.getMessage());
            return List.of();
        }
    }

    private String serializeMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize map: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> deserializeMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize map: {}", e.getMessage());
            return Map.of();
        }
    }

    private Map<String, Object> mergeMetadata(Map<String, Object> base, Map<String, Object> extra) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (base != null) merged.putAll(base);
        if (extra != null) merged.putAll(extra);
        return merged;
    }
}
