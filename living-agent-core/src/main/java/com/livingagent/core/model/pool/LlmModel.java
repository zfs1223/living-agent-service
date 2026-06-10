package com.livingagent.core.model.pool;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "llm_models", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"provider_id", "model_name"})
})
public class LlmModel {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID id;

    @Column(name = "provider_id", length = 50, nullable = false)
    private String providerId;

    @Column(name = "model_name", length = 100, nullable = false)
    private String modelName;

    @Column(name = "display_name", length = 200)
    private String displayName;

    @Column(name = "context_window")
    private int contextWindow = 32768;

    @Column(name = "max_output_tokens")
    private int maxOutputTokens = 4096;

    @Column(name = "supports_vision")
    private boolean supportsVision = false;

    @Column(name = "supports_reasoning")
    private boolean supportsReasoning = false;

    @Column(name = "temperature")
    private Double temperature;

    @Column(name = "enabled")
    private boolean enabled = true;

    @Column(name = "recommended")
    private boolean recommended = false;

    @Column(name = "best_for", columnDefinition = "TEXT")
    private String bestFor;

    @Column(name = "input_types", length = 50)
    private String inputTypes = "text";

    /** 模型能力标签，如: coding, reasoning, frontend, creative, chat 等 */
    @Column(name = "capability_tags", length = 500)
    private String capabilityTags;

    /** 模型综合性能评分 0-100 */
    @Column(name = "performance_score")
    private Integer performanceScore;

    /** 模型参数量（如 9B, 72B 等，用于评估能力） */
    @Column(name = "parameter_size", length = 20)
    private String parameterSize;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public LlmModel() {}

    public LlmModel(String providerId, String modelName, String displayName,
                    int contextWindow, int maxOutputTokens, boolean supportsVision,
                    boolean supportsReasoning, Double temperature, boolean enabled,
                    boolean recommended, String bestFor, String inputTypes) {
        this.providerId = providerId;
        this.modelName = modelName;
        this.displayName = displayName;
        this.contextWindow = contextWindow;
        this.maxOutputTokens = maxOutputTokens;
        this.supportsVision = supportsVision;
        this.supportsReasoning = supportsReasoning;
        this.temperature = temperature;
        this.enabled = enabled;
        this.recommended = recommended;
        this.bestFor = bestFor;
        this.inputTypes = inputTypes;
        this.createdAt = LocalDateTime.now();
    }

    public java.util.UUID getId() { return id; }
    public void setId(java.util.UUID id) { this.id = id; }

    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public int getContextWindow() { return contextWindow; }
    public void setContextWindow(int contextWindow) { this.contextWindow = contextWindow; }

    public int getMaxOutputTokens() { return maxOutputTokens; }
    public void setMaxOutputTokens(int maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; }

    public boolean isSupportsVision() { return supportsVision; }
    public void setSupportsVision(boolean supportsVision) { this.supportsVision = supportsVision; }

    public boolean isSupportsReasoning() { return supportsReasoning; }
    public void setSupportsReasoning(boolean supportsReasoning) { this.supportsReasoning = supportsReasoning; }

    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isRecommended() { return recommended; }
    public void setRecommended(boolean recommended) { this.recommended = recommended; }

    public String getBestFor() { return bestFor; }
    public void setBestFor(String bestFor) { this.bestFor = bestFor; }

    public String getInputTypes() { return inputTypes; }
    public void setInputTypes(String inputTypes) { this.inputTypes = inputTypes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getCapabilityTags() { return capabilityTags; }
    public void setCapabilityTags(String capabilityTags) { this.capabilityTags = capabilityTags; }

    public Integer getPerformanceScore() { return performanceScore; }
    public void setPerformanceScore(Integer performanceScore) { this.performanceScore = performanceScore; }

    public String getParameterSize() { return parameterSize; }
    public void setParameterSize(String parameterSize) { this.parameterSize = parameterSize; }
}
