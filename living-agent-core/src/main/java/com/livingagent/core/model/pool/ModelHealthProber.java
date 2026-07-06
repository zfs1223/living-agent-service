package com.livingagent.core.model.pool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 模型健康探测器
 * 定时检查禁用的模型是否恢复可用
 * 
 * 优化策略：只有在可用模型数量低于阈值时才执行检查，避免频繁探测
 */
@Component
public class ModelHealthProber {

    private static final Logger log = LoggerFactory.getLogger(ModelHealthProber.class);

    private final LlmModelRepository modelRepo;
    private final ProviderConfigRepository providerRepo;
    private final ModelHealthRegistry healthRegistry;
    private final BrainAutoAssigner brainAutoAssigner;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private final ConcurrentHashMap<String, Instant> lastProbeTime = new ConcurrentHashMap<>();
    private final AtomicInteger activeProbes = new AtomicInteger(0);

    private static final int MAX_CONCURRENT_PROBES = 5;
    private static final Duration PROBE_INTERVAL = Duration.ofMinutes(5);
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(30);

    private static final String PROBE_PROMPT = "{\"model\":\"%s\",\"messages\":[{\"role\":\"user\",\"content\":\"Hi, reply with OK\"}],\"max_tokens\":10,\"temperature\":0.1}";

    /**
     * 可用模型数量阈值
     * 当可用模型数量 >= 此阈值时，跳过健康探测（避免频繁检查）
     * 当可用模型数量 < 此阈值时，才执行探测（尝试恢复禁用模型）
     * 默认值：3（建议至少保持3个可用模型）
     */
    @Value("${model.health-prober.min-available-threshold:3}")
    private int minAvailableThreshold;

    public ModelHealthProber(LlmModelRepository modelRepo,
                             ProviderConfigRepository providerRepo,
                             ModelHealthRegistry healthRegistry,
                             BrainAutoAssigner brainAutoAssigner) {
        this.modelRepo = modelRepo;
        this.providerRepo = providerRepo;
        this.healthRegistry = healthRegistry;
        this.brainAutoAssigner = brainAutoAssigner;
    }

    @Scheduled(fixedRate = 300000, initialDelay = 60000)
    public void probeDisabledModels() {
        if (activeProbes.get() >= MAX_CONCURRENT_PROBES) {
            log.debug("ModelHealthProber: max concurrent probes reached, skipping");
            return;
        }

        // 先检查可用模型数量：如果足够，跳过探测
        List<LlmModel> enabledModels = modelRepo.findByEnabledTrue();
        int availableCount = enabledModels.size();

        if (availableCount >= minAvailableThreshold) {
            // 模型充足时跳过探测，改为TRACE级别避免频繁日志
            log.trace("ModelHealthProber: {} available models >= threshold {}, skipping probe (sufficient models)",
                availableCount, minAvailableThreshold);
            return;
        }

        log.info("ModelHealthProber: only {} available models < threshold {}, starting probe to recover disabled models",
            availableCount, minAvailableThreshold);

        List<LlmModel> disabledModels = modelRepo.findByEnabledFalse();
        if (disabledModels.isEmpty()) {
            log.info("ModelHealthProber: no disabled models to probe (all {} models are enabled)", availableCount);
            return;
        }

        List<LlmModel> candidates = disabledModels.stream()
            .filter(this::shouldProbe)
            .filter(m -> {
                ProviderConfig p = providerRepo.findById(m.getProviderId()).orElse(null);
                return p != null && p.isEnabled() && p.getBaseUrl() != null && !p.getBaseUrl().isEmpty();
            })
            .limit(MAX_CONCURRENT_PROBES - activeProbes.get())
            .toList();

        if (candidates.isEmpty()) {
            log.debug("ModelHealthProber: {} disabled models but none eligible for probing (no enabled provider or base URL)", disabledModels.size());
            return;
        }

        log.info("ModelHealthProber: probing {} disabled models (total disabled: {}, available: {})",
            candidates.size(), disabledModels.size(), availableCount);

        for (LlmModel model : candidates) {
            activeProbes.incrementAndGet();
            try {
                probeModel(model);
            } finally {
                activeProbes.decrementAndGet();
            }
        }
    }

    private boolean shouldProbe(LlmModel model) {
        String key = model.getId() != null ? model.getId().toString() : model.getModelName();
        Instant last = lastProbeTime.get(key);
        if (last == null) return true;
        return Instant.now().isAfter(last.plus(PROBE_INTERVAL));
    }

    private void probeModel(LlmModel model) {
        String modelKey = model.getId() != null ? model.getId().toString() : model.getModelName();
        lastProbeTime.put(modelKey, Instant.now());

        // 嵌入模型不支持 /chat/completions 端点探测，跳过
        // 嵌入模型的健康检查由 ModelPerformanceAssessorImpl 使用 /embeddings 端点处理
        if (isEmbeddingModel(model)) {
            log.debug("ModelHealthProber: skipping embedding model {}/{} (not a chat model)",
                model.getProviderId(), model.getModelName());
            return;
        }

        ProviderConfig provider = providerRepo.findById(model.getProviderId()).orElse(null);

        log.info("ModelHealthProber: probing disabled model {}/{} at {}",
            model.getProviderId(), model.getModelName(), provider != null ? provider.getBaseUrl() : "N/A");

        if (provider == null || !provider.isEnabled() || provider.getBaseUrl() == null || provider.getBaseUrl().isEmpty()) {
            log.debug("ModelHealthProber: skipping model {}/{} (provider unavailable or disabled)",
                model.getProviderId(), model.getModelName());
            return;
        }

        ProbeResult result = executeProbe(provider.getBaseUrl(), model.getModelName(), provider.getApiKeyEncrypted());

        if (result.success) {
            model.setEnabled(true);
            modelRepo.save(model);
            healthRegistry.recordSuccess(modelKey, model.getProviderId(), result.latency.toMillis());
            log.info("ModelHealthProber: RE-ENABLED model {}/{} (probe succeeded, latency={}ms)",
                model.getProviderId(), model.getModelName(), result.latency.toMillis());
            brainAutoAssigner.resetAndReassign();
        } else {
            boolean isTransient = isTransientFailure(result.failureCategory);
            healthRegistry.recordFailure(modelKey, model.getProviderId(),
                result.failureCategory + ": " + result.detail);
            if (isTransient) {
                log.info("ModelHealthProber: disabled model {}/{} probe failed with TRANSIENT error: {} - will retry next cycle",
                    model.getProviderId(), model.getModelName(), result.failureCategory);
            } else {
                log.warn("ModelHealthProber: disabled model {}/{} probe failed with NON-TRANSIENT error: {} ({}) - remains disabled",
                    model.getProviderId(), model.getModelName(), result.failureCategory, result.detail);
            }
        }
    }

    private ProbeResult executeProbe(String baseUrl, String modelName, String apiKey) {
        boolean hasVersionSuffix = baseUrl.endsWith("/v1") || baseUrl.endsWith("/v1/")
            || baseUrl.endsWith("/v4") || baseUrl.endsWith("/v4/");
        String url = hasVersionSuffix
            ? baseUrl + "/chat/completions"
            : baseUrl + "/v1/chat/completions";
        try {
            String body = String.format(PROBE_PROMPT, modelName);
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(PROBE_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));

            if (apiKey != null && !apiKey.isEmpty()) {
                requestBuilder.header("Authorization", "Bearer " + apiKey);
            }

            long startMs = System.currentTimeMillis();
            HttpResponse<String> response = httpClient.send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString()
            );
            long latencyMs = System.currentTimeMillis() - startMs;

            if (response.statusCode() == 200) {
                String responseBody = response.body();
                boolean hasContent = responseBody != null && responseBody.contains("content");
                return new ProbeResult(true, 200, hasContent ? null : "empty_response",
                    Duration.ofMillis(latencyMs), null);
            } else if (response.statusCode() == 429) {
                return new ProbeResult(false, 429, "rate_limited",
                    Duration.ofMillis(latencyMs), "HTTP 429 Too Many Requests");
            } else if (response.statusCode() == 503) {
                return new ProbeResult(false, 503, "service_unavailable",
                    Duration.ofMillis(latencyMs), "HTTP 503 Service Unavailable");
            } else {
                return new ProbeResult(false, response.statusCode(), "http_error",
                    Duration.ofMillis(latencyMs), "HTTP " + response.statusCode());
            }
        } catch (java.net.ConnectException e) {
            return new ProbeResult(false, 0, "connection_refused",
                Duration.ofMillis(0), "Connection refused: " + e.getMessage());
        } catch (java.net.http.HttpTimeoutException e) {
            return new ProbeResult(false, 0, "timeout",
                Duration.ofMillis(0), "Request timed out after " + PROBE_TIMEOUT.getSeconds() + "s");
        } catch (java.net.UnknownHostException e) {
            return new ProbeResult(false, 0, "dns_failure",
                Duration.ofMillis(0), "DNS resolution failed: " + e.getMessage());
        } catch (Exception e) {
            return new ProbeResult(false, 0, "unknown_error",
                Duration.ofMillis(0), e.getMessage());
        }
    }

    /**
     * 判断模型是否为嵌入模型
     * 优先级：inputTypes 字段 > 模型名关键词
     */
    private boolean isEmbeddingModel(LlmModel model) {
        if ("embedding".equalsIgnoreCase(model.getInputTypes())) {
            return true;
        }
        String name = model.getModelName();
        if (name == null) return false;
        String lower = name.toLowerCase();
        return lower.contains("embed") || lower.contains("bge-")
            || lower.contains("nomic-embed") || lower.contains("text-embedding")
            || lower.contains("e5-") || lower.contains("qwen3-embedding");
    }

    private boolean isTransientFailure(String category) {
        if (category == null) return false;
        return switch (category) {
            case "rate_limited", "timeout", "connection_refused", "dns_failure", "service_unavailable"
                -> true;
            case "empty_response", "http_error", "unknown_error"
                -> false;
            default -> false;
        };
    }

    public int getDisabledModelCount() {
        return modelRepo.findByEnabledFalse().size();
    }

    private record ProbeResult(
        boolean success,
        int statusCode,
        String failureCategory,
        Duration latency,
        String detail
    ) {}
}
