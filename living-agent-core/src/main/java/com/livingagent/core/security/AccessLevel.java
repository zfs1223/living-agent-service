package com.livingagent.core.security;

import java.util.Set;

public enum AccessLevel {
    
    CHAT_ONLY(0, "仅闲聊", 
        Set.of("Qwen3-0.6B"), 
        Set.of(), 
        false),
    
    LIMITED(1, "受限访问", 
        Set.of("Qwen3.5-27B", "Qwen3-0.6B"), 
        Set.of("AdminBrain", "CsBrain"), 
        true),
    
    DEPARTMENT(2, "部门访问", 
        Set.of("Qwen3.5-27B", "Qwen3-0.6B", "BitNet-1.58-3B"), 
        Set.of("TechBrain", "HrBrain", "FinanceBrain", "SalesBrain", 
               "CsBrain", "AdminBrain", "LegalBrain", "OpsBrain"), 
        true),
    
    FULL(3, "完全访问", 
        Set.of("Qwen3.5-27B", "Qwen3-0.6B", "BitNet-1.58-3B"), 
        Set.of("TechBrain", "HrBrain", "FinanceBrain", "SalesBrain", 
               "CsBrain", "AdminBrain", "LegalBrain", "OpsBrain", "MainBrain"), 
        true);

    private final int level;
    private final String description;
    private final Set<String> allowedModels;
    private final Set<String> allowedBrains;
    private final boolean canAccessKnowledge;

    AccessLevel(int level, String description, Set<String> allowedModels, 
                Set<String> allowedBrains, boolean canAccessKnowledge) {
        this.level = level;
        this.description = description;
        this.allowedModels = allowedModels;
        this.allowedBrains = allowedBrains;
        this.canAccessKnowledge = canAccessKnowledge;
    }

    public int getLevel() { return level; }
    public String getDescription() { return description; }
    public Set<String> getAllowedModels() { return allowedModels; }
    public Set<String> getAllowedBrains() { return allowedBrains; }
    public boolean canAccessKnowledge() { return canAccessKnowledge; }

    public boolean canUseModel(String modelName) {
        return allowedModels.contains(modelName);
    }

    public boolean canAccessBrain(String brainName) {
        return allowedBrains.contains(brainName);
    }

    public boolean isHigherThan(AccessLevel other) {
        return this.level > other.level;
    }

    public boolean isLowerThan(AccessLevel other) {
        return this.level < other.level;
    }
}
