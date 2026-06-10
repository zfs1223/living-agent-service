package com.livingagent.core.model.pool.impl;

import com.livingagent.core.model.pool.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ModelPerformanceAssessorImpl implements ModelPerformanceAssessor {

    private static final Logger log = LoggerFactory.getLogger(ModelPerformanceAssessorImpl.class);

    private static final String DEFAULT_TEST_PROMPT = "你好";

    /**
     * Embedding 模型常见关键词（用于在不知道 inputTypes 时识别）
     * 与 ModelPoolManager.EMBEDDING_KEYWORDS 保持同步
     */
    private static final java.util.Set<String> EMBEDDING_KEYWORDS = java.util.Set.of(
        "bge", "e5", "embedding", "text-embedding", "ada-002", "ada",
        "gte", "jina", "m3e", "voyage", "cohere-embed", "retrieval",
        "sentence", "nomic-embed", "mxbai-embed", "snowflake-arctic-embed",
        "embed", "sbert", "minilm", "stella"
    );

    private final ProviderConfigRepository providerRepo;
    private final LlmModelRepository modelRepo;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger total = new AtomicInteger(0);
    private final AtomicInteger completed = new AtomicInteger(0);
    private final AtomicInteger failed = new AtomicInteger(0);

    public ModelPerformanceAssessorImpl(ProviderConfigRepository providerRepo, LlmModelRepository modelRepo) {
        this.providerRepo = providerRepo;
        this.modelRepo = modelRepo;
    }

    @Override
    public AssessmentResult assessModel(LlmModel model, String testPrompt) {
        String prompt = (testPrompt != null && !testPrompt.isBlank()) ? testPrompt : DEFAULT_TEST_PROMPT;
        ProviderConfig provider = providerRepo.findById(model.getProviderId()).orElse(null);

        if (provider == null || !provider.isEnabled()) {
            log.warn("Provider not found or disabled for model: {} ({})", model.getModelName(), model.getProviderId());
            return new AssessmentResult(
                model.getId().toString(), model.getProviderId(), model.getModelName(),
                false, 0, null, null, 0, "Provider not found or disabled"
            );
        }

        if ("openrouter".equalsIgnoreCase(model.getProviderId()) && !model.getModelName().endsWith(":free")) {
            log.debug("Skipping non-free OpenRouter model: {}", model.getModelName());
            model.setEnabled(false);
            modelRepo.save(model);
            return new AssessmentResult(
                model.getId().toString(), model.getProviderId(), model.getModelName(),
                false, 0, null, null, 0, "OpenRouter non-free model (requires API key)"
            );
        }

        // 关键修复：embedding 模型走 /embeddings 端点，而不是 /chat/completions
        // 否则连续 3 次失败 → ModelHealthRegistry 冷却 → 知识检索/语义搜索不可用
        boolean isEmbedding = isEmbeddingModel(model);
        long start = System.currentTimeMillis();
        try {
            LlmClient client = LlmClientFactory.create(provider);
            String response;
            if (isEmbedding) {
                log.info("[assessModel] Detected embedding model: {} ({}), probing /embeddings",
                    model.getModelName(), model.getProviderId());
                response = client.embed("hello world, test embedding", model.getModelName());
            } else {
                response = client.complete(prompt, model.getModelName(), 16);
            }
            long responseTime = System.currentTimeMillis() - start;

            boolean available = response != null && !response.isBlank();
            String tags = inferCapabilityTags(model);
            int baseScore = model.getPerformanceScore() != null && model.getPerformanceScore() > 0
                ? model.getPerformanceScore() : 50;
            int score = available ? Math.max(baseScore, adjustScoreByLatency(baseScore, responseTime)) : 0;

            model.setCapabilityTags(tags);
            model.setPerformanceScore(score);
            model.setParameterSize(inferParameterSize(model.getModelName()));
            // embedding 模型成功时自动写回 inputTypes，便于后续自动路由
            if (isEmbedding && available) {
                model.setInputTypes("embedding");
            }
            modelRepo.save(model);

            log.info("Model assessed: provider={}, model={}, type={}, available={}, time={}ms, tags={}",
                model.getProviderId(), model.getModelName(), isEmbedding ? "embedding" : "chat",
                available, responseTime, tags);

            return new AssessmentResult(
                model.getId().toString(), model.getProviderId(), model.getModelName(),
                available, responseTime, truncate(response, 100), tags, score, null
            );
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - start;
            log.error("Model assessment failed: provider={}, model={}, type={}, error={}",
                model.getProviderId(), model.getModelName(), isEmbedding ? "embedding" : "chat",
                e.getMessage());

            // 关键修复：embedding 探针失败时，不要直接 setEnabled(false) 把模型禁掉
            // 否则知识库/语义搜索整体挂掉，且 ModelHealthRegistry 冷却 5min
            // 改为：embedding 失败 → 仅清空 performanceScore + 标记 inputTypes 不准确，不禁用
            if (isEmbedding) {
                log.warn("Embedding model assessment failed, NOT auto-disabling: provider={}, model={}, error={}. " +
                        "知识检索/语义搜索将尝试其它 embedding 模型或回退到本地降级方案。",
                    model.getProviderId(), model.getModelName(), e.getMessage());
                model.setPerformanceScore(0);
                // 不调用 setEnabled(false)，也不计入 ModelHealthRegistry
                modelRepo.save(model);
                return new AssessmentResult(
                    model.getId().toString(), model.getProviderId(), model.getModelName(),
                    false, responseTime, null, null, 0,
                    "Embedding probe failed: " + e.getMessage()
                );
            }

            model.setPerformanceScore(0);
            model.setEnabled(false);
            modelRepo.save(model);
            log.warn("Chat model auto-disabled due to assessment failure: provider={}, model={}",
                model.getProviderId(), model.getModelName());

            return new AssessmentResult(
                model.getId().toString(), model.getProviderId(), model.getModelName(),
                false, responseTime, null, null, 0, e.getMessage()
            );
        }
    }

    /**
     * 判断模型是否为 embedding 模型
     * 优先级：inputTypes 字段 > 模型名关键词
     */
    private boolean isEmbeddingModel(LlmModel model) {
        if ("embedding".equalsIgnoreCase(model.getInputTypes())) {
            return true;
        }
        String name = model.getModelName();
        if (name == null) return false;
        String lower = name.toLowerCase();
        for (String kw : EMBEDDING_KEYWORDS) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }

    @Override
    public List<AssessmentResult> assessAllEnabledModels() {
        if (running.compareAndSet(false, true)) {
            total.set(0);
            completed.set(0);
            failed.set(0);

            List<LlmModel> models = modelRepo.findByEnabledTrue();
            total.set(models.size());

            log.info("Starting assessment of {} enabled models...", models.size());

            List<AssessmentResult> results = new ArrayList<>();
            for (LlmModel model : models) {
                if (!running.get()) {
                    log.info("Assessment stopped by user");
                    break;
                }

                try {
                    AssessmentResult result = assessModel(model, null);
                    results.add(result);
                    if (result.available()) {
                        completed.incrementAndGet();
                    } else {
                        failed.incrementAndGet();
                    }
                } catch (Exception e) {
                    failed.incrementAndGet();
                    log.error("Failed to assess model {}: {}", model.getModelName(), e.getMessage());
                }
            }

            running.set(false);
            log.info("Assessment completed: total={}, success={}, failed={}", total.get(), completed.get(), failed.get());

            return results;
        } else {
            throw new IllegalStateException("Assessment already running");
        }
    }

    @Override
    public List<AssessmentResult> assessProviderModels(String providerId) {
        List<LlmModel> models = modelRepo.findByProviderIdAndEnabledTrue(providerId);
        total.set(models.size());
        completed.set(0);
        failed.set(0);
        running.set(true);

        log.info("Starting assessment of {} models for provider {}", models.size(), providerId);

        List<AssessmentResult> results = new ArrayList<>();
        for (LlmModel model : models) {
            if (!running.get()) break;

            try {
                AssessmentResult result = assessModel(model, null);
                results.add(result);
                if (result.available()) {
                    completed.incrementAndGet();
                } else {
                    failed.incrementAndGet();
                }
            } catch (Exception e) {
                failed.incrementAndGet();
            }
        }

        running.set(false);
        return results;
    }

    @Override
    public AssessmentProgress getProgress() {
        return new AssessmentProgress(total.get(), completed.get(), failed.get(), running.get());
    }

    @Override
    public void stopAssessment() {
        running.set(false);
        log.info("Assessment stop requested");
    }

    /**
     * 根据模型名称和供应商推断能力标签
     */
    private String inferCapabilityTags(LlmModel model) {
        List<String> tags = new ArrayList<>();

        // 基于模型名称推断能力
        String name = model.getModelName().toLowerCase();
        if (name.contains("coder") || name.contains("code")) {
            tags.add("coding");
        }
        if (name.contains("instruct") || name.contains("chat")) {
            tags.add("chat");
        }
        if (name.contains("reason") || name.contains("think")) {
            tags.add("reasoning");
        }
        if (name.contains("vision") || name.contains("vl")) {
            tags.add("vision");
        }

        // 基于供应商推断
        String provider = model.getProviderId().toLowerCase();
        if (provider.equals("openai") || provider.equals("anthropic")) {
            if (!tags.contains("coding")) tags.add("coding");
            if (!tags.contains("reasoning")) tags.add("reasoning");
        } else if (provider.equals("qwen") || provider.equals("modelscope") || provider.equals("deepseek")) {
            if (!tags.contains("coding")) tags.add("coding");
            if (!tags.contains("chat")) tags.add("chat");
        }

        // 默认标签
        if (tags.isEmpty()) {
            tags.add("chat");
        }

        return String.join(",", tags);
    }

    /**
     * 从模型名称推断参数量
     */
    private String inferParameterSize(String modelName) {
        String name = modelName.toLowerCase();
        if (name.contains("235b") || name.contains("200b") || name.contains("100b") || name.contains("72b") || name.contains("70b")) {
            return "large";
        } else if (name.contains("32b") || name.contains("27b") || name.contains("14b") || name.contains("13b") || name.contains("34b")) {
            return "medium";
        } else {
            return "small";
        }
    }

    private String truncate(String str, int maxLen) {
        if (str == null) return null;
        return str.length() > maxLen ? str.substring(0, maxLen) + "..." : str;
    }

    private int adjustScoreByLatency(int baseScore, long responseTimeMs) {
        if (responseTimeMs < 3000) return baseScore;
        if (responseTimeMs < 10000) return (int) (baseScore * 0.9);
        if (responseTimeMs < 30000) return (int) (baseScore * 0.7);
        if (responseTimeMs < 60000) return (int) (baseScore * 0.5);
        return (int) (baseScore * 0.3);
    }
}
