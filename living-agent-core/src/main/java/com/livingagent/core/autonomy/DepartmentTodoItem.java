package com.livingagent.core.autonomy;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 部门待办项。
 *
 * <p>表示部门大脑发布的、等待员工领取的任务待办。
 * 使用 AtomicInteger 实现乐观锁领取机制。
 */
public class DepartmentTodoItem {

    public enum Status {
        PENDING,        // 待领取
        CLAIMED,        // 已被员工领取
        ASSIGNED,       // 由大脑兜底指派
        IN_PROGRESS,    // 执行中
        COMPLETED,      // 已完成
        CANCELLED       // 已取消
    }

    public enum Priority {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    private final String id;
    private final String department;
    private final String objective;
    private final String instruction;
    private final List<String> requiredRoles;
    private final List<String> requiredTools;
    private final List<String> requiredCapabilities;
    private final Priority priority;
    private final String planId;
    private final String taskType;
    private final Instant createdAt;

    private volatile Status status;
    private volatile String claimedBy;       // 领取员工代码
    private volatile Instant claimedAt;
    private final AtomicInteger claimVersion; // 乐观锁版本号

    public DepartmentTodoItem(String id, String department, String objective, String instruction,
                               List<String> requiredRoles, List<String> requiredTools,
                               List<String> requiredCapabilities, Priority priority,
                               String planId, String taskType) {
        this.id = id;
        this.department = department;
        this.objective = objective;
        this.instruction = instruction;
        this.requiredRoles = requiredRoles != null ? List.copyOf(requiredRoles) : List.of();
        this.requiredTools = requiredTools != null ? List.copyOf(requiredTools) : List.of();
        this.requiredCapabilities = requiredCapabilities != null ? List.copyOf(requiredCapabilities) : List.of();
        this.priority = priority != null ? priority : Priority.MEDIUM;
        this.planId = planId;
        this.taskType = taskType;
        this.createdAt = Instant.now();
        this.status = Status.PENDING;
        this.claimVersion = new AtomicInteger(0);
    }

    /**
     * 乐观锁领取：只有一个员工能成功领取。
     *
     * @param employeeCode 领取员工代码
     * @return true=领取成功，false=已被他人领取
     */
    public boolean claim(String employeeCode) {
        int expected = claimVersion.get();
        if (expected > 0) {
            return false; // 已被领取
        }
        if (claimVersion.compareAndSet(0, 1)) {
            this.claimedBy = employeeCode;
            this.claimedAt = Instant.now();
            this.status = Status.CLAIMED;
            return true;
        }
        return false;
    }

    /**
     * 大脑兜底指派。
     */
    public void assign(String employeeCode) {
        this.claimedBy = employeeCode;
        this.claimedAt = Instant.now();
        this.status = Status.ASSIGNED;
        claimVersion.incrementAndGet();
    }

    public void startProgress() {
        this.status = Status.IN_PROGRESS;
    }

    public void complete() {
        this.status = Status.COMPLETED;
    }

    public void cancel() {
        this.status = Status.CANCELLED;
    }

    // Getters
    public String getId() { return id; }
    public String getDepartment() { return department; }
    public String getObjective() { return objective; }
    public String getInstruction() { return instruction; }
    public List<String> getRequiredRoles() { return requiredRoles; }
    public List<String> getRequiredTools() { return requiredTools; }
    public List<String> getRequiredCapabilities() { return requiredCapabilities; }
    public Priority getPriority() { return priority; }
    public String getPlanId() { return planId; }
    public String getTaskType() { return taskType; }
    public Instant getCreatedAt() { return createdAt; }
    public Status getStatus() { return status; }
    public String getClaimedBy() { return claimedBy; }
    public Instant getClaimedAt() { return claimedAt; }
    public int getClaimVersion() { return claimVersion.get(); }

    public boolean isPending() { return status == Status.PENDING; }
    public boolean isClaimed() { return status == Status.CLAIMED || status == Status.ASSIGNED; }
}
