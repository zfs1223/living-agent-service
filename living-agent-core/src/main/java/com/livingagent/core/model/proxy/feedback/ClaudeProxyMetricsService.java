package com.livingagent.core.model.proxy.feedback;

import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
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
        if (metrics.totalRequests.sum() > 10 && errorRate > HIGH_ERROR_RATE) {
            log.warn("[闭环63] Provider错误率过高: provider={}, errorRate={:.0%}%", provider, errorRate);
            eventBus.publish(63, "performance_issue", CrossLoopEvent.EventPriority.DEGRADATION,
                Map.of("content", String.format("Proxy provider %s error rate %.0f%% exceeds %.0f%%, suggest reducing route weight", provider, errorRate * 100, HIGH_ERROR_RATE * 100)));
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

    public static class ProviderMetrics {
        LongAdder totalRequests = new LongAdder();
        LongAdder successCount = new LongAdder();
        LongAdder failureCount = new LongAdder();
        LongAdder totalLatencyMs = new LongAdder();
    }

    public record ProxyMetricsReport(long totalRequests, long successful, long failed,
                                      double successRate, int providerCount) {}
}
