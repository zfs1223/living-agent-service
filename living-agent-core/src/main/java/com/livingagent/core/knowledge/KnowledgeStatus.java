package com.livingagent.core.knowledge;

public enum KnowledgeStatus {

    DRAFT("draft", "草稿 - 仅创建者可见"),
    PUBLISHED("published", "已发布 - 按作用域可见"),
    DEPRECATED("deprecated", "已降级 - 即将淘汰，仍可检索但标记过时"),
    ARCHIVED("archived", "已归档 - 不可检索");

    private final String code;
    private final String description;

    KnowledgeStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() { return code; }
    public String getDescription() { return description; }

    public static KnowledgeStatus fromCode(String code) {
        if (code == null) return DRAFT;
        for (KnowledgeStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        return DRAFT;
    }
}
