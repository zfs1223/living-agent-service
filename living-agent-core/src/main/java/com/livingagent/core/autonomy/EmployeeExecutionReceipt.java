package com.livingagent.core.autonomy;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 员工执行回执
 * 64-E-1: 扩展 steps/toolCalls/validation/knowledgeCandidates 结构化详情
 */
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
    String diffPath,
    /** 64-E-1: 执行步骤详情 */
    List<ActionStep> steps,
    /** 64-E-1: 工具调用详情 */
    List<ToolCallRecord> toolCalls,
    /** 64-E-1: 输出验证结果 */
    ValidationRecord validation,
    /** 64-E-1: 可沉淀的知识候选 */
    List<KnowledgeCaptureCandidate> knowledgeCandidates
) {
    /** 执行步骤 */
    public record ActionStep(
        int step,
        String action,
        String description,
        boolean success,
        long durationMs
    ) {}

    /** 工具调用记录 */
    public record ToolCallRecord(
        String toolName,
        String action,
        boolean success,
        long durationMs,
        String detail
    ) {}

    /** 输出验证记录 */
    public record ValidationRecord(
        boolean valid,
        List<String> issues,
        List<String> warnings
    ) {
        public static ValidationRecord ok() {
            return new ValidationRecord(true, List.of(), List.of());
        }
        public static ValidationRecord fail(List<String> issues) {
            return new ValidationRecord(false, issues, List.of());
        }
    }

    /** 知识沉淀候选 */
    public record KnowledgeCaptureCandidate(
        String type,
        String domain,
        String content,
        double confidence
    ) {}

    /** 兼容旧构造：无扩展字段 */
    public EmployeeExecutionReceipt(String receiptId, String executionId, String dispatchId,
                                     String assignmentId, String employeeCode, String employeeNeuronId,
                                     ReceiptStatus status, String summary, Instant receivedAt,
                                     Map<String, Object> metadata) {
        this(receiptId, executionId, dispatchId, assignmentId, employeeCode, employeeNeuronId,
             null, null, status, summary, 1.0, receivedAt, metadata, null, null,
             null, null, null, null);
    }

    /** 兼容旧构造：无 employeeId/projectId/qualityScore/worktree/diff 时使用 */
    public EmployeeExecutionReceipt(String receiptId, String executionId, String dispatchId,
                                     String assignmentId, String employeeCode, String employeeNeuronId,
                                     String employeeId, String projectId,
                                     ReceiptStatus status, String summary, double qualityScore,
                                     Instant receivedAt, Map<String, Object> metadata,
                                     String worktreePath, String diffPath) {
        this(receiptId, executionId, dispatchId, assignmentId, employeeCode, employeeNeuronId,
             employeeId, projectId, status, summary, qualityScore, receivedAt, metadata,
             worktreePath, diffPath, null, null, null, null);
    }

    /** 兼容旧构造：有 worktree/diff 路径但无扩展字段 */
    public EmployeeExecutionReceipt(String receiptId, String executionId, String dispatchId,
                                     String assignmentId, String employeeCode, String employeeNeuronId,
                                     ReceiptStatus status, String summary, Instant receivedAt,
                                     Map<String, Object> metadata, String worktreePath, String diffPath) {
        this(receiptId, executionId, dispatchId, assignmentId, employeeCode, employeeNeuronId,
             null, null, status, summary, 1.0, receivedAt, metadata, worktreePath, diffPath,
             null, null, null, null);
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
            null, null, receiptStatus, summary, 1.0, receivedAt, metadata, worktreePath, diffPath,
            null, null, null, null);
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
