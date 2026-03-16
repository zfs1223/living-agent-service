package com.livingagent.core.knowledge;

public enum KnowledgeType {
    FACT("fact"),
    PROCESS("process"),
    EXPERIENCE("experience"),
    BEST_PRACTICE("best_practice"),
    TEMPORARY("temporary");
    
    private final String value;
    
    KnowledgeType(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
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
