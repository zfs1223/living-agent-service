package com.livingagent.core.config;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ConfigVersionControl {

    ConfigVersion createConfig(String configType, String configKey, String configValue, String reason, String changedBy);
    
    ConfigVersion updateConfig(String configType, String configKey, String newValue, String reason, String changedBy);
    
    Optional<ConfigVersion> getActiveConfig(String configType, String configKey);
    
    Optional<ConfigVersion> getConfigByVersion(String configType, String configKey, int versionNumber);
    
    List<ConfigVersion> getConfigHistory(String configType, String configKey);
    
    List<ConfigVersion> getConfigHistory(String configType, String configKey, int limit);
    
    boolean rollbackToVersion(String configType, String configKey, int versionNumber, String reason, String changedBy);
    
    void deactivateConfig(String configType, String configKey);
    
    List<ConfigVersion> getAllConfigsByType(String configType);
    
    enum ConfigType {
        SYSTEM,
        BRAIN,
        NEURON,
        SKILL,
        PERSONALITY,
        KNOWLEDGE,
        SECURITY,
        INTEGRATION,
        PAYOUT
    }
    
    record ConfigVersion(
        String versionId,
        String configType,
        String configKey,
        int versionNumber,
        String configValue,
        String previousValue,
        String changeReason,
        String changedBy,
        Instant changedAt,
        boolean isActive
    ) {}
}
