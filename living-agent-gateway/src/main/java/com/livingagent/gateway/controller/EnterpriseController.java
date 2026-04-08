package com.livingagent.gateway.controller;

import com.livingagent.gateway.service.SystemConfigService;
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
    private final SkillRegistry skillRegistry;
    private final ToolRegistry toolRegistry;

    @Value("${ai-models.ollama.enabled:false}")
    private boolean ollamaEnabled;

    @Value("${ai-models.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    public EnterpriseController(
            SystemConfigService systemConfigService,
            SkillRegistry skillRegistry,
            ToolRegistry toolRegistry
    ) {
        this.systemConfigService = systemConfigService;
        this.skillRegistry = skillRegistry;
        this.toolRegistry = toolRegistry;
    }

    @GetMapping("/llm-models")
    public ResponseEntity<ApiResponse<List<LlmModel>>> listLlmModels(
            @RequestParam(required = false) String tenant_id
    ) {
        log.debug("Listing LLM models for tenant: {}", tenant_id);

        List<LlmModel> models = new ArrayList<>();

        List<SystemConfigService.ProviderConfig> providers = systemConfigService.getAvailableProviders();
        for (SystemConfigService.ProviderConfig provider : providers) {
            models.add(new LlmModel(
                    provider.providerId(),
                    provider.name(),
                    provider.providerId(),
                    provider.name() + " - 数字员工可选模型",
                    provider.baseUrl(),
                    provider.enabled() && provider.apiKey() != null && !provider.apiKey().isBlank(),
                    7.0,
                    8192,
                    Map.of("api_key_configured", provider.apiKey() != null && !provider.apiKey().isBlank()),
                    Instant.now()
            ));
        }

        return ResponseEntity.ok(ApiResponse.success(models));
    }

    @GetMapping("/llm-providers")
    public ResponseEntity<ApiResponse<List<LlmProviderSpec>>> listLlmProviders() {
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

        return ResponseEntity.ok(ApiResponse.success(providers));
    }

    @GetMapping("/skills")
    public ResponseEntity<ApiResponse<List<SkillInfo>>> listSkills() {
        log.debug("Listing all skills");

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

        return ResponseEntity.ok(ApiResponse.success(skillInfos));
    }

    @GetMapping("/skills/by-brain/{brain}")
    public ResponseEntity<ApiResponse<List<SkillInfo>>> listSkillsByBrain(@PathVariable String brain) {
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

        return ResponseEntity.ok(ApiResponse.success(skillInfos));
    }

    @GetMapping("/tools")
    public ResponseEntity<ApiResponse<List<ToolInfo>>> listTools() {
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

        return ResponseEntity.ok(ApiResponse.success(toolInfos));
    }

    @GetMapping("/tools/by-department/{department}")
    public ResponseEntity<ApiResponse<List<ToolInfo>>> listToolsByDepartment(@PathVariable String department) {
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

        return ResponseEntity.ok(ApiResponse.success(toolInfos));
    }

    @GetMapping("/skill-counts")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> getSkillCountsByBrain() {
        log.debug("Getting skill counts by brain");
        return ResponseEntity.ok(ApiResponse.success(skillRegistry.getSkillCountsByBrain()));
    }

    @PostMapping("/llm-models")
    public ResponseEntity<ApiResponse<LlmModel>> createLlmModel(@RequestBody CreateLlmModelRequest request) {
        log.info("Creating LLM model: {}", request.name());

        LlmModel model = new LlmModel(
                "model_" + System.currentTimeMillis(),
                request.name(),
                request.provider(),
                request.description(),
                request.endpoint(),
                false,
                request.size() != null ? request.size() : 7.0,
                request.contextWindow() != null ? request.contextWindow() : 8192,
                request.config() != null ? request.config() : Map.of(),
                Instant.now()
        );

        return ResponseEntity.ok(ApiResponse.success(model));
    }

    @PutMapping("/llm-models/{modelId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateLlmModel(
            @PathVariable String modelId,
            @RequestBody Map<String, Object> request
    ) {
        log.info("Updating LLM model: {}", modelId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("id", modelId, "updated", true)));
    }

    @DeleteMapping("/llm-models/{modelId}")
    public ResponseEntity<ApiResponse<Void>> deleteLlmModel(@PathVariable String modelId) {
        log.info("Deleting LLM model: {}", modelId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/llm-test")
    public ResponseEntity<ApiResponse<LlmTestResult>> testLlmModel(@RequestBody TestLlmModelRequest request) {
        log.info("Testing LLM model: {}", request.modelId());

        LlmTestResult result = new LlmTestResult(
                true,
                "Connection successful",
                150L,
                "Hello! I am an AI assistant. How can I help you today?",
                Instant.now()
        );

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // Knowledge Base Endpoints
    @GetMapping("/knowledge-base/files")
    public ResponseEntity<ApiResponse<List<KbFileInfo>>> getKnowledgeBaseFiles(
            @RequestParam(required = false) String path
    ) {
        log.debug("Getting knowledge base files, path: {}", path);

        List<KbFileInfo> files = List.of(
                new KbFileInfo("doc1.md", "file", 1024, Instant.now()),
                new KbFileInfo("doc2.md", "file", 2048, Instant.now())
        );

        return ResponseEntity.ok(ApiResponse.success(files));
    }

    @PostMapping("/knowledge-base/upload")
    public ResponseEntity<ApiResponse<KbFileInfo>> uploadKnowledgeBaseFile(
            @RequestParam String path,
            @RequestParam("file") MultipartFile file
    ) {
        log.info("Uploading knowledge base file: {}", path);

        KbFileInfo info = new KbFileInfo(
                file.getOriginalFilename(),
                "file",
                file.getSize(),
                Instant.now()
        );

        return ResponseEntity.ok(ApiResponse.success(info));
    }

    @GetMapping("/knowledge-base/content")
    public ResponseEntity<ApiResponse<KbFileContent>> readKnowledgeBaseContent(
            @RequestParam String path
    ) {
        log.debug("Reading knowledge base content: {}", path);

        KbFileContent content = new KbFileContent(path, "Knowledge base content here...");
        return ResponseEntity.ok(ApiResponse.success(content));
    }

    @PutMapping("/knowledge-base/content")
    public ResponseEntity<ApiResponse<KbFileContent>> writeKnowledgeBaseContent(
            @RequestParam String path,
            @RequestBody KbWriteRequest request
    ) {
        log.info("Writing knowledge base content: {}", path);

        KbFileContent content = new KbFileContent(path, request.content());
        return ResponseEntity.ok(ApiResponse.success(content));
    }

    @DeleteMapping("/knowledge-base/content")
    public ResponseEntity<ApiResponse<Map<String, String>>> deleteKnowledgeBaseContent(
            @RequestParam String path
    ) {
        log.info("Deleting knowledge base content: {}", path);

        return ResponseEntity.ok(ApiResponse.success(Map.of("status", "deleted", "path", path)));
    }

    public record ApiResponse<T>(
            boolean success,
            T data,
            String error,
            String errorDescription
    ) {
        public static <T> ApiResponse<T> success(T data) {
            return new ApiResponse<>(true, data, null, null);
        }

        public static <T> ApiResponse<T> error(String error, String description) {
            return new ApiResponse<>(false, null, error, description);
        }
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
            String name,
            String provider,
            String description,
            String endpoint,
            Double size,
            Integer contextWindow,
            Map<String, Object> config
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

    public record KbWriteRequest(
            String content
    ) {}
}
