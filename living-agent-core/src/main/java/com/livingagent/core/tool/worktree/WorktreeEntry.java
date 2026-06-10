package com.livingagent.core.tool.worktree;

public record WorktreeEntry(
    String name,
    String path,
    String branch,
    String taskId,
    WorktreeStatus status,
    long createdAt
) {
    public enum WorktreeStatus {
        ACTIVE,
        KEPT,
        REMOVED
    }

    public static WorktreeEntry create(String name, String path, String branch, String taskId) {
        return new WorktreeEntry(name, path, branch, taskId, WorktreeStatus.ACTIVE, System.currentTimeMillis());
    }

    public WorktreeEntry withStatus(WorktreeStatus status) {
        return new WorktreeEntry(name, path, branch, taskId, status, createdAt);
    }
}
