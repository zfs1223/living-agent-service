package com.livingagent.core.autonomy.impl;

import com.livingagent.core.autonomy.ArtifactRecord;
import com.livingagent.core.autonomy.CodeReviewWorkflowService;
import com.livingagent.core.autonomy.DepartmentExecutionResult;
import com.livingagent.core.autonomy.EmployeeExecutionReceipt;
import com.livingagent.core.autonomy.EmployeeExecutionReceiptService;
import com.livingagent.core.autonomy.ReceiptStatus;
import com.livingagent.core.autonomy.TaskMetadataKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class InMemoryEmployeeExecutionReceiptService implements EmployeeExecutionReceiptService {

    private static final Logger log = LoggerFactory.getLogger(InMemoryEmployeeExecutionReceiptService.class);

    private final Map<String, List<EmployeeExecutionReceipt>> receiptsByExecutionId = new ConcurrentHashMap<>();
    private final Map<String, DepartmentExecutionResult> executionResultsById = new ConcurrentHashMap<>();
    private final List<ReceiptListener> listeners = new CopyOnWriteArrayList<>();
    private final CodeReviewWorkflowService codeReviewWorkflowService;
    private final CodeArtifactMetadataBinder artifactMetadataBinder;

    public InMemoryEmployeeExecutionReceiptService(CodeReviewWorkflowService codeReviewWorkflowService,
                                                   CodeArtifactMetadataBinder artifactMetadataBinder) {
        this.codeReviewWorkflowService = codeReviewWorkflowService;
        this.artifactMetadataBinder = artifactMetadataBinder;
    }

    @Override
    public void registerExecution(DepartmentExecutionResult executionResult) {
        if (executionResult == null || executionResult.executionId() == null) {
            return;
        }
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
        receiptsByExecutionId.computeIfAbsent(receipt.executionId(), k -> new ArrayList<>()).add(receipt);
        DepartmentExecutionResult executionResult = executionResultsById.get(receipt.executionId());
        for (ReceiptListener listener : listeners) {
            try {
                listener.onReceiptRecorded(receipt, executionResult);
            } catch (Exception e) {
                log.warn("Receipt listener failed for receiptId={}: {}", receipt.receiptId(), e.getMessage());
            }
        }
        if (codeReviewWorkflowService != null && executionResult != null) {
            routeToReviewWorkflow(receipt, executionResult);
        }
        return receipt;
    }

    @Override
    public List<EmployeeExecutionReceipt> getReceipts(String executionId) {
        return receiptsByExecutionId.containsKey(executionId)
            ? List.copyOf(receiptsByExecutionId.get(executionId))
            : List.of();
    }

    @Override
    public List<EmployeeExecutionReceipt> getReceiptsByDepartment(String department) {
        return receiptsByExecutionId.values().stream()
            .flatMap(List::stream)
            .filter(r -> {
                if (r.metadata() == null) return false;
                Object dept = r.metadata().get("department");
                return department.equals(dept);
            })
            .toList();
    }

    @Override
    public boolean isExecutionComplete(String executionId) {
        List<EmployeeExecutionReceipt> receipts = receiptsByExecutionId.get(executionId);
        if (receipts == null || receipts.isEmpty()) {
            return false;
        }
        return receipts.stream().allMatch(r -> r.status() == ReceiptStatus.COMPLETED);
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

    private void routeToReviewWorkflow(EmployeeExecutionReceipt receipt, DepartmentExecutionResult executionResult) {
        String taskId = normalizeTaskId(receipt);
        if (taskId == null) {
            return;
        }
        Map<String, Object> meta = Map.of(
            TaskMetadataKeys.RECEIPT_ID, receipt.receiptId(),
            TaskMetadataKeys.EXECUTION_ID, receipt.executionId(),
            TaskMetadataKeys.STATUS, receipt.status() != null ? receipt.status().getCode() : null,
            TaskMetadataKeys.RECEIVED_AT, receipt.receivedAt().toString(),
            TaskMetadataKeys.TASK_TYPE, stringValue(executionResult.metadata(), TaskMetadataKeys.TASK_TYPE),
            TaskMetadataKeys.TASK_SCOPE, stringValue(executionResult.metadata(), TaskMetadataKeys.TASK_SCOPE),
            TaskMetadataKeys.WORKFLOW_TYPE, stringValue(executionResult.metadata(), TaskMetadataKeys.WORKFLOW_TYPE),
            TaskMetadataKeys.PROJECT_ID, stringValue(executionResult.metadata(), TaskMetadataKeys.PROJECT_ID),
            TaskMetadataKeys.SCHEDULE_ID, stringValue(executionResult.metadata(), TaskMetadataKeys.SCHEDULE_ID),
            TaskMetadataKeys.PARENT_TASK_ID, stringValue(executionResult.metadata(), TaskMetadataKeys.PARENT_TASK_ID)
        );
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
        } catch (Exception e) {
            log.warn("Failed to advance review stage for taskId={}: {}", taskId, e.getMessage());
        }
        String worktreePath = stringValue(receipt.metadata(), TaskMetadataKeys.WORKTREE_PATH);
        if (artifactMetadataBinder != null && worktreePath != null && !worktreePath.isBlank()) {
            ArtifactRecord worktreeArtifact = artifactMetadataBinder.registerWorktree(
                receipt.executionId(),
                executionResult.department(),
                receipt.employeeCode(),
                receipt.employeeNeuronId(),
                taskId,
                stringValue(executionResult.metadata(), TaskMetadataKeys.PROJECT_ID),
                worktreePath,
                stringValue(receipt.metadata(), TaskMetadataKeys.BRANCH_NAME),
                receipt.metadata()
            );
            log.debug("Registered worktree artifact for receipt {} -> {}", receipt.receiptId(), worktreeArtifact.artifactId());
        }
    }

    private String normalizeTaskId(com.livingagent.core.autonomy.EmployeeExecutionDispatch dispatch) {
        if (dispatch == null) {
            return null;
        }
        if (dispatch.assignmentId() != null && !dispatch.assignmentId().isBlank()) {
            return dispatch.assignmentId();
        }
        return dispatch.dispatchId() != null && !dispatch.dispatchId().isBlank() ? dispatch.dispatchId() : null;
    }

    private String normalizeTaskId(EmployeeExecutionReceipt receipt) {
        if (receipt == null) {
            return null;
        }
        if (receipt.assignmentId() != null && !receipt.assignmentId().isBlank()) {
            return receipt.assignmentId();
        }
        return receipt.dispatchId() != null && !receipt.dispatchId().isBlank() ? receipt.dispatchId() : null;
    }

    private String stringValue(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object value = map.get(key);
        return value != null ? String.valueOf(value) : null;
    }
}
