package com.livingagent.core.proxy.anthropic;

import com.livingagent.core.model.pool.LlmModel;
import com.livingagent.core.model.pool.ModelCapabilityAssessor;
import com.livingagent.core.model.pool.ModelPoolManager;
import com.livingagent.core.model.pool.Protocol;
import com.livingagent.core.model.pool.ProviderConfig;
import com.livingagent.core.model.selector.BrainModelSelector;
import com.livingagent.core.model.selector.BrainModelSelector.BrainModel;
import com.livingagent.core.model.selector.BrainModelSelectorManager;
import com.livingagent.core.sandbox.ClaudeCliProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ClaudeProxyModelRouter {

    private static final Logger log = LoggerFactory.getLogger(ClaudeProxyModelRouter.class);

    private static final Map<String, String> VIRTUAL_MODEL_TASK_MAP = Map.of(
        "claude-sonnet-4-20250514", "code_generation",
        "claude-sonnet-4", "code_generation",
        "claude-opus-4-20250514", "complex_reasoning",
        "claude-opus-4", "complex_reasoning",
        "claude-haiku-4-20250514", "fast_chat",
        "claude-haiku-4", "fast_chat"
    );

    private final BrainModelSelectorManager selectorManager;
    private final ModelPoolManager modelPoolManager;
    private final ModelCapabilityAssessor modelCapabilityAssessor;
    private final ClaudeCliProperties properties;

    public ClaudeProxyModelRouter(BrainModelSelectorManager selectorManager,
                                  ModelPoolManager modelPoolManager,
                                  ModelCapabilityAssessor modelCapabilityAssessor,
                                  ClaudeCliProperties properties) {
        this.selectorManager = selectorManager;
        this.modelPoolManager = modelPoolManager;
        this.modelCapabilityAssessor = modelCapabilityAssessor;
        this.properties = properties;
    }

    public RoutingResult resolve(AnthropicMessagesRequest request, ClaudeProxyRequestContext context) {
        String requestedModel = request.resolveModelName();
        log.info("Resolving model for request: {}, employeeId: {}, brainId: {}, dept: {}",
            requestedModel, context.employeeId(), context.brainId(), context.departmentId());

        ResolvedModel resolved = resolveByBrainSelector(context.brainId());
        if (resolved == null) {
            resolved = resolveByCapability(requestedModel, context.taskType());
        }

        if (resolved == null) {
            resolved = resolveByFirstAvailable();
        }

        if (resolved == null) {
            log.warn("No available model found for Claude CLI request");
            return new RoutingResult(requestedModel, requestedModel, false, null, "No provider configured");
        }

        log.info("Model resolved: virtual={}, actual={}, provider={}, score={}",
            requestedModel, resolved.modelName, resolved.provider.getDisplayName(), resolved.score);

        return new RoutingResult(requestedModel, resolved.modelName, true, resolved.provider, null);
    }

    private ResolvedModel resolveByBrainSelector(String brainId) {
        if (brainId == null || brainId.isBlank()) return null;
        Optional<BrainModelSelector> selectorOpt = selectorManager.getByBrainId(brainId);
        if (selectorOpt.isEmpty()) return null;

        BrainModel brainModel = selectorOpt.get().selectModel();
        if (brainModel == null) return null;

        ProviderConfig provider = modelPoolManager.getAllProviders().stream()
            .filter(p -> p.getId().equals(brainModel.provider()))
            .filter(ProviderConfig::isEnabled)
            .filter(p -> p.getProtocol() == Protocol.OPENAI_COMPATIBLE)
            .findFirst()
            .orElse(null);

        if (provider == null) return null;

        log.debug("Brain selector resolved: model={}, provider={}", brainModel.id(), provider.getId());
        return new ResolvedModel(brainModel.id(), provider, -1);
    }

    private ResolvedModel resolveByCapability(String virtualModel, String taskType) {
        String effectiveTaskType = taskType;
        if (effectiveTaskType == null || effectiveTaskType.isBlank()) {
            effectiveTaskType = VIRTUAL_MODEL_TASK_MAP.getOrDefault(virtualModel, "code_generation");
        }

        Map<String, String> virtualModelMapping = properties.getProxy().getVirtualModelMapping();
        String category = virtualModelMapping.getOrDefault(virtualModel, "balanced");

        List<LlmModel> availableModels = modelPoolManager.getAvailableModels().stream()
            .filter(LlmModel::isEnabled)
            .filter(m -> {
                ProviderConfig p = findProvider(m.getProviderId());
                return p != null && p.isEnabled() && p.getProtocol() == Protocol.OPENAI_COMPATIBLE;
            })
            .toList();

        if (availableModels.isEmpty()) return null;

        LlmModel bestModel = modelCapabilityAssessor.selectBestModelForTask(
            effectiveTaskType, category, availableModels);

        if (bestModel == null) {
            bestModel = availableModels.stream()
                .max(Comparator.comparingInt(m -> m.getPerformanceScore() != null ? m.getPerformanceScore() : 0))
                .orElse(null);
        }

        if (bestModel == null) return null;

        ProviderConfig provider = findProvider(bestModel.getProviderId());
        if (provider == null) return null;

        int score = bestModel.getPerformanceScore() != null ? bestModel.getPerformanceScore() : 0;
        log.debug("Capability-based selection: taskType={}, category={}, bestModel={}, provider={}, score={}, tags={}",
            effectiveTaskType, category, bestModel.getModelName(), provider.getId(), score, bestModel.getCapabilityTags());

        return new ResolvedModel(bestModel.getModelName(), provider, score);
    }

    private ResolvedModel resolveByFirstAvailable() {
        LlmModel firstModel = modelPoolManager.getAvailableModels().stream()
            .filter(LlmModel::isEnabled)
            .filter(m -> {
                ProviderConfig p = findProvider(m.getProviderId());
                return p != null && p.isEnabled() && p.getProtocol() == Protocol.OPENAI_COMPATIBLE;
            })
            .findFirst()
            .orElse(null);

        if (firstModel == null) return null;

        ProviderConfig provider = findProvider(firstModel.getProviderId());
        if (provider == null) return null;

        return new ResolvedModel(firstModel.getModelName(), provider, 0);
    }

    private ProviderConfig findProvider(String providerId) {
        if (providerId == null) return null;
        return modelPoolManager.getAllProviders().stream()
            .filter(p -> p.getId().equals(providerId))
            .findFirst()
            .orElse(null);
    }

    private record ResolvedModel(String modelName, ProviderConfig provider, int score) {}

    public record RoutingResult(String requestedModel, String actualModel, boolean success, ProviderConfig provider, String error) {}
}
