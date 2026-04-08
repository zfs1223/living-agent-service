package com.livingagent.core.security;

public enum ChangeStatus {
    PENDING("待确认"),
    CONFIRMED("已确认"),
    REJECTED("已拒绝"),
    APPLIED("已应用");

    private final String description;

    ChangeStatus(String description) {
        this.description = description;
    }

    public String getDescription() { return description; }
}
