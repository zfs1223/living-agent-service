package com.livingagent.core.diagnosis.feedback;

import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

@Component
public class ServiceBootstrapHealthTracker {

    private static final Logger log = LoggerFactory.getLogger(ServiceBootstrapHealthTracker.class);
    private static final int MAX_AUTO_RETRIES = 3;

    private final CrossLoopEventBus eventBus;
    private final Map<String, BootstrapRecord> serviceRecords = new ConcurrentHashMap<>();
    private final LongAdder totalBootstraps = new LongAdder();
    private final LongAdder failedBootstraps = new LongAdder();
    private final Map<String, Integer> retryCountMap = new ConcurrentHashMap<>();
    private final Set<String> servicesNeedingIntervention = ConcurrentHashMap.newKeySet();
    private final Set<String> pendingAutoRetries = ConcurrentHashMap.newKeySet();
    private volatile long autoRetryDelayMs = 60_000;
    private volatile int maxConcurrentRetries = 2;

    public ServiceBootstrapHealthTracker(@Autowired(required = false) CrossLoopEventBus eventBus) {
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
        retryCountMap.remove(serviceName);
        servicesNeedingIntervention.remove(serviceName);
    }

    public void recordBootstrapFailure(String serviceName, String error) {
        BootstrapRecord record = serviceRecords.get(serviceName);
        if (record != null) {
            record.failureCount.increment();
        }
        failedBootstraps.increment();

        int retryCount = retryCountMap.merge(serviceName, 1, Integer::sum);
        log.error("[闭环60] 服务初始化失败: service={}, error={}, retryCount={}/{}",
            serviceName, error, retryCount, MAX_AUTO_RETRIES);

        if (retryCount >= MAX_AUTO_RETRIES) {
            servicesNeedingIntervention.add(serviceName);
            log.warn("[闭环60] 服务{}超过最大重试次数{}，标记为需人工干预",
                serviceName, MAX_AUTO_RETRIES);
        } else {
            pendingAutoRetries.add(serviceName);
            log.info("[闭环60] 服务{}加入自动重试队列，当前重试{}/{}", serviceName, retryCount, MAX_AUTO_RETRIES);
        }

        if (eventBus != null) {
            eventBus.publish(60, "auth_error", CrossLoopEvent.EventPriority.SECURITY,
                Map.of("content", String.format("Service %s bootstrap failed (retry %d/%d): %s", serviceName, retryCount, MAX_AUTO_RETRIES, error),
                    "source", "bootstrap", "needsIntervention", retryCount >= MAX_AUTO_RETRIES));
        }
    }

    @Scheduled(fixedRate = 30 * 60 * 1000)
    public void scheduledBootstrapHealthCheck() {
        if (serviceRecords.isEmpty()) return;

        for (Map.Entry<String, BootstrapRecord> entry : serviceRecords.entrySet()) {
            String serviceName = entry.getKey();
            BootstrapRecord record = entry.getValue();
            if (record.failureCount.sum() > 0 && record.successCount.sum() == 0) {
                if (!servicesNeedingIntervention.contains(serviceName)) {
                    servicesNeedingIntervention.add(serviceName);
                    log.warn("[闭环60] 服务{}从未成功启动，标记需人工干预", serviceName);
                }
            }
        }

        if (!servicesNeedingIntervention.isEmpty() && eventBus != null) {
            eventBus.publish(60, "services_need_intervention", CrossLoopEvent.EventPriority.SECURITY,
                Map.of("services", servicesNeedingIntervention.toString(), "count", servicesNeedingIntervention.size()), 300);
        }
    }

    @Scheduled(fixedRate = 60 * 1000)
    public void executeAutoRetries() {
        if (pendingAutoRetries.isEmpty()) return;

        int retried = 0;
        for (String serviceName : Set.copyOf(pendingAutoRetries)) {
            if (retried >= maxConcurrentRetries) break;

            Integer retryCount = retryCountMap.get(serviceName);
            if (retryCount == null || retryCount >= MAX_AUTO_RETRIES) {
                pendingAutoRetries.remove(serviceName);
                continue;
            }

            log.info("[闭环60] 自动重试服务: service={}, attempt={}/{}", serviceName, retryCount, MAX_AUTO_RETRIES);
            pendingAutoRetries.remove(serviceName);
            retried++;

            if (eventBus != null) {
                eventBus.publish(60, "service_auto_retry", CrossLoopEvent.EventPriority.SELF_HEALING,
                    Map.of("serviceName", serviceName, "attempt", retryCount,
                        "action", "restart_service"), 0);
            }
        }

        adjustRetryParameters();
    }

    private void adjustRetryParameters() {
        long total = totalBootstraps.sum();
        if (total < 10) return;

        double failureRate = (double) failedBootstraps.sum() / total;
        if (failureRate > 0.3) {
            autoRetryDelayMs = Math.min(300_000, autoRetryDelayMs + 30_000);
            maxConcurrentRetries = Math.max(1, maxConcurrentRetries - 1);
            log.info("[闭环60] 高失败率{}%，增加重试间隔至{}ms，减少并发至{}",
                String.format("%.0f", failureRate * 100), autoRetryDelayMs, maxConcurrentRetries);
        } else if (failureRate < 0.1) {
            autoRetryDelayMs = Math.max(30_000, autoRetryDelayMs - 10_000);
            maxConcurrentRetries = Math.min(5, maxConcurrentRetries + 1);
        }
    }

    public boolean needsIntervention(String serviceName) {
        return servicesNeedingIntervention.contains(serviceName);
    }

    public Set<String> getServicesNeedingIntervention() {
        return Set.copyOf(servicesNeedingIntervention);
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
