package com.livingagent.core.database.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * DAG 任务持久化实体 - P2-3 修复。
 * 对应 InMemoryTaskDagService 的 tasks 内存 Map。
 */
@Entity
@Table(name = "dag_tasks", indexes = {
    @Index(name = "idx_dag_task_status", columnList = "status"),
    @Index(name = "idx_dag_task_assignee", columnList = "assignee"),
    @Index(name = "idx_dag_task_role", columnList = "role")
})
public class DagTaskEntity {

    @Id
    @Column(name = "task_id", length = 50)
    private String taskId;

    @Column(name = "subject", length = 255)
    private String subject;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "status", length = 32)
    @Enumerated(EnumType.STRING)
    private DagTaskStatus status;

    @Column(name = "blocked_by", columnDefinition = "text")
    private String blockedByJson;  // JSON array of task IDs

    @Column(name = "blocks", columnDefinition = "text")
    private String blocksJson;  // JSON array of task IDs

    @Column(name = "assignee", length = 255)
    private String assignee;

    @Column(name = "worktree", length = 100)
    private String worktree;

    @Column(name = "role", length = 100)
    private String role;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public enum DagTaskStatus {
        PENDING, IN_PROGRESS, COMPLETED, FAILED, CANCELLED
    }

    public DagTaskEntity() {
        this.status = DagTaskStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // === Getters & Setters ===

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public DagTaskStatus getStatus() { return status; }
    public void setStatus(DagTaskStatus status) { this.status = status; }

    public String getBlockedByJson() { return blockedByJson; }
    public void setBlockedByJson(String blockedByJson) { this.blockedByJson = blockedByJson; }

    public String getBlocksJson() { return blocksJson; }
    public void setBlocksJson(String blocksJson) { this.blocksJson = blocksJson; }

    public String getAssignee() { return assignee; }
    public void setAssignee(String assignee) { this.assignee = assignee; }

    public String getWorktree() { return worktree; }
    public void setWorktree(String worktree) { this.worktree = worktree; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    // === Helper methods for JSON conversion ===

    public List<String> getBlockedByList() {
        if (blockedByJson == null || blockedByJson.isEmpty()) {
            return new ArrayList<>();
        }
        // Simple parsing: "[id1, id2, id3]"
        return parseJsonArray(blockedByJson);
    }

    public void setBlockedByList(List<String> blockedBy) {
        this.blockedByJson = toJsonArray(blockedBy);
    }

    public List<String> getBlocksList() {
        if (blocksJson == null || blocksJson.isEmpty()) {
            return new ArrayList<>();
        }
        return parseJsonArray(blocksJson);
    }

    public void setBlocksList(List<String> blocks) {
        this.blocksJson = toJsonArray(blocks);
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isEmpty() || json.equals("[]")) {
            return new ArrayList<>();
        }
        // Remove brackets and split
        String content = json.substring(1, json.length() - 1);
        if (content.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        for (String part : content.split(",")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                trimmed = trimmed.substring(1, trimmed.length() - 1);
            }
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private String toJsonArray(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(list.get(i)).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    public boolean isBlocked() {
        List<String> blockedBy = getBlockedByList();
        return !blockedBy.isEmpty();
    }

    public boolean isClaimable(String role) {
        return status == DagTaskStatus.PENDING
            && (assignee == null || assignee.isEmpty())
            && !isBlocked()
            && (this.role == null || this.role.equals(role));
    }

    @Override
    public String toString() {
        return String.format("DagTaskEntity{id=%s, subject=%s, status=%s, assignee=%s}",
            taskId, subject, status, assignee);
    }
}