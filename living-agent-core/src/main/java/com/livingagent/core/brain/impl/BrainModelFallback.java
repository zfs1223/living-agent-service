package com.livingagent.core.brain.impl;

import com.livingagent.core.model.pool.BrainModelAssigner;
import com.livingagent.core.model.pool.BrainModelResolver;
import com.livingagent.core.model.pool.ModelHealthRegistry;
import com.livingagent.core.model.pool.ModelPoolManager;
import com.livingagent.core.model.pool.ResolvedBrainModel;
import com.livingagent.core.model.selector.BrainModelSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.UUID;

/**
 * 大脑模型降级管理器 — 从 AbstractBrain 中提取的模型降级逻辑。
 * <p>
 * 负责：模型解析、降级查找、健康状态记录、模型自动分配。
 */
public class BrainModelFallback {

    private static final Logger log = LoggerFactory.getLogger(BrainModelFallback.class);

    protected static final int DEFAULT_MAX_TOKENS = 4096;
    protected static final double DEFAULT_TEMPERATURE = 0.7;

    private final String brainId;
    private final String brainName;

    private volatile BrainModelResolver brainModelResolver;
    private volatile BrainModelSelector modelSelector;
    private volatile BrainModelAssigner brainModelAssigner;
    private volatile ModelPoolManager modelPoolManager;

    public BrainModelFallback(String brainId, String brainName) {
        this.brainId = brainId;
        this.brainName = brainName;
    }

    public void setBrainModelResolver(BrainModelResolver brainModelResolver) {
        this.brainModelResolver = brainModelResolver;
        log.info("Brain model resolver set for brain {}: {}", brainId, brainModelResolver != null);
    }

    public BrainModelResolver getBrainModelResolver() {
        return brainModelResolver;
    }

    public void setModelSelector(BrainModelSelector modelSelector) {
        this.modelSelector = modelSelector;
        log.info("Model selector set for brain {}: {}", brainId,
            modelSelector != null ? modelSelector.getBrainName() : "null");
    }

    public BrainModelSelector getModelSelector() {
        return modelSelector;
    }

    public void setBrainModelAssigner(BrainModelAssigner brainModelAssigner) {
        this.brainModelAssigner = brainModelAssigner;
        log.info("Brain model assigner set for brain {}: {}", brainId, brainModelAssigner != null);
    }

    public void setModelPoolManager(ModelPoolManager modelPoolManager) {
        this.modelPoolManager = modelPoolManager;
        log.info("Model pool manager set for brain {}: {}", brainId, modelPoolManager != null);
    }

    /**
     * 获取当前解析的模型。
     */
    public ResolvedBrainModel getCurrentModel() {
        if (brainModelResolver != null) {
            return brainModelResolver.resolve(brainId);
        }
        return null;
    }

    /**
     * 获取默认模型名称。
     */
    public String getDefaultModel() {
        if (brainModelResolver != null) {
            ResolvedBrainModel model = getCurrentModel();
            if (model != null) {
                return model.getModelName();
            }
        }
        return resolveDefaultModelName();
    }

    /**
     * 解析默认模型名称。
     */
    public String resolveDefaultModelName() {
        if (brainModelResolver != null) {
            try {
                String resolved = brainModelResolver.resolveDefault(brainId).getModelName();
                if (resolved != null && !resolved.isEmpty()) {
                    return resolved;
                }
            } catch (Exception e) {
                log.warn("Brain {} failed to resolve default model from BrainModelResolver: {}", brainId, e.getMessage());
            }
        }
        log.warn("Brain {} BrainModelResolver unavailable, returning null as default model name", brainId);
        return null;
    }

    /**
     * 从模型中获取最大 token 数。
     */
    public int getMaxTokens() {
        if (brainModelResolver != null) {
            ResolvedBrainModel model = getCurrentModel();
            if (model != null) {
                return model.getMaxTokens();
            }
        }
        return DEFAULT_MAX_TOKENS;
    }

    /**
     * 从模型中获取温度参数。
     */
    public double getTemperature() {
        if (brainModelResolver != null) {
            ResolvedBrainModel model = getCurrentModel();
            if (model != null) {
                return model.getTemperature();
            }
        }
        return DEFAULT_TEMPERATURE;
    }

    /**
     * 尝试从模型池中找到另一个可用的模型作为降级替代。
     * 跳过当前失败的模型和处于冷却期的模型。
     * 如果找到可用的降级模型，会自动更新数据库中的大脑模型配置。
     */
    public ResolvedBrainModel tryFallbackModel(ResolvedBrainModel failedModel) {
        if (brainModelResolver == null) {
            return null;
        }
        try {
            ResolvedBrainModel candidate = brainModelResolver.resolve(brainId);
            if (candidate == null) {
                candidate = findBestAvailableModel(failedModel);
            }
            if (candidate == null) {
                return null;
            }
            if (failedModel != null && candidate.getModelId() != null &&
                candidate.getModelId().equals(failedModel.getModelId())) {
                candidate = findBestAvailableModel(failedModel);
                if (candidate == null) {
                    return null;
                }
                if (failedModel != null && candidate.getModelId() != null &&
                    candidate.getModelId().equals(failedModel.getModelId())) {
                    return null;
                }
            }
            ModelHealthRegistry registry = getModelHealthRegistry();
            if (registry != null && !registry.isModelAvailable(candidate.getModelId().toString())) {
                candidate = findBestAvailableModel(failedModel);
                if (candidate == null) {
                    return null;
                }
            }
            if (candidate != null && brainModelAssigner != null && modelPoolManager != null) {
                try {
                    brainModelAssigner.assignModel(
                        brainId,
                        brainName,
                        "department",
                        candidate.getModelId(),
                        "auto_fallback"
                    );
                    log.info("[BrainTrace] brain={} event=model_auto_reassigned fromModel={} toModel={} toModelId={}",
                        brainId,
                        failedModel != null ? failedModel.getModelName() : "null",
                        candidate.getModelName(),
                        candidate.getModelId());
                } catch (Exception e) {
                    log.warn("Failed to auto-assign fallback model to brain {}: {}", brainId, e.getMessage());
                }
            }
            return candidate;
        } catch (Exception e) {
            log.warn("Failed to resolve fallback model: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 从模型池中找到综合评分最高的可用模型作为降级替代。
     * 排序优先级：支持 chat > 本地供应商 > 成功率 > 静态性能分
     * 排除：embedding 模型（不支持 chat completions）
     */
    public ResolvedBrainModel findBestAvailableModel(ResolvedBrainModel failedModel) {
        if (modelPoolManager == null || brainModelResolver == null) {
            return null;
        }
        try {
            ModelHealthRegistry registry = getModelHealthRegistry();
            UUID failedModelId = failedModel != null ? failedModel.getModelId() : null;
            Set<String> localProviders = Set.of("vllm", "ollama");

            return modelPoolManager.getAllModels().stream()
                .filter(com.livingagent.core.model.pool.LlmModel::isEnabled)
                .filter(m -> failedModelId == null || !m.getId().equals(failedModelId))
                .filter(m -> registry == null || registry.isModelAvailable(m.getId().toString()))
                // 关键：排除 embedding 模型（不支持 chat completions）
                .filter(m -> !isEmbeddingModel(m.getModelName()))
                .sorted((a, b) -> {
                    // 1. 本地供应商优先
                    boolean aLocal = localProviders.contains(a.getProviderId());
                    boolean bLocal = localProviders.contains(b.getProviderId());
                    if (aLocal != bLocal) return aLocal ? -1 : 1;
                    // 2. 成功率高的优先（运行时动态评分）
                    double rateA = (registry != null && a.getId() != null)
                        ? getSuccessRate(registry, a.getId().toString()) : 1.0;
                    double rateB = (registry != null && b.getId() != null)
                        ? getSuccessRate(registry, b.getId().toString()) : 1.0;
                    int rateCompare = Double.compare(rateB, rateA);
                    if (rateCompare != 0) return rateCompare;
                    // 3. 静态性能分高的优先（配置评分）
                    double scoreA = a.getPerformanceScore() != null ? a.getPerformanceScore() : 0.0;
                    double scoreB = b.getPerformanceScore() != null ? b.getPerformanceScore() : 0.0;
                    int scoreCompare = Double.compare(scoreB, scoreA);
                    if (scoreCompare != 0) return scoreCompare;
                    // 4. 最后用 id 确保稳定排序（避免依赖数据库顺序）
                    return a.getId().compareTo(b.getId());
                })
                .findFirst()
                .map(m -> brainModelResolver.resolveRaw(m.getProviderId(), m.getModelName()))
                .orElse(null);
        } catch (Exception e) {
            log.warn("Failed to find best available model: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 判断是否为 embedding 模型（不支持 chat completions）
     */
    private boolean isEmbeddingModel(String modelName) {
        if (modelName == null) return false;
        String lower = modelName.toLowerCase();
        Set<String> embeddingKeywords = Set.of(
            "bge", "e5", "embedding", "text-embedding", "ada", "gte", "jina",
            "m3e", "voyage", "cohere-embed", "retrieval", "sentence"
        );
        for (String kw : embeddingKeywords) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }

    /**
     * 获取模型运行时成功率（0.0~1.0）
     */
    private double getSuccessRate(ModelHealthRegistry registry, String modelId) {
        ModelHealthRegistry.ModelHealthRecord health = registry.getHealth(modelId);
        if (health.totalCalls() <= 0) return 1.0;
        return health.totalSuccesses() * 1.0 / health.totalCalls();
    }

    /**
     * 获取模型健康注册表。
     */
    public ModelHealthRegistry getModelHealthRegistry() {
        return brainModelResolver != null ? brainModelResolver.getModelHealthRegistry() : null;
    }

    /**
     * 记录模型调用成功。
     */
    public void recordModelSuccess(ResolvedBrainModel resolvedModel, long latencyMs) {
        ModelHealthRegistry registry = getModelHealthRegistry();
        if (registry == null || resolvedModel == null || resolvedModel.getModelId() == null) {
            return;
        }
        registry.recordSuccess(resolvedModel.getModelId().toString(), resolvedModel.getProviderId(), latencyMs);
    }

    /**
     * 记录模型调用失败。
     */
    public void recordModelFailure(ResolvedBrainModel resolvedModel, String errorMessage) {
        ModelHealthRegistry registry = getModelHealthRegistry();
        if (registry == null || resolvedModel == null || resolvedModel.getModelId() == null) {
            return;
        }
        registry.recordFailure(resolvedModel.getModelId().toString(), resolvedModel.getProviderId(), errorMessage);
    }
}
