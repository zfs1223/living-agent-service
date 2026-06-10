package com.livingagent.gateway.service;

import com.livingagent.core.database.entity.TenantEntity;
import com.livingagent.core.database.service.TenantService;
import com.livingagent.core.security.auth.FounderService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SystemConfigService {

    private static final Logger log = LoggerFactory.getLogger(SystemConfigService.class);

    private final FounderService founderService;
    private final TenantService tenantService;

    private String companyName = "Living Agent";
    private String companyLogo;
    private String defaultModel = "qwen_local";
    private final Map<String, Object> settings = new ConcurrentHashMap<>();
    private final Map<String, ProviderConfig> providerConfigs = new ConcurrentHashMap<>();
    private final List<ConfigChangeRecord> changeHistory = new CopyOnWriteArrayList<>();

    public SystemConfigService(FounderService founderService, TenantService tenantService) {
        this.founderService = founderService;
        this.tenantService = tenantService;
        initDefaultProviders();
    }

    @PostConstruct
    public void initializeDefaultTenant() {
        String defaultTenantId = "tenant_default";
        if (tenantService.exists(defaultTenantId)) {
            log.info("Default tenant {} already exists in database, skipping initialization", defaultTenantId);
            return;
        }
        tenantService.createTenant(defaultTenantId, "Living Agent", "system");
        log.info("Initialized default tenant in database: {}", defaultTenantId);
    }

    private void initDefaultProviders() {
        providerConfigs.put("openai", new ProviderConfig(
            "openai", "OpenAI", null, null,
            "https://api.openai.com/v1", true
        ));
        
        providerConfigs.put("anthropic", new ProviderConfig(
            "anthropic", "Anthropic (Claude)", null, null,
            "https://api.anthropic.com", false
        ));
        
        providerConfigs.put("deepseek", new ProviderConfig(
            "deepseek", "DeepSeek", null, null,
            "https://api.deepseek.com", false
        ));
        
        providerConfigs.put("qwen_local", new ProviderConfig(
            "qwen_local", "Qwen Local (Ollama)", null, null,
            "http://localhost:11434/v1", true
        ));
    }

    public boolean isConfigured() {
        return providerConfigs.values().stream()
                .anyMatch(p -> p.enabled() && p.apiKey() != null && !p.apiKey().isBlank());
    }

    public List<String> getConfiguredProviders() {
        return providerConfigs.values().stream()
                .filter(p -> p.enabled() && p.apiKey() != null && !p.apiKey().isBlank())
                .map(ProviderConfig::providerId)
                .toList();
    }

    public SystemConfig getSystemConfig() {
        return new SystemConfig(
            companyName,
            companyLogo,
            defaultModel,
            new HashMap<>(providerConfigs),
            new HashMap<>(settings)
        );
    }

    public SystemConfig updateSystemConfig(SystemConfigUpdateRequest request) {
        String before = snapshotConfig();
        if (request.companyName() != null) {
            this.companyName = request.companyName();
        }
        if (request.companyLogo() != null) {
            this.companyLogo = request.companyLogo();
        }
        if (request.defaultModel() != null) {
            this.defaultModel = request.defaultModel();
        }
        if (request.settings() != null) {
            this.settings.putAll(request.settings());
        }
        recordChange("system.config", before, snapshotConfig(), "System config updated");
        log.info("System config updated");
        return getSystemConfig();
    }

    public Map<String, ProviderConfig> getProviderConfigs() {
        return new LinkedHashMap<>(providerConfigs);
    }

    public List<ProviderConfig> getAvailableProviders() {
        return new ArrayList<>(providerConfigs.values());
    }

    public Map<String, Object> getSettings() {
        return new LinkedHashMap<>(settings);
    }

    public ProviderConfig getProviderConfig(String providerId) {
        return providerConfigs.get(providerId);
    }

    public ProviderConfig updateProviderConfig(String providerId, ProviderConfigUpdateRequest request) {
        ProviderConfig existing = providerConfigs.get(providerId);
        if (existing == null) {
            throw new IllegalArgumentException("Provider not found: " + providerId);
        }

        ProviderConfig updated = new ProviderConfig(
            existing.providerId(),
            existing.name(),
            request.apiKey() != null ? request.apiKey() : existing.apiKey(),
            request.apiSecret() != null ? request.apiSecret() : existing.apiSecret(),
            request.baseUrl() != null ? request.baseUrl() : existing.baseUrl(),
            request.enabled() != null ? request.enabled() : existing.enabled()
        );

        providerConfigs.put(providerId, updated);
        recordChange("provider." + providerId, existing, updated, "Provider config updated");
        log.info("Provider config updated: {}", providerId);
        return updated;
    }

    public ProviderConfig createProviderConfig(ProviderConfig config) {
        String providerId = config.providerId();
        if (providerId == null || providerId.isBlank()) {
            providerId = "custom_" + System.currentTimeMillis();
        }
        ProviderConfig newConfig = new ProviderConfig(
            providerId,
            config.name(),
            config.apiKey(),
            config.apiSecret(),
            config.baseUrl(),
            config.enabled()
        );
        providerConfigs.put(providerId, newConfig);
        recordChange("provider." + providerId, null, newConfig, "Provider config created");
        log.info("Created provider config: {}", providerId);
        return newConfig;
    }

    public boolean deleteProviderConfig(String providerId) {
        if (providerConfigs.containsKey(providerId)) {
            ProviderConfig removed = providerConfigs.remove(providerId);
            recordChange("provider." + providerId, removed, null, "Provider config deleted");
            log.info("Deleted provider config: {}", providerId);
            return true;
        }
        return false;
    }

    public boolean removeProviderConfig(String providerId) {
        return deleteProviderConfig(providerId);
    }

    public ProviderConfig enableProviderConfig(String providerId, boolean enabled) {
        ProviderConfig existing = providerConfigs.get(providerId);
        if (existing == null) {
            return null;
        }
        ProviderConfig updated = new ProviderConfig(
            existing.providerId(),
            existing.name(),
            existing.apiKey(),
            existing.apiSecret(),
            existing.baseUrl(),
            enabled
        );
        providerConfigs.put(providerId, updated);
        log.info("Provider {} enabled: {}", providerId, enabled);
        return updated;
    }

    public void createTenantWithCompany(String tenantId, String companyName, String ownerId) {
        tenantService.createTenant(tenantId, companyName, ownerId);
        log.info("Created tenant in database: {} with company name: {}", tenantId, companyName);
    }

    public TenantInfo getTenant(String tenantId) {
        return tenantService.findById(tenantId).map(this::toTenantInfo).orElse(null);
    }

    public TenantInfo updateTenant(String tenantId, String name) {
        return tenantService.updateName(tenantId, name)
            .map(this::toTenantInfo)
            .orElse(null);
    }

    private TenantInfo toTenantInfo(TenantEntity entity) {
        return new TenantInfo(
            entity.getTenantId(),
            entity.getName(),
            entity.getNameEn(),
            entity.getDescription(),
            entity.getWebsite(),
            entity.getCreatedAt(),
            entity.isActive(),
            entity.getOwnerId()
        );
    }

    public record SystemConfig(
        String companyName,
        String companyLogo,
        String defaultModel,
        Map<String, ProviderConfig> providers,
        Map<String, Object> settings
    ) {}

    public record ProviderConfig(
        String providerId,
        String name,
        String apiKey,
        String apiSecret,
        String baseUrl,
        boolean enabled
    ) {}

    public record SystemConfigUpdateRequest(
        String companyName,
        String companyLogo,
        String defaultModel,
        Map<String, Object> settings
    ) {}

    public record ProviderConfigUpdateRequest(
        String apiKey,
        String apiSecret,
        String baseUrl,
        Boolean enabled
    ) {}

    public record TenantInfo(
        String tenantId,
        String name,
        String nameEn,
        String description,
        String website,
        java.time.Instant createdAt,
        boolean active,
        String ownerId
    ) {}

    public record ConfigChangeRecord(
        String changeId,
        String target,
        Object beforeValue,
        Object afterValue,
        String reason,
        String changedAt
    ) {}

    private void recordChange(String target, Object beforeValue, Object afterValue, String reason) {
        changeHistory.add(new ConfigChangeRecord(
                UUID.randomUUID().toString(),
                target,
                beforeValue,
                afterValue,
                reason,
                java.time.Instant.now().toString()
        ));
        if (changeHistory.size() > 200) {
            changeHistory.remove(0);
        }
    }

    private String snapshotConfig() {
        return companyName + "|" + companyLogo + "|" + defaultModel + "|" + settings.toString();
    }
}
