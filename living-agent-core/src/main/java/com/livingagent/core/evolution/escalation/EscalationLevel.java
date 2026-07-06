package com.livingagent.core.evolution.escalation;

/**
 * 升级级别
 * WARNING: 性能下降、知识质量下降、非关键错误
 * CRITICAL: 熔断触发、连续失败、高风险任务失败
 * EMERGENCY: 安全违规、数据损坏、系统不可用
 */
public enum EscalationLevel {
    WARNING("warning", "警告 - 非紧急问题，需要关注"),
    CRITICAL("critical", "严重 - 需要尽快处理"),
    EMERGENCY("emergency", "紧急 - 系统面临严重风险");

    private final String value;
    private final String description;

    EscalationLevel(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String getValue() { return value; }
    public String getDescription() { return description; }

    public static EscalationLevel fromString(String value) {
        if (value == null) return WARNING;
        for (EscalationLevel level : values()) {
            if (level.value.equalsIgnoreCase(value)) return level;
        }
        return WARNING;
    }
}
