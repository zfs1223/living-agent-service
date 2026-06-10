package com.livingagent.core.model.pool;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "model_providers")
public class ProviderConfig {
    @Id
    @Column(name = "id", length = 50)
    private String id;

    @Column(name = "display_name", length = 100, nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "protocol", nullable = false, length = 20)
    private Protocol protocol;

    @Column(name = "base_url", length = 500)
    private String baseUrl;

    @Column(name = "api_key_encrypted", length = 500)
    private String apiKeyEncrypted;

    @Column(name = "enabled")
    private boolean enabled = true;

    @Column(name = "supports_tool_choice")
    private boolean supportsToolChoice = true;

    @Column(name = "default_max_tokens")
    private int defaultMaxTokens = 4096;

    @Column(name = "auto_discover_models")
    private boolean autoDiscoverModels = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public ProviderConfig() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public Protocol getProtocol() { return protocol; }
    public void setProtocol(Protocol protocol) { this.protocol = protocol; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) {
        if (baseUrl != null && !baseUrl.isEmpty()) {
            while (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
        }
        this.baseUrl = baseUrl;
    }

    public String getApiKeyEncrypted() { return apiKeyEncrypted; }
    public void setApiKeyEncrypted(String apiKeyEncrypted) { this.apiKeyEncrypted = apiKeyEncrypted; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isSupportsToolChoice() { return supportsToolChoice; }
    public void setSupportsToolChoice(boolean supportsToolChoice) { this.supportsToolChoice = supportsToolChoice; }

    public int getDefaultMaxTokens() { return defaultMaxTokens; }
    public void setDefaultMaxTokens(int defaultMaxTokens) { this.defaultMaxTokens = defaultMaxTokens; }

    public boolean isAutoDiscoverModels() { return autoDiscoverModels; }
    public void setAutoDiscoverModels(boolean autoDiscoverModels) { this.autoDiscoverModels = autoDiscoverModels; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public ProviderConfig cloneWithoutKey() {
        ProviderConfig copy = new ProviderConfig();
        copy.setId(this.id);
        copy.setDisplayName(this.displayName);
        copy.setProtocol(this.protocol);
        copy.setBaseUrl(this.baseUrl);
        copy.setApiKeyEncrypted("");
        copy.setEnabled(this.enabled);
        copy.setSupportsToolChoice(this.supportsToolChoice);
        copy.setDefaultMaxTokens(this.defaultMaxTokens);
        copy.setAutoDiscoverModels(this.autoDiscoverModels);
        copy.setCreatedAt(this.createdAt);
        copy.setUpdatedAt(this.updatedAt);
        return copy;
    }

    public String getMaskedApiKey() {
        if (apiKeyEncrypted == null || apiKeyEncrypted.isEmpty()) {
            return "";
        }
        if (apiKeyEncrypted.length() <= 8) {
            return "****";
        }
        return apiKeyEncrypted.substring(0, 4) + "****" + apiKeyEncrypted.substring(apiKeyEncrypted.length() - 4);
    }
}
