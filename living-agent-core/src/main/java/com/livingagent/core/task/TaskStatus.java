package com.livingagent.core.task;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public enum TaskStatus {
    PENDING("pending", "待处理"),
    ASSIGNED("assigned", "已分配"),
    IN_PROGRESS("in_progress", "执行中"),
    WAITING_RECEIPT("waiting_receipt", "等待回执"),
    NEEDS_CLARIFICATION("needs_clarification", "需要澄清"),
    BLOCKED("blocked", "已阻塞"),
    NEEDS_HUMAN_REVIEW("needs_human_review", "需要人工审核"),
    COMPLETED("completed", "已完成"),
    PARTIALLY_COMPLETED("partially_completed", "部分完成"),
    FAILED("failed", "执行失败"),
    CANCELLED("cancelled", "已取消"),
    /** DAG 子任务状态：部分子任务完成后中间状态 */
    DAG_PARTIAL("dag_partial", "DAG部分完成"),
    /** DAG 整个图执行完成 */
    DAG_COMPLETED("dag_completed", "DAG完成"),
    /** DAG 图破裂（某子任务不可恢复失败） */
    DAG_BROKEN("dag_broken", "DAG中断");

    private final String dbValue;
    private final String displayName;

    /** 允许的状态转移规则 */
    private static final Set<StateTransition> ALLOWED_TRANSITIONS = new HashSet<>();

    static {
        // 普通任务转移
        ALLOWED_TRANSITIONS.add(new StateTransition(PENDING, ASSIGNED));
        ALLOWED_TRANSITIONS.add(new StateTransition(PENDING, CANCELLED));
        ALLOWED_TRANSITIONS.add(new StateTransition(ASSIGNED, IN_PROGRESS));
        ALLOWED_TRANSITIONS.add(new StateTransition(ASSIGNED, CANCELLED));
        ALLOWED_TRANSITIONS.add(new StateTransition(IN_PROGRESS, WAITING_RECEIPT));
        ALLOWED_TRANSITIONS.add(new StateTransition(IN_PROGRESS, BLOCKED));
        ALLOWED_TRANSITIONS.add(new StateTransition(IN_PROGRESS, NEEDS_CLARIFICATION));
        ALLOWED_TRANSITIONS.add(new StateTransition(IN_PROGRESS, NEEDS_HUMAN_REVIEW));
        ALLOWED_TRANSITIONS.add(new StateTransition(IN_PROGRESS, FAILED));
        ALLOWED_TRANSITIONS.add(new StateTransition(IN_PROGRESS, CANCELLED));
        ALLOWED_TRANSITIONS.add(new StateTransition(WAITING_RECEIPT, COMPLETED));
        ALLOWED_TRANSITIONS.add(new StateTransition(WAITING_RECEIPT, PARTIALLY_COMPLETED));
        ALLOWED_TRANSITIONS.add(new StateTransition(WAITING_RECEIPT, FAILED));
        ALLOWED_TRANSITIONS.add(new StateTransition(WAITING_RECEIPT, NEEDS_HUMAN_REVIEW));
        ALLOWED_TRANSITIONS.add(new StateTransition(BLOCKED, IN_PROGRESS));
        ALLOWED_TRANSITIONS.add(new StateTransition(BLOCKED, CANCELLED));
        ALLOWED_TRANSITIONS.add(new StateTransition(NEEDS_CLARIFICATION, IN_PROGRESS));
        ALLOWED_TRANSITIONS.add(new StateTransition(NEEDS_CLARIFICATION, CANCELLED));
        ALLOWED_TRANSITIONS.add(new StateTransition(NEEDS_HUMAN_REVIEW, IN_PROGRESS));
        ALLOWED_TRANSITIONS.add(new StateTransition(NEEDS_HUMAN_REVIEW, CANCELLED));
        ALLOWED_TRANSITIONS.add(new StateTransition(PARTIALLY_COMPLETED, COMPLETED));
        ALLOWED_TRANSITIONS.add(new StateTransition(PARTIALLY_COMPLETED, DAG_PARTIAL));

        // DAG 任务转移
        ALLOWED_TRANSITIONS.add(new StateTransition(PENDING, DAG_PARTIAL));
        ALLOWED_TRANSITIONS.add(new StateTransition(IN_PROGRESS, DAG_PARTIAL));
        ALLOWED_TRANSITIONS.add(new StateTransition(DAG_PARTIAL, DAG_COMPLETED));
        ALLOWED_TRANSITIONS.add(new StateTransition(DAG_PARTIAL, DAG_BROKEN));
        ALLOWED_TRANSITIONS.add(new StateTransition(DAG_PARTIAL, FAILED));
        ALLOWED_TRANSITIONS.add(new StateTransition(DAG_PARTIAL, DAG_PARTIAL)); // 允许同状态刷新
        ALLOWED_TRANSITIONS.add(new StateTransition(DAG_COMPLETED, COMPLETED));
        ALLOWED_TRANSITIONS.add(new StateTransition(DAG_BROKEN, NEEDS_HUMAN_REVIEW));
        ALLOWED_TRANSITIONS.add(new StateTransition(DAG_BROKEN, FAILED));
        ALLOWED_TRANSITIONS.add(new StateTransition(DAG_BROKEN, CANCELLED));
    }

    TaskStatus(String dbValue, String displayName) {
        this.dbValue = dbValue;
        this.displayName = displayName;
    }

    public String getDbValue() { return dbValue; }
    public String getDisplayName() { return displayName; }

    public static TaskStatus fromDbValue(String value) {
        if (value == null) return PENDING;
        for (TaskStatus s : values()) {
            if (s.dbValue.equalsIgnoreCase(value)) return s;
        }
        return PENDING;
    }

    /** 检查是否允许从当前状态转移到目标状态 */
    public boolean canTransitionTo(TaskStatus target) {
        if (this == target) return true;
        // 任何状态都可以取消（安全阀）
        if (target == CANCELLED) return true;
        return ALLOWED_TRANSITIONS.contains(new StateTransition(this, target));
    }

    /** 获取允许转移到的下一个状态列表 */
    public List<TaskStatus> allowedTransitions() {
        return ALLOWED_TRANSITIONS.stream()
            .filter(t -> t.from == this)
            .map(t -> t.to)
            .distinct()
            .toList();
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == PARTIALLY_COMPLETED
            || this == FAILED || this == CANCELLED
            || this == DAG_COMPLETED || this == DAG_BROKEN;
    }

    public boolean isActive() {
        return !isTerminal();
    }

    /** 是否为 DAG 任务相关状态 */
    public boolean isDagState() {
        return this == DAG_PARTIAL || this == DAG_COMPLETED || this == DAG_BROKEN;
    }

    public static List<String> activeDbValues() {
        return List.of(PENDING.dbValue, ASSIGNED.dbValue, IN_PROGRESS.dbValue,
            WAITING_RECEIPT.dbValue, NEEDS_CLARIFICATION.dbValue, BLOCKED.dbValue,
            NEEDS_HUMAN_REVIEW.dbValue, DAG_PARTIAL.dbValue);
    }

    public static List<String> terminalDbValues() {
        return List.of(COMPLETED.dbValue, PARTIALLY_COMPLETED.dbValue,
            FAILED.dbValue, CANCELLED.dbValue, DAG_COMPLETED.dbValue, DAG_BROKEN.dbValue);
    }

    /** 状态转移记录 */
    private record StateTransition(TaskStatus from, TaskStatus to) {}
}
