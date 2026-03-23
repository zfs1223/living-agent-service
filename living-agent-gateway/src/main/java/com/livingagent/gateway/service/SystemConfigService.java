package com.livingagent.gateway.service;

import com.livingagent.core.security.auth.FounderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SystemConfigService {

    private static final Logger log = LoggerFactory.getLogger(SystemConfigService.class);

    private final FounderService founderService;
    
    private String companyName = "Living Agent";
    private String companyLogo;
    private String defaultModel = "qwen_local";
    private final Map<String, Object> settings = new ConcurrentHashMap<>();
    private final Map<String, ProviderConfig> providerConfigs = new ConcurrentHashMap<>();

    public SystemConfigService(FounderService founderService) {
        this.founderService = founderService;
        initDefaultProviders();
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
        
        log.info("System config updated");
        return getSystemConfig();
    }

    public List<ProviderConfig> getAvailableProviders() {
        return new ArrayList<>(providerConfigs.values());
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
        log.info("Provider config updated: {}", providerId);
        return updated;
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
}
