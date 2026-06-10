package com.livingagent.core.model.selector;

import com.livingagent.core.model.pool.LlmModel;
import com.livingagent.core.model.pool.ModelPoolManager;
import com.livingagent.core.model.pool.ProviderConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class BrainModelSelectorManager {

    private static final Logger log = LoggerFactory.getLogger(BrainModelSelectorManager.class);

    private final Map<String, BrainModelSelector> selectorsByBrainId = new ConcurrentHashMap<>();
    private final Map<String, BrainModelSelector> selectorsByDepartment = new ConcurrentHashMap<>();
    private final ModelPoolManager modelPoolManager;
    private MainBrainModelSelector mainBrainModelSelector;

    public BrainModelSelectorManager(ModelPoolManager modelPoolManager) {
        this.modelPoolManager = modelPoolManager;
    }

    @Autowired(required = false)
    public void setMainBrainModelSelector(MainBrainModelSelector mainBrainModelSelector) {
        this.mainBrainModelSelector = mainBrainModelSelector;
        log.info("MainBrainModelSelector injected: {}", mainBrainModelSelector != null);
    }

    public void register(BrainModelSelector selector) {
        if (selector == null) return;
        selectorsByBrainId.put(selector.getBrainId(), selector);
        selectorsByDepartment.put(selector.getDepartment(), selector);
    }

    public Optional<BrainModelSelector> getByBrainId(String brainId) {
        BrainModelSelector selector = selectorsByBrainId.get(brainId);
        if (selector != null) {
            return Optional.of(selector);
        }
        if (brainId.contains("main")) {
            if (mainBrainModelSelector == null) {
                log.warn("MainBrainModelSelector is null when resolving brain: {}, this may cause fallback to model-pool", brainId);
            } else {
                log.debug("Creating MainBrainSelectorAdapter for brain: {}", brainId);
                return Optional.of(new MainBrainSelectorAdapter(mainBrainModelSelector));
            }
        }
        return Optional.empty();
    }

    public Optional<BrainModelSelector> getByDepartment(String department) {
        return Optional.ofNullable(selectorsByDepartment.get(department));
    }

    public List<BrainModelSelector.BrainModelConfigInfo> getAllConfigs() {
        return selectorsByBrainId.values().stream()
            .map(BrainModelSelector::getConfigInfo)
            .toList();
    }

    public BrainModelSelector.BrainModelConfigInfo switchModel(String brainId, String modelId) {
        BrainModelSelector selector = selectorsByBrainId.get(brainId);
        if (selector == null) {
            throw new IllegalArgumentException("Unknown brain: " + brainId);
        }
        selector.setModelById(modelId);
        return selector.getConfigInfo();
    }

    public Optional<LlmModel> selectBestCandidateModel(String brainId, String brainType, UUID currentModelId) {
        List<LlmModel> allModels = modelPoolManager.getAllModels();
        if (allModels.isEmpty()) {
            log.warn("No models available for candidate selection");
            return Optional.empty();
        }

        BrainModelSelector selector = getByBrainId(brainId).orElse(null);

        if (selector != null && !selector.supportsAutoAdjust()) {
            log.warn("Brain {} does not support auto-adjust", brainId);
            return Optional.empty();
        }

        List<LlmModel> candidates = allModels.stream()
                .filter(LlmModel::isEnabled)
                .filter(model -> !model.getId().equals(currentModelId))
                .filter(this::isProviderEnabled)
                .filter(model -> isBrainTypeCompatible(selector, model, brainType))
                .filter(model -> hasRequiredCapabilities(selector, model, brainType))
                .filter(model -> isCandidateCompatible(selector, model, brainType))
                .map(model -> {
                    double score = scoreCandidate(selector, model, brainType);
                    return Map.entry(model, score);
                })
                .filter(entry -> entry.getValue() > 0.0)
                .sorted(Map.Entry.<LlmModel, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            log.warn("No suitable candidate found for brain: {} (type: {})", brainId, brainType);
            return Optional.empty();
        }

        LlmModel best = candidates.get(0);
        log.info("Selected best candidate for brain {}: {} (provider: {})", 
                brainId, best.getModelName(), best.getProviderId());
        return Optional.of(best);
    }

    private boolean isProviderEnabled(LlmModel model) {
        try {
            List<ProviderConfig> providers = modelPoolManager.getAllProviders();
            return providers.stream()
                    .anyMatch(p -> p.getId().equals(model.getProviderId()) && p.isEnabled());
        } catch (Exception e) {
            log.warn("Failed to check provider status for {}: {}", model.getProviderId(), e.getMessage());
            return false;
        }
    }

    private boolean isBrainTypeCompatible(BrainModelSelector selector, LlmModel model, String brainType) {
        if (selector != null) {
            return selector.supportsBrainType(brainType);
        }
        return switch (brainType) {
            case "main" -> model.getContextWindow() >= 32768;
            case "tech" -> model.getContextWindow() >= 16384;
            default -> model.getContextWindow() >= 8192;
        };
    }

    private boolean hasRequiredCapabilities(BrainModelSelector selector, LlmModel model, String brainType) {
        if (selector != null) {
            return true;
        }
        return switch (brainType) {
            case "main", "tech" -> model.isSupportsReasoning();
            case "finance", "legal" -> model.isSupportsReasoning();
            default -> true;
        };
    }

    private boolean isCandidateCompatible(BrainModelSelector selector, LlmModel model, String brainType) {
        if (selector != null) {
            BrainModelSelector.BrainModel brainModel = toBrainModel(model);
            return selector.isCandidateCompatible(brainModel);
        }
        return true;
    }

    private BrainModelSelector.BrainModel toBrainModel(LlmModel model) {
        return new BrainModelSelector.BrainModel(
                model.getId().toString(),
                model.getDisplayName(),
                model.getProviderId(),
                model.getContextWindow(),
                true,
                model.isRecommended(),
                model.getBestFor()
        );
    }

    private double scoreCandidate(BrainModelSelector selector, LlmModel model, String brainType) {
        if (selector != null) {
            BrainModelSelector.BrainModel brainModel = toBrainModel(model);
            return selector.scoreCandidate(brainModel);
        }

        double score = 0.0;
        
        if (model.isRecommended()) {
            score += 0.3;
        }
        
        int contextWindow = model.getContextWindow();
        if ("main".equals(brainType)) {
            score += Math.min(0.3, contextWindow / 100000.0 * 0.3);
        } else if ("tech".equals(brainType)) {
            score += Math.min(0.3, contextWindow / 50000.0 * 0.3);
        } else {
            score += Math.min(0.3, contextWindow / 30000.0 * 0.3);
        }
        
        if (model.isSupportsReasoning()) {
            score += 0.2;
        }
        if (model.isSupportsVision()) {
            score += 0.1;
        }
        
        return Math.min(1.0, score);
    }

    private static class MainBrainSelectorAdapter extends BrainModelSelector {

        private final MainBrainModelSelector delegate;

        MainBrainSelectorAdapter(MainBrainModelSelector delegate) {
            super("neuron://core/main-brain/001", "MainBrain", "core");
            this.delegate = delegate;
            if (delegate == null) {
                log.warn("MainBrainSelectorAdapter created with null delegate - this should not happen");
            }
        }

        @Override
        protected void initializeAvailableModels() {
        }

        @Override
        protected BrainModel createDefaultModel() {
            if (delegate == null) {
                log.warn("MainBrainSelectorAdapter.createDefaultModel called with null delegate, returning fallback");
                return new BrainModel("model-pool", "model-pool", "model-pool", 32768, true, true, "fallback");
            }
            String modelId = delegate.getEffectiveModelId();
            String provider = delegate.getModelConfig().provider();
            return new BrainModel(modelId, modelId, provider, 32768, true, true, "complex reasoning");
        }

        @Override
        public BrainModel getCurrentModel() {
            if (delegate == null) {
                log.warn("MainBrainSelectorAdapter.getCurrentModel called with null delegate, returning fallback");
                return new BrainModel("model-pool", "model-pool", "model-pool", 32768, true, true, "fallback");
            }
            String modelId = delegate.getEffectiveModelId();
            String provider = delegate.getModelConfig().provider();
            return new BrainModel(modelId, modelId, provider, 32768, true, true, "complex reasoning");
        }

        @Override
        public BrainModelConfigInfo getConfigInfo() {
            if (delegate == null) {
                log.warn("MainBrainSelectorAdapter.getConfigInfo called with null delegate, returning fallback");
                return new BrainModelConfigInfo(
                    brainId,
                    brainName,
                    department,
                    new ModelConfig("model-pool", "model-pool", "model-pool", 32768, "", false),
                    Collections.emptyList(),
                    System.currentTimeMillis()
                );
            }
            MainBrainModelSelector.ModelConfig config = delegate.getModelConfig();
            return new BrainModelConfigInfo(
                brainId,
                brainName,
                department,
                new ModelConfig(
                    config.modelId(),
                    config.displayName(),
                    config.provider(),
                    config.contextLength(),
                    config.baseUrl(),
                    config.hasApiKey()
                ),
                Collections.emptyList(),
                System.currentTimeMillis()
            );
        }
    }
}
