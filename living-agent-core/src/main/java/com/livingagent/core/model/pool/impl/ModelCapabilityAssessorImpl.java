package com.livingagent.core.model.pool.impl;

import com.livingagent.core.model.pool.LlmModel;
import com.livingagent.core.model.pool.ModelCapabilityAssessor;
import com.livingagent.core.model.pool.ModelHealthRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 模型能力评定服务实现
 * 
 * 评定策略：
 * 1. 已知模型：使用预定义的规则和元数据直接评定
 * 2. 未知模型：根据模型名称、参数量、供应商等信息推断能力
 * 3. Ollama 本地模型：根据模型名称模式和能力标签自动评定
 */
@Service
public class ModelCapabilityAssessorImpl implements ModelCapabilityAssessor {

    private static final Logger log = LoggerFactory.getLogger(ModelCapabilityAssessorImpl.class);

    /** 可选的模型健康注册表，用于运行时成功率加权 */
    private volatile ModelHealthRegistry modelHealthRegistry;

    /**
     * 设置模型健康注册表（可选注入）
     */
    public void setModelHealthRegistry(ModelHealthRegistry registry) {
        this.modelHealthRegistry = registry;
    }

    /**
     * 能力标签常量
     */
    public static final String CAP_CODING = "coding";
    public static final String CAP_REASONING = "reasoning";
    public static final String CAP_FRONTEND = "frontend";
    public static final String CAP_CREATIVE = "creative";
    public static final String CAP_CHAT = "chat";
    public static final String CAP_ANALYSIS = "analysis";
    public static final String CAP_DOCUMENT = "document";
    public static final String CAP_FAST = "fast";
    public static final String CAP_VISION = "vision";

    /**
     * 任务类型到能力标签的映射
     */
    private static final Map<String, List<String>> TASK_TO_CAPABILITY = Map.of(
        "web_development", List.of(CAP_FRONTEND, CAP_CODING, CAP_CREATIVE),
        "web_prototype", List.of(CAP_FRONTEND, CAP_CODING, CAP_CREATIVE),
        "software_development", List.of(CAP_CODING, CAP_REASONING),
        "code_review", List.of(CAP_CODING, CAP_REASONING),
        "data_analysis", List.of(CAP_ANALYSIS, CAP_REASONING),
        "document_generation", List.of(CAP_DOCUMENT, CAP_CREATIVE),
        "chat", List.of(CAP_CHAT, CAP_REASONING),
        "review", List.of(CAP_REASONING, CAP_ANALYSIS)
    );

    /**
     * 已知模型能力数据库 - 根据模型名称精确匹配
     */
    private static final Map<String, ModelProfile> KNOWN_MODEL_PROFILES = new HashMap<>();

    static {
        // === OpenAI 模型 ===
        KNOWN_MODEL_PROFILES.put("gpt-4o", new ModelProfile("coding,reasoning,frontend,creative,chat,analysis", 95, "large"));
        KNOWN_MODEL_PROFILES.put("gpt-4o-mini", new ModelProfile("coding,chat,fast,creative", 80, "medium"));
        KNOWN_MODEL_PROFILES.put("gpt-4-turbo", new ModelProfile("coding,reasoning,chat,analysis", 90, "large"));
        KNOWN_MODEL_PROFILES.put("gpt-3.5-turbo", new ModelProfile("chat,fast,creative", 65, "small"));

        // === Anthropic 模型 ===
        KNOWN_MODEL_PROFILES.put("claude-sonnet-4-5", new ModelProfile("coding,reasoning,analysis,document", 95, "large"));
        KNOWN_MODEL_PROFILES.put("claude-opus-4-5", new ModelProfile("coding,reasoning,analysis,document", 98, "large"));
        KNOWN_MODEL_PROFILES.put("claude-haiku-3-5", new ModelProfile("chat,fast,document", 70, "small"));

        // === DeepSeek 模型 ===
        KNOWN_MODEL_PROFILES.put("deepseek-v3", new ModelProfile("coding,reasoning,analysis", 90, "large"));
        KNOWN_MODEL_PROFILES.put("deepseek-coder-7b", new ModelProfile("coding,fast", 70, "small"));
        KNOWN_MODEL_PROFILES.put("deepseek-coder-33b", new ModelProfile("coding,reasoning", 85, "medium"));

        // === Qwen/通义千问 模型 ===
        KNOWN_MODEL_PROFILES.put("qwen3.5-27b", new ModelProfile("coding,reasoning,chat,analysis", 88, "medium"));
        KNOWN_MODEL_PROFILES.put("qwen3.5-14b", new ModelProfile("coding,chat,fast", 78, "medium"));
        KNOWN_MODEL_PROFILES.put("qwen3-32b", new ModelProfile("coding,reasoning,chat", 85, "medium"));
        KNOWN_MODEL_PROFILES.put("qwen2.5-72b", new ModelProfile("coding,reasoning,analysis,document", 92, "large"));
        KNOWN_MODEL_PROFILES.put("qwen2.5-coder-32b", new ModelProfile("coding,reasoning,fast", 85, "medium"));
        KNOWN_MODEL_PROFILES.put("qwen2.5-14b", new ModelProfile("coding,chat,fast", 75, "medium"));
        KNOWN_MODEL_PROFILES.put("qwen2.5-7b", new ModelProfile("coding,chat,fast", 65, "small"));

        // === Ollama 常见模型 ===
        KNOWN_MODEL_PROFILES.put("qwen3.5:27b", new ModelProfile("coding,reasoning,chat,analysis", 88, "medium"));
        KNOWN_MODEL_PROFILES.put("qwen3.5:14b", new ModelProfile("coding,chat,fast", 78, "medium"));
        KNOWN_MODEL_PROFILES.put("qwen3.5:9b", new ModelProfile("chat,fast,coding", 60, "small"));
        KNOWN_MODEL_PROFILES.put("qwen3.5:7b", new ModelProfile("chat,fast,coding", 55, "small"));
        KNOWN_MODEL_PROFILES.put("qwen2.5:72b", new ModelProfile("coding,reasoning,analysis", 92, "large"));
        KNOWN_MODEL_PROFILES.put("qwen2.5:32b", new ModelProfile("coding,reasoning,chat", 85, "medium"));
        KNOWN_MODEL_PROFILES.put("qwen2.5:14b", new ModelProfile("coding,chat,fast", 75, "medium"));
        KNOWN_MODEL_PROFILES.put("qwen2.5:7b", new ModelProfile("chat,fast", 55, "small"));
        KNOWN_MODEL_PROFILES.put("llama3.3:70b", new ModelProfile("coding,reasoning,chat,analysis", 90, "large"));
        KNOWN_MODEL_PROFILES.put("llama3.2:3b", new ModelProfile("chat,fast", 45, "small"));
        KNOWN_MODEL_PROFILES.put("llama3.1:8b", new ModelProfile("chat,fast", 55, "small"));
        KNOWN_MODEL_PROFILES.put("codellama:34b", new ModelProfile("coding,reasoning", 80, "medium"));
        KNOWN_MODEL_PROFILES.put("codellama:13b", new ModelProfile("coding,fast", 65, "small"));
        KNOWN_MODEL_PROFILES.put("mistral:7b", new ModelProfile("chat,fast", 55, "small"));
        KNOWN_MODEL_PROFILES.put("mixtral:8x7b", new ModelProfile("coding,reasoning,chat", 82, "medium"));
        KNOWN_MODEL_PROFILES.put("phi3:14b", new ModelProfile("chat,fast,coding", 65, "small"));
        KNOWN_MODEL_PROFILES.put("gemma2:27b", new ModelProfile("coding,reasoning,chat", 80, "medium"));

        // === ModelScope 模型 ===
        KNOWN_MODEL_PROFILES.put("qwen/qwen3-235b-a22b-instruct-2507", new ModelProfile("coding,reasoning,analysis,document", 97, "large"));
        KNOWN_MODEL_PROFILES.put("qwen/qwen3-32b", new ModelProfile("coding,reasoning,chat", 85, "medium"));
        KNOWN_MODEL_PROFILES.put("qwen/qwen2.5-72b-instruct", new ModelProfile("coding,reasoning,analysis,document", 92, "large"));
        KNOWN_MODEL_PROFILES.put("qwen/qwen2.5-coder-32b-instruct", new ModelProfile("coding,reasoning,fast", 85, "medium"));

        // === 智谱 模型 ===
        KNOWN_MODEL_PROFILES.put("glm-4", new ModelProfile("coding,reasoning,chat,analysis", 85, "large"));
        KNOWN_MODEL_PROFILES.put("glm-4-flash", new ModelProfile("chat,fast,creative", 60, "small"));

        // === Kimi/Moonshot 模型 ===
        KNOWN_MODEL_PROFILES.put("moonshot-v1-8k", new ModelProfile("chat,analysis,document", 70, "medium"));
        KNOWN_MODEL_PROFILES.put("moonshot-v1-32k", new ModelProfile("chat,analysis,document", 75, "medium"));

        // === 百度千帆 模型 ===
        KNOWN_MODEL_PROFILES.put("ernie-4.0-8k", new ModelProfile("coding,reasoning,chat,analysis", 82, "medium"));
        KNOWN_MODEL_PROFILES.put("ernie-3.5-8k", new ModelProfile("chat,fast,document", 65, "small"));
    }

    /**
     * 模型参数大小到基础评分的映射
     */
    private static final Map<String, Integer> PARAM_SIZE_BASE_SCORE = Map.of(
        "small", 50,    // 7B 以下
        "medium", 70,   // 7B-34B
        "large", 90     // 34B 以上
    );

    /**
     * 模型名称中的参数量正则匹配
     */
    private static final Pattern PARAM_PATTERN = Pattern.compile("(\\d+)(?:b|B)");

    /**
     * 员工角色到所需能力的映射
     */
    private static final Map<String, List<String>> ROLE_TO_CAPABILITY;
    static {
        Map<String, List<String>> map = new HashMap<>();
        map.put("frontend_engineer", List.of(CAP_FRONTEND, CAP_CODING, CAP_CREATIVE));
        map.put("backend_engineer", List.of(CAP_CODING, CAP_REASONING));
        map.put("code_reviewer", List.of(CAP_CODING, CAP_REASONING, CAP_ANALYSIS));
        map.put("data_analyst", List.of(CAP_ANALYSIS, CAP_REASONING));
        map.put("architect", List.of(CAP_CODING, CAP_REASONING, CAP_ANALYSIS));
        map.put("tech_lead", List.of(CAP_CODING, CAP_REASONING, CAP_ANALYSIS, CAP_DOCUMENT));
        map.put("document_writer", List.of(CAP_DOCUMENT, CAP_CREATIVE, CAP_CHAT));
        map.put("qa_tester", List.of(CAP_CODING, CAP_ANALYSIS, CAP_REASONING));
        map.put("devops_engineer", List.of(CAP_CODING, CAP_ANALYSIS));
        map.put("ui_ux_designer", List.of(CAP_CREATIVE, CAP_FRONTEND));
        map.put("chat_assistant", List.of(CAP_CHAT, CAP_CREATIVE));
        map.put("general_worker", List.of(CAP_CHAT, CAP_CODING, CAP_ANALYSIS));
        ROLE_TO_CAPABILITY = Map.copyOf(map);
    }

    @Override
    public LlmModel assessModel(LlmModel model) {
        String key = normalizeModelKey(model);
        ModelProfile profile = findModelProfile(key, model);

        if (profile != null) {
            model.setCapabilityTags(profile.capabilityTags);
            model.setPerformanceScore(profile.performanceScore);
            model.setParameterSize(profile.parameterSize);
        } else {
            // 未知模型，根据名称和供应商推断
            profile = inferModelProfile(model);
            model.setCapabilityTags(profile.capabilityTags);
            model.setPerformanceScore(profile.performanceScore);
            model.setParameterSize(profile.parameterSize);
        }

        log.info("Assessed model: {} ({}), tags={}, score={}, size={}",
            model.getModelName(), model.getProviderId(),
            model.getCapabilityTags(), model.getPerformanceScore(), model.getParameterSize());

        return model;
    }

    @Override
    public void assessModels(List<LlmModel> models) {
        log.info("Assessing {} models...", models.size());
        int assessed = 0;
        for (LlmModel model : models) {
            if (model.isEnabled()) {
                assessModel(model);
                assessed++;
            }
        }
        log.info("Assessed {} enabled models out of {}", assessed, models.size());
    }

    @Override
    public LlmModel selectBestModelForTask(String taskType, String employeeRole, List<LlmModel> availableModels) {
        if (availableModels == null || availableModels.isEmpty()) {
            return null;
        }

        // 1. 确定所需能力标签
        List<String> requiredCapabilities = getRequiredCapabilities(taskType, employeeRole);

        // 2. 过滤并评分
        return availableModels.stream()
            .filter(LlmModel::isEnabled)
            .map(model -> new ScoredModel(model, calculateModelScore(model, requiredCapabilities)))
            .filter(sm -> sm.score > 0)
            .max(Comparator.comparingInt(sm -> sm.score))
            .map(sm -> sm.model)
            .orElse(availableModels.stream().filter(LlmModel::isEnabled).findFirst().orElse(null));
    }

    @Override
    public Map<String, List<String>> getCapabilityTaskMapping() {
        return TASK_TO_CAPABILITY;
    }

    @Override
    public void reassessAllEnabledModels() {
        log.info("Reassessing all enabled models...");
        // 实际应用中会从数据库加载所有启用模型并重新评定
        // 这里由 ModelPoolManager 调用
    }

    // ==================== 内部方法 ====================

    /**
     * 标准化模型键（用于查找已知模型配置）
     */
    private String normalizeModelKey(LlmModel model) {
        String name = model.getModelName().toLowerCase().trim();
        // 移除 provider 前缀（如 qwen/、qwen3.5: 等）
        name = name.replaceAll("^[a-z0-9_-]+[/::]", "");
        return name;
    }

    /**
     * 查找已知模型配置
     */
    private ModelProfile findModelProfile(String normalizedKey, LlmModel model) {
        // 精确匹配
        if (KNOWN_MODEL_PROFILES.containsKey(normalizedKey)) {
            return KNOWN_MODEL_PROFILES.get(normalizedKey);
        }

        // 模糊匹配（模型名称包含）
        String fullName = model.getModelName().toLowerCase();
        for (Map.Entry<String, ModelProfile> entry : KNOWN_MODEL_PROFILES.entrySet()) {
            if (fullName.contains(entry.getKey()) || entry.getKey().contains(normalizedKey)) {
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * 推断未知模型的能力
     */
    private ModelProfile inferModelProfile(LlmModel model) {
        String name = model.getModelName().toLowerCase();
        Set<String> caps = new LinkedHashSet<>();
        int baseScore = 50;
        String paramSize = "small";

        // 1. 从模型名称推断参数量
        Matcher matcher = PARAM_PATTERN.matcher(name);
        if (matcher.find()) {
            int billions = Integer.parseInt(matcher.group(1));
            if (billions >= 34) {
                paramSize = "large";
                baseScore = 90;
            } else if (billions >= 7) {
                paramSize = "medium";
                baseScore = 70;
            } else {
                paramSize = "small";
                baseScore = 50;
            }
        }

        // 2. 从模型名称关键词推断能力
        if (name.contains("coder") || name.contains("code")) {
            caps.add(CAP_CODING);
        }
        if (name.contains("instruct") || name.contains("chat")) {
            caps.add(CAP_CHAT);
        }
        if (name.contains("vision") || name.contains("vl")) {
            caps.add(CAP_VISION);
        }
        if (name.contains("reason") || name.contains("think")) {
            caps.add(CAP_REASONING);
        }

        // 3. 从供应商推断
        String provider = model.getProviderId().toLowerCase();
        switch (provider) {
            case "openai":
                caps.add(CAP_CODING);
                caps.add(CAP_REASONING);
                caps.add(CAP_CHAT);
                break;
            case "anthropic":
                caps.add(CAP_CODING);
                caps.add(CAP_REASONING);
                caps.add(CAP_ANALYSIS);
                break;
            case "deepseek":
                caps.add(CAP_CODING);
                caps.add(CAP_REASONING);
                break;
            case "qwen":
            case "modelscope":
                caps.add(CAP_CODING);
                caps.add(CAP_CHAT);
                caps.add(CAP_ANALYSIS);
                break;
            case "ollama":
                // Ollama 模型根据参数量判断
                if ("large".equals(paramSize)) {
                    caps.add(CAP_CODING);
                    caps.add(CAP_REASONING);
                    caps.add(CAP_CHAT);
                } else if ("medium".equals(paramSize)) {
                    caps.add(CAP_CODING);
                    caps.add(CAP_CHAT);
                } else {
                    caps.add(CAP_CHAT);
                    caps.add(CAP_FAST);
                }
                break;
        }

        // 4. 默认能力
        if (caps.isEmpty()) {
            caps.add(CAP_CHAT);
        }

        return new ModelProfile(String.join(",", caps), baseScore, paramSize);
    }

    /**
     * 获取任务类型和员工角色所需的能力标签
     */
    private List<String> getRequiredCapabilities(String taskType, String employeeRole) {
        Set<String> required = new LinkedHashSet<>();

        // 从任务类型获取
        if (taskType != null) {
            String normalizedType = taskType.toLowerCase();
            for (Map.Entry<String, List<String>> entry : TASK_TO_CAPABILITY.entrySet()) {
                if (normalizedType.contains(entry.getKey()) || entry.getKey().contains(normalizedType)) {
                    required.addAll(entry.getValue());
                }
            }
        }

        // 从员工角色获取
        if (employeeRole != null) {
            String normalizedRole = employeeRole.toLowerCase();
            for (Map.Entry<String, List<String>> entry : ROLE_TO_CAPABILITY.entrySet()) {
                if (normalizedRole.contains(entry.getKey()) || entry.getKey().contains(normalizedRole)) {
                    required.addAll(entry.getValue());
                }
            }
        }

        return new ArrayList<>(required);
    }

    /**
     * 计算模型与所需能力的匹配分数
     * 评分权重：匹配度 50% + 静态性能分 25% + 运行时成功率 25%
     */
    private int calculateModelScore(LlmModel model, List<String> requiredCapabilities) {
        if (model.getCapabilityTags() == null || requiredCapabilities.isEmpty()) {
            int base = model.getPerformanceScore() != null ? model.getPerformanceScore() / 2 : 25;
            return base + getHealthBonus(model);
        }

        Set<String> modelCaps = new HashSet<>(Arrays.asList(model.getCapabilityTags().split(",")));
        int matched = 0;
        int total = requiredCapabilities.size();

        for (String required : requiredCapabilities) {
            if (modelCaps.contains(required)) {
                matched++;
            }
        }

        // 匹配度占 50%，静态性能分占 25%，运行时成功率占 25%
        int matchScore = (int) ((double) matched / total * 100);
        int perfScore = model.getPerformanceScore() != null ? model.getPerformanceScore() : 50;
        int healthBonus = getHealthBonus(model);

        return (int) (matchScore * 0.5 + perfScore * 0.25 + healthBonus);
    }

    /**
     * 计算模型运行时健康度加分（0~25）
     * 高成功率模型额外加分，低成功率或冷却中模型扣分
     */
    private int getHealthBonus(LlmModel model) {
        if (modelHealthRegistry == null || model.getId() == null) return 12; // 无数据给中间分
        ModelHealthRegistry.ModelHealthRecord health = modelHealthRegistry.getHealth(model.getId().toString());
        if (health.totalCalls() <= 0) return 15; // 无调用记录，略高于中间
        double successRate = health.totalSuccesses() * 1.0 / health.totalCalls();
        if (!health.isAvailable()) return 0; // 冷却中，不给分
        return (int) Math.round(successRate * 25); // 成功率100%=25分, 80%=20分, 50%=12.5分
    }

    /**
     * 模型配置内部类
     */
    private record ModelProfile(String capabilityTags, int performanceScore, String parameterSize) {}

    /**
     * 评分模型内部类
     */
    private record ScoredModel(LlmModel model, int score) {}
}
