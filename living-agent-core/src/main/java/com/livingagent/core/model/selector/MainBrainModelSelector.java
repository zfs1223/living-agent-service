package com.livingagent.core.model.selector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class MainBrainModelSelector {

    private static final Logger log = LoggerFactory.getLogger(MainBrainModelSelector.class);

    public enum MainBrainModel {
        QWEN35_27B("qwen3.5-27b", "Qwen3.5-27B", "阿里云", 32 * 1024, true, true, "复杂推理、跨部门协调、战略决策"),
        QWEN35_14B("qwen3.5-14b", "Qwen3.5-14B", "阿里云", 16 * 1024, true, false, "中等复杂度任务"),
        QWEN3_32B("qwen3-32b", "Qwen3-32B", "阿里云", 32 * 1024, true, false, "高复杂度推理"),
        DEEPSEEK_V3("deepseek-v3", "DeepSeek-V3", "DeepSeek", 64 * 1024, true, false, "长上下文推理"),
        DEEPSEEK_R1("deepseek-r1", "DeepSeek-R1", "DeepSeek", 64 * 1024, true, false, "深度思考、复杂分析"),
        CLAUDE_3_5_SONNET("claude-3-5-sonnet", "Claude 3.5 Sonnet", "Anthropic", 200 * 1024, true, false, "高质量推理、多语言"),
        GPT_4O("gpt-4o", "GPT-4o", "OpenAI", 128 * 1024, true, false, "通用高质量推理"),
        LOCAL_QWEN35_27B("local-qwen3.5-27b", "Local Qwen3.5-27B", "本地部署", 32 * 1024, false, true, "本地私有化部署");

        private final String id;
        private final String displayName;
        private final String provider;
        private final int contextLength;
        private final boolean cloudAvailable;
        private final boolean recommended;
        private final String bestFor;

        MainBrainModel(String id, String displayName, String provider, int contextLength, 
                       boolean cloudAvailable, boolean recommended, String bestFor) {
            this.id = id;
            this.displayName = displayName;
            this.provider = provider;
            this.contextLength = contextLength;
            this.cloudAvailable = cloudAvailable;
            this.recommended = recommended;
            this.bestFor = bestFor;
        }

        public String getId() { return id; }
        public String getDisplayName() { return displayName; }
        public String getProvider() { return provider; }
        public int getContextLength() { return contextLength; }
        public boolean isCloudAvailable() { return cloudAvailable; }
        public boolean isRecommended() { return recommended; }
        public String getBestFor() { return bestFor; }
    }

    @Value("${main-brain.model.default:qwen3.5-27b}")
    private String defaultModelId;

    @Value("${main-brain.model.provider:qwen}")
    private String defaultProvider;

    @Value("${main-brain.model.auto-select:true}")
    private boolean autoSelectEnabled;

    @Value("${main-brain.model.api-key:}")
    private String apiKey;

    @Value("${main-brain.model.base-url:}")
    private String baseUrl;

    private final AtomicReference<MainBrainModel> currentModel = new AtomicReference<>();
    private final AtomicReference<String> customModelId = new AtomicReference<>();

    public MainBrainModel selectModel() {
        if (!autoSelectEnabled) {
            MainBrainModel manualModel = getModelById(defaultModelId);
            currentModel.set(manualModel);
            return manualModel;
        }

        MainBrainModel selected = selectBasedOnTask();
        MainBrainModel previous = currentModel.getAndSet(selected);

        if (previous != selected) {
            log.info("Main brain model changed: {} -> {}",
                previous != null ? previous.getDisplayName() : "none",
                selected.getDisplayName());
        }

        return selected;
    }

    private MainBrainModel selectBasedOnTask() {
        return getModelById(defaultModelId);
    }

    public MainBrainModel getCurrentModel() {
        MainBrainModel model = currentModel.get();
        if (model == null) {
            return selectModel();
        }
        return model;
    }

    public void setCurrentModel(MainBrainModel model) {
        MainBrainModel previous = currentModel.getAndSet(model);
        log.info("Main brain model manually set: {} -> {}",
            previous != null ? previous.getDisplayName() : "none",
            model.getDisplayName());
    }

    public void setCurrentModel(String modelId) {
        MainBrainModel model = getModelById(modelId);
        if (model != null) {
            setCurrentModel(model);
        } else {
            customModelId.set(modelId);
            log.info("Main brain model set to custom model: {}", modelId);
        }
    }

    public String getEffectiveModelId() {
        String custom = customModelId.get();
        if (custom != null && !custom.isEmpty()) {
            return custom;
        }
        return getCurrentModel().getId();
    }

    public MainBrainModel getModelById(String id) {
        if (id == null || id.isEmpty()) {
            return MainBrainModel.QWEN35_27B;
        }
        for (MainBrainModel model : MainBrainModel.values()) {
            if (model.getId().equalsIgnoreCase(id)) {
                return model;
            }
        }
        return null;
    }

    public List<MainBrainModel> getAvailableModels() {
        return Arrays.asList(MainBrainModel.values());
    }

    public List<MainBrainModel> getRecommendedModels() {
        return Arrays.stream(MainBrainModel.values())
            .filter(MainBrainModel::isRecommended)
            .toList();
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        if (baseUrl != null && !baseUrl.isEmpty()) {
            return baseUrl;
        }
        return getProviderBaseUrl(getCurrentModel().getProvider());
    }

    private String getProviderBaseUrl(String provider) {
        return switch (provider.toLowerCase()) {
            case "阿里云", "qwen" -> "https://dashscope.aliyuncs.com/compatible-mode/v1";
            case "deepseek" -> "https://api.deepseek.com/v1";
            case "anthropic" -> "https://api.anthropic.com";
            case "openai" -> "https://api.openai.com/v1";
            case "本地部署", "local" -> "http://localhost:8000/v1";
            default -> "";
        };
    }

    public ModelConfig getModelConfig() {
        MainBrainModel model = getCurrentModel();
        return new ModelConfig(
            getEffectiveModelId(),
            model.getDisplayName(),
            model.getProvider(),
            model.getContextLength(),
            getBaseUrl(),
            apiKey != null && !apiKey.isEmpty()
        );
    }

    public static record ModelConfig(
        String modelId,
        String displayName,
        String provider,
        int contextLength,
        String baseUrl,
        boolean hasApiKey
    ) {
        public String toString() {
            return String.format("ModelConfig{id=%s, provider=%s, contextLength=%d, hasApiKey=%s}",
                modelId, provider, contextLength, hasApiKey);
        }
    }
}
