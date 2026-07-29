package com.livingagent.core.model.pool;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class ModelPoolManager {

    private static final Logger log = LoggerFactory.getLogger(ModelPoolManager.class);

    private final ProviderConfigRepository providerRepo;
    private final LlmModelRepository modelRepo;
    private final BrainModelAssignmentRepository assignmentRepo;
    private final ModelCapabilityAssessor modelCapabilityAssessor;
    private final ModelPerformanceAssessor modelPerformanceAssessor;
    private final BrainAutoAssigner brainAutoAssigner;

    public ModelPoolManager(ProviderConfigRepository providerRepo, LlmModelRepository modelRepo,
                            BrainModelAssignmentRepository assignmentRepo,
                            ModelCapabilityAssessor modelCapabilityAssessor,
                            ModelPerformanceAssessor modelPerformanceAssessor,
                            BrainAutoAssigner brainAutoAssigner) {
        this.providerRepo = providerRepo;
        this.modelRepo = modelRepo;
        this.assignmentRepo = assignmentRepo;
        this.modelCapabilityAssessor = modelCapabilityAssessor;
        this.modelPerformanceAssessor = modelPerformanceAssessor;
        this.brainAutoAssigner = brainAutoAssigner;
    }

    @PostConstruct
    public void init() {
        disableNonFreeOpenRouterModels();
        
        if (modelCapabilityAssessor != null) {
            List<LlmModel> allModels = modelRepo.findAll();
            if (!allModels.isEmpty()) {
                log.info("Assessing {} existing models on startup (capability inference)...", allModels.size());
                modelCapabilityAssessor.assessModels(allModels);
                modelRepo.saveAll(allModels);
                log.info("Model capability assessment completed. All models now have capability tags and performance scores.");
            } else {
                log.info("No models found in database, will assess during seedDefaults or when models are added.");
            }
        }

        if (modelPerformanceAssessor != null) {
            List<LlmModel> enabledModels = modelRepo.findByEnabledTrue();
            if (!enabledModels.isEmpty()) {
                log.info("Starting performance assessment of {} enabled models on startup...", enabledModels.size());
                Thread.startVirtualThread(() -> {
                    try {
                        Thread.sleep(15000);
                        List<ModelPerformanceAssessor.AssessmentResult> results = modelPerformanceAssessor.assessAllEnabledModels();
                        long available = results.stream().filter(ModelPerformanceAssessor.AssessmentResult::available).count();
                        long unavailable = results.stream().filter(r -> !r.available()).count();
                        log.info("Startup performance assessment completed: available={}, unavailable={}", available, unavailable);
                        brainAutoAssigner.tryAutoAssignIfNeeded();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        log.error("Startup performance assessment failed: {}", e.getMessage());
                    }
                });
            }
        }
    }
    
    private void disableNonFreeOpenRouterModels() {
        List<LlmModel> openRouterModels = modelRepo.findByProviderId("openrouter");
        int disabled = 0;
        for (LlmModel model : openRouterModels) {
            if (!model.getModelName().endsWith(":free") && model.isEnabled()) {
                model.setEnabled(false);
                modelRepo.save(model);
                disabled++;
                log.info("Disabled non-free OpenRouter model: {}", model.getModelName());
            }
        }
        if (disabled > 0) {
            log.info("Disabled {} non-free OpenRouter models (only :free suffix models are usable without API key)", disabled);
        }
    }

    public List<ProviderConfig> getAllProviders() {
        return providerRepo.findByEnabledTrue();
    }

    public ProviderConfig getProviderWithoutKey(String providerId) {
        ProviderConfig config = providerRepo.findById(providerId).orElse(null);
        if (config != null) {
            return config.cloneWithoutKey();
        }
        return null;
    }

    @Transactional
    public ProviderConfig addProvider(ProviderConfig config) {
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());
        ProviderConfig saved = providerRepo.save(config);

        if (config.isAutoDiscoverModels()) {
            discoverModels(config);
        }

        return saved;
    }

    @Transactional
    public ProviderConfig updateProvider(String providerId, ProviderConfig config) {
        ProviderConfig existing = providerRepo.findById(providerId).orElseThrow(
            () -> new IllegalArgumentException("Provider not found: " + providerId));

        existing.setDisplayName(config.getDisplayName());
        existing.setProtocol(config.getProtocol());
        existing.setBaseUrl(config.getBaseUrl());
        if (config.getApiKeyEncrypted() != null && !config.getApiKeyEncrypted().isEmpty()) {
            existing.setApiKeyEncrypted(config.getApiKeyEncrypted());
        }
        existing.setEnabled(config.isEnabled());
        existing.setSupportsToolChoice(config.isSupportsToolChoice());
        existing.setDefaultMaxTokens(config.getDefaultMaxTokens());
        existing.setAutoDiscoverModels(config.isAutoDiscoverModels());
        existing.setUpdatedAt(LocalDateTime.now());

        return providerRepo.save(existing);
    }

    @Transactional
    public void deleteProvider(String providerId) {
        if (!providerRepo.existsById(providerId)) {
            throw new IllegalArgumentException("Provider not found: " + providerId);
        }
        providerRepo.deleteById(providerId);
    }

    public ProviderTestResult testProvider(String providerId, String testModel, String tempBaseUrl, String tempApiKey) {
        ProviderConfig config = providerRepo.findById(providerId).orElse(null);
        
        ProviderConfig testConfig = new ProviderConfig();
        
        if (config != null) {
            // Existing provider - use its config
            testConfig.setProtocol(config.getProtocol());
            testConfig.setEnabled(true);
            testConfig.setSupportsToolChoice(config.isSupportsToolChoice());
        } else {
            // New provider (not yet saved) - use preset defaults
            Protocol presetProtocol = switch (providerId.toLowerCase()) {
                case "anthropic" -> Protocol.ANTHROPIC;
                case "gemini" -> Protocol.GEMINI;
                case "openai-response" -> Protocol.OPENAI_RESPONSES;
                default -> Protocol.OPENAI_COMPATIBLE;
            };
            testConfig.setProtocol(presetProtocol);
            testConfig.setEnabled(true);
            testConfig.setSupportsToolChoice(true);
        }

        String effectiveBaseUrl = (tempBaseUrl != null && !tempBaseUrl.isBlank()) ? tempBaseUrl : 
                (config != null ? config.getBaseUrl() : null);
            if (effectiveBaseUrl == null || effectiveBaseUrl.isBlank()) {
                // Fallback to preset defaults
                effectiveBaseUrl = switch (providerId.toLowerCase()) {
                    case "openai", "openrouter" -> "https://api.openai.com/v1";
                    case "anthropic" -> "https://api.anthropic.com";
                    case "gemini" -> "https://generativelanguage.googleapis.com/v1beta";
                    case "deepseek" -> "https://api.deepseek.com/v1";
                    case "qwen" -> "https://dashscope.aliyuncs.com/compatible-mode/v1";
                    case "siliconflow" -> "https://api.siliconflow.cn/v1";
                    case "zhipu" -> "https://open.bigmodel.cn/api/paas/v4";
                    case "kimi" -> "https://api.moonshot.cn/v1";
                    case "modelscope" -> "https://api-inference.modelscope.cn/v1";
                    default -> "http://host.docker.internal:11434/v1";
                };
            }
        testConfig.setBaseUrl(effectiveBaseUrl);

        String effectiveApiKey = (tempApiKey != null && !tempApiKey.isBlank()) ? tempApiKey :
            (config != null ? config.getApiKeyEncrypted() : null);
        testConfig.setApiKeyEncrypted(effectiveApiKey);

        log.info("[testProvider] providerId={}, testModel={}, baseUrl={}, apiKeySource={}, hasKey={}",
            providerId, testModel, effectiveBaseUrl,
            (tempApiKey != null && !tempApiKey.isBlank()) ? "frontend-param" : "db-fallback",
            effectiveApiKey != null && !effectiveApiKey.isEmpty());

        boolean isEmbedding = isEmbeddingModel(testModel);
        if (isEmbedding) {
            log.info("[testProvider] Detected embedding model: {}, using /embeddings endpoint", testModel);
        }

        long start = System.currentTimeMillis();
        try {
            LlmClient client = LlmClientFactory.create(testConfig);
            String response;
            if (isEmbedding) {
                response = client.embed("hello world, test embedding", testModel);
            } else {
                response = client.complete("Say 'ok' and nothing else.", testModel, 16);
            }
            long latency = System.currentTimeMillis() - start;
            return ProviderTestResult.success(latency, response);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            return ProviderTestResult.error("CONNECTION_FAILED", e.getMessage());
        }
    }

    @Transactional
    public List<String> discoverModels(ProviderConfig config) {
        List<String> discovered = new ArrayList<>();
        String baseUrl = config.getBaseUrl() != null ? config.getBaseUrl() : "http://host.docker.internal:11434";
        String apiKey = config.getApiKeyEncrypted();
        String providerId = config.getId().toLowerCase();

        switch (providerId) {
            case "ollama":
                discoverOllama(config, baseUrl, discovered);
                break;
            case "vllm":
            case "sglang":
                discoverOpenAICompatible(config, baseUrl, apiKey, discovered);
                break;
            case "openai":
            case "deepseek":
            case "moonshot":
            case "zhipu":
            case "openrouter":
                discoverOpenAICompatible(config, baseUrl, apiKey, discovered);
                break;
            default:
                discoverOpenAICompatible(config, baseUrl, apiKey, discovered);
                break;
        }
        return discovered;
    }

    private void discoverOllama(ProviderConfig config, String baseUrl, List<String> discovered) {
        String ollamaDiscovered = tryOllamaDiscover(config, baseUrl);
        if (ollamaDiscovered != null) {
            String[] names = ollamaDiscovered.split(",");
            for (String modelId : names) {
                if (!modelId.isBlank()) {
                    discovered.add(modelId.trim());
                    saveDiscoveredModel(config, modelId.trim(), "本地模型", "text");
                }
            }
        }
    }

    private void discoverOpenAICompatible(ProviderConfig config, String baseUrl, String apiKey, List<String> discovered) {
        if (apiKey != null && !apiKey.isEmpty()) {
            tryDiscoverOpenAI(config, baseUrl, apiKey, discovered);
        } else {
            tryDiscoverOpenAIWithoutAuth(config, baseUrl, discovered);
        }
    }

    private String tryOllamaDiscover(ProviderConfig config, String baseUrl) {
        String ollamaBase = baseUrl.endsWith("/v1") ? baseUrl.substring(0, baseUrl.length() - 3) : baseUrl;
        ollamaBase = ollamaBase.endsWith("/") ? ollamaBase.substring(0, ollamaBase.length() - 1) : ollamaBase;
        try {
            String urlStr = ollamaBase + "/api/tags";
            log.info("Trying Ollama-style discovery for provider {} at URL: {}", config.getId(), urlStr);
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            int status = conn.getResponseCode();
            log.info("Ollama discovery response status: {} for provider {}", status, config.getId());
            if (status == 200) {
                String body = new String(conn.getInputStream().readAllBytes());
                var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                var root = mapper.readTree(body);
                var models = root.path("models");
                StringBuilder sb = new StringBuilder();
                for (var model : models) {
                    String name = model.path("name").asText();
                    if (!name.isEmpty()) {
                        if (!sb.isEmpty()) sb.append(",");
                        sb.append(name);
                    }
                }
                log.info("Ollama discovery found models: {}", sb);
                return sb.toString();
            }
        } catch (Exception e) {
            log.info("Ollama discover attempt failed for {} (expected for non-Ollama providers): {}", config.getId(), e.getMessage());
        }
        return null;
    }

    private void tryDiscoverOpenAIWithoutAuth(ProviderConfig config, String baseUrl, List<String> discovered) {
        try {
            String urlStr = baseUrl + "/models";
            log.info("Discovering models for provider {} at URL: {}", config.getId(), urlStr);
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            int status = conn.getResponseCode();
            log.info("Model discovery response status: {} for provider {}", status, config.getId());
            if (status == 200) {
                String body = new String(conn.getInputStream().readAllBytes());
                log.info("Model discovery response body: {}", body.length() > 500 ? body.substring(0, 500) + "..." : body);
                var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                var root = mapper.readTree(body);
                var models = root.path("data");

                for (var model : models) {
                    String modelId = model.path("id").asText();
                    if (modelId != null && !modelId.isEmpty()) {
                        discovered.add(modelId);
                        saveDiscoveredModel(config, modelId, "本地模型", "text");
                    }
                }
                log.info("Discovered {} models for provider {}", discovered.size(), config.getId());
            } else {
                String errorBody = "";
                try {
                    errorBody = new String(conn.getErrorStream().readAllBytes());
                } catch (Exception ignored) {}
                log.warn("Model discovery failed with status {} for provider {}: {}", status, config.getId(), errorBody);
            }
        } catch (Exception e) {
            log.warn("OpenAI discover (no auth) failed for {}: {}", config.getId(), e.getMessage());
        }
    }

    private void tryDiscoverOpenAI(ProviderConfig config, String baseUrl, String apiKey, List<String> discovered) {
        try {
            String urlStr = baseUrl + "/models";
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            int status = conn.getResponseCode();
            if (status == 200) {
                String body = new String(conn.getInputStream().readAllBytes());
                var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                var root = mapper.readTree(body);
                var models = root.path("data");

                boolean isOpenRouter = "openrouter".equalsIgnoreCase(config.getId());
                int totalModels = 0;
                int freeModels = 0;

                for (var model : models) {
                    String modelId = model.path("id").asText();
                    if (modelId != null && !modelId.isEmpty()) {
                        totalModels++;
                        
                        if (isOpenRouter) {
                            if (modelId.endsWith(":free")) {
                                freeModels++;
                                discovered.add(modelId);
                                saveDiscoveredModel(config, modelId, "云端模型(免费)", "text");
                            } else {
                                log.debug("Skipping non-free OpenRouter model: {}", modelId);
                            }
                        } else {
                            discovered.add(modelId);
                            saveDiscoveredModel(config, modelId, "云端模型", "text");
                        }
                    }
                }
                
                if (isOpenRouter) {
                    log.info("OpenRouter model discovery: total={}, free models saved={}", totalModels, freeModels);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to discover API models for {}: {}", config.getId(), e.getMessage());
        }
    }

    private void saveDiscoveredModel(ProviderConfig config, String modelId, String bestFor, String inputTypes) {
        if (!modelRepo.existsByProviderIdAndModelName(config.getId(), modelId)) {
            LlmModel m = new LlmModel(
                config.getId(), modelId, modelId, 128000, config.getDefaultMaxTokens(),
                false, false, null, true, false, bestFor, inputTypes
            );
            m.setCreatedAt(LocalDateTime.now());
            modelRepo.save(m);

            if (modelCapabilityAssessor != null) {
                modelCapabilityAssessor.assessModel(m);
                modelRepo.save(m);
            }

            // 发现模型时进行性能测试（验证可用性）
            if (modelPerformanceAssessor != null && m.isEnabled()) {
                try {
                    ModelPerformanceAssessor.AssessmentResult result = modelPerformanceAssessor.assessModel(m, null);
                    if (result.available()) {
                        log.info("Discovered model performance test passed: provider={}, model={}, time={}ms",
                            config.getId(), modelId, result.responseTimeMs());
                    } else {
                        log.warn("Discovered model performance test FAILED: provider={}, model={}, error={}",
                            config.getId(), modelId, result.error());
                        m.setEnabled(false);
                        modelRepo.save(m);
                    }
                } catch (Exception e) {
                    log.error("Discovered model performance test error: provider={}, model={}, error={}",
                        config.getId(), modelId, e.getMessage());
                }
            }

            log.info("Discovered model {} for provider {} at {} (tags={}, score={})",
                modelId, config.getId(), config.getBaseUrl(), m.getCapabilityTags(), m.getPerformanceScore());
        }
    }

    public List<LlmModel> getAllModels() {
        return modelRepo.findAll();
    }

    public List<LlmModel> getModelsByProvider(String providerId) {
        return modelRepo.findByProviderIdAndEnabledTrue(providerId);
    }

    public LlmModel getModelById(java.util.UUID modelId) {
        return modelRepo.findById(modelId).orElse(null);
    }

    @Transactional
    public LlmModel addModel(LlmModel model) {
        Optional<LlmModel> existing = modelRepo.findByProviderIdAndModelName(model.getProviderId(), model.getModelName());
        if (existing.isPresent()) {
            LlmModel e = existing.get();
            e.setDisplayName(model.getDisplayName());
            e.setContextWindow(model.getContextWindow());
            e.setMaxOutputTokens(model.getMaxOutputTokens());
            e.setSupportsVision(model.isSupportsVision());
            e.setSupportsReasoning(model.isSupportsReasoning());
            e.setTemperature(model.getTemperature());
            e.setEnabled(model.isEnabled());
            e.setRecommended(model.isRecommended());
            e.setBestFor(model.getBestFor());
            e.setInputTypes(model.getInputTypes());
            return modelRepo.save(e);
        }
        model.setCreatedAt(LocalDateTime.now());
        LlmModel saved = modelRepo.save(model);

        // 新增模型时，先进行能力评定（基于规则推断）
        if (modelCapabilityAssessor != null) {
            modelCapabilityAssessor.assessModel(saved);
            modelRepo.save(saved);
            log.info("Model capability assessed on add (rule-based): provider={}, model={}, tags={}, score={}",
                saved.getProviderId(), saved.getModelName(), saved.getCapabilityTags(), saved.getPerformanceScore());
        }

        // 然后进行性能测试（实际调用模型验证可用性）
        if (modelPerformanceAssessor != null && saved.isEnabled()) {
            try {
                ModelPerformanceAssessor.AssessmentResult result = modelPerformanceAssessor.assessModel(saved, null);
                if (result.available()) {
                    log.info("Model performance test passed on add: provider={}, model={}, time={}ms, score={}",
                        saved.getProviderId(), saved.getModelName(), result.responseTimeMs(), result.performanceScore());
                } else {
                    log.warn("Model performance test FAILED on add: provider={}, model={}, error={}",
                        saved.getProviderId(), saved.getModelName(), result.error());
                    saved.setEnabled(false);
                    modelRepo.save(saved);
                }
            } catch (Exception e) {
                log.error("Model performance test error on add: provider={}, model={}, error={}",
                    saved.getProviderId(), saved.getModelName(), e.getMessage());
            }
        }

        return saved;
    }

    @Transactional
    public LlmModel updateModel(java.util.UUID modelId, LlmModel model) {
        LlmModel existing = modelRepo.findById(modelId).orElseThrow(
            () -> new IllegalArgumentException("Model not found: " + modelId));

        existing.setModelName(model.getModelName());
        existing.setDisplayName(model.getDisplayName());
        existing.setContextWindow(model.getContextWindow());
        existing.setMaxOutputTokens(model.getMaxOutputTokens());
        existing.setSupportsVision(model.isSupportsVision());
        existing.setSupportsReasoning(model.isSupportsReasoning());
        existing.setTemperature(model.getTemperature());
        existing.setEnabled(model.isEnabled());
        existing.setRecommended(model.isRecommended());
        existing.setBestFor(model.getBestFor());
        existing.setInputTypes(model.getInputTypes());

        return modelRepo.save(existing);
    }

    @Transactional
    public void deleteModel(java.util.UUID modelId) {
        if (!modelRepo.existsById(modelId)) {
            throw new IllegalArgumentException("Model not found: " + modelId);
        }
        modelRepo.deleteById(modelId);
    }

    @Transactional
    public int batchDeleteModels(List<java.util.UUID> modelIds) {
        int count = 0;
        for (java.util.UUID modelId : modelIds) {
            if (modelRepo.existsById(modelId)) {
                modelRepo.deleteById(modelId);
                count++;
            }
        }
        return count;
    }

    public List<BrainModelAssignment> getAllAssignments() {
        return assignmentRepo.findAll();
    }

    public BrainModelAssignment getAssignmentByBrain(String brainId) {
        return assignmentRepo.findByBrainId(brainId).orElse(null);
    }

    @Transactional
    public BrainModelAssignment assignModel(String brainId, java.util.UUID modelId, String assignedBy) {
        LlmModel model = modelRepo.findById(modelId).orElseThrow(
            () -> new IllegalArgumentException("Model not found: " + modelId));

        BrainModelAssignment assignment = assignmentRepo.findByBrainId(brainId).orElse(new BrainModelAssignment());
        assignment.setBrainId(brainId);
        assignment.setBrainName(model.getDisplayName());
        assignment.setModelId(modelId);
        assignment.setAssignedBy(assignedBy != null ? assignedBy : "system");
        assignment.setUpdatedAt(LocalDateTime.now());

        if (assignment.getId() == null) {
            assignment.setAssignedAt(LocalDateTime.now());
        }

        return assignmentRepo.save(assignment);
    }

    @Transactional
    public void clearAssignment(String brainId) {
        assignmentRepo.findByBrainId(brainId).ifPresent(assignmentRepo::delete);
    }

    @Transactional
    public List<LlmModel> getAvailableModels() {
        List<LlmModel> models = modelRepo.findByEnabledTrue();

        if (modelCapabilityAssessor != null) {
            List<LlmModel> needsAssessment = models.stream()
                .filter(m -> m.getCapabilityTags() == null || m.getPerformanceScore() == null)
                .toList();

            if (!needsAssessment.isEmpty()) {
                log.info("Assessing {} models that haven't been evaluated yet", needsAssessment.size());
                modelCapabilityAssessor.assessModels(needsAssessment);
                modelRepo.saveAll(needsAssessment);
            }
        }

        return models;
    }

    @Transactional
    public void seedDefaults() {
        long existingModelCount = modelRepo.count();
        if (existingModelCount > 0) {
            log.info("Model pool already has {} models, skipping seed defaults", existingModelCount);
            return;
        }

        log.info("Model pool is empty, seeding default data...");

        if (!providerRepo.existsById("qwen")) {
            ProviderConfig qwen = new ProviderConfig();
            qwen.setId("qwen");
            qwen.setDisplayName("Qwen (DashScope)");
            qwen.setProtocol(Protocol.OPENAI_COMPATIBLE);
            qwen.setBaseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1");
            qwen.setEnabled(true);
            qwen.setDefaultMaxTokens(8192);
            qwen.setCreatedAt(LocalDateTime.now());
            qwen.setUpdatedAt(LocalDateTime.now());
            providerRepo.save(qwen);
        }

        if (!providerRepo.existsById("ollama")) {
            ProviderConfig ollama = new ProviderConfig();
            ollama.setId("ollama");
            ollama.setDisplayName("Ollama");
            ollama.setProtocol(Protocol.OPENAI_COMPATIBLE);
            ollama.setBaseUrl("http://host.docker.internal:11434");
            ollama.setEnabled(true);
            ollama.setDefaultMaxTokens(4096);
            ollama.setCreatedAt(LocalDateTime.now());
            ollama.setUpdatedAt(LocalDateTime.now());
            providerRepo.save(ollama);
        }

        if (!providerRepo.existsById("anthropic")) {
            ProviderConfig anthropic = new ProviderConfig();
            anthropic.setId("anthropic");
            anthropic.setDisplayName("Anthropic");
            anthropic.setProtocol(Protocol.ANTHROPIC);
            anthropic.setBaseUrl("https://api.anthropic.com");
            anthropic.setEnabled(false);
            anthropic.setDefaultMaxTokens(8192);
            anthropic.setCreatedAt(LocalDateTime.now());
            anthropic.setUpdatedAt(LocalDateTime.now());
            providerRepo.save(anthropic);
        }

        for (LlmModel model : BuiltinModelCatalog.getAllModels()) {
            if (!modelRepo.existsByProviderIdAndModelName(model.getProviderId(), model.getModelName())) {
                modelRepo.save(model);
            }
        }

        if (modelCapabilityAssessor != null) {
            List<LlmModel> allModels = modelRepo.findAll();
            modelCapabilityAssessor.assessModels(allModels);
            modelRepo.saveAll(allModels);
        }

        log.info("Model pool default data seeded successfully ({} models)", BuiltinModelCatalog.getAllModels().size());
    }

    private static final java.util.Set<String> EMBEDDING_KEYWORDS = java.util.Set.of(
        "bge", "e5", "embed", "embedding", "text-embedding", "ada", "gte", "jina",
        "m3e", "voyage", "cohere-embed", "retrieval", "sentence", "nomic"
    );

    private boolean isEmbeddingModel(String modelName) {
        if (modelName == null) return false;
        String lower = modelName.toLowerCase();
        for (String kw : EMBEDDING_KEYWORDS) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }
}
