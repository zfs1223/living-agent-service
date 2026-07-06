package com.livingagent.core.diagnosis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * P27-A: 降级小流量回归服务。
 * 模型从降级恢复后，先以10%流量回归（PROBING），效果对比后全量切换。
 * 三阶段: FULL(正常) -> PROBING(10%探测) -> FULL(确认恢复)
 * 
 * P27: 定量指标判断（响应时间阈值+成功率阈值）
 */
@Service
public class DegradedTrafficCanary {

    private static final Logger log = LoggerFactory.getLogger(DegradedTrafficCanary.class);

    public enum CanaryState {
        FULL,
        PROBING
    }

    private static final double PROBE_TRAFFIC_RATIO = 0.1;
    private static final int MIN_PROBE_SUCCESS_FOR_PROMOTION = 5;
    private static final int MAX_PROBE_FAILURES_FOR_ROLLBACK = 3;
    private static final long PROBE_TIMEOUT_SECONDS = 300;
    
    // P27: 定量指标阈值
    private static final double MAX_RESPONSE_TIME_MS = 5000; // 响应时间阈值：5秒
    private static final double MIN_SUCCESS_RATE = 0.8; // 成功率阈值：80%
    private static final double MAX_ERROR_RATE = 0.2; // 错误率阈值：20%

    private final Map<String, CanaryRecord> canaryRecords = new ConcurrentHashMap<>();

    public record CanaryRecord(
        String componentId,
        CanaryState state,
        Instant enteredProbingAt,
        int probeSuccesses,
        int probeFailures,
        double probeTrafficRatio,
        String fallbackComponentId,
        // P27: 定量指标字段
        double avgResponseTimeMs,
        double successRate,
        int totalSamples
    ) {
        public CanaryRecord withSuccess() {
            return new CanaryRecord(componentId, state, enteredProbingAt,
                probeSuccesses + 1, probeFailures, probeTrafficRatio, fallbackComponentId,
                avgResponseTimeMs, successRate, totalSamples);
        }

        public CanaryRecord withFailure() {
            return new CanaryRecord(componentId, state, enteredProbingAt,
                probeSuccesses, probeFailures + 1, probeTrafficRatio, fallbackComponentId,
                avgResponseTimeMs, successRate, totalSamples);
        }
        
        // P27: 带定量指标的更新
        public CanaryRecord withMetrics(double responseTimeMs, boolean success) {
            int newTotal = totalSamples + 1;
            double newAvgResponseTime = (avgResponseTimeMs * totalSamples + responseTimeMs) / newTotal;
            double newSuccessRate = (successRate * totalSamples + (success ? 1.0 : 0.0)) / newTotal;
            return new CanaryRecord(componentId, state, enteredProbingAt,
                success ? probeSuccesses + 1 : probeSuccesses,
                success ? probeFailures : probeFailures + 1,
                probeTrafficRatio, fallbackComponentId,
                newAvgResponseTime, newSuccessRate, newTotal);
        }

        public boolean shouldPromote() {
            // P27: 同时满足次数和定量指标
            return probeSuccesses >= MIN_PROBE_SUCCESS_FOR_PROMOTION
                && avgResponseTimeMs <= MAX_RESPONSE_TIME_MS
                && successRate >= MIN_SUCCESS_RATE;
        }

        public boolean shouldRollback() {
            // P27: 次数超限或定量指标不达标
            return probeFailures >= MAX_PROBE_FAILURES_FOR_ROLLBACK
                || avgResponseTimeMs > MAX_RESPONSE_TIME_MS
                || successRate < MIN_SUCCESS_RATE
                || (enteredProbingAt != null && Instant.now().isAfter(enteredProbingAt.plusSeconds(PROBE_TIMEOUT_SECONDS)));
        }
    }

    /**
     * 开始对组件进行小流量探测。
     */
    public void startProbing(String componentId, String fallbackComponentId) {
        CanaryRecord record = new CanaryRecord(
            componentId, CanaryState.PROBING, Instant.now(),
            0, 0, PROBE_TRAFFIC_RATIO, fallbackComponentId,
            0.0, 0.0, 0); // P27: 初始化定量指标
        canaryRecords.put(componentId, record);
        log.info("P27-A: Started canary probing for component={}, trafficRatio={}, fallback={}, thresholds: responseTime={}ms, successRate={}",
            componentId, PROBE_TRAFFIC_RATIO, fallbackComponentId, MAX_RESPONSE_TIME_MS, MIN_SUCCESS_RATE);
    }

    /**
     * P27: 带定量指标的探测记录。
     * @param componentId 组件 ID
     * @param responseTimeMs 响应时间（毫秒）
     * @param success 是否成功
     * @return true 如果应该提升为全量或回滚
     */
    public boolean recordProbeWithMetrics(String componentId, double responseTimeMs, boolean success) {
        CanaryRecord record = canaryRecords.get(componentId);
        if (record == null || record.state() != CanaryState.PROBING) {
            return false;
        }

        CanaryRecord updated = record.withMetrics(responseTimeMs, success);
        canaryRecords.put(componentId, updated);

        log.debug("P27: Probe metrics for {}: responseTime={}ms, success={}, avgResponseTime={}ms, successRate={}",
            componentId, String.format("%.1f", responseTimeMs), success,
            String.format("%.1f", updated.avgResponseTimeMs()), String.format("%.2f", updated.successRate()));

        if (updated.shouldPromote()) {
            promoteToFull(componentId);
            log.info("P27: Component {} promoted - avgResponseTime={}ms <= {}ms, successRate={} >= {}",
                componentId, String.format("%.1f", updated.avgResponseTimeMs()), MAX_RESPONSE_TIME_MS,
                String.format("%.2f", updated.successRate()), MIN_SUCCESS_RATE);
            return true;
        }
        
        if (updated.shouldRollback()) {
            rollback(componentId);
            log.warn("P27: Component {} rolled back - avgResponseTime={}ms, successRate={}, failures={}",
                componentId, String.format("%.1f", updated.avgResponseTimeMs()), 
                String.format("%.2f", updated.successRate()), updated.probeFailures());
            return true;
        }
        
        return false;
    }

    /**
     * 判断是否应该将请求路由到正在探测的组件。
     */
    public boolean shouldRouteToComponent(String componentId) {
        CanaryRecord record = canaryRecords.get(componentId);
        if (record == null || record.state() == CanaryState.FULL) {
            return true;
        }

        // PROBING: 10% 概率路由到该组件
        boolean shouldRoute = Math.random() < record.probeTrafficRatio();
        log.debug("P27-A: Canary routing decision for {}: {}", componentId, shouldRoute);
        return shouldRoute;
    }

    /**
     * 记录探测成功。
     * @return true 如果应该提升为全量
     */
    public boolean recordProbeSuccess(String componentId) {
        CanaryRecord record = canaryRecords.get(componentId);
        if (record == null || record.state() != CanaryState.PROBING) {
            return false;
        }

        CanaryRecord updated = record.withSuccess();
        canaryRecords.put(componentId, updated);

        if (updated.shouldPromote()) {
            promoteToFull(componentId);
            return true;
        }
        return false;
    }

    /**
     * 记录探测失败。
     * @return true 如果应该回滚
     */
    public boolean recordProbeFailure(String componentId) {
        CanaryRecord record = canaryRecords.get(componentId);
        if (record == null || record.state() != CanaryState.PROBING) {
            return false;
        }

        CanaryRecord updated = record.withFailure();
        canaryRecords.put(componentId, updated);

        if (updated.shouldRollback()) {
            rollback(componentId);
            return true;
        }
        return false;
    }

    public void promoteToFull(String componentId) {
        CanaryRecord record = canaryRecords.get(componentId);
        if (record != null) {
            canaryRecords.put(componentId, new CanaryRecord(
                componentId, CanaryState.FULL, null,
                record.probeSuccesses(), record.probeFailures(), 1.0, record.fallbackComponentId(),
                record.avgResponseTimeMs(), record.successRate(), record.totalSamples()));
            log.info("P27-A: Component {} promoted to FULL traffic (successes={}, failures={}, avgResponseTime={}ms, successRate={})",
                componentId, record.probeSuccesses(), record.probeFailures(),
                String.format("%.1f", record.avgResponseTimeMs()), String.format("%.2f", record.successRate()));
        }
    }

    public void rollback(String componentId) {
        CanaryRecord record = canaryRecords.remove(componentId);
        if (record != null) {
            log.warn("P27-A: Component {} rolled back from probing (successes={}, failures={}, fallback={})",
                componentId, record.probeSuccesses(), record.probeFailures(), record.fallbackComponentId());
        }
    }

    public CanaryState getState(String componentId) {
        CanaryRecord record = canaryRecords.get(componentId);
        return record != null ? record.state() : CanaryState.FULL;
    }

    public boolean isProbing(String componentId) {
        return getState(componentId) == CanaryState.PROBING;
    }

    public List<CanaryRecord> getAllProbing() {
        return canaryRecords.values().stream()
            .filter(r -> r.state() == CanaryState.PROBING)
            .toList();
    }
}
