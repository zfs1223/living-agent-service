package com.livingagent.core.autonomy;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record EmployeeTaskExecutionOutcome(
    String outcomeId,
    String executionId,
    String employeeCode,
    ExecutionStatus status,
    String summary,
    String modelProvider,
    String modelName,
    List<String> artifacts,
    List<ToolCallRecord> toolCalls,
    String failureReason,
    double confidence,
    boolean needsRetry,
    boolean needsHumanReview,
    Instant completedAt,
    Map<String, Object> metadata
) {
    public enum ExecutionStatus {
        COMPLETED,
        FAILED,
        DEGRADED,
        NEEDS_RETRY,
        PARTIAL
    }

    public static EmployeeTaskExecutionOutcome completed(
            String executionId, String employeeCode,
            String summary, String modelProvider, String modelName) {
        return new EmployeeTaskExecutionOutcome(
            java.util.UUID.randomUUID().toString(),
            executionId, employeeCode,
            ExecutionStatus.COMPLETED,
            summary, modelProvider, modelName,
            List.of(), List.of(), null, 0.9, false, false,
            Instant.now(), Map.of()
        );
    }

    public static EmployeeTaskExecutionOutcome degraded(
            String executionId, String employeeCode,
            String summary, String failureReason) {
        return new EmployeeTaskExecutionOutcome(
            java.util.UUID.randomUUID().toString(),
            executionId, employeeCode,
            ExecutionStatus.DEGRADED,
            summary, null, null,
            List.of(), List.of(), failureReason, 0.3, true, false,
            Instant.now(), Map.of("degraded", true)
        );
    }

    public static EmployeeTaskExecutionOutcome failed(
            String executionId, String employeeCode,
            String error) {
        return new EmployeeTaskExecutionOutcome(
            java.util.UUID.randomUUID().toString(),
            executionId, employeeCode,
            ExecutionStatus.FAILED,
            null, null, null,
            List.of(), List.of(), error, 0.0, true, true,
            Instant.now(), Map.of()
        );
    }

    public boolean isSuccessful() {
        return status == ExecutionStatus.COMPLETED;
    }

    public boolean needsAttention() {
        return status == ExecutionStatus.DEGRADED 
            || status == ExecutionStatus.FAILED 
            || status == ExecutionStatus.NEEDS_RETRY
            || needsRetry 
            || needsHumanReview;
    }

    // DP1-3: 添加 withNeedsHumanReview 方法，用于审查提交失败时标记
    public EmployeeTaskExecutionOutcome withNeedsHumanReview(boolean needsHumanReview) {
        return new EmployeeTaskExecutionOutcome(
            outcomeId, executionId, employeeCode, status, summary,
            modelProvider, modelName, artifacts, toolCalls, failureReason,
            confidence, needsRetry, needsHumanReview, completedAt, metadata
        );
    }

    public EmployeeExecutionReceipt toReceipt(
            String dispatchId, String assignmentId, String neuronId) {
        ReceiptStatus receiptStatus = switch (status) {
            case COMPLETED -> ReceiptStatus.COMPLETED;
            case FAILED -> ReceiptStatus.FAILED;
            case DEGRADED -> ReceiptStatus.DEGRADED;
            case NEEDS_RETRY -> ReceiptStatus.NEEDS_RETRY;
            case PARTIAL -> ReceiptStatus.DEGRADED;
        };

        Map<String, Object> receiptMetadata = new java.util.HashMap<>(metadata);
        receiptMetadata.put("modelProvider", modelProvider);
        receiptMetadata.put("modelName", modelName);
        receiptMetadata.put("confidence", confidence);
        receiptMetadata.put("needsRetry", needsRetry);
        receiptMetadata.put("needsHumanReview", needsHumanReview);
        receiptMetadata.put("failureReason", failureReason);
        receiptMetadata.put("outcomeStatus", status.name());

        return new EmployeeExecutionReceipt(
            java.util.UUID.randomUUID().toString(),
            executionId,
            dispatchId,
            assignmentId,
            employeeCode,
            neuronId,
            receiptStatus,
            summary,
            completedAt,
            receiptMetadata
        );
    }
}
