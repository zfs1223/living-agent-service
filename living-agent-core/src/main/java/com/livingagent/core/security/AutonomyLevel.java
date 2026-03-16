package com.livingagent.core.security;

public enum AutonomyLevel {
    READ_ONLY(0, "只读：可观察但不能操作"),
    SUPERVISED(1, "监督：可操作但需要审批"),
    FULL(2, "完全：在策略范围内自主执行");

    private final int level;
    private final String description;

    AutonomyLevel(int level, String description) {
        this.level = level;
        this.description = description;
    }

    public int getLevel() { return level; }
    public String getDescription() { return description; }

    public boolean canAct() {
        return this != READ_ONLY;
    }

    public boolean requiresApproval() {
        return this == SUPERVISED;
    }

    public boolean isFullyAutonomous() {
        return this == FULL;
    }
}
