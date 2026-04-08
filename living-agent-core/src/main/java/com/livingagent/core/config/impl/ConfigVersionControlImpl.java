package com.livingagent.core.config.impl;

import com.livingagent.core.config.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ConfigVersionControlImpl implements ConfigVersionControl {

    private static final Logger log = LoggerFactory.getLogger(ConfigVersionControlImpl.class);

    private final ConfigVersionRepository repository;

    public ConfigVersionControlImpl(ConfigVersionRepository repository) {
        this.repository = repository;
    }

    @Override
    public ConfigVersion createConfig(String configType, String configKey, String configValue, String reason, String changedBy) {
        Optional<ConfigVersionEntity> existing = repository.findByConfigTypeAndConfigKeyAndIsActiveTrue(configType, configKey);
        if (existing.isPresent()) {
            throw new IllegalStateException("Config already exists: " + configType + "/" + configKey);
        }
        
        String versionId = "cfg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        
        ConfigVersionEntity entity = new ConfigVersionEntity();
        entity.setVersionId(versionId);
        entity.setConfigType(configType);
        entity.setConfigKey(configKey);
        entity.setVersionNumber(1);
        entity.setConfigValue(configValue);
        entity.setPreviousValue(null);
        entity.setChangeReason(reason);
        entity.setChangedBy(changedBy);
        entity.setChangedAt(Instant.now());
        entity.setActive(true);
        
        repository.save(entity);
        
        log.info("Created config: {}/{} version 1 by {}", configType, configKey, changedBy);
        
        return toConfigVersion(entity);
    }

    @Override
    public ConfigVersion updateConfig(String configType, String configKey, String newValue, String reason, String changedBy) {
        Optional<ConfigVersionEntity> currentOpt = repository.findByConfigTypeAndConfigKeyAndIsActiveTrue(configType, configKey);
        
        if (currentOpt.isEmpty()) {
            return createConfig(configType, configKey, newValue, reason, changedBy);
        }
        
        ConfigVersionEntity current = currentOpt.get();
        
        int newVersionNumber = repository.findMaxVersionNumber(configType, configKey).orElse(0) + 1;
        
        current.setActive(false);
        repository.save(current);
        
        String versionId = "cfg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        
        ConfigVersionEntity newVersion = new ConfigVersionEntity();
        newVersion.setVersionId(versionId);
        newVersion.setConfigType(configType);
        newVersion.setConfigKey(configKey);
        newVersion.setVersionNumber(newVersionNumber);
        newVersion.setConfigValue(newValue);
        newVersion.setPreviousValue(current.getConfigValue());
        newVersion.setChangeReason(reason);
        newVersion.setChangedBy(changedBy);
        newVersion.setChangedAt(Instant.now());
        newVersion.setActive(true);
        
        repository.save(newVersion);
        
        log.info("Updated config: {}/{} to version {} by {}", configType, configKey, newVersionNumber, changedBy);
        
        return toConfigVersion(newVersion);
    }

    @Override
    public Optional<ConfigVersion> getActiveConfig(String configType, String configKey) {
        return repository.findByConfigTypeAndConfigKeyAndIsActiveTrue(configType, configKey)
                .map(this::toConfigVersion);
    }

    @Override
    public Optional<ConfigVersion> getConfigByVersion(String configType, String configKey, int versionNumber) {
        return repository.findByConfigTypeAndConfigKeyAndVersionNumber(configType, configKey, versionNumber)
                .map(this::toConfigVersion);
    }

    @Override
    public List<ConfigVersion> getConfigHistory(String configType, String configKey) {
        return repository.findByConfigTypeAndConfigKeyOrderByVersionNumberDesc(configType, configKey)
                .stream()
                .map(this::toConfigVersion)
                .collect(Collectors.toList());
    }

    @Override
    public List<ConfigVersion> getConfigHistory(String configType, String configKey, int limit) {
        return repository.findByConfigTypeAndConfigKeyOrderByVersionNumberDesc(configType, configKey)
                .stream()
                .limit(limit)
                .map(this::toConfigVersion)
                .collect(Collectors.toList());
    }

    @Override
    public boolean rollbackToVersion(String configType, String configKey, int versionNumber, String reason, String changedBy) {
        Optional<ConfigVersionEntity> targetOpt = repository.findByConfigTypeAndConfigKeyAndVersionNumber(configType, configKey, versionNumber);
        
        if (targetOpt.isEmpty()) {
            log.warn("Cannot rollback: version {} not found for {}/{}", versionNumber, configType, configKey);
            return false;
        }
        
        ConfigVersionEntity target = targetOpt.get();
        Optional<ConfigVersionEntity> currentOpt = repository.findByConfigTypeAndConfigKeyAndIsActiveTrue(configType, configKey);
        
        currentOpt.ifPresent(current -> {
            current.setActive(false);
            repository.save(current);
        });
        
        String versionId = "cfg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        int newVersionNumber = repository.findMaxVersionNumber(configType, configKey).orElse(0) + 1;
        
        ConfigVersionEntity rollbackVersion = new ConfigVersionEntity();
        rollbackVersion.setVersionId(versionId);
        rollbackVersion.setConfigType(configType);
        rollbackVersion.setConfigKey(configKey);
        rollbackVersion.setVersionNumber(newVersionNumber);
        rollbackVersion.setConfigValue(target.getConfigValue());
        rollbackVersion.setPreviousValue(currentOpt.map(ConfigVersionEntity::getConfigValue).orElse(null));
        rollbackVersion.setChangeReason("Rollback to version " + versionNumber + ": " + reason);
        rollbackVersion.setChangedBy(changedBy);
        rollbackVersion.setChangedAt(Instant.now());
        rollbackVersion.setActive(true);
        
        repository.save(rollbackVersion);
        
        log.info("Rolled back config: {}/{} to version {} (new version {}) by {}", 
                configType, configKey, versionNumber, newVersionNumber, changedBy);
        
        return true;
    }

    @Override
    public void deactivateConfig(String configType, String configKey) {
        repository.findByConfigTypeAndConfigKeyAndIsActiveTrue(configType, configKey).ifPresent(entity -> {
            entity.setActive(false);
            repository.save(entity);
            log.info("Deactivated config: {}/{}", configType, configKey);
        });
    }

    @Override
    public List<ConfigVersion> getAllConfigsByType(String configType) {
        return repository.findByConfigTypeAndIsActiveTrue(configType)
                .stream()
                .map(this::toConfigVersion)
                .collect(Collectors.toList());
    }

    private ConfigVersion toConfigVersion(ConfigVersionEntity entity) {
        return new ConfigVersion(
            entity.getVersionId(),
            entity.getConfigType(),
            entity.getConfigKey(),
            entity.getVersionNumber(),
            entity.getConfigValue(),
            entity.getPreviousValue(),
            entity.getChangeReason(),
            entity.getChangedBy(),
            entity.getChangedAt(),
            entity.isActive()
        );
    }
}
