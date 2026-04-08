package com.livingagent.core.knowledge;

public enum KnowledgeScope {
    L1_PRIVATE("profile", "私有知识 - 仅所有者可访问"),
    L2_DEPARTMENT("department", "部门知识 - 部门成员可访问"),
    L3_SHARED("shared", "共享知识 - 所有正式员工可访问");

    private final String prefix;
    private final String description;

    KnowledgeScope(String prefix, String description) {
        this.prefix = prefix;
        this.description = description;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getDescription() {
        return description;
    }

    public String buildNamespace(String identifier) {
        return switch (this) {
            case L1_PRIVATE -> "profile:" + identifier + ":private";
            case L2_DEPARTMENT -> "department:" + identifier;
            case L3_SHARED -> "shared:global";
        };
    }

    public static KnowledgeScope fromNamespace(String namespace) {
        if (namespace == null || namespace.isEmpty()) {
            return L3_SHARED;
        }
        if (namespace.startsWith("profile:")) {
            return L1_PRIVATE;
        }
        if (namespace.startsWith("department:")) {
            return L2_DEPARTMENT;
        }
        return L3_SHARED;
    }

    public static KnowledgeScope fromLevel(int level) {
        return switch (level) {
            case 1 -> L1_PRIVATE;
            case 2 -> L2_DEPARTMENT;
            default -> L3_SHARED;
        };
    }
}
