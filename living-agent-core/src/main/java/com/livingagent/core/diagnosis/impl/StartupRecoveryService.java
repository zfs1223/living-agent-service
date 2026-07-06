package com.livingagent.core.diagnosis.impl;

import com.livingagent.core.diagnosis.AppModeUtil;
import com.livingagent.core.diagnosis.DegradedTrafficCanary;
import com.livingagent.core.diagnosis.HealthMonitor;
import com.livingagent.core.diagnosis.HealthStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * P12-D: 启动失败自动恢复服务。
 * 当系统处于降级模式时，周期性重试启动依赖检查，依赖恢复后通过 P27-A canary 探测逐步恢复。
 */
@Service
public class StartupRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(StartupRecoveryService.class);

    private final HealthMonitor healthMonitor;
    private final DegradedTrafficCanary canary;
    private volatile int recoveryAttempts = 0;
    private volatile int successfulRecoveries = 0;
    private static final int MAX_CONTINUOUS_ATTEMPTS = 20;

    public StartupRecoveryService(HealthMonitor healthMonitor, DegradedTrafficCanary canary) {
        this.healthMonitor = healthMonitor;
        this.canary = canary;
    }

    @Scheduled(fixedRate = 60000, initialDelay = 120000)
    public void checkAndRecover() {
        if (!AppModeUtil.isDegraded()) {
            return;
        }

        if (recoveryAttempts >= MAX_CONTINUOUS_ATTEMPTS) {
            log.warn("P12-D: Max recovery attempts ({}) reached, stopping automatic recovery", MAX_CONTINUOUS_ATTEMPTS);
            return;
        }

        recoveryAttempts++;
        log.info("P12-D: Recovery attempt #{} - checking if dependencies are now available...", recoveryAttempts);

        HealthStatus status = healthMonitor.checkHealth();
        boolean allHealthy = status.getStatus() == HealthStatus.Status.HEALTHY;

        if (allHealthy) {
            // P27-A修复: 先启动小流量探测，确认探测成功后再全量恢复
            if (!canary.isProbing("model_daemon")) {
                canary.startProbing("model_daemon", "degraded_mode");
                log.info("P27-A: Dependencies recovered, starting canary probing for model_daemon (10% traffic)");
            }

            // 健康检查通过视为一次探测成功
            canary.recordProbeSuccess("model_daemon");

            // 检查 canary 探测结果：成功则全量恢复，失败则保持降级
            var probingRecords = canary.getAllProbing();
            var modelDaemonRecord = probingRecords.stream()
                .filter(r -> r.componentId().equals("model_daemon"))
                .findFirst();

            if (modelDaemonRecord.isPresent() && modelDaemonRecord.get().shouldPromote()) {
                canary.promoteToFull("model_daemon");
                AppModeUtil.clearDegraded();
                successfulRecoveries++;
                recoveryAttempts = 0;
                log.info("P12-D: Canary probing successful ({} successes), app mode restored to NORMAL",
                    modelDaemonRecord.get().probeSuccesses());
            } else if (modelDaemonRecord.isPresent() && modelDaemonRecord.get().shouldRollback()) {
                canary.rollback("model_daemon");
                log.warn("P12-D: Canary probing failed (timeout), keeping degraded mode");
            } else {
                log.info("P12-D: Canary probing in progress, waiting for more successful checks...");
            }
        } else {
            // 依赖不健康，如果正在探测则记录失败
            if (canary.isProbing("model_daemon")) {
                canary.recordProbeFailure("model_daemon");
            }
            log.warn("P12-D: Dependencies still unhealthy after attempt #{}: {}", recoveryAttempts, status.getMessage());
        }
    }

    public int getRecoveryAttempts() {
        return recoveryAttempts;
    }

    public int getSuccessfulRecoveries() {
        return successfulRecoveries;
    }
}
