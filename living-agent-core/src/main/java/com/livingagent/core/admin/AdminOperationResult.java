package com.livingagent.core.admin;

/**
 * 管理操作结果
 * <p>统一 AdminService 的操作返回格式。
 * <p>关联文档：docs/core/MAINBRAIN_ADMIN_BRIDGE_PLAN.md
 */
public record AdminOperationResult(
    boolean success,
    String operation,       // 操作名称
    String entityId,        // 创建/操作的实体ID
    String message,         // 结果消息
    String detail           // 详细信息（错误原因等）
) {
    public static AdminOperationResult success(String operation, String entityId, String message) {
        return new AdminOperationResult(true, operation, entityId, message, null);
    }

    public static AdminOperationResult skipped(String operation, String entityId, String message) {
        return new AdminOperationResult(true, operation, entityId, message, "SKIPPED: already exists");
    }

    public static AdminOperationResult failure(String operation, String message, String detail) {
        return new AdminOperationResult(false, operation, null, message, detail);
    }
}
