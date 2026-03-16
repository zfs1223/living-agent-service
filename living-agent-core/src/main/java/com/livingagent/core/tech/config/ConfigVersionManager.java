package com.livingagent.core.tech.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ConfigVersionManager {

    private static final Logger log = LoggerFactory.getLogger(ConfigVersionManager.class);

    private final Map<String, ConfigEntry> configs = new ConcurrentHashMap<>();
    private final Map<String, List<ConfigVersion>> versionHistory = new ConcurrentHashMap<>();
    private final Map<String, List<ConfigChangeAudit>> changeAudits = new ConcurrentHashMap<>();
    private final int maxVersions = 50;

    public ConfigEntry createConfig(String configKey, String value, String changedBy, String reason) {
        String configId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        
        ConfigVersion version = new ConfigVersion(
            1,
            value,
            now,
            changedBy,
            reason,
            null
        );
        
        ConfigEntry entry = new ConfigEntry(
            configId,
            configKey,
            value,
            1,
            now,
            changedBy,
            ConfigStatus.ACTIVE
        );
        
        configs.put(configKey, entry);
        
        List<ConfigVersion> versions = new CopyOnWriteArrayList<>();
        versions.add(version);
        versionHistory.put(configKey, versions);
        
        recordAudit(configKey, "CREATE", null, value, changedBy, reason);
        
        log.info("Created config: {} by {}", configKey, changedBy);
        return entry;
    }

    public ConfigEntry updateConfig(String configKey, String newValue, String changedBy, String reason) {
        ConfigEntry current = configs.get(configKey);
        if (current == null) {
            throw new IllegalArgumentException("Config not found: " + configKey);
        }
        
        String oldValue = current.value();
        int newVersion = current.version() + 1;
        Instant now = Instant.now();
        
        ConfigVersion version = new ConfigVersion(
            newVersion,
            newValue,
            now,
            changedBy,
            reason,
            oldValue
        );
        
        ConfigEntry updated = new ConfigEntry(
            current.configId(),
            configKey,
            newValue,
            newVersion,
            now,
            changedBy,
            ConfigStatus.ACTIVE
        );
        
        configs.put(configKey, updated);
        
        List<ConfigVersion> versions = versionHistory.get(configKey);
        versions.add(version);
        
        while (versions.size() > maxVersions) {
            versions.remove(0);
        }
        
        recordAudit(configKey, "UPDATE", oldValue, newValue, changedBy, reason);
        
        log.info("Updated config: {} v{} by {} - {}", configKey, newVersion, changedBy, reason);
        return updated;
    }

    public ConfigEntry deleteConfig(String configKey, String changedBy, String reason) {
        ConfigEntry current = configs.get(configKey);
        if (current == null) {
            throw new IllegalArgumentException("Config not found: " + configKey);
        }
        
        ConfigEntry deleted = new ConfigEntry(
            current.configId(),
            configKey,
            current.value(),
            current.version(),
            Instant.now(),
            changedBy,
            ConfigStatus.DELETED
        );
        
        configs.put(configKey, deleted);
        
        recordAudit(configKey, "DELETE", current.value(), null, changedBy, reason);
        
        log.info("Deleted config: {} by {}", configKey, changedBy);
        return deleted;
    }

    public Optional<ConfigEntry> getConfig(String configKey) {
        ConfigEntry entry = configs.get(configKey);
        if (entry != null && entry.status() == ConfigStatus.ACTIVE) {
            return Optional.of(entry);
        }
        return Optional.empty();
    }

    public String getConfigValue(String configKey, String defaultValue) {
        return getConfig(configKey)
            .map(ConfigEntry::value)
            .orElse(defaultValue);
    }

    public List<ConfigVersion> getVersionHistory(String configKey) {
        return new ArrayList<>(versionHistory.getOrDefault(configKey, List.of()));
    }

    public Optional<ConfigEntry> rollback(String configKey, int targetVersion, String changedBy, String reason) {
        List<ConfigVersion> versions = versionHistory.get(configKey);
        if (versions == null || versions.isEmpty()) {
            return Optional.empty();
        }
        
        Optional<ConfigVersion> targetOpt = versions.stream()
            .filter(v -> v.version() == targetVersion)
            .findFirst();
            
        if (targetOpt.isEmpty()) {
            return Optional.empty();
        }
        
        ConfigVersion target = targetOpt.get();
        ConfigEntry rolledBack = updateConfig(configKey, target.value(), changedBy, 
            "Rollback to v" + targetVersion + ": " + reason);
        
        recordAudit(configKey, "ROLLBACK", configs.get(configKey).value(), target.value(), 
            changedBy, "Rollback to v" + targetVersion);
        
        log.info("Rolled back config: {} to v{} by {}", configKey, targetVersion, changedBy);
        return Optional.of(rolledBack);
    }

    public Optional<ConfigEntry> rollbackToPrevious(String configKey, String changedBy, String reason) {
        List<ConfigVersion> versions = versionHistory.get(configKey);
        if (versions == null || versions.size() < 2) {
            return Optional.empty();
        }
        
        int currentVersion = versions.size();
        return rollback(configKey, currentVersion - 1, changedBy, reason);
    }

    public List<ConfigEntry> listConfigs() {
        return configs.values().stream()
            .filter(e -> e.status() == ConfigStatus.ACTIVE)
            .sorted(Comparator.comparing(ConfigEntry::configKey))
            .toList();
    }

    public List<ConfigEntry> listConfigsByPrefix(String prefix) {
        return configs.entrySet().stream()
            .filter(e -> e.getKey().startsWith(prefix))
            .filter(e -> e.getValue().status() == ConfigStatus.ACTIVE)
            .map(Map.Entry::getValue)
            .sorted(Comparator.comparing(ConfigEntry::configKey))
            .toList();
    }

    public List<ConfigChangeAudit> getAuditLog(String configKey) {
        return new ArrayList<>(changeAudits.getOrDefault(configKey, List.of()));
    }

    public List<ConfigChangeAudit> getAuditLogByUser(String changedBy) {
        return changeAudits.values().stream()
            .flatMap(List::stream)
            .filter(a -> a.changedBy().equals(changedBy))
            .sorted(Comparator.comparing(ConfigChangeAudit::timestamp).reversed())
            .toList();
    }

    public ConfigDiff compareVersions(String configKey, int version1, int version2) {
        List<ConfigVersion> versions = versionHistory.get(configKey);
        if (versions == null) {
            return null;
        }
        
        Optional<ConfigVersion> v1 = versions.stream().filter(v -> v.version() == version1).findFirst();
        Optional<ConfigVersion> v2 = versions.stream().filter(v -> v.version() == version2).findFirst();
        
        if (v1.isEmpty() || v2.isEmpty()) {
            return null;
        }
        
        return new ConfigDiff(
            configKey,
            v1.get(),
            v2.get(),
            !v1.get().value().equals(v2.get().value())
        );
    }

    public ConfigStatistics getStatistics() {
        int totalConfigs = (int) configs.values().stream()
            .filter(e -> e.status() == ConfigStatus.ACTIVE)
            .count();
        
        int totalVersions = versionHistory.values().stream()
            .mapToInt(List::size)
            .sum();
        
        int totalChanges = changeAudits.values().stream()
            .mapToInt(List::size)
            .sum();
        
        return new ConfigStatistics(
            totalConfigs,
            totalVersions,
            totalChanges,
            configs.size()
        );
    }

    private void recordAudit(String configKey, String action, String oldValue, String newValue, 
                            String changedBy, String reason) {
        ConfigChangeAudit audit = new ConfigChangeAudit(
            UUID.randomUUID().toString(),
            configKey,
            action,
            oldValue,
            newValue,
            Instant.now(),
            changedBy,
            reason
        );
        
        changeAudits.computeIfAbsent(configKey, k -> new CopyOnWriteArrayList<>()).add(audit);
    }

    public record ConfigEntry(
        String configId,
        String configKey,
        String value,
        int version,
        Instant updatedAt,
        String updatedBy,
        ConfigStatus status
    ) {}

    public record ConfigVersion(
        int version,
        String value,
        Instant timestamp,
        String changedBy,
        String reason,
        String previousValue
    ) {}

    public record ConfigChangeAudit(
        String auditId,
        String configKey,
        String action,
        String oldValue,
        String newValue,
        Instant timestamp,
        String changedBy,
        String reason
    ) {}

    public record ConfigDiff(
        String configKey,
        ConfigVersion version1,
        ConfigVersion version2,
        boolean hasChanges
    ) {}

    public record ConfigStatistics(
        int totalConfigs,
        int totalVersions,
        int totalChanges,
        int totalKeys
    ) {}

    public enum ConfigStatus {
        ACTIVE,
        DELETED,
        ARCHIVED
    }
}
