package com.livingagent.core.autonomy.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.autonomy.*;
import com.livingagent.core.database.entity.EmployeeExecutionReceiptEntity;
import com.livingagent.core.database.repository.EmployeeExecutionReceiptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * JPA 持久化实现的员工执行回执服务。
 * 回执数据存储在 PostgreSQL，重启不丢失。
 */
public class JpaEmployeeExecutionReceiptService implements EmployeeExecutionReceiptService {

    private static final Logger log = LoggerFactory.getLogger(JpaEmployeeExecutionReceiptService.class);

    private final EmployeeExecutionReceiptRepository receiptRepository;
    private final CodeReviewWorkflowService codeReviewWorkflowService;
    private final CodeArtifactMetadataBinder artifactMetadataBinder;
    private final ObjectMapper objectMapper;
    private final ExecutionReceiptReviewer executionReceiptReviewer;

    private final List<ReceiptListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * 内存缓存 DepartmentExecutionResult，确保 recordReceipt 的 listener 能拿到完整的执行结果。
     * 与 InMemoryEmployeeExecutionReceiptService / FileBasedEmployeeExecutionReceiptService 行为对齐。
     */
    private final ConcurrentMap<String, DepartmentExecutionResult> executionResultsById = new ConcurrentHashMap<>();

    public JpaEmployeeExecutionReceiptService(EmployeeExecutionReceiptRepository receiptRepository,
                                               CodeReviewWorkflowService codeReviewWorkflowService,
                                               CodeArtifactMetadataBinder artifactMetadataBinder) {
        this(receiptRepository, codeReviewWorkflowService, artifactMetadataBinder, null);
    }

    public JpaEmployeeExecutionReceiptService(EmployeeExecutionReceiptRepository receiptRepository,
                                               CodeReviewWorkflowService codeReviewWorkflowService,
                                               CodeArtifactMetadataBinder artifactMetadataBinder,
                                               ExecutionReceiptReviewer executionReceiptReviewer) {
        this.receiptRepository = receiptRepository;
        this.codeReviewWorkflowService = codeReviewWorkflowService;
        this.artifactMetadataBinder = artifactMetadataBinder;
        this.executionReceiptReviewer = executionReceiptReviewer;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void registerExecution(DepartmentExecutionResult executionResult) {
        if (executionResult == null || executionResult.executionId() == null) {
            return;
        }
        // 缓存执行结果，确保后续 recordReceipt 的 listener 能获取到
        executionResultsById.put(executionResult.executionId(), executionResult);
        if (executionResult.dispatchedAssignments() != null) {
            for (com.livingagent.core.autonomy.EmployeeExecutionDispatch dispatch : executionResult.dispatchedAssignments()) {
                if (dispatch == null) continue;
                String taskId = normalizeTaskId(dispatch);
                if (taskId == null) {
                    continue;
                }
                if (codeReviewWorkflowService != null) {
                    Map<String, Object> meta = new java.util.LinkedHashMap<>();
                    meta.put(TaskMetadataKeys.EXECUTION_ID, executionResult.executionId());
                    meta.put(TaskMetadataKeys.DEPARTMENT, executionResult.department());
                    meta.put(TaskMetadataKeys.EMPLOYEE_CODE, dispatch.employeeCode());
                    meta.put(TaskMetadataKeys.EMPLOYEE_NEURON_ID, dispatch.employeeNeuronId());
                    meta.put(TaskMetadataKeys.TASK_ID, taskId);
                    meta.put(TaskMetadataKeys.DISPATCH_ID, dispatch.dispatchId());
                    meta.put(TaskMetadataKeys.STATUS, dispatch.status());
                    meta.put(TaskMetadataKeys.TASK_TYPE, stringValue(executionResult.metadata(), TaskMetadataKeys.TASK_TYPE));
                    meta.put(TaskMetadataKeys.TASK_SCOPE, stringValue(executionResult.metadata(), TaskMetadataKeys.TASK_SCOPE));
                    meta.put(TaskMetadataKeys.WORKFLOW_TYPE, stringValue(executionResult.metadata(), TaskMetadataKeys.WORKFLOW_TYPE));
                    meta.put(TaskMetadataKeys.PROJECT_ID, stringValue(executionResult.metadata(), TaskMetadataKeys.PROJECT_ID));
                    meta.put(TaskMetadataKeys.SCHEDULE_ID, stringValue(executionResult.metadata(), TaskMetadataKeys.SCHEDULE_ID));
                    meta.put(TaskMetadataKeys.PARENT_TASK_ID, stringValue(executionResult.metadata(), TaskMetadataKeys.PARENT_TASK_ID));
                    meta.entrySet().removeIf(e -> e.getValue() == null);
                    codeReviewWorkflowService.createOrUpdate(
                        taskId,
                        executionResult.department(),
                        executionResult.executionId(),
                        CodeReviewWorkflowService.ReviewStage.CODE_SUBMITTED,
                        dispatch.employeeCode(),
                        null,
                        stringValue(dispatch.metadata(), TaskMetadataKeys.WORKTREE_PATH),
                        stringValue(dispatch.metadata(), TaskMetadataKeys.DIFF_PATH),
                        stringValue(dispatch.metadata(), TaskMetadataKeys.REVIEW_REPORT_PATH),
                        stringValue(dispatch.metadata(), TaskMetadataKeys.FINAL_SUMMARY_PATH),
                        List.of(),
                        meta
                    );
                }
            }
        }
    }

    @Override
    public EmployeeExecutionReceipt recordReceipt(EmployeeExecutionReceipt receipt) {
        if (receipt == null) {
            return null;
        }

        EmployeeExecutionReceiptEntity entity = toEntity(receipt);
        entity = receiptRepository.save(entity);
        log.info("Persisted receipt: receiptId={}, executionId={}, status={}",
            entity.getReceiptId(), entity.getExecutionId(), entity.getStatus());

        EmployeeExecutionReceipt savedReceipt = toReceipt(entity);

        // 从缓存中获取执行结果，传递给 listener（与 InMemory/FileBased 实现对齐）
        DepartmentExecutionResult executionResult = executionResultsById.get(receipt.executionId());

        for (ReceiptListener listener : listeners) {
            try {
                listener.onReceiptRecorded(savedReceipt, executionResult);
            } catch (Exception e) {
                log.warn("Receipt listener failed for receiptId={}: {}", savedReceipt.receiptId(), e.getMessage());
            }
        }

        if (codeReviewWorkflowService != null) {
            routeToReviewWorkflow(savedReceipt);
        }

        return savedReceipt;
    }

    @Override
    public List<EmployeeExecutionReceipt> getReceipts(String executionId) {
        return receiptRepository.findByExecutionId(executionId)
            .stream()
            .map(this::toReceipt)
            .toList();
    }

    @Override
    public boolean isExecutionComplete(String executionId) {
        return isExecutionComplete(executionId, 0);
    }

    /**
     * 检查执行是否完成。
     * @param executionId 执行ID
     * @param expectedCount 期望的回执数量（0 表示不校验数量，仅检查终态）
     */
    public boolean isExecutionComplete(String executionId, int expectedCount) {
        List<EmployeeExecutionReceiptEntity> receipts = receiptRepository.findByExecutionId(executionId);
        if (receipts == null || receipts.isEmpty()) {
            return false;
        }
        // 数量校验：如果指定了期望数量，必须收到足够多的回执
        if (expectedCount > 0 && receipts.size() < expectedCount) {
            log.debug("isExecutionComplete: executionId={} received {}/{} receipts, not yet complete",
                executionId, receipts.size(), expectedCount);
            return false;
        }
        // COMPLETED、DEGRADED、FAILED 均视为终态，允许触发最终汇总
        boolean allTerminal = receipts.stream().allMatch(r -> {
            String status = r.getStatus();
            return "COMPLETED".equals(status) || "DEGRADED".equals(status) || "FAILED".equals(status);
        });
        if (!allTerminal) {
            log.debug("isExecutionComplete: executionId={} has {} receipts but not all terminal",
                executionId, receipts.size());
        }
        return allTerminal;
    }

    @Override
    public void addReceiptListener(ReceiptListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    @Override
    public void removeReceiptListener(ReceiptListener listener) {
        listeners.remove(listener);
    }

    private void routeToReviewWorkflow(EmployeeExecutionReceipt receipt) {
        String taskId = normalizeTaskId(receipt);
        if (taskId == null) {
            return;
        }
        // 使用 LinkedHashMap 替代 Map.of()，避免 null 值导致 NPE
        Map<String, Object> meta = new java.util.LinkedHashMap<>();
        meta.put(TaskMetadataKeys.RECEIPT_ID, receipt.receiptId());
        meta.put(TaskMetadataKeys.EXECUTION_ID, receipt.executionId());
        meta.put(TaskMetadataKeys.STATUS, receipt.status() != null ? receipt.status().getCode() : null);
        meta.put(TaskMetadataKeys.RECEIVED_AT, receipt.receivedAt() != null ? receipt.receivedAt().toString() : null);
        meta.entrySet().removeIf(e -> e.getValue() == null);
        if (receipt.status() == ReceiptStatus.FAILED) {
            codeReviewWorkflowService.requestChanges(taskId, List.of(receipt.summary()), meta);
            return;
        }
        try {
            CodeReviewWorkflowService.ReviewState currentState = codeReviewWorkflowService.getByTaskId(taskId).orElse(null);
            if (currentState == null) {
                log.debug("No review state found for taskId={}, skipping review workflow", taskId);
                return;
            }
            CodeReviewWorkflowService.ReviewStage currentStage = currentState.stage();
            if (currentStage == CodeReviewWorkflowService.ReviewStage.CODE_SUBMITTED) {
                codeReviewWorkflowService.advanceStage(taskId, CodeReviewWorkflowService.ReviewStage.ASSIGN_REVIEWER, meta);
                codeReviewWorkflowService.advanceStage(taskId, CodeReviewWorkflowService.ReviewStage.REVIEWING, meta);
            } else if (currentStage == CodeReviewWorkflowService.ReviewStage.ASSIGN_REVIEWER) {
                codeReviewWorkflowService.advanceStage(taskId, CodeReviewWorkflowService.ReviewStage.REVIEWING, meta);
            } else {
                log.debug("Review stage for taskId={} is {}, no auto-advance", taskId, currentStage);
            }

            // 推进到 REVIEWING 后，自动调用 reviewer 完成审查闭环
            // REVIEWING -> REVIEW_APPROVED 或 REVIEW_CHANGES_REQUESTED
            if (executionReceiptReviewer != null) {
                try {
                    Optional<ExecutionReceiptReviewer.ReceiptReviewResult> reviewResult =
                        executionReceiptReviewer.reviewReceipt(receipt, null, List.of());
                    reviewResult.ifPresent(result -> {
                        Map<String, Object> reviewMeta = new java.util.LinkedHashMap<>();
                        reviewMeta.put(TaskMetadataKeys.RECEIPT_ID, receipt.receiptId());
                        reviewMeta.put("quality_score", result.qualityScore());
                        reviewMeta.put("review_comment", result.reviewComment());
                        reviewMeta.entrySet().removeIf(e -> e.getValue() == null);
                        if (result.accepted()) {
                            codeReviewWorkflowService.approve(taskId, reviewMeta);
                            log.info("Auto-approved review for taskId={}, qualityScore={}", taskId, result.qualityScore());
                        } else {
                            codeReviewWorkflowService.requestChanges(taskId, result.unmetCriteria(), reviewMeta);
                            log.info("Auto-requested changes for taskId={}, unmetCriteria={}", taskId, result.unmetCriteria());
                        }
                    });
                } catch (Exception reviewEx) {
                    log.warn("Auto-review failed for taskId={}, review stays in REVIEWING: {}", taskId, reviewEx.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to advance review stage for taskId={}: {}", taskId, e.getMessage());
        }
        if (artifactMetadataBinder != null && receipt.worktreePath() != null && !receipt.worktreePath().isBlank()) {
            ArtifactRecord worktreeArtifact = artifactMetadataBinder.registerWorktree(
                receipt.executionId(),
                null,
                receipt.employeeCode(),
                receipt.employeeNeuronId(),
                taskId,
                null,
                receipt.worktreePath(),
                stringValue(receipt.metadata(), TaskMetadataKeys.BRANCH_NAME),
                receipt.metadata()
            );
            log.debug("Registered worktree artifact for receipt {} -> {}", receipt.receiptId(), worktreeArtifact.artifactId());
        }
    }

    private String normalizeTaskId(com.livingagent.core.autonomy.EmployeeExecutionDispatch dispatch) {
        if (dispatch == null) return null;
        if (dispatch.assignmentId() != null && !dispatch.assignmentId().isBlank()) return dispatch.assignmentId();
        return dispatch.dispatchId() != null && !dispatch.dispatchId().isBlank() ? dispatch.dispatchId() : null;
    }

    private String normalizeTaskId(EmployeeExecutionReceipt receipt) {
        if (receipt == null) return null;
        if (receipt.assignmentId() != null && !receipt.assignmentId().isBlank()) return receipt.assignmentId();
        return receipt.dispatchId() != null && !receipt.dispatchId().isBlank() ? receipt.dispatchId() : null;
    }

    private String stringValue(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object value = map.get(key);
        return value != null ? String.valueOf(value) : null;
    }

    // ========== Entity <-> Domain 转换 ==========

    private EmployeeExecutionReceiptEntity toEntity(EmployeeExecutionReceipt receipt) {
        EmployeeExecutionReceiptEntity entity = new EmployeeExecutionReceiptEntity();
        entity.setReceiptId(receipt.receiptId());
        entity.setExecutionId(receipt.executionId());
        entity.setDispatchId(receipt.dispatchId());
        entity.setAssignmentId(receipt.assignmentId());
        entity.setEmployeeCode(receipt.employeeCode());
        entity.setEmployeeNeuronId(receipt.employeeNeuronId());
        entity.setStatus(receipt.status() != null ? receipt.status().getCode() : "UNKNOWN");
        entity.setSummary(receipt.summary());
        entity.setReceivedAt(receipt.receivedAt() != null ? receipt.receivedAt() : Instant.now());
        entity.setMetadataJson(serializeMap(receipt.metadata()));
        entity.setWorktreePath(receipt.worktreePath());
        entity.setDiffPath(receipt.diffPath());
        return entity;
    }

    private EmployeeExecutionReceipt toReceipt(EmployeeExecutionReceiptEntity entity) {
        return new EmployeeExecutionReceipt(
            entity.getReceiptId(),
            entity.getExecutionId(),
            entity.getDispatchId(),
            entity.getAssignmentId(),
            entity.getEmployeeCode(),
            entity.getEmployeeNeuronId(),
            ReceiptStatus.fromString(entity.getStatus()),
            entity.getSummary(),
            entity.getReceivedAt(),
            deserializeMap(entity.getMetadataJson()),
            entity.getWorktreePath(),
            entity.getDiffPath()
        );
    }

    // ========== JSON 序列化/反序列化 ==========

    private String serializeMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize receipt metadata: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> deserializeMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize receipt metadata: {}", e.getMessage());
            return Map.of();
        }
    }
}
