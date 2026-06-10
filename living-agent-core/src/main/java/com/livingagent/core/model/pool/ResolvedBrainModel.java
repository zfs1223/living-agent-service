package com.livingagent.core.model.pool;

import java.util.UUID;

public class ResolvedBrainModel {
    private final UUID modelId;
    private final String providerId;
    private final String modelName;
    private final String displayName;
    private final String baseUrl;
    private final String apiKey;
    private final Protocol protocol;
    private final int contextLength;
    private final int maxTokens;
    private final double temperature;
    private final boolean supportsToolChoice;

    public ResolvedBrainModel(UUID modelId, String providerId, String modelName, String displayName,
                              String baseUrl, String apiKey, Protocol protocol,
                              int contextLength, int maxTokens, double temperature, boolean supportsToolChoice) {
        this.modelId = modelId;
        this.providerId = providerId;
        this.modelName = modelName;
        this.displayName = displayName;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.protocol = protocol;
        this.contextLength = contextLength;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.supportsToolChoice = supportsToolChoice;
    }

    public UUID getModelId() { return modelId; }
    public String getProviderId() { return providerId; }
    public String getModelName() { return modelName; }
    public String getDisplayName() { return displayName; }
    public String getBaseUrl() { return baseUrl; }
    public String getApiKey() { return apiKey; }
    public Protocol getProtocol() { return protocol; }
    public int getContextLength() { return contextLength; }
    public int getMaxTokens() { return maxTokens; }
    public double getTemperature() { return temperature; }
    public boolean isSupportsToolChoice() { return supportsToolChoice; }
}
