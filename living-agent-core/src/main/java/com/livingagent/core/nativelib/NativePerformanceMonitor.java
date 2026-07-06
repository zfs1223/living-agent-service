package com.livingagent.core.nativelib;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class NativePerformanceMonitor {

    private static final Logger log = LoggerFactory.getLogger(NativePerformanceMonitor.class);

    private static final double SLOW_CALL_THRESHOLD_MS = 500.0;
    private static final double FAILURE_RATE_ALERT_THRESHOLD = 0.3;

    private final Map<String, NativeCallMetrics> metricsMap = new ConcurrentHashMap<>();

    public NativeCallMetrics getOrCreateMetrics(String operationName) {
        return metricsMap.computeIfAbsent(operationName, NativeCallMetrics::new);
    }

    public void recordSuccess(String operationName, long durationMs) {
        NativeCallMetrics metrics = getOrCreateMetrics(operationName);
        metrics.recordSuccess(durationMs);
        if (durationMs > SLOW_CALL_THRESHOLD_MS) {
            log.warn("Slow native call detected: {} took {}ms (threshold={}ms)", operationName, durationMs, SLOW_CALL_THRESHOLD_MS);
        }
    }

    public void recordFailure(String operationName, long durationMs, String error) {
        NativeCallMetrics metrics = getOrCreateMetrics(operationName);
        metrics.recordFailure(durationMs, error);
        checkFailureRate(metrics);
    }

    private void checkFailureRate(NativeCallMetrics metrics) {
        long totalCalls = metrics.getTotalCalls();
        if (totalCalls >= 5 && metrics.getSuccessRate() < (1.0 - FAILURE_RATE_ALERT_THRESHOLD)) {
            double failureRate = (1.0 - metrics.getSuccessRate()) * 100;
            log.error("High failure rate for native operation '{}': {}% ({} failures / {} total)",
                metrics.getOperationName(),
                String.format("%.1f", failureRate),
                metrics.getFailureCalls(),
                totalCalls);
        }
    }

    public NativeCallMetrics getMetrics(String operationName) {
        return metricsMap.get(operationName);
    }

    public List<NativeCallMetrics> getAllMetrics() {
        return Collections.unmodifiableList(metricsMap.values().stream().collect(Collectors.toList()));
    }

    public List<NativeCallMetrics> getSlowOperations() {
        return metricsMap.values().stream()
            .filter(m -> m.getAvgDurationMs() > SLOW_CALL_THRESHOLD_MS)
            .collect(Collectors.toList());
    }

    public List<NativeCallMetrics> getUnhealthyOperations() {
        return metricsMap.values().stream()
            .filter(m -> m.getTotalCalls() >= 5 && m.getSuccessRate() < (1.0 - FAILURE_RATE_ALERT_THRESHOLD))
            .collect(Collectors.toList());
    }

    public void resetAll() {
        metricsMap.values().forEach(NativeCallMetrics::reset);
    }

    public void reset(String operationName) {
        NativeCallMetrics metrics = metricsMap.get(operationName);
        if (metrics != null) {
            metrics.reset();
        }
    }

    public double getOverallSuccessRate() {
        long totalSuccess = 0;
        long totalCalls = 0;
        for (NativeCallMetrics m : metricsMap.values()) {
            totalSuccess += m.getSuccessCalls();
            totalCalls += m.getTotalCalls();
        }
        return totalCalls > 0 ? (double) totalSuccess / totalCalls : 1.0;
    }

    public String getSummary() {
        return String.format("NativePerformanceMonitor: %d operations, overall success rate=%.1f%%, slow=%d, unhealthy=%d",
            metricsMap.size(),
            getOverallSuccessRate() * 100,
            getSlowOperations().size(),
            getUnhealthyOperations().size());
    }
}
