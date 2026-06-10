package com.livingagent.core.autonomy;

import java.time.Instant;
import java.util.Map;

public record EmployeeExecutionReceipt(
    String receiptId,
    String executionId,
    String dispatchId,
    String assignmentId,
    String employeeCode,
    String employeeNeuronId,
    String employeeId,
    String projectId,
    ReceiptStatus status,
    String summary,
    double qualityScore,
    Instant receivedAt,
    Map<String, Object> metadata,
    String worktreePath,
    String diffPath
) {
    /** 兼容旧构造：无 employeeId/projectId/qualityScore/worktree/diff 时使用 */
    public EmployeeExecutionReceipt(String receiptId, String executionId, String dispatchId,
                                     String assignmentId, String employeeCode, String employeeNeuronId,
                                     ReceiptStatus status, String summary, Instant receivedAt,
                                     Map<String, Object> metadata) {
        this(receiptId, executionId, dispatchId, assignmentId, employeeCode, employeeNeuronId,
             null, null, status, summary, 1.0, receivedAt, metadata, null, null);
    }

    /** 兼容旧构造：无 worktree/diff 路径时使用 */
    public EmployeeExecutionReceipt(String receiptId, String executionId, String dispatchId,
                                     String assignmentId, String employeeCode, String employeeNeuronId,
                                     ReceiptStatus status, String summary, Instant receivedAt,
                                     Map<String, Object> metadata, String worktreePath, String diffPath) {
        this(receiptId, executionId, dispatchId, assignmentId, employeeCode, employeeNeuronId,
             null, null, status, summary, 1.0, receivedAt, metadata, worktreePath, diffPath);
    }

    /**
     * 兼容旧代码：从字符串 status 构造回执
     */
    public static EmployeeExecutionReceipt fromLegacyString(
            String receiptId, String executionId, String dispatchId,
            String assignmentId, String employeeCode, String employeeNeuronId,
            String statusStr, String summary, Instant receivedAt,
            Map<String, Object> metadata, String worktreePath, String diffPath) {
        ReceiptStatus receiptStatus = ReceiptStatus.fromString(statusStr);
        return new EmployeeExecutionReceipt(
            receiptId, executionId, dispatchId, assignmentId, employeeCode, employeeNeuronId,
            null, null, receiptStatus, summary, 1.0, receivedAt, metadata, worktreePath, diffPath);
    }

    /**
     * 兼容旧代码：从字符串 status 构造回执（无 worktree/diff）
     */
    public static EmployeeExecutionReceipt fromLegacyString(
            String receiptId, String executionId, String dispatchId,
            String assignmentId, String employeeCode, String employeeNeuronId,
            String statusStr, String summary, Instant receivedAt,
            Map<String, Object> metadata) {
        return fromLegacyString(receiptId, executionId, dispatchId, assignmentId,
            employeeCode, employeeNeuronId, statusStr, summary, receivedAt,
            metadata, null, null);
    }
}
