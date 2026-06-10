package com.livingagent.core.model.pool;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * LLM Provider registry — single source of truth for supported LLM providers.
 * Mirrors Clawith's PROVIDER_REGISTRY for consistency.
 *
 * Renamed from ProviderRegistry to LlmProviderRegistry to avoid conflict
 * with the interface in com.livingagent.core.provider.ProviderRegistry.
 */
public class LlmProviderRegistry {

    public record ProviderEntry(
        String id,
        String displayName,
        String protocol,
        String defaultBaseUrl,
        boolean supportsToolChoice,
        int defaultMaxTokens
    ) {}

    private static final Map<String, ProviderEntry> REGISTRY = Map.ofEntries(
        Map.entry("anthropic", new ProviderEntry("anthropic", "Anthropic", "ANTHROPIC",
            "https://api.anthropic.com", false, 8192)),
        Map.entry("openai", new ProviderEntry("openai", "OpenAI", "OPENAI_COMPATIBLE",
            "https://api.openai.com/v1", true, 16384)),
        Map.entry("openai-response", new ProviderEntry("openai-response", "OpenAI Responses", "OPENAI_RESPONSES",
            "https://api.openai.com/v1", true, 16384)),
        Map.entry("azure", new ProviderEntry("azure", "Azure OpenAI", "OPENAI_COMPATIBLE",
            null, true, 16384)),
        Map.entry("deepseek", new ProviderEntry("deepseek", "DeepSeek", "OPENAI_COMPATIBLE",
            "https://api.deepseek.com/v1", true, 8192)),
        Map.entry("qwen", new ProviderEntry("qwen", "Qwen (DashScope)", "OPENAI_COMPATIBLE",
            "https://dashscope.aliyuncs.com/compatible-mode/v1", true, 8192)),
        Map.entry("minimax", new ProviderEntry("minimax", "MiniMax", "OPENAI_COMPATIBLE",
            "https://api.minimaxi.com/v1", true, 16384)),
        Map.entry("openrouter", new ProviderEntry("openrouter", "OpenRouter", "OPENAI_COMPATIBLE",
            "https://openrouter.ai/api/v1", true, 4096)),
        Map.entry("zhipu", new ProviderEntry("zhipu", "智谱 (Zhipu)", "OPENAI_COMPATIBLE",
            "https://open.bigmodel.cn/api/paas/v4", true, 8192)),
        Map.entry("baidu", new ProviderEntry("baidu", "百度 (千帆)", "OPENAI_COMPATIBLE",
            "https://qianfan.baidubce.com/v2", false, 4096)),
        Map.entry("gemini", new ProviderEntry("gemini", "Gemini", "GEMINI",
            "https://generativelanguage.googleapis.com/v1beta", true, 8192)),
        Map.entry("kimi", new ProviderEntry("kimi", "Kimi (月之暗面)", "OPENAI_COMPATIBLE",
            "https://api.moonshot.cn/v1", true, 8192)),
        Map.entry("vllm", new ProviderEntry("vllm", "vLLM", "OPENAI_COMPATIBLE",
            "http://localhost:8000/v1", true, 4096)),
        Map.entry("ollama", new ProviderEntry("ollama", "Ollama", "OPENAI_COMPATIBLE",
            "http://localhost:11434/v1", true, 4096)),
        Map.entry("sglang", new ProviderEntry("sglang", "SGLang", "OPENAI_COMPATIBLE",
            "http://localhost:30000/v1", true, 4096)),
        Map.entry("siliconflow", new ProviderEntry("siliconflow", "硅基流动 (SiliconFlow)", "OPENAI_COMPATIBLE",
            "https://api.siliconflow.cn/v1", true, 8192)),
        Map.entry("modelscope", new ProviderEntry("modelscope", "ModelScope 魔塔", "OPENAI_COMPATIBLE",
            "https://api-inference.modelscope.cn/v1", true, 8192)),
        Map.entry("custom", new ProviderEntry("custom", "自定义", "OPENAI_COMPATIBLE",
            null, true, 4096))
    );

    public static ProviderEntry getById(String id) {
        return REGISTRY.get(id);
    }

    public static String getDefaultBaseUrl(String id) {
        ProviderEntry entry = REGISTRY.get(id);
        return entry != null ? entry.defaultBaseUrl() : null;
    }

    public static String getDefaultProtocol(String id) {
        ProviderEntry entry = REGISTRY.get(id);
        return entry != null ? entry.protocol() : "OPENAI_COMPATIBLE";
    }

    public static boolean supportsToolChoice(String id) {
        ProviderEntry entry = REGISTRY.get(id);
        return entry != null && entry.supportsToolChoice();
    }

    public static List<ProviderEntry> getAll() {
        return List.copyOf(REGISTRY.values());
    }
}
