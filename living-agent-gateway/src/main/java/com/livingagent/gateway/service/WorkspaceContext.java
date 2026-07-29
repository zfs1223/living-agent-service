package com.livingagent.gateway.service;

/**
 * 工作目录上下文
 * 用于指定 AI 操作的本地目录
 */
public record WorkspaceContext(
    String id,           // 工作目录 ID
    String name,         // 工作目录名称
    String path,         // 工作目录路径
    String scope         // 权限范围：read（只读）或 read-write（读写）
) {
    /**
     * 默认工作目录
     */
    public static final WorkspaceContext DEFAULT = new WorkspaceContext(
        "default",
        "默认目录",
        "",
        "read-write"
    );
}
