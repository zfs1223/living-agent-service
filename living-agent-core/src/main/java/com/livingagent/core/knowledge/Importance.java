package com.livingagent.core.knowledge;

public enum Importance {
    HIGH("high", 1.0),
    MEDIUM("medium", 0.6),
    LOW("low", 0.3);
    
    private final String value;
    private final double weight;
    
    Importance(String value, double weight) {
        this.value = value;
        this.weight = weight;
    }
    
    public String getValue() {
        return value;
    }
    
    public double getWeight() {
        return weight;
    }
    
    public static Importance fromString(String value) {
        if (value == null) return MEDIUM;
        for (Importance imp : values()) {
            if (imp.value.equalsIgnoreCase(value)) {
                return imp;
            }
        }
        return MEDIUM;
    }
}
