package com.livingagent.core.evolution.circuitbreaker;

public enum CircuitTripReason {
    REPAIR_LOOP("连续修复循环，需要创新"),
    FAILURE_STREAK("连续失败，需要策略变更"),
    EMPTY_CYCLE("连续空循环，进化饱和"),
    SATURATION("进化空间耗尽，进入稳态");
    
    private final String description;
    
    CircuitTripReason(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}
