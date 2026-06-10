package com.livingagent.core.planner.dag;

import java.time.Instant;
import java.util.List;

public record DagTask(
    String id,
    String subject,
    String description,
    DagTaskStatus status,
    List<String> blockedBy,
    List<String> blocks,
    String assignee,
    String worktree,
    String role,
    Instant createdAt,
    Instant updatedAt
) {
    public static DagTask create(String id, String subject, String description) {
        return new DagTask(id, subject, description, DagTaskStatus.PENDING,
            List.of(), List.of(), null, null, null, Instant.now(), Instant.now());
    }

    public DagTask withBlockedBy(List<String> blockedBy) {
        return new DagTask(id, subject, description, status, blockedBy, blocks,
            assignee, worktree, role, createdAt, Instant.now());
    }

    public DagTask withBlocks(List<String> blocks) {
        return new DagTask(id, subject, description, status, blockedBy, blocks,
            assignee, worktree, role, createdAt, Instant.now());
    }

    public DagTask withStatus(DagTaskStatus status) {
        return new DagTask(id, subject, description, status, blockedBy, blocks,
            assignee, worktree, role, createdAt, Instant.now());
    }

    public DagTask withAssignee(String assignee) {
        return new DagTask(id, subject, description, status, blockedBy, blocks,
            assignee, worktree, role, createdAt, Instant.now());
    }

    public DagTask withWorktree(String worktree) {
        return new DagTask(id, subject, description, status, blockedBy, blocks,
            assignee, worktree, role, createdAt, Instant.now());
    }

    public DagTask withRole(String role) {
        return new DagTask(id, subject, description, status, blockedBy, blocks,
            assignee, worktree, role, createdAt, Instant.now());
    }

    public boolean isBlocked() {
        return blockedBy != null && !blockedBy.isEmpty();
    }

    public boolean isClaimable(String role) {
        return status == DagTaskStatus.PENDING
            && (assignee == null || assignee.isEmpty())
            && !isBlocked()
            && (this.role == null || this.role.equals(role));
    }
}
