package com.livingagent.core.model.proxy.feedback;

import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

@Component
public class ClaudeProxyMetricsService {

    private static final Logger log = LoggerFactory.getLogger(ClaudeProxyMetricsService.class);
    private static final double HIGH_ERROR_RATE = 0.10;
    private static final long HIGH_LATENCY_MS = 5000;

    private final CrossLoopEventBus eventBus;
    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder successfulRequests = new LongAdder();
    private final LongAdder failedRequests = new LongAdder();
    private final Map<String, ProviderMetrics> providerMetricsMap = new ConcurrentHashMap<>();

    private volatile Set<String> degradedProviders = ConcurrentHashMap.newKeySet();
    private volatile double errorRateThreshold = HIGH_ERROR_RATE;

    public ClaudeProxyMetricsService(CrossLoopEventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void recordRequest(String provider, boolean success, long latencyMs) {
        totalRequests.increment();
        if (success) {
            successfulRequests.increment();
        } else {
            failedRequests.increment();
        }

        ProviderMetrics metrics = providerMetricsMap.computeIfAbsent(provider, k -> new ProviderMetrics());
        metrics.totalRequests.increment();
        if (success) {
            metrics.successCount.increment();
        } else {
            metrics.failureCount.increment();
        }
        metrics.totalLatencyMs.add(latencyMs);

        double errorRate = (double) metrics.failureCount.sum() / metrics.totalRequests.sum();
        if (metrics.totalRequests.sum() > 10 && errorRate > errorRateThreshold) {
            log.warn("[闭环63] Provider错误率过高: provider={}, errorRate={}%", provider, String.format("%.0f", errorRate * 100));
            eventBus.publish(63, "performance_issue", CrossLoopEvent.EventPriority.DEGRADATION,
                Map.of("content", String.format("Proxy provider %s error rate %.0f%% exceeds %.0f%%, suggest reducing route weight", provider, errorRate * 100, HIGH_ERROR_RATE * 100)));

            // 闭环33 improvement: 降级高错误率provider，调整路由权重
            degradedProviders.add(provider);
            eventBus.publish(33, "tool_quality_degraded", CrossLoopEvent.EventPriority.DEGRADATION,
                Map.of("provider", provider, "errorRate", errorRate,
                    "action", "reduce_route_weight", "content",
                    String.format("Claude CLI工具provider %s 错误率%.0f%%已降级", provider, errorRate * 100)));
            log.info("[闭环33] Provider降级: provider={}, degradedProviders={}", provider, degradedProviders);
        }

        if (latencyMs > HIGH_LATENCY_MS) {
            log.warn("[闭环63] Proxy高延迟: provider={}, latency={}ms", provider, latencyMs);
        }
    }

    public ProxyMetricsReport getReport() {
        long total = totalRequests.sum();
        long success = successfulRequests.sum();
        long failed = failedRequests.sum();
        double successRate = total > 0 ? (double) success / total : 1.0;
        return new ProxyMetricsReport(total, success, failed, successRate, providerMetricsMap.size());
    }

    public boolean isProviderDegraded(String provider) {
        return degradedProviders.contains(provider);
    }

    public Set<String> getDegradedProviders() {
        return Set.copyOf(degradedProviders);
    }

    @Scheduled(fixedRate = 30 * 60 * 1000)
    public void reevaluateDegradedProviders() {
        if (degradedProviders.isEmpty()) return;

        for (String provider : Set.copyOf(degradedProviders)) {
            ProviderMetrics metrics = providerMetricsMap.get(provider);
            if (metrics == null) {
                degradedProviders.remove(provider);
                continue;
            }
            long recentTotal = metrics.totalRequests.sum();
            if (recentTotal == 0) {
                degradedProviders.remove(provider);
                continue;
            }
            double currentRate = (double) metrics.failureCount.sum() / recentTotal;
            if (currentRate < errorRateThreshold * 0.5) {
                degradedProviders.remove(provider);
                log.info("[闭环33] Provider恢复: provider={}, errorRate={}%", provider,
                    String.format("%.0f", currentRate * 100));
                eventBus.publish(33, "tool_quality_recovered", CrossLoopEvent.EventPriority.DEGRADATION,
                    Map.of("provider", provider, "action", "restore_route_weight"));
            }
        }

        // 动态调整errorRateThreshold：全局错误率低时适当放宽
        long total = totalRequests.sum();
        if (total > 100) {
            double globalErrorRate = (double) failedRequests.sum() / total;
            errorRateThreshold = Math.max(0.05, Math.min(0.20, globalErrorRate * 1.5));
        }
    }

    public static class ProviderMetrics {
        LongAdder totalRequests = new LongAdder();
        LongAdder successCount = new LongAdder();
        LongAdder failureCount = new LongAdder();
        LongAdder totalLatencyMs = new LongAdder();
    }

    public record ProxyMetricsReport(long totalRequests, long successful, long failed,
                                      double successRate, int providerCount) {}
}
