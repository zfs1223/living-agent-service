package com.livingagent.core.model.pool;

import com.livingagent.core.model.selector.BrainModelSelector;
import com.livingagent.core.model.selector.BrainModelSelectorManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class BrainModelResolver {

    private static final Logger log = LoggerFactory.getLogger(BrainModelResolver.class);
    private static final long MODEL_AVAILABILITY_CACHE_TTL_MS = 60_000L;

    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<String, AvailabilityCacheEntry> availabilityCache = new ConcurrentHashMap<>();

    private final BrainModelAssignmentRepository assignmentRepo;
    private final LlmModelRepository modelRepo;
    private final ProviderConfigRepository providerRepo;
    private final BrainModelSelectorManager selectorManager;
    private final ModelHealthRegistry modelHealthRegistry;
    private final ModelCapabilityAssessor modelCapabilityAssessor;

    public BrainModelResolver(
            BrainModelAssignmentRepository assignmentRepo,
            LlmModelRepository modelRepo,
            ProviderConfigRepository providerRepo,
            BrainModelSelectorManager selectorManager) {
        this(assignmentRepo, modelRepo, providerRepo, selectorManager, new ModelHealthRegistry(), null);
    }

    public BrainModelResolver(
            BrainModelAssignmentRepository assignmentRepo,
            LlmModelRepository modelRepo,
            ProviderConfigRepository providerRepo,
            BrainModelSelectorManager selectorManager,
            ModelHealthRegistry modelHealthRegistry) {
        this(assignmentRepo, modelRepo, providerRepo, selectorManager, modelHealthRegistry, null);
    }

    public BrainModelResolver(
            BrainModelAssignmentRepository assignmentRepo,
            LlmModelRepository modelRepo,
            ProviderConfigRepository providerRepo,
            BrainModelSelectorManager selectorManager,
            ModelHealthRegistry modelHealthRegistry,
            ModelCapabilityAssessor modelCapabilityAssessor) {
        this.assignmentRepo = assignmentRepo;
        this.modelRepo = modelRepo;
        this.providerRepo = providerRepo;
        this.selectorManager = selectorManager;
        this.modelHealthRegistry = modelHealthRegistry;
        this.modelCapabilityAssessor = modelCapabilityAssessor;
    }

    public ModelHealthRegistry getModelHealthRegistry() {
        return modelHealthRegistry;
    }

    public ResolvedBrainModel resolve(String brainId) {
        try {
            return assignmentRepo.findByBrainId(brainId)
                .map(this::resolveFromAssignment)
                .flatMap(opt -> opt)
                .or(() -> resolveFromSelector(brainId))
                .orElseGet(() -> resolveDefault(brainId));
        } catch (Exception e) {
            log.warn("Failed to resolve model for brain {}: {}, falling back to default", brainId, e.getMessage());
            return resolveDefault(brainId);
        }
    }

    private Optional<ResolvedBrainModel> resolveFromAssignment(BrainModelAssignment assignment) {
        LlmModel model = modelRepo.findById(assignment.getModelId()).orElse(null);
        if (model == null || !model.isEnabled()) {
            log.warn("Assigned model {} for brain {} not found or disabled, trying fallback",
                assignment.getModelId(), assignment.getBrainId());
            return Optional.empty();
        }

        ProviderConfig provider = providerRepo.findById(model.getProviderId()).orElse(null);
        if (provider == null || !provider.isEnabled()) {
            log.warn("Provider {} for model {} not found or disabled, trying fallback",
                model.getProviderId(), model.getModelName());
            return Optional.empty();
        }

        if (!isModelAvailable(model, provider)) {
            log.warn("Assigned model {} for brain {} is not available at provider {}, trying fallback",
                model.getModelName(), assignment.getBrainId(), provider.getId());
            return Optional.empty();
        }

        if (!modelHealthRegistry.isModelAvailable(model.getId().toString())) {
            ModelHealthRegistry.ModelHealthRecord health = modelHealthRegistry.getHealth(model.getId().toString());
            if (health.isInCooldown()) {
                log.warn("Assigned model {} for brain {} is in cooldown (consecutive failures={}), trying fallback. Reason: {}",
                    model.getModelName(), assignment.getBrainId(), health.consecutiveFailures(), health.lastFailureReason());
                return Optional.empty();
            }
        }

        return Optional.of(buildResolvedModel(model, provider));
    }

    private Optional<ResolvedBrainModel> resolveFromSelector(String brainId) {
        return selectorManager.getByBrainId(brainId)
            .map(BrainModelSelector::getCurrentModel)
            .map(selectorModel -> buildFromSelectorModel(selectorModel, brainId))
            .filter(resolved -> resolved != null && isHealthy(resolved));
    }

    private boolean isHealthy(ResolvedBrainModel resolved) {
        if (resolved.getModelId() == null) {
            return true;
        }
        String modelIdStr = resolved.getModelId().toString();
        if (!modelHealthRegistry.isModelAvailable(modelIdStr)) {
            ModelHealthRegistry.ModelHealthRecord health = modelHealthRegistry.getHealth(modelIdStr);
            if (health.isInCooldown()) {
                log.warn("Selector model {} (provider={}) is in cooldown (consecutive failures={}), skipping. Reason: {}",
                    resolved.getModelName(), resolved.getProviderId(), health.consecutiveFailures(), health.lastFailureReason());
                return false;
            }
        }
        return true;
    }

    private boolean isModelHealthy(LlmModel model) {
        if (model.getId() == null) {
            return true;
        }
        String modelIdStr = model.getId().toString();
        if (!modelHealthRegistry.isModelAvailable(modelIdStr)) {
            ModelHealthRegistry.ModelHealthRecord health = modelHealthRegistry.getHealth(modelIdStr);
            if (health.isInCooldown()) {
                log.warn("Model {} (provider={}) is in cooldown (consecutive failures={}), skipping. Reason: {}",
                    model.getModelName(), model.getProviderId(), health.consecutiveFailures(), health.lastFailureReason());
                return false;
            }
        }
        return true;
    }

    public ResolvedBrainModel resolveDefault(String brainId) {
        String brainType = getBrainType(brainId);

        ResolvedBrainModel recommendedModel = findDefaultConfiguredModel();
        if (recommendedModel != null) {
            log.info("Using configured default model for brain {} (type={}): provider={}, model={}",
                brainId, brainType, recommendedModel.getProviderId(), recommendedModel.getModelName());
            return recommendedModel;
        }

        ResolvedBrainModel anyEnabledModel = findFirstEnabledModel();
        if (anyEnabledModel != null) {
            log.info("Using first available enabled model as default for brain {} (type={}): provider={}, model={}",
                brainId, brainType, anyEnabledModel.getProviderId(), anyEnabledModel.getModelName());
            return anyEnabledModel;
        }

        log.warn("No enabled model found in database for brain {}. BrainAutoAssigner should have configured models at startup. " +
            "Please add enabled models to the model pool via API or frontend, then restart or trigger re-assignment.", brainId);
        return null;
    }

    public ResolvedBrainModel resolveForEmployee(String employeeId, String departmentId, String departmentBrainId) {
        String employeeBrainId = buildEmployeeBrainId(employeeId, departmentId);

        return assignmentRepo.findByBrainId(employeeBrainId)
            .map(this::resolveFromAssignment)
            .flatMap(opt -> opt)
            .or(() -> resolveForEmployeeByCapability(employeeId, departmentId))
            .or(() -> selectDepartmentAssignedEmployeeModel(departmentId, employeeId))
            .or(() -> Optional.ofNullable(resolve(departmentBrainId)))
            .orElseGet(() -> resolveDefault(departmentBrainId != null ? departmentBrainId : employeeBrainId));
    }

    private Optional<ResolvedBrainModel> resolveForEmployeeByCapability(String employeeId, String departmentId) {
        if (modelCapabilityAssessor == null) {
            return Optional.empty();
        }

        String employeeRole = determineEmployeeRole(employeeId);
        String taskType = determineTaskType(departmentId);

        List<LlmModel> availableModels = modelRepo.findByEnabledTrue().stream()
            .filter(model -> {
                ProviderConfig provider = providerRepo.findById(model.getProviderId()).orElse(null);
                return provider != null && provider.isEnabled() && isModelAvailable(model, provider);
            })
            .filter(model -> isModelHealthy(model))
            .peek(model -> {
                if (model.getCapabilityTags() == null || model.getPerformanceScore() == null) {
                    modelCapabilityAssessor.assessModel(model);
                }
            })
            .toList();

        if (availableModels.isEmpty()) {
            return Optional.empty();
        }

        LlmModel bestModel = modelCapabilityAssessor.selectBestModelForTask(taskType, employeeRole, availableModels);
        if (bestModel == null) {
            return Optional.empty();
        }

        ProviderConfig provider = providerRepo.findById(bestModel.getProviderId()).orElse(null);
        if (provider == null || !provider.isEnabled()) {
            return Optional.empty();
        }

        ResolvedBrainModel resolved = buildResolvedModel(bestModel, provider);
        log.info("Capability-based model selection for employee {} (role={}, task={}): provider={}, model={}, tags={}, score={}",
            employeeId, employeeRole, taskType, resolved.getProviderId(), resolved.getModelName(),
            bestModel.getCapabilityTags(), bestModel.getPerformanceScore());

        return Optional.of(resolved);
    }

    private String determineEmployeeRole(String employeeId) {
        if (employeeId == null) return "general_worker";
        String id = employeeId.toLowerCase();
        if (id.contains("t09") || id.contains("frontend")) return "frontend_engineer";
        if (id.contains("t08") || id.contains("backend")) return "backend_engineer";
        if (id.contains("t12") || id.contains("review")) return "code_reviewer";
        if (id.contains("t02") || id.contains("data")) return "data_analyst";
        if (id.contains("t01") || id.contains("architect")) return "architect";
        if (id.contains("t10") || id.contains("lead")) return "tech_lead";
        if (id.contains("t07") || id.contains("doc")) return "document_writer";
        if (id.contains("t11") || id.contains("qa")) return "qa_tester";
        if (id.contains("t05") || id.contains("devops")) return "devops_engineer";
        if (id.contains("t06") || id.contains("design")) return "ui_ux_designer";
        if (id.contains("t03") || id.contains("chat")) return "chat_assistant";
        return "general_worker";
    }

    private String determineTaskType(String departmentId) {
        if (departmentId == null) return "chat";
        String dept = departmentId.toLowerCase();
        if (dept.contains("tech")) return "web_development";
        if (dept.contains("data") || dept.contains("anal")) return "data_analysis";
        if (dept.contains("doc") || dept.contains("content")) return "document_generation";
        if (dept.contains("review") || dept.contains("qa")) return "code_review";
        return "chat";
    }

    private Optional<ResolvedBrainModel> selectDepartmentAssignedEmployeeModel(String departmentId, String employeeId) {
        List<ResolvedBrainModel> candidates = modelRepo.findByEnabledTrue().stream()
            .filter(model -> isEmployeeExecutionCandidate(model, departmentId))
            .filter(model -> isModelHealthy(model))
            .sorted(employeeModelComparator(departmentId))
            .map(model -> {
                ProviderConfig provider = providerRepo.findById(model.getProviderId()).orElse(null);
                if (provider == null || !provider.isEnabled() || !isModelAvailable(model, provider)) {
                    return null;
                }
                return buildResolvedModel(model, provider);
            })
            .filter(model -> model != null)
            .toList();

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        int index = Math.floorMod((employeeId != null ? employeeId : "").hashCode(), candidates.size());
        ResolvedBrainModel selected = candidates.get(index);
        log.info("Department {} autonomously selected employee model for {}: provider={}, model={}",
            departmentId, employeeId, selected.getProviderId(), selected.getModelName());
        return Optional.of(selected);
    }

    private boolean isEmployeeExecutionCandidate(LlmModel model, String departmentId) {
        if (model == null || !model.isEnabled()) {
            return false;
        }
        String bestFor = model.getBestFor() != null ? model.getBestFor().toLowerCase() : "";
        String inputTypes = model.getInputTypes() != null ? model.getInputTypes().toLowerCase() : "";
        boolean textCapable = inputTypes.isBlank() || inputTypes.contains("text");
        if (!textCapable) {
            return false;
        }
        if ("tech".equalsIgnoreCase(departmentId)) {
            return bestFor.contains("代码")
                || bestFor.contains("编程")
                || bestFor.contains("开发")
                || bestFor.contains("推理")
                || model.isRecommended();
        }
        return model.isRecommended() || !bestFor.isBlank();
    }

    private Comparator<LlmModel> employeeModelComparator(String departmentId) {
        return Comparator
            .comparing((LlmModel model) -> !isDepartmentPreferred(model, departmentId))
            .thenComparing(model -> !model.isRecommended())
            .thenComparing(LlmModel::getProviderId)
            .thenComparing(LlmModel::getModelName);
    }

    private boolean isDepartmentPreferred(LlmModel model, String departmentId) {
        if (model == null || departmentId == null) {
            return false;
        }
        String bestFor = model.getBestFor() != null ? model.getBestFor().toLowerCase() : "";
        if ("tech".equalsIgnoreCase(departmentId)) {
            return bestFor.contains("代码") || bestFor.contains("编程") || bestFor.contains("开发");
        }
        return bestFor.contains(departmentId.toLowerCase());
    }

    private String buildEmployeeBrainId(String employeeId, String departmentId) {
        if (employeeId != null && !employeeId.isBlank()) {
            return "employee://" + employeeId;
        }
        return "employee://" + (departmentId != null ? departmentId : "unknown");
    }

    private ResolvedBrainModel findDefaultConfiguredModel() {
        return modelRepo.findByEnabledTrue().stream()
            .filter(LlmModel::isRecommended)
            .sorted(Comparator.comparing(LlmModel::getProviderId).thenComparing(LlmModel::getModelName))
            .map(model -> {
                ProviderConfig provider = providerRepo.findById(model.getProviderId()).orElse(null);
                if (provider == null || !provider.isEnabled()) {
                    return null;
                }
                return buildResolvedModel(model, provider);
            })
            .filter(model -> model != null)
            .filter(this::isHealthy)
            .findFirst()
            .orElse(null);
    }
    
    private ResolvedBrainModel findFirstEnabledModel() {
        for (LlmModel model : modelRepo.findByEnabledTrue()) {
            ProviderConfig provider = providerRepo.findById(model.getProviderId()).orElse(null);
            if (provider != null && provider.isEnabled() && isModelAvailable(model, provider)) {
                ResolvedBrainModel resolved = buildResolvedModel(model, provider);
                if (isHealthy(resolved)) {
                    return resolved;
                }
                log.warn("First enabled model {} (provider={}) is in cooldown, skipping", model.getModelName(), provider.getId());
            }
        }
        return null;
    }

    private ResolvedBrainModel resolveFromProvider(String providerId, String modelName) {
        return modelRepo.findByProviderIdAndModelName(providerId, modelName)
            .map(model -> providerRepo.findById(providerId)
                .filter(ProviderConfig::isEnabled)
                .filter(provider -> isModelAvailable(model, provider))
                .map(provider -> buildResolvedModel(model, provider))
                .orElse(null))
            .orElse(null);
    }

    public ResolvedBrainModel resolveRaw(String providerId, String modelName) {
        return resolveFromProvider(providerId, modelName);
    }

    private ResolvedBrainModel buildResolvedModel(LlmModel model, ProviderConfig provider) {
        double temp = model.getTemperature() != null ? model.getTemperature() : 0.7;
        return new ResolvedBrainModel(
            model.getId(),
            provider.getId(),
            model.getModelName(),
            model.getDisplayName(),
            provider.getBaseUrl(),
            provider.getApiKeyEncrypted(),
            provider.getProtocol(),
            model.getContextWindow(),
            model.getMaxOutputTokens(),
            temp,
            provider.isSupportsToolChoice()
        );
    }

    private ResolvedBrainModel buildFromSelectorModel(BrainModelSelector.BrainModel selectorModel, String brainId) {
        ProviderConfig provider = providerRepo.findById(selectorModel.provider()).orElse(null);
        if (provider == null) {
            log.warn("Selector model provider {} not found, cannot build resolved model for brain {}",
                selectorModel.provider(), brainId);
            return null;
        }

        LlmModel model = modelRepo.findByProviderIdAndModelName(selectorModel.provider(), selectorModel.id()).orElse(null);
        if (model == null) {
            log.warn("Selector model {} from provider {} not found in LlmModel repo for brain {}",
                selectorModel.id(), selectorModel.provider(), brainId);
            return null;
        }
        if (!model.isEnabled() || !provider.isEnabled() || !isModelAvailable(model, provider)) {
            log.warn("Selector model {} from provider {} is disabled or unavailable for brain {}",
                selectorModel.id(), selectorModel.provider(), brainId);
            return null;
        }

        return buildResolvedModel(model, provider);
    }

    private boolean isModelAvailable(LlmModel model, ProviderConfig provider) {
        if (model == null || provider == null) {
            return false;
        }
        if (model.getModelName() == null || model.getModelName().isBlank()) {
            return false;
        }
        if (!"ollama".equalsIgnoreCase(provider.getId())) {
            return true;
        }
        String baseUrl = provider.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return false;
        }

        String cacheKey = provider.getId() + "|" + baseUrl;
        long now = System.currentTimeMillis();
        AvailabilityCacheEntry cached = availabilityCache.get(cacheKey);
        if (cached != null && now - cached.loadedAtMillis() < MODEL_AVAILABILITY_CACHE_TTL_MS) {
            return cached.models().contains(model.getModelName());
        }

        Set<String> availableModels = discoverOllamaModels(baseUrl);
        availabilityCache.put(cacheKey, new AvailabilityCacheEntry(availableModels, now));
        boolean available = availableModels.isEmpty() || availableModels.contains(model.getModelName());
        if (!available) {
            log.warn("Ollama model {} not found in runtime model list from {}", model.getModelName(), baseUrl);
        }
        return available;
    }

    private Set<String> discoverOllamaModels(String baseUrl) {
        String rootUrl = baseUrl.replaceAll("/v1/?$", "");
        String tagsUrl = rootUrl + "/api/tags";
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(tagsUrl, Map.class);
            Object models = response.getBody() != null ? response.getBody().get("models") : null;
            if (!(models instanceof Collection<?> collection)) {
                return Set.of();
            }
            Set<String> names = ConcurrentHashMap.newKeySet();
            for (Object item : collection) {
                if (item instanceof Map<?, ?> modelMap) {
                    Object name = modelMap.get("name");
                    if (name instanceof String modelName && !modelName.isBlank()) {
                        names.add(modelName);
                    }
                }
            }
            log.info("Discovered {} Ollama models from {}", names.size(), tagsUrl);
            return Set.copyOf(names);
        } catch (Exception e) {
            log.warn("Failed to discover Ollama models from {}: {}. Keeping configured model pool as tentative.",
                tagsUrl, e.getMessage());
            return Set.of();
        }
    }

    private record AvailabilityCacheEntry(Set<String> models, long loadedAtMillis) {}

    private String getBrainType(String brainId) {
        if (brainId == null) return "default";
        if (brainId.contains("main")) return "main";
        if (brainId.contains("tech")) return "tech";
        if (brainId.contains("admin")) return "admin";
        if (brainId.contains("hr")) return "hr";
        if (brainId.contains("finance")) return "finance";
        if (brainId.contains("sales")) return "sales";
        if (brainId.contains("cs")) return "cs";
        if (brainId.contains("ops")) return "ops";
        if (brainId.contains("legal")) return "legal";
        return "default";
    }
}
