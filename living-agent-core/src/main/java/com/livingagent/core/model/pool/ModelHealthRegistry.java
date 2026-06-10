package com.livingagent.core.model.pool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 模型健康注册表，记录模型成功/失败/超时/熔断状态
 * 用于防止连续选择不健康模型，支持短期熔断和自动恢复
 */
public class ModelHealthRegistry {

    private static final Logger log = LoggerFactory.getLogger(ModelHealthRegistry.class);

    /** 模型健康状态枚举 */
    public enum HealthStatus {
        /** 可用 */
        AVAILABLE,
        /** 降级运行（部分失败但可用） */
        DEGRADED,
        /** 冷却中（连续失败后的短期熔断） */
        COOLDOWN,
        /** 不可用 */
        UNAVAILABLE,
        /** 未知（尚未有调用记录） */
        UNKNOWN
    }

    /** 模型健康记录 */
    public record ModelHealthRecord(
        String modelId,
        String providerId,
        HealthStatus status,
        int consecutiveSuccesses,
        int consecutiveFailures,
        Instant lastSuccessTime,
        Instant lastFailureTime,
        Instant cooldownUntil,
        String lastFailureReason,
        long totalCalls,
        long totalSuccesses,
        long totalFailures,
        long averageLatencyMs,
        Instant lastUpdatedAt
    ) {
        public boolean isInCooldown() {
            return status == HealthStatus.COOLDOWN && cooldownUntil != null && Instant.now().isBefore(cooldownUntil);
        }

        public boolean isAvailable() {
            return status == HealthStatus.AVAILABLE || status == HealthStatus.DEGRADED || status == HealthStatus.UNKNOWN;
        }
    }

    private final Map<String, ModelHealthRecord> healthRecords = new ConcurrentHashMap<>();

    /** 连续失败次数阈值，超过后进入冷却 */
    private final int failureThreshold;

    /** 冷却持续时间 */
    private final Duration cooldownDuration;

    /** 连续成功次数阈值，成功后恢复可用状态 */
    private final int recoveryThreshold;

    public ModelHealthRegistry() {
        this(3, Duration.ofMinutes(5), 2);
    }

    public ModelHealthRegistry(int failureThreshold, Duration cooldownDuration, int recoveryThreshold) {
        this.failureThreshold = failureThreshold;
        this.cooldownDuration = cooldownDuration;
        this.recoveryThreshold = recoveryThreshold;
    }

    /**
     * 记录模型调用成功
     */
    public void recordSuccess(String modelId, String providerId, long latencyMs) {
        ModelHealthRecord record = healthRecords.computeIfAbsent(modelId, k -> createInitialRecord(modelId, providerId));

        ModelHealthRecord updated = new ModelHealthRecord(
            modelId,
            providerId,
            record.isInCooldown() ? HealthStatus.AVAILABLE : 
                (record.consecutiveSuccesses() + 1 >= recoveryThreshold ? HealthStatus.AVAILABLE : record.status()),
            record.consecutiveSuccesses() + 1,
            0,
            Instant.now(),
            record.lastFailureTime(),
            null,
            null,
            record.totalCalls() + 1,
            record.totalSuccesses() + 1,
            record.totalFailures(),
            calculateNewAverage(record.averageLatencyMs(), latencyMs, record.totalCalls() + 1),
            Instant.now()
        );

        healthRecords.put(modelId, updated);

        if (record.status() != updated.status()) {
            log.info("Model {} recovered to status={}", modelId, updated.status());
        }
    }

    /**
     * 记录模型调用失败
     */
    public void recordFailure(String modelId, String providerId, String failureReason) {
        ModelHealthRecord record = healthRecords.computeIfAbsent(modelId, k -> createInitialRecord(modelId, providerId));

        int newConsecutiveFailures = record.consecutiveFailures() + 1;
        HealthStatus newStatus = record.status();

        if (newConsecutiveFailures >= failureThreshold) {
            newStatus = HealthStatus.COOLDOWN;
            log.warn("Model {} entered cooldown after {} consecutive failures, reason: {}", 
                modelId, newConsecutiveFailures, failureReason);
        } else if (newConsecutiveFailures >= 2) {
            newStatus = HealthStatus.DEGRADED;
        }

        ModelHealthRecord updated = new ModelHealthRecord(
            modelId,
            providerId,
            newStatus,
            0,
            newConsecutiveFailures,
            record.lastSuccessTime(),
            Instant.now(),
            newStatus == HealthStatus.COOLDOWN ? Instant.now().plus(cooldownDuration) : record.cooldownUntil(),
            failureReason,
            record.totalCalls() + 1,
            record.totalSuccesses(),
            record.totalFailures() + 1,
            record.averageLatencyMs(),
            Instant.now()
        );

        healthRecords.put(modelId, updated);
    }

    /**
     * 获取模型健康状态
     */
    public ModelHealthRecord getHealth(String modelId) {
        ModelHealthRecord record = healthRecords.get(modelId);
        if (record == null) {
            return createInitialRecord(modelId, "unknown");
        }
        // 检查冷却是否到期
        if (record.isInCooldown()) {
            return record;
        }
        // 冷却到期，恢复为 AVAILABLE
        if (record.status() == HealthStatus.COOLDOWN && !record.isInCooldown()) {
            ModelHealthRecord recovered = new ModelHealthRecord(
                record.modelId(),
                record.providerId(),
                HealthStatus.AVAILABLE,
                0,
                0,
                record.lastSuccessTime(),
                record.lastFailureTime(),
                null,
                null,
                record.totalCalls(),
                record.totalSuccesses(),
                record.totalFailures(),
                record.averageLatencyMs(),
                Instant.now()
            );
            healthRecords.put(modelId, recovered);
            return recovered;
        }
        return record;
    }

    /**
     * 获取所有可用模型列表（过滤掉不可用和冷却中的模型）
     */
    public Map<String, ModelHealthRecord> getAvailableModels() {
        return healthRecords.entrySet().stream()
            .filter(e -> e.getValue().isAvailable() || !e.getValue().isInCooldown())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * 检查模型是否可用
     */
    public boolean isModelAvailable(String modelId) {
        ModelHealthRecord record = getHealth(modelId);
        return record.isAvailable() && !record.isInCooldown();
    }

    /**
     * 获取模型健康摘要（用于日志和决策上下文）
     */
    public String getHealthSummary() {
        return healthRecords.values().stream()
            .map(r -> String.format("%s(status=%s,successRate=%.1f%%,failures=%d)",
                r.modelId(),
                r.status(),
                r.totalCalls() > 0 ? (r.totalSuccesses() * 100.0 / r.totalCalls()) : 100.0,
                r.consecutiveFailures()))
            .collect(Collectors.joining(", "));
    }

    /**
     * 重置模型健康状态
     */
    public void reset(String modelId) {
        healthRecords.remove(modelId);
        log.info("Model health reset for {}", modelId);
    }

    /**
     * 清除所有健康记录
     */
    public void clearAll() {
        healthRecords.clear();
        log.info("All model health records cleared");
    }

    private ModelHealthRecord createInitialRecord(String modelId, String providerId) {
        return new ModelHealthRecord(
            modelId, providerId, HealthStatus.UNKNOWN,
            0, 0, null, null, null, null,
            0, 0, 0, 0, Instant.now()
        );
    }

    private long calculateNewAverage(long currentAvg, long newValue, long totalCount) {
        if (totalCount <= 0) return newValue;
        return (currentAvg * (totalCount - 1) + newValue) / totalCount;
    }
}
