package com.livingagent.core.security;

import org.springframework.context.ApplicationEvent;

import java.time.Instant;

/**
 * P14-B: 权限变更事件。
 * 当用户权限级别变更时发布，供 WebSocket 层监听并实时更新/断连。
 */
public class PermissionChangeEvent extends ApplicationEvent {

    private final String employeeId;
    private final AccessLevel oldLevel;
    private final AccessLevel newLevel;
    private final String changedBy;
    private final Instant changedAt;

    public PermissionChangeEvent(Object source, String employeeId,
                                  AccessLevel oldLevel, AccessLevel newLevel,
                                  String changedBy) {
        super(source);
        this.employeeId = employeeId;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
        this.changedBy = changedBy;
        this.changedAt = Instant.now();
    }

    public String getEmployeeId() { return employeeId; }
    public AccessLevel getOldLevel() { return oldLevel; }
    public AccessLevel getNewLevel() { return newLevel; }
    public String getChangedBy() { return changedBy; }
    public Instant getChangedAt() { return changedAt; }

    public boolean isDowngrade() {
        return newLevel != null && oldLevel != null && newLevel.getLevel() < oldLevel.getLevel();
    }
}
