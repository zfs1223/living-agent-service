package com.livingagent.core.conversation;

/**
 * 对话状态枚举，统一全链路状态命名。
 * 数据库存储统一使用小写，与现有数据兼容。
 */
public enum ConversationStatus {
    ACTIVE("active", "活跃"),
    IDLE("idle", "空闲"),
    ARCHIVED("archived", "已归档"),
    DELETED("deleted", "已删除");

    private final String dbValue;
    private final String displayName;

    ConversationStatus(String dbValue, String displayName) {
        this.dbValue = dbValue;
        this.displayName = displayName;
    }

    public String getDbValue() {
        return dbValue;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 从数据库值解析枚举，兼容大小写
     */
    public static ConversationStatus fromDbValue(String value) {
        if (value == null) return ACTIVE;
        for (ConversationStatus status : values()) {
            if (status.dbValue.equalsIgnoreCase(value)) {
                return status;
            }
        }
        return ACTIVE;
    }

    /**
     * 判断给定字符串是否为活跃状态（ACTIVE 或 IDLE）
     */
    public static boolean isActiveStatus(String value) {
        ConversationStatus status = fromDbValue(value);
        return status == ACTIVE || status == IDLE;
    }

    /**
     * 获取所有活跃状态的数据库值列表
     */
    public static java.util.List<String> activeDbValues() {
        return java.util.List.of(ACTIVE.dbValue, IDLE.dbValue);
    }
}
