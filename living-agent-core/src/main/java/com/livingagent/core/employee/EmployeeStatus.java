package com.livingagent.core.employee;

public enum EmployeeStatus {
    ONLINE("在线/活跃"),
    OFFLINE("离线/休眠"),
    BUSY("忙碌/处理中"),
    AWAY("离开/等待中"),
    ACTIVE("在职/运行中"),
    DISABLED("禁用"),
    TERMINATED("离职/已销毁"),
    LEARNING("学习中"),
    EVOLVING("进化中");

    private final String description;

    EmployeeStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isWorkingStatus() {
        return this == ONLINE || this == BUSY || this == AWAY;
    }

    public boolean isLifecycleStatus() {
        return this == ACTIVE || this == DISABLED || this == TERMINATED;
    }

    public boolean isDigitalEmployeeOnly() {
        return this == LEARNING || this == EVOLVING;
    }

    public boolean canTransitionTo(EmployeeStatus target) {
        return switch (this) {
            case ONLINE -> target == BUSY || target == AWAY || target == OFFLINE;
            case BUSY -> target == ONLINE || target == AWAY || target == OFFLINE;
            case AWAY -> target == ONLINE || target == BUSY || target == OFFLINE;
            case OFFLINE -> target == ONLINE;
            case ACTIVE -> target == DISABLED || target == TERMINATED;
            case DISABLED -> target == ACTIVE || target == TERMINATED;
            case TERMINATED -> false;
            case LEARNING -> target == EVOLVING || target == ONLINE;
            case EVOLVING -> target == ONLINE;
        };
    }
}
