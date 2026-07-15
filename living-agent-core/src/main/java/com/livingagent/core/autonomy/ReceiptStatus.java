package com.livingagent.core.autonomy;

/**
 * 回执状态枚举，约束 EmployeeExecutionReceipt.status 字段
 * 与 EmployeeTaskExecutionOutcome.ExecutionStatus 对齐
 */
public enum ReceiptStatus {

    COMPLETED("COMPLETED", "执行完成"),
    FAILED("FAILED", "执行失败"),
    DEGRADED("DEGRADED", "降级完成"),
    NEEDS_RETRY("NEEDS_RETRY", "需要重试"),
    NEEDS_REWORK("NEEDS_REWORK", "需要返工"),  // 64-D-2: 输出验证失败时使用
    NEEDS_APPROVAL("NEEDS_APPROVAL", "需要审批"),
    NEEDS_HUMAN_REVIEW("NEEDS_HUMAN_REVIEW", "需要人工审核");

    private final String code;
    private final String description;

    ReceiptStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() { return code; }
    public String getDescription() { return description; }

    /**
     * 从字符串解析 ReceiptStatus，兼容旧的自由字符串值
     */
    public static ReceiptStatus fromString(String value) {
        if (value == null) return null;
        for (ReceiptStatus s : values()) {
            if (s.code.equalsIgnoreCase(value)) return s;
        }
        // 兼容旧值映射
        if ("SUCCESS".equalsIgnoreCase(value)) return COMPLETED;
        if ("ERROR".equalsIgnoreCase(value)) return FAILED;
        if ("PARTIAL".equalsIgnoreCase(value)) return DEGRADED;
        return null;
    }

    /**
     * 判断是否为终态（不再需要后续处理）
     * B-2-3: DEGRADED 也视为终态（降级完成）
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == DEGRADED;
    }

    /**
     * 判断是否需要人工介入
     * 64-D-2: NEEDS_REWORK 视为需要人工介入（返工需人工判定）
     */
    public boolean needsHumanIntervention() {
        return this == NEEDS_APPROVAL || this == NEEDS_HUMAN_REVIEW || this == NEEDS_REWORK;
    }
}
