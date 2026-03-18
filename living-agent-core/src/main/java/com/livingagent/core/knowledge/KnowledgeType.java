package com.livingagent.core.knowledge;

public enum KnowledgeType {
    FACT("fact", "事实知识"),
    PROCESS("process", "过程知识"),
    EXPERIENCE("experience", "经验知识"),
    BEST_PRACTICE("best_practice", "最佳实践"),
    TEMPORARY("temporary", "临时知识");
    
    private final String value;
    private final String description;
    
    KnowledgeType(String value, String description) {
        this.value = value;
        this.description = description;
    }
    
    public String getValue() {
        return value;
    }
    
    public String getDescription() {
        return description;
    }
    
    public static KnowledgeType fromString(String value) {
        if (value == null) return FACT;
        for (KnowledgeType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return FACT;
    }
}
