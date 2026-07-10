package com.livingagent.core.security.auth;

import com.livingagent.core.evolution.signal.EvolutionSignal;
import com.livingagent.core.evolution.signal.EvolutionSignal.SignalType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 闭环38-P38-A: 认证指标收集服务
 * 按 method/source/success/failure/reason 聚合认证指标，注册到 HealthMonitor
 */
public class AuthMetricsService {

    private static final Logger log = LoggerFactory.getLogger(AuthMetricsService.class);

    private static final double FAILURE_RATE_ALERT_THRESHOLD = 0.30;

    private final LongAdder totalAttempts = new LongAdder();
    private final LongAdder totalSuccess = new LongAdder();
    private final LongAdder totalFailure = new LongAdder();
    private final Map<String, MethodMetrics> methodMetrics = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> failureReasons = new ConcurrentHashMap<>();

    private volatile AuthAlertCallback alertCallback;

    public interface AuthAlertCallback {
        void onHighFailureRate(String method, double failureRate, EvolutionSignal signal);
    }

    public void setAlertCallback(AuthAlertCallback callback) {
        this.alertCallback = callback;
    }

    public void recordSuccess(String method, String source) {
        totalAttempts.increment();
        totalSuccess.increment();
        methodMetrics.computeIfAbsent(method, k -> new MethodMetrics()).recordSuccess();
        log.debug("Auth success: method={}, source={}", method, source);
    }

    public void recordFailure(String method, String source, String reason) {
        totalAttempts.increment();
        totalFailure.increment();
        methodMetrics.computeIfAbsent(method, k -> new MethodMetrics()).recordFailure();
        failureReasons.computeIfAbsent(reason, k -> new LongAdder()).increment();
        log.debug("Auth failure: method={}, source={}, reason={}", method, source, reason);

        checkFailureRate(method);
    }

    private void checkFailureRate(String method) {
        MethodMetrics metrics = methodMetrics.get(method);
        if (metrics == null || metrics.getAttemptCount() < 5) return;

        double failureRate = metrics.getFailureRate();
        if (failureRate > FAILURE_RATE_ALERT_THRESHOLD && alertCallback != null) {
            EvolutionSignal signal = new EvolutionSignal(SignalType.ERROR,
                String.format("认证方法 %s 失败率 %.1f%% 超过阈值 %.0f%%",
                    method, failureRate * 100, FAILURE_RATE_ALERT_THRESHOLD * 100));
            signal.setSource("AuthMetricsService");
            signal.addMetadata("method", method);
            signal.addMetadata("failureRate", failureRate);
            signal.addMetadata("threshold", FAILURE_RATE_ALERT_THRESHOLD);
            signal.addTag("auth-metrics");

            alertCallback.onHighFailureRate(method, failureRate, signal);
            log.warn("[闭环38] 认证失败率告警: method={}, failureRate={}/{}", method,
                String.format("%.1f%%", failureRate * 100),
                String.format("%.0f%%", FAILURE_RATE_ALERT_THRESHOLD * 100));
        }
    }

    public AuthMetricsSnapshot getSnapshot() {
        Map<String, MethodMetricsSnapshot> methodSnapshots = new ConcurrentHashMap<>();
        methodMetrics.forEach((k, v) -> methodSnapshots.put(k, v.toSnapshot()));

        Map<String, Long> reasonCounts = new ConcurrentHashMap<>();
        failureReasons.forEach((k, v) -> reasonCounts.put(k, v.sum()));

        return new AuthMetricsSnapshot(
            totalAttempts.sum(), totalSuccess.sum(), totalFailure.sum(),
            totalAttempts.sum() > 0 ? (double) totalFailure.sum() / totalAttempts.sum() : 0,
            methodSnapshots, reasonCounts, Instant.now()
        );
    }

    public String getHealthSummary() {
        AuthMetricsSnapshot snap = getSnapshot();
        if (snap.totalAttempts() == 0) return "No auth attempts recorded";
        return String.format("Auth: attempts=%d, success=%d, failure=%d, rate=%.1f%%",
            snap.totalAttempts(), snap.totalSuccess(), snap.totalFailure(),
            snap.overallFailureRate() * 100);
    }

    public record AuthMetricsSnapshot(
        long totalAttempts, long totalSuccess, long totalFailure,
        double overallFailureRate,
        Map<String, MethodMetricsSnapshot> methodMetrics,
        Map<String, Long> failureReasons,
        Instant capturedAt
    ) {}

    public record MethodMetricsSnapshot(long attempts, long success, long failure, double failureRate) {}

    private static class MethodMetrics {
        private final LongAdder attempts = new LongAdder();
        private final LongAdder success = new LongAdder();
        private final LongAdder failure = new LongAdder();

        void recordSuccess() { attempts.increment(); success.increment(); }
        void recordFailure() { attempts.increment(); failure.increment(); }
        long getAttemptCount() { return attempts.sum(); }
        double getFailureRate() {
            long a = attempts.sum();
            return a > 0 ? (double) failure.sum() / a : 0;
        }
        MethodMetricsSnapshot toSnapshot() {
            return new MethodMetricsSnapshot(attempts.sum(), success.sum(), failure.sum(), getFailureRate());
        }
    }
}
