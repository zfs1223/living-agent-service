package com.livingagent.core.model.selector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * MainBrain model selector - compatibility adapter layer.
 * The actual model resolution is handled by BrainModelResolver via the model pool.
 * This class only provides fallback configuration from application.yml.
 */
@Component
public class MainBrainModelSelector {

    private static final Logger log = LoggerFactory.getLogger(MainBrainModelSelector.class);

    @Value("${main-brain.model.default:model-pool}")
    private String defaultModelId;

    @Value("${main-brain.model.provider:model-pool}")
    private String defaultProvider;

    @Value("${main-brain.model.base-url:}")
    private String baseUrl;

    @Value("${main-brain.model.api-key:}")
    private String apiKey;

    private final AtomicReference<String> currentModelId = new AtomicReference<>();

    public MainBrainModelSelector() {
        currentModelId.set(defaultModelId);
        log.info("MainBrainModelSelector initialized with default model: {}", defaultModelId);
    }

    public String getEffectiveModelId() {
        String current = currentModelId.get();
        return current != null ? current : defaultModelId;
    }

    public void setCurrentModel(String modelId) {
        String previous = currentModelId.getAndSet(modelId);
        log.info("Main brain model changed: {} -> {}", previous, modelId);
    }

    public String getCurrentModelDisplayName() {
        return getEffectiveModelId();
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getBaseUrl() {
        if (baseUrl != null && !baseUrl.isEmpty()) {
            return baseUrl;
        }
        return getDefaultBaseUrl();
    }

    private String getDefaultBaseUrl() {
        return switch (defaultProvider.toLowerCase()) {
            case "qwen", "阿里云" -> "https://dashscope.aliyuncs.com/compatible-mode/v1";
            case "deepseek" -> "https://api.deepseek.com/v1";
            case "model-pool" -> "";
            default -> "";
        };
    }

    public ModelConfig getModelConfig() {
        return new ModelConfig(
            getEffectiveModelId(),
            getEffectiveModelId(),
            defaultProvider,
            32768,
            getBaseUrl(),
            apiKey != null && !apiKey.isEmpty()
        );
    }

    public List<BrainModelSelector.BrainModel> getAvailableModels() {
        return Collections.emptyList();
    }

    public record ModelConfig(
        String modelId,
        String displayName,
        String provider,
        int contextLength,
        String baseUrl,
        boolean hasApiKey
    ) {}
}
