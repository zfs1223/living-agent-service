package com.livingagent.core.diagnosis.feedback;

import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

@Component
public class ServiceBootstrapHealthTracker {

    private static final Logger log = LoggerFactory.getLogger(ServiceBootstrapHealthTracker.class);

    private final CrossLoopEventBus eventBus;
    private final Map<String, BootstrapRecord> serviceRecords = new ConcurrentHashMap<>();
    private final LongAdder totalBootstraps = new LongAdder();
    private final LongAdder failedBootstraps = new LongAdder();

    public ServiceBootstrapHealthTracker(CrossLoopEventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void recordBootstrapStart(String serviceName) {
        serviceRecords.computeIfAbsent(serviceName, k -> new BootstrapRecord());
        totalBootstraps.increment();
        log.info("[闭环60] 服务初始化: service={}", serviceName);
    }

    public void recordBootstrapComplete(String serviceName, long durationMs) {
        BootstrapRecord record = serviceRecords.get(serviceName);
        if (record != null) {
            record.successCount.increment();
            record.lastDurationMs = durationMs;
        }
    }

    public void recordBootstrapFailure(String serviceName, String error) {
        BootstrapRecord record = serviceRecords.get(serviceName);
        if (record != null) {
            record.failureCount.increment();
        }
        failedBootstraps.increment();
        log.error("[闭环60] 服务初始化失败: service={}, error={}", serviceName, error);
        eventBus.publish(60, "auth_error", CrossLoopEvent.EventPriority.SECURITY,
            Map.of("content", String.format("Service %s bootstrap failed: %s", serviceName, error), "source", "bootstrap"));
    }

    public BootstrapHealthReport getReport() {
        long total = totalBootstraps.sum();
        long failed = failedBootstraps.sum();
        double failureRate = total > 0 ? (double) failed / total : 0;
        return new BootstrapHealthReport(total, failed, failureRate, serviceRecords.size());
    }

    public static class BootstrapRecord {
        LongAdder successCount = new LongAdder();
        LongAdder failureCount = new LongAdder();
        long lastDurationMs;
    }

    public record BootstrapHealthReport(long totalBootstraps, long failedBootstraps,
                                         double failureRate, int trackedServices) {}
}
