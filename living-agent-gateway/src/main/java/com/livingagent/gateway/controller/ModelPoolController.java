package com.livingagent.gateway.controller;

import com.livingagent.core.model.pool.*;
import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.security.AuthContext;
import com.livingagent.core.security.auth.UnifiedAuthService;
import com.livingagent.core.security.auth.UnifiedAuthService.AuthSession;
import com.livingagent.gateway.dto.*;
import com.livingagent.gateway.security.ModelPoolPermission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/model-pool")
public class ModelPoolController {

    private final Logger log = LoggerFactory.getLogger(ModelPoolController.class);
    private final ModelPoolManager modelPoolManager;
    private final com.livingagent.core.model.pool.ModelPerformanceAssessor modelPerformanceAssessor;
    private final UnifiedAuthService unifiedAuthService;
    private final com.livingagent.core.model.pool.BrainAutoAssigner brainAutoAssigner;

    public ModelPoolController(ModelPoolManager modelPoolManager,
                               com.livingagent.core.model.pool.ModelPerformanceAssessor modelPerformanceAssessor,
                               UnifiedAuthService unifiedAuthService,
                               com.livingagent.core.model.pool.BrainAutoAssigner brainAutoAssigner) {
        this.modelPoolManager = modelPoolManager;
        this.modelPerformanceAssessor = modelPerformanceAssessor;
        this.unifiedAuthService = unifiedAuthService;
        this.brainAutoAssigner = brainAutoAssigner;
    }

    @GetMapping("/providers")
    public ResponseEntity<Map<String, Object>> getAllProviders() {
        List<ProviderConfig> providers = modelPoolManager.getAllProviders();
        List<Map<String, Object>> maskedProviders = providers.stream().map(p -> {
            Map<String, Object> providerMap = new HashMap<>();
            providerMap.put("id", p.getId());
            providerMap.put("displayName", p.getDisplayName());
            providerMap.put("protocol", p.getProtocol().name());
            providerMap.put("baseUrl", p.getBaseUrl());
            providerMap.put("apiKeyConfigured", p.getApiKeyEncrypted() != null && !p.getApiKeyEncrypted().isEmpty());
            providerMap.put("enabled", p.isEnabled());
            providerMap.put("supportsToolChoice", p.isSupportsToolChoice());
            providerMap.put("defaultMaxTokens", p.getDefaultMaxTokens());
            providerMap.put("autoDiscoverModels", p.isAutoDiscoverModels());
            providerMap.put("createdAt", p.getCreatedAt());
            providerMap.put("updatedAt", p.getUpdatedAt());
            return providerMap;
        }).toList();
        return ResponseEntity.ok(Map.of("success", true, "data", maskedProviders));
    }

    @GetMapping("/providers/{id}")
    public ResponseEntity<Map<String, Object>> getProvider(@PathVariable String id) {
        ProviderConfig provider = modelPoolManager.getProviderWithoutKey(id);
        if (provider == null) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> data = new HashMap<>();
        data.put("id", provider.getId());
        data.put("displayName", provider.getDisplayName());
        data.put("protocol", provider.getProtocol().name());
        data.put("baseUrl", provider.getBaseUrl());
        data.put("apiKeyConfigured", provider.getApiKeyEncrypted() != null && !provider.getApiKeyEncrypted().isEmpty());
        data.put("enabled", provider.isEnabled());
        data.put("supportsToolChoice", provider.isSupportsToolChoice());
        data.put("defaultMaxTokens", provider.getDefaultMaxTokens());
        data.put("autoDiscoverModels", provider.isAutoDiscoverModels());
        data.put("createdAt", provider.getCreatedAt());
        data.put("updatedAt", provider.getUpdatedAt());
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }

    @PostMapping("/providers")
    public ResponseEntity<Map<String, Object>> addProvider(@RequestBody ProviderRequest request) {
        // Check if provider already exists (upsert behavior)
        ProviderConfig existing = modelPoolManager.getAllProviders().stream()
            .filter(p -> p.getId().equals(request.id()))
            .findFirst()
            .orElse(null);
        
        if (existing != null) {
            // Update existing provider
            ProviderConfig updateConfig = new ProviderConfig();
            updateConfig.setDisplayName(request.displayName());
            updateConfig.setProtocol(request.protocol());
            updateConfig.setBaseUrl(request.baseUrl());
            if (request.apiKeyEncrypted() != null && !request.apiKeyEncrypted().isEmpty()) {
                updateConfig.setApiKeyEncrypted(request.apiKeyEncrypted());
            }
            updateConfig.setEnabled(request.enabled());
            updateConfig.setSupportsToolChoice(request.supportsToolChoice());
            updateConfig.setDefaultMaxTokens(request.defaultMaxTokens());
            updateConfig.setAutoDiscoverModels(request.autoDiscoverModels());
            ProviderConfig updated = modelPoolManager.updateProvider(request.id(), updateConfig);
            return ResponseEntity.ok(Map.of("success", true, "data", updated));
        }
        
        ProviderConfig config = new ProviderConfig();
        config.setId(request.id());
        config.setDisplayName(request.displayName());
        config.setProtocol(request.protocol());
        config.setBaseUrl(request.baseUrl());
        config.setApiKeyEncrypted(request.apiKeyEncrypted());
        config.setEnabled(request.enabled());
        config.setSupportsToolChoice(request.supportsToolChoice());
        config.setDefaultMaxTokens(request.defaultMaxTokens());
        config.setAutoDiscoverModels(request.autoDiscoverModels());

        ProviderConfig saved = modelPoolManager.addProvider(config);
        return ResponseEntity.ok(Map.of("success", true, "data", saved));
    }

    @PutMapping("/providers/{id}")
    public ResponseEntity<Map<String, Object>> updateProvider(
            @PathVariable String id, @RequestBody ProviderRequest request) {
        ProviderConfig config = new ProviderConfig();
        config.setDisplayName(request.displayName());
        config.setProtocol(request.protocol());
        config.setBaseUrl(request.baseUrl());
        config.setApiKeyEncrypted(request.apiKeyEncrypted());
        config.setEnabled(request.enabled());
        config.setSupportsToolChoice(request.supportsToolChoice());
        config.setDefaultMaxTokens(request.defaultMaxTokens());
        config.setAutoDiscoverModels(request.autoDiscoverModels());

        ProviderConfig updated = modelPoolManager.updateProvider(id, config);
        return ResponseEntity.ok(Map.of("success", true, "data", updated));
    }

    @DeleteMapping("/providers/{id}")
    public ResponseEntity<Map<String, Object>> deleteProvider(@PathVariable String id) {
        modelPoolManager.deleteProvider(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/providers/{id}/test")
    public ResponseEntity<Map<String, Object>> testProvider(
            @PathVariable String id, @RequestBody ProviderTestRequest request) {
        ProviderTestResult result = modelPoolManager.testProvider(id, request.testModel(), request.baseUrl(), request.apiKeyEncrypted());
        return ResponseEntity.ok(Map.of("success", result.isSuccess(), "data", Map.of(
            "success", result.isSuccess(),
            "latencyMs", result.getLatencyMs(),
            "response", result.getResponse() != null ? result.getResponse() : "",
            "message", result.getMessage() != null ? result.getMessage() : "",
            "error", result.getError() != null ? result.getError() : ""
        )));
    }

    @PostMapping("/providers/{id}/discover")
    public ResponseEntity<Map<String, Object>> discoverModels(
            @PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        String baseUrl = body != null ? body.get("baseUrl") : null;
        String apiKey = body != null ? body.get("apiKeyEncrypted") : null;
        
        ProviderConfig config = modelPoolManager.getProviderWithoutKey(id);
        if (config == null) {
            config = new ProviderConfig();
            config.setId(id);
            config.setBaseUrl(baseUrl);
        }
        if (baseUrl != null) config.setBaseUrl(baseUrl);
        if (apiKey != null) config.setApiKeyEncrypted(apiKey);
        
        List<String> discovered = modelPoolManager.discoverModels(config);
        if (!discovered.isEmpty()) {
            java.util.concurrent.Executors.newSingleThreadExecutor().submit(() -> brainAutoAssigner.tryAutoAssignIfNeeded());
        }
        return ResponseEntity.ok(Map.of("success", true, "data", discovered));
    }

    @GetMapping("/models")
    public ResponseEntity<Map<String, Object>> getAllModels() {
        List<LlmModel> models = modelPoolManager.getAllModels();
        return ResponseEntity.ok(Map.of("success", true, "data", models));
    }

    @GetMapping("/models/{id}")
    public ResponseEntity<Map<String, Object>> getModel(@PathVariable java.util.UUID id) {
        LlmModel model = modelPoolManager.getModelById(id);
        if (model == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("success", true, "data", model));
    }

    @GetMapping("/models/provider/{providerId}")
    public ResponseEntity<Map<String, Object>> getModelsByProvider(@PathVariable String providerId) {
        List<LlmModel> models = modelPoolManager.getModelsByProvider(providerId);
        return ResponseEntity.ok(Map.of("success", true, "data", models));
    }

    @PostMapping("/models")
    public ResponseEntity<Map<String, Object>> addModel(@RequestBody ModelRequest request) {
        try {
            LlmModel model = new LlmModel(
                request.providerId(), request.modelName(), request.displayName(),
                request.contextWindow(), request.maxOutputTokens(),
                request.supportsVision(), request.supportsReasoning(),
                request.temperature(), request.enabled(), request.recommended(),
                request.bestFor(), request.inputTypes()
            );
            LlmModel saved = modelPoolManager.addModel(model);
            java.util.concurrent.Executors.newSingleThreadExecutor().submit(() -> brainAutoAssigner.tryAutoAssignIfNeeded());
            return ResponseEntity.ok(Map.of("success", true, "data", saved));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(409)
                .body(Map.of("success", false, "error", "MODEL_EXISTS", "message", e.getMessage()));
        }
    }

    @PutMapping("/models/{id}")
    public ResponseEntity<Map<String, Object>> updateModel(
            @PathVariable java.util.UUID id, @RequestBody ModelRequest request) {
        LlmModel model = new LlmModel(
            request.providerId(), request.modelName(), request.displayName(),
            request.contextWindow(), request.maxOutputTokens(),
            request.supportsVision(), request.supportsReasoning(),
            request.temperature(), request.enabled(), request.recommended(),
            request.bestFor(), request.inputTypes()
        );
        model.setId(id);
        LlmModel updated = modelPoolManager.updateModel(id, model);
        return ResponseEntity.ok(Map.of("success", true, "data", updated));
    }

    @DeleteMapping("/models/{id}")
    public ResponseEntity<Map<String, Object>> deleteModel(@PathVariable java.util.UUID id) {
        modelPoolManager.deleteModel(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/models/batch-delete")
    public ResponseEntity<Map<String, Object>> batchDeleteModels(@RequestBody Map<String, List<java.util.UUID>> body) {
        List<java.util.UUID> ids = body.get("ids");
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "ids is required"));
        }
        int deleted = modelPoolManager.batchDeleteModels(ids);
        return ResponseEntity.ok(Map.of("success", true, "data", Map.of("deleted", deleted, "total", ids.size())));
    }

    @GetMapping("/assignments")
    public ResponseEntity<Map<String, Object>> getAllAssignments() {
        List<BrainModelAssignment> assignments = modelPoolManager.getAllAssignments();
        return ResponseEntity.ok(Map.of("success", true, "data", assignments));
    }

    @GetMapping("/assignments/{brainId}")
    public ResponseEntity<Map<String, Object>> getAssignment(@PathVariable String brainId) {
        BrainModelAssignment assignment = modelPoolManager.getAssignmentByBrain(brainId);
        if (assignment == null) {
            return ResponseEntity.ok(Map.of("success", true, "data", null));
        }
        return ResponseEntity.ok(Map.of("success", true, "data", assignment));
    }

    @PostMapping("/assignments/{brainId}")
    public ResponseEntity<Map<String, Object>> assignModel(
            @PathVariable String brainId, @RequestBody AssignModelRequest request) {
        BrainModelAssignment assignment = modelPoolManager.assignModel(brainId, request.modelId(), request.assignedBy());
        return ResponseEntity.ok(Map.of("success", true, "data", assignment));
    }

    @DeleteMapping("/assignments/{brainId}")
    public ResponseEntity<Map<String, Object>> clearAssignment(@PathVariable String brainId) {
        modelPoolManager.clearAssignment(brainId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/models/available")
    public ResponseEntity<Map<String, Object>> getAvailableModels() {
        List<LlmModel> models = modelPoolManager.getAvailableModels();
        return ResponseEntity.ok(Map.of("success", true, "data", models));
    }

    /**
     * 根据当前用户的部门和角色，返回可见模型列表
     *
     * GET /api/model-pool/visible
     */
    @GetMapping("/visible")
    public ResponseEntity<Map<String, Object>> getVisibleModels(
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        List<LlmModel> allModels = modelPoolManager.getAvailableModels();

        if (authorization == null || !authorization.startsWith("Bearer ")) {
            // 未登录用户只能看到基础模型
            List<LlmModel> basicModels = allModels.stream()
                .filter(m -> m.getBestFor() != null && m.getBestFor().contains("chat"))
                .limit(5)
                .toList();
            return ResponseEntity.ok(Map.of("success", true, "data", basicModels));
        }

        String sessionId = authorization.substring(7);
        Optional<AuthSession> sessionOpt = unifiedAuthService.validateSession(sessionId);

        if (sessionOpt.isEmpty()) {
            return ResponseEntity.status(401)
                .body(Map.of("success", false, "error", "session_expired"));
        }

        AuthContext authContext = sessionOpt.get().authContext();
        AccessLevel accessLevel = authContext.getAccessLevel();
        String department = authContext.getDepartment();

        List<LlmModel> visibleModels = switch (accessLevel) {
            case FULL -> allModels;
            case DEPARTMENT -> allModels.stream()
                .filter(m -> isModelVisibleToDepartment(m, department))
                .toList();
            case LIMITED -> allModels.stream()
                .filter(m -> isModelVisibleToLimited(m))
                .toList();
            case CHAT_ONLY -> allModels.stream()
                .filter(m -> m.getBestFor() != null && m.getBestFor().contains("chat"))
                .limit(3)
                .toList();
        };

        return ResponseEntity.ok(Map.of("success", true, "data", visibleModels));
    }

    private boolean isModelVisibleToDepartment(LlmModel model, String department) {
        String bestFor = model.getBestFor();
        if (bestFor == null) return true;
        if (bestFor.contains("chat")) return true;
        if (bestFor.contains(department)) return true;
        if (bestFor.contains("general")) return true;
        return bestFor.length() <= 10;
    }

    private boolean isModelVisibleToLimited(LlmModel model) {
        String bestFor = model.getBestFor();
        if (bestFor == null) return false;
        return bestFor.contains("chat") ||
               bestFor.contains("admin") ||
               bestFor.contains("cs");
    }

    @GetMapping("/providers/manifest")
    public ResponseEntity<Map<String, Object>> getProviderManifest() {
        List<LlmProviderRegistry.ProviderEntry> providers = LlmProviderRegistry.getAll();
        return ResponseEntity.ok(Map.of("success", true, "data", providers));
    }

    @GetMapping("/providers/{id}/default-base-url")
    public ResponseEntity<Map<String, Object>> getDefaultBaseUrl(@PathVariable String id) {
        String url = LlmProviderRegistry.getDefaultBaseUrl(id);
        return ResponseEntity.ok(Map.of("success", true, "data", url));
    }

    /**
     * T05 模型管理员专用：评定所有已启用模型的性能
     */
    @PostMapping("/assess/all")
    public ResponseEntity<Map<String, Object>> assessAllModels() {
        try {
            List<com.livingagent.core.model.pool.ModelPerformanceAssessor.AssessmentResult> results = 
                modelPerformanceAssessor.assessAllEnabledModels();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", Map.of(
                    "total", results.size(),
                    "available", results.stream().filter(r -> r.available()).count(),
                    "failed", results.stream().filter(r -> !r.available()).count(),
                    "results", results
                )
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * T05 模型管理员专用：评定指定 Provider 的模型性能
     */
    @PostMapping("/assess/provider/{providerId}")
    public ResponseEntity<Map<String, Object>> assessProviderModels(@PathVariable String providerId) {
        try {
            List<com.livingagent.core.model.pool.ModelPerformanceAssessor.AssessmentResult> results = 
                modelPerformanceAssessor.assessProviderModels(providerId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", Map.of(
                    "providerId", providerId,
                    "total", results.size(),
                    "available", results.stream().filter(r -> r.available()).count(),
                    "failed", results.stream().filter(r -> !r.available()).count(),
                    "results", results
                )
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * T05 模型管理员专用：获取评定进度
     */
    @GetMapping("/assess/progress")
    public ResponseEntity<Map<String, Object>> getAssessmentProgress() {
        com.livingagent.core.model.pool.ModelPerformanceAssessor.AssessmentProgress progress = 
            modelPerformanceAssessor.getProgress();
        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", Map.of(
                "total", progress.total(),
                "completed", progress.completed(),
                "failed", progress.failed(),
                "running", progress.running()
            )
        ));
    }

    /**
     * T05 模型管理员专用：停止正在进行的评定
     */
    @PostMapping("/assess/stop")
    public ResponseEntity<Map<String, Object>> stopAssessment() {
        modelPerformanceAssessor.stopAssessment();
        return ResponseEntity.ok(Map.of("success", true, "message", "Assessment stop requested"));
    }

    @PostMapping("/auto-assign-brains")
    public ResponseEntity<Map<String, Object>> triggerAutoAssign() {
        try {
            brainAutoAssigner.resetAndReassign();
            return ResponseEntity.ok(Map.of("success", true, "message", "Brain auto-assignment triggered"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("success", false, "error", "AUTO_ASSIGN_FAILED", "message", e.getMessage()));
        }
    }
}
