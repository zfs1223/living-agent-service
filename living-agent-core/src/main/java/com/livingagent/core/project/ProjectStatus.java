package com.livingagent.core.project;

public enum ProjectStatus {
    PLANNING("规划中"),
    IN_PROGRESS("进行中"),
    ON_HOLD("暂停"),
    COMPLETED("已完成"),
    CANCELLED("已取消");
    
    private final String displayName;
    
    ProjectStatus(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}
