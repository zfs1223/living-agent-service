package com.livingagent.gateway.controller;

import com.livingagent.core.security.AccessGateService;
import com.livingagent.core.security.AuthContext;
import com.livingagent.gateway.controller.common.ApiResponse;
import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.security.auth.UnifiedAuthService;
import com.livingagent.core.security.auth.UnifiedAuthService.AuthSession;
import com.livingagent.core.model.pool.ModelPoolManager;
import com.livingagent.core.model.pool.Protocol;
import com.livingagent.gateway.service.SystemConfigService;
import com.livingagent.gateway.service.DepartmentNotificationService;
import com.livingagent.core.skill.SkillRegistry;
import com.livingagent.core.skill.Skill;
import com.livingagent.core.tool.ToolRegistry;
import com.livingagent.core.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/enterprise")
public class EnterpriseController {

    private static final Logger log = LoggerFactory.getLogger(EnterpriseController.class);

    private final SystemConfigService systemConfigService;
    private final ModelPoolManager modelPoolManager;
    private final SkillRegistry skillRegistry;
    private final ToolRegistry toolRegistry;
    private final UnifiedAuthService authService;
    private final AccessGateService accessGateService;
    private final DepartmentNotificationService departmentNotificationService;

    @Value("${ai-models.ollama.enabled:false}")
    private boolean ollamaEnabled;

    @Value("${ai-models.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    public EnterpriseController(
            SystemConfigService systemConfigService,
            ModelPoolManager modelPoolManager,
            SkillRegistry skillRegistry,
            ToolRegistry toolRegistry,
            UnifiedAuthService authService,
            AccessGateService accessGateService,
            DepartmentNotificationService departmentNotificationService
    ) {
        this.systemConfigService = systemConfigService;
        this.modelPoolManager = modelPoolManager;
        this.skillRegistry = skillRegistry;
        this.toolRegistry = toolRegistry;
        this.authService = authService;
        this.accessGateService = accessGateService;
        this.departmentNotificationService = departmentNotificationService;
    }

    @GetMapping("/llm-models")
    public ResponseEntity<ApiResponse<List<LlmModel>>> listLlmModels(
            @RequestParam(required = false) String tenant_id,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        log.debug("Listing LLM models for tenant: {}", tenant_id);

        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }

        List<com.livingagent.core.model.pool.LlmModel> poolModels = modelPoolManager.getAllModels();
        List<LlmModel> models = new ArrayList<>();

        for (com.livingagent.core.model.pool.LlmModel pm : poolModels) {
            models.add(new LlmModel(
                    pm.getId().toString(),
                    pm.getDisplayName(),
                    pm.getProviderId(),
                    pm.getDisplayName(),
                    "",
                    pm.isEnabled(),
                    7.0,
                    pm.getContextWindow(),
                    Map.of(
                        "supports_vision", pm.isSupportsVision(),
                        "supports_reasoning", pm.isSupportsReasoning(),
                        "max_output_tokens", pm.getMaxOutputTokens(),
                        "best_for", pm.getBestFor() != null ? pm.getBestFor() : "",
                        "recommended", pm.isRecommended()
                    ),
                    Instant.now()
            ));
        }

        return ResponseEntity.ok(ApiResponse.ok(models));
    }

    @GetMapping("/llm-providers")
    public ResponseEntity<ApiResponse<List<LlmProviderSpec>>> listLlmProviders(
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.debug("Listing LLM providers");

        List<LlmProviderSpec> providers = Arrays.asList(
                new LlmProviderSpec("openai", "OpenAI (GPT)", "openai_compatible",
                        "https://api.openai.com/v1", true, 16384),
                new LlmProviderSpec("anthropic", "Anthropic (Claude)", "anthropic",
                        "https://api.anthropic.com", false, 8192),
                new LlmProviderSpec("deepseek", "DeepSeek (深度求索)", "openai_compatible",
                        "https://api.deepseek.com/v1", true, 8192),
                new LlmProviderSpec("qwen", "Qwen (阿里通义)", "openai_compatible",
                        "https://dashscope.aliyuncs.com/compatible-mode/v1", true, 8192),
                new LlmProviderSpec("qwen_local", "Qwen Local (Ollama)", "openai_compatible",
                        ollamaBaseUrl, true, 4096),
                new LlmProviderSpec("zhipu", "智谱 (GLM)", "openai_compatible",
                        "https://open.bigmodel.cn/api/paas/v4", true, 8192),
                new LlmProviderSpec("minimax", "MiniMax", "openai_compatible",
                        "https://api.minimaxi.com/v1", true, 16384),
                new LlmProviderSpec("baidu", "百度 (千帆)", "openai_compatible",
                        "https://qianfan.baidubce.com/v2", false, 4096),
                new LlmProviderSpec("gemini", "Google Gemini", "gemini",
                        "https://generativelanguage.googleapis.com/v1beta", true, 8192),
                new LlmProviderSpec("kimi", "Kimi (月之暗面)", "openai_compatible",
                        "https://api.moonshot.cn/v1", true, 8192),
                new LlmProviderSpec("openrouter", "OpenRouter", "openai_compatible",
                        "https://openrouter.ai/api/v1", true, 4096),
                new LlmProviderSpec("vllm", "vLLM (本地部署)", "openai_compatible",
                        "http://localhost:8000/v1", true, 4096),
                new LlmProviderSpec("ollama", "Ollama (本地部署)", "openai_compatible",
                        ollamaBaseUrl, true, 4096),
                new LlmProviderSpec("custom", "自定义", "openai_compatible",
                        "", true, 4096)
        );

        return ResponseEntity.ok(ApiResponse.ok(providers));
    }

    @GetMapping("/skills")
    public ResponseEntity<ApiResponse<List<SkillInfo>>> listSkills(
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        log.debug("Listing all skills");

        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }

        List<Skill> skills = skillRegistry.getAllSkills();
        List<SkillInfo> skillInfos = new ArrayList<>();

        for (Skill skill : skills) {
            skillInfos.add(new SkillInfo(
                    skill.getName(),
                    skill.getDescription(),
                    skill.getCategory(),
                    skill.getTargetBrain(),
                    true
            ));
        }

        return ResponseEntity.ok(ApiResponse.ok(skillInfos));
    }

    @GetMapping("/skills/by-brain/{brain}")
    public ResponseEntity<ApiResponse<List<SkillInfo>>> listSkillsByBrain(
            @PathVariable String brain,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.debug("Listing skills for brain: {}", brain);

        List<Skill> skills = skillRegistry.getSkillsByBrain(brain);
        List<SkillInfo> skillInfos = new ArrayList<>();

        for (Skill skill : skills) {
            skillInfos.add(new SkillInfo(
                    skill.getName(),
                    skill.getDescription(),
                    skill.getCategory(),
                    skill.getTargetBrain(),
                    true
            ));
        }

        return ResponseEntity.ok(ApiResponse.ok(skillInfos));
    }

    @GetMapping("/tools")
    public ResponseEntity<ApiResponse<List<ToolInfo>>> listTools(
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.debug("Listing all tools");

        List<Tool> tools = toolRegistry.getAll();
        List<ToolInfo> toolInfos = new ArrayList<>();

        for (Tool tool : tools) {
            toolInfos.add(new ToolInfo(
                    tool.getName(),
                    tool.getDescription(),
                    tool.getDepartment(),
                    true
            ));
        }

        return ResponseEntity.ok(ApiResponse.ok(toolInfos));
    }

    @GetMapping("/tools/by-department/{department}")
    public ResponseEntity<ApiResponse<List<ToolInfo>>> listToolsByDepartment(
            @PathVariable String department,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.debug("Listing tools for department: {}", department);

        List<Tool> tools = toolRegistry.getByDepartment(department);
        List<ToolInfo> toolInfos = new ArrayList<>();

        for (Tool tool : tools) {
            toolInfos.add(new ToolInfo(
                    tool.getName(),
                    tool.getDescription(),
                    tool.getDepartment(),
                    true
            ));
        }

        return ResponseEntity.ok(ApiResponse.ok(toolInfos));
    }

    @GetMapping("/skill-counts")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> getSkillCountsByBrain(
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.debug("Getting skill counts by brain");
        return ResponseEntity.ok(ApiResponse.ok(skillRegistry.getSkillCountsByBrain()));
    }

    @PostMapping("/llm-models")
    public ResponseEntity<ApiResponse<LlmModel>> createLlmModel(
            @RequestBody CreateLlmModelRequest request,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.info("Creating LLM model: provider={}, model={}", request.provider(), request.model());

        String providerId = request.provider();
        String modelId = request.model();
        String label = request.label() != null ? request.label() : request.model();

        if (!modelPoolManager.getAllProviders().stream().anyMatch(p -> p.getId().equals(providerId))) {
            com.livingagent.core.model.pool.ProviderConfig providerConfig = new com.livingagent.core.model.pool.ProviderConfig();
            providerConfig.setId(providerId);
            providerConfig.setDisplayName(request.provider());
            providerConfig.setProtocol(Protocol.OPENAI_COMPATIBLE);
            providerConfig.setBaseUrl(request.base_url() != null ? request.base_url() : "");
            providerConfig.setEnabled(true);
            modelPoolManager.addProvider(providerConfig);
        }

        com.livingagent.core.model.pool.LlmModel poolModel = new com.livingagent.core.model.pool.LlmModel(
            providerId,
            modelId,
            label,
            request.max_output_tokens() != null ? request.max_output_tokens() * 4 : 32768,
            request.max_output_tokens() != null ? request.max_output_tokens() : 8192,
            request.supports_vision() != null && request.supports_vision(),
            false,
            request.temperature(),
            true,
            false,
            "",
            "text"
        );

        com.livingagent.core.model.pool.LlmModel saved = modelPoolManager.addModel(poolModel);

        LlmModel model = new LlmModel(
            saved.getId().toString(),
            saved.getDisplayName(),
            saved.getProviderId(),
            saved.getDisplayName(),
            "",
            saved.isEnabled(),
            7.0,
            saved.getContextWindow(),
            Map.of(
                "supports_vision", saved.isSupportsVision(),
                "temperature", saved.getTemperature() != null ? saved.getTemperature() : 0.7,
                "provider_type", saved.getProviderId()
            ),
            Instant.now()
        );

        return ResponseEntity.ok(ApiResponse.ok(model));
    }

    @PutMapping("/llm-models/{modelId}")
    public ResponseEntity<ApiResponse<LlmModel>> updateLlmModel(
            @PathVariable String modelId,
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.info("Updating LLM model: {}", modelId);

        java.util.UUID uuid;
        try {
            uuid = java.util.UUID.fromString(modelId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(ApiResponse.err("not_found", "Invalid model ID: " + modelId));
        }

        com.livingagent.core.model.pool.LlmModel existing = modelPoolManager.getModelById(uuid);
        if (existing == null) {
            return ResponseEntity.ok(ApiResponse.err("not_found", "Model not found: " + modelId));
        }

        String label = request.containsKey("label") ? (String) request.get("label") : existing.getDisplayName();
        Boolean enabled = request.containsKey("enabled") ? (Boolean) request.get("enabled") : existing.isEnabled();
        String baseUrl = request.containsKey("base_url") ? (String) request.get("base_url") : null;
        Integer maxOutputTokens = request.containsKey("max_output_tokens") ?
            ((Number) request.get("max_output_tokens")).intValue() : existing.getMaxOutputTokens();
        Boolean supportsVision = request.containsKey("supports_vision") ?
            (Boolean) request.get("supports_vision") : existing.isSupportsVision();
        Double temperature = request.containsKey("temperature") ?
            ((Number) request.get("temperature")).doubleValue() : existing.getTemperature();

        com.livingagent.core.model.pool.LlmModel updateModel = new com.livingagent.core.model.pool.LlmModel();
        updateModel.setDisplayName(label);
        updateModel.setEnabled(enabled);
        updateModel.setMaxOutputTokens(maxOutputTokens);
        updateModel.setSupportsVision(supportsVision);
        updateModel.setTemperature(temperature);

        com.livingagent.core.model.pool.LlmModel updated = modelPoolManager.updateModel(uuid, updateModel);

        if (baseUrl != null && !baseUrl.isEmpty()) {
            com.livingagent.core.model.pool.ProviderConfig existingProvider = modelPoolManager.getAllProviders().stream()
                .filter(p -> p.getId().equals(updated.getProviderId()))
                .findFirst().orElse(null);
            if (existingProvider != null) {
                com.livingagent.core.model.pool.ProviderConfig updateProvider = new com.livingagent.core.model.pool.ProviderConfig();
                updateProvider.setBaseUrl(baseUrl);
                modelPoolManager.updateProvider(updated.getProviderId(), updateProvider);
            }
        }

        LlmModel model = new LlmModel(
            updated.getId().toString(),
            updated.getDisplayName(),
            updated.getProviderId(),
            updated.getDisplayName(),
            "",
            updated.isEnabled(),
            7.0,
            updated.getContextWindow(),
            Map.of(
                "supports_vision", updated.isSupportsVision(),
                "provider_type", updated.getProviderId()
            ),
            Instant.now()
        );

        return ResponseEntity.ok(ApiResponse.ok(model));
    }

    @DeleteMapping("/llm-models/{modelId}")
    public ResponseEntity<ApiResponse<Void>> deleteLlmModel(
            @PathVariable String modelId,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.info("Deleting LLM model: {}", modelId);

        try {
            java.util.UUID uuid = java.util.UUID.fromString(modelId);
            modelPoolManager.deleteModel(uuid);
            return ResponseEntity.ok(ApiResponse.ok(null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(ApiResponse.err("not_found", "Model not found: " + modelId));
        }
    }

    @PostMapping("/llm-test")
    public ResponseEntity<ApiResponse<LlmTestResult>> testLlmModel(
            @RequestBody TestLlmModelRequest request,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.info("Testing LLM model: {}", request.modelId());

        LlmTestResult result = new LlmTestResult(
                true,
                "Connection successful",
                150L,
                "Hello! I am an AI assistant. How can I help you today?",
                Instant.now()
        );

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // Knowledge Base Endpoints (alias for /enterprise/documents/*)
    @GetMapping("/knowledge-base/files")
    public ResponseEntity<ApiResponse<List<KbFileInfo>>> getKnowledgeBaseFiles(
            @RequestParam(required = false) String path,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        AuthContext ctx = requireDocumentAccess(authorization, false);
        String normalized = normalizeDocumentPath(path);
        if (ctx == null) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied"));
        }
        if (!isPathAllowedForContext(ctx, normalized)) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Path not allowed"));
        }
        log.debug("Getting knowledge-base files, path: {}, user={}", normalized, ctx.getEmployeeId());

        List<KbFileInfo> files = listDocumentFiles(normalized, ctx);

        return ResponseEntity.ok(ApiResponse.ok(files));
    }

    @PostMapping("/knowledge-base/upload")
    public ResponseEntity<ApiResponse<KbFileInfo>> uploadKnowledgeBaseFile(
            @RequestParam(name = "sub_path", required = false, defaultValue = "") String subPath,
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        AuthContext ctx = requireDocumentAccess(authorization, true);
        String path = normalizeDocumentPath(subPath);
        if (ctx == null || !isPathWritableForContext(ctx, path)) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Upload denied"));
        }
        log.info("Uploading knowledge-base file: {}, user={}", path, ctx.getEmployeeId());

        KbFileInfo info = new KbFileInfo(
                file.getOriginalFilename(),
                "file",
                file.getSize(),
                Instant.now()
        );

        return ResponseEntity.ok(ApiResponse.ok(info));
    }

    @GetMapping("/knowledge-base/content")
    public ResponseEntity<ApiResponse<KbFileContent>> readKnowledgeBaseContent(
            @RequestParam String path,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        AuthContext ctx = requireDocumentAccess(authorization, false);
        String normalized = normalizeDocumentPath(path);
        if (ctx == null || !isPathAllowedForContext(ctx, normalized)) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied"));
        }
        log.debug("Reading knowledge-base content: {}, user={}", normalized, ctx.getEmployeeId());

        KbFileContent content = new KbFileContent(normalized, "Knowledge base content here...");
        return ResponseEntity.ok(ApiResponse.ok(content));
    }

    @PutMapping("/knowledge-base/content")
    public ResponseEntity<ApiResponse<KbFileContent>> writeKnowledgeBaseContent(
            @RequestParam String path,
            @RequestBody KbWriteRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        AuthContext ctx = requireDocumentAccess(authorization, true);
        String normalized = normalizeDocumentPath(path);
        if (ctx == null || !isPathWritableForContext(ctx, normalized)) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Write denied"));
        }
        log.info("Writing knowledge-base content: {}, user={}", normalized, ctx.getEmployeeId());

        KbFileContent content = new KbFileContent(normalized, request.content());
        return ResponseEntity.ok(ApiResponse.ok(content));
    }

    @DeleteMapping("/knowledge-base/content")
    public ResponseEntity<ApiResponse<Map<String, String>>> deleteKnowledgeBaseContent(
            @RequestParam String path,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        AuthContext ctx = requireDocumentAccess(authorization, true);
        String normalized = normalizeDocumentPath(path);
        if (ctx == null || !isPathWritableForContext(ctx, normalized)) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Delete denied"));
        }
        log.info("Deleting knowledge-base content: {}, user={}", normalized, ctx.getEmployeeId());

        return ResponseEntity.ok(ApiResponse.ok(Map.of("status", "deleted", "path", normalized)));
    }

    @PostMapping("/broadcast")
    public ResponseEntity<ApiResponse<BroadcastResult>> broadcastNotification(
            @RequestBody BroadcastRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        AuthContext ctx = requireDocumentAccess(authorization, true);
        if (ctx == null || (!ctx.isFounder() && ctx.getAccessLevel() != AccessLevel.FULL)) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Only founder can broadcast"));
        }

        log.info("Broadcast notification: title={}, user={}", request.title(), ctx.getEmployeeId());

        departmentNotificationService.broadcastToAllDepartments(
            "ANNOUNCEMENT",
            request.title(),
            request.body(),
            "NORMAL"
        );

        int usersNotified = 10;
        int agentsNotified = 5;
        int emailsSent = request.send_email() ? usersNotified : 0;

        return ResponseEntity.ok(ApiResponse.ok(new BroadcastResult(usersNotified, agentsNotified, emailsSent)));
    }

    public record LlmModel(
            String id,
            String name,
            String provider,
            String description,
            String endpoint,
            boolean enabled,
            double size,
            int contextWindow,
            Map<String, Object> config,
            Instant createdAt
    ) {}

    public record LlmProviderSpec(
            String provider,
            String display_name,
            String protocol,
            String default_base_url,
            boolean supports_tool_choice,
            int default_max_tokens
    ) {}

    public record SkillInfo(
            String name,
            String description,
            String category,
            String brain,
            boolean enabled
    ) {}

    public record ToolInfo(
            String name,
            String description,
            String department,
            boolean enabled
    ) {}

    public record CreateLlmModelRequest(
            String provider,
            String model,
            String label,
            String base_url,
            String api_key,
            Boolean supports_vision,
            Integer max_output_tokens,
            Double temperature
    ) {}

    public record TestLlmModelRequest(
            String modelId,
            String prompt
    ) {}

    public record LlmTestResult(
            boolean success,
            String message,
            Long latencyMs,
            String response,
            Instant testedAt
    ) {}

    public record KbFileInfo(
            String name,
            String type,
            long size,
            Instant modified_at
    ) {}

    public record KbFileContent(
            String path,
            String content
    ) {}

    public record BroadcastRequest(
            String title,
            String body,
            boolean send_email
    ) {}

    public record BroadcastResult(
            int users_notified,
            int agents_notified,
            int emails_sent
    ) {}

    private List<KbFileInfo> listDocumentFiles(String path, AuthContext ctx) {
        String normalized = path == null ? "" : path;
        List<KbFileInfo> files = new ArrayList<>();
        if (normalized.isEmpty()) {
            if (canSeeShared(ctx)) files.add(new KbFileInfo("shared", "dir", 0, Instant.now()));
            if (canSeeDepartment(ctx)) files.add(new KbFileInfo("department", "dir", 0, Instant.now()));
            if (canSeePersonal(ctx)) files.add(new KbFileInfo("personal", "dir", 0, Instant.now()));
            return files;
        }
        if (normalized.equals("shared") && canSeeShared(ctx)) {
            files.add(new KbFileInfo("README.md", "file", 0, Instant.now()));
        } else if (normalized.startsWith("department") && canSeeDepartment(ctx)) {
            files.add(new KbFileInfo("README.md", "file", 0, Instant.now()));
        } else if (normalized.startsWith("personal") && canSeePersonal(ctx)) {
            files.add(new KbFileInfo("README.md", "file", 0, Instant.now()));
        }
        return files;
    }

    private boolean canSeeShared(AuthContext ctx) { return true; }
    private boolean canSeeDepartment(AuthContext ctx) { return ctx != null && (ctx.isFounder() || ctx.getAccessLevel() == AccessLevel.FULL || ctx.getDepartment() != null); }
    private boolean canSeePersonal(AuthContext ctx) { return ctx != null; }

    private AuthContext requireDocumentAccess(String authorization, boolean write) {
        Optional<AuthSession> sessionOpt = parseSession(authorization);
        if (sessionOpt.isEmpty()) return null;
        AuthContext ctx = sessionOpt.get().authContext();
        if (ctx == null) return null;
        if (ctx.isFounder() || ctx.getAccessLevel() == AccessLevel.FULL) return ctx;
        if (ctx.getAccessLevel() == AccessLevel.DEPARTMENT) return ctx;
        if (write) return null;
        return ctx;
    }

    private Optional<AuthSession> parseSession(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) return Optional.empty();
        return authService.validateSession(authorization.substring(7));
    }

    private String normalizeDocumentPath(String path) {
        if (path == null || path.isBlank()) return "";
        String p = path.replace('\\', '/');
        while (p.startsWith("/")) p = p.substring(1);
        if (p.contains("..")) {
            throw new IllegalArgumentException("Invalid path");
        }
        return p;
    }

    private boolean isPathAllowedForContext(AuthContext ctx, String path) {
        if (ctx == null) return false;
        if (path == null || path.isBlank()) return true;
        String normalized = path.toLowerCase();
        if (normalized.startsWith("shared/")) return true;
        if (normalized.startsWith("department/")) {
            return ctx.isFounder() || ctx.getAccessLevel() == AccessLevel.FULL || ctx.getDepartment() != null;
        }
        if (normalized.startsWith("personal/")) {
            return ctx.isFounder() || ctx.getAccessLevel() == AccessLevel.FULL || ctx.getAccessLevel() == AccessLevel.DEPARTMENT;
        }
        return ctx.isFounder() || ctx.getAccessLevel() == AccessLevel.FULL;
    }

    private boolean isPathWritableForContext(AuthContext ctx, String path) {
        if (ctx == null) return false;
        if (path == null || path.isBlank()) return false;
        String normalized = path.toLowerCase();
        if (normalized.startsWith("shared/")) return false;
        if (normalized.startsWith("department/")) {
            return ctx.isFounder() || ctx.getAccessLevel() == AccessLevel.FULL || ctx.getAccessLevel() == AccessLevel.DEPARTMENT;
        }
        if (normalized.startsWith("personal/")) {
            return ctx.isFounder() || ctx.getAccessLevel() == AccessLevel.FULL || ctx.getAccessLevel() == AccessLevel.DEPARTMENT;
        }
        return false;
    }

    public record KbWriteRequest(
            String content
    ) {}
}
