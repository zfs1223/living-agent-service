package com.livingagent.core.evolution;

import com.livingagent.core.evolution.circuitbreaker.EvolutionCircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * P25-A: 硬件升级 ROI 验证服务。
 * 升级后跟踪7天性能指标，与升级前基线对比。
 * ROI 为负时触发 CircuitBreaker，自动执行回滚。
 */
@Service
public class HardwareUpgradeRoiValidator {

    private static final Logger log = LoggerFactory.getLogger(HardwareUpgradeRoiValidator.class);

    private static final long ROI_EVALUATION_PERIOD_DAYS = 7;
    private static final double NEGATIVE_ROI_THRESHOLD = -0.2;
    private static final double ROLLBACK_ROI_THRESHOLD = -0.5; // P25: ROI<-50% 自动回滚

    private final EvolutionCircuitBreaker circuitBreaker;
    private final HardwareUpgradeService upgradeService; // P25: 注入升级服务用于自动回滚
    private final Map<String, UpgradeTracking> activeTrackings = new ConcurrentHashMap<>();

    public HardwareUpgradeRoiValidator(
            EvolutionCircuitBreaker circuitBreaker,
            HardwareUpgradeService upgradeService) {
        this.circuitBreaker = circuitBreaker;
        this.upgradeService = upgradeService;
    }

    public record PerformanceSnapshot(
        double avgResponseTimeMs,
        double avgTokensPerSecond,
        double avgSuccessRate,
        int dailyRequestCount,
        Instant capturedAt
    ) {}
    
    // P25: 性能基线定义（升级前的性能指标）
    public record PerformanceBaseline(
        double avgResponseTimeMs,
        double avgTokensPerSecond,
        double avgSuccessRate,
        int dailyRequestCount,
        Instant capturedAt
    ) {
        /**
         * 计算与快照的 ROI 差异。
         */
        public double calculateRoi(PerformanceSnapshot snapshot) {
            // ROI = 响应时间改善(40%) + 吞吐提升(40%) + 成功率提升(20%) - 成本摊销
            double responseTimeImprovement = (avgResponseTimeMs - snapshot.avgResponseTimeMs()) / avgResponseTimeMs;
            double throughputImprovement = (snapshot.avgTokensPerSecond() - avgTokensPerSecond) / avgTokensPerSecond;
            double successRateImprovement = snapshot.avgSuccessRate() - avgSuccessRate;
            
            return responseTimeImprovement * 0.4 + throughputImprovement * 0.4 + successRateImprovement * 0.2;
        }
    }

    public record UpgradeTracking(
        String upgradeId,
        String employeeId,
        String hardwareName,
        int costCents,
        PerformanceBaseline baseline,
        Instant upgradeTime,
        List<PerformanceSnapshot> dailySnapshots,
        RoiStatus status
    ) {
        public UpgradeTracking withSnapshot(PerformanceSnapshot snapshot) {
            List<PerformanceSnapshot> updated = new ArrayList<>(dailySnapshots);
            updated.add(snapshot);
            return new UpgradeTracking(upgradeId, employeeId, hardwareName, costCents,
                baseline, upgradeTime, updated, status);
        }

        public UpgradeTracking withStatus(RoiStatus newStatus) {
            return new UpgradeTracking(upgradeId, employeeId, hardwareName, costCents,
                baseline, upgradeTime, dailySnapshots, newStatus);
        }
    }

    public enum RoiStatus {
        TRACKING,
        POSITIVE_ROI,
        NEGATIVE_ROI,
        ROLLBACK_RECOMMENDED,
        ROLLBACK_EXECUTED, // P25: 新增已回滚状态
        EXPIRED
    }

    /**
     * 注册升级后的 ROI 跟踪。
     */
    public void startTracking(String upgradeId, String employeeId, String hardwareName,
                              int costCents, PerformanceBaseline baseline) {
        UpgradeTracking tracking = new UpgradeTracking(
            upgradeId, employeeId, hardwareName, costCents, baseline,
            Instant.now(), new ArrayList<>(), RoiStatus.TRACKING);
        activeTrackings.put(upgradeId, tracking);
        log.info("P25-A: Started ROI tracking for upgrade={}, employee={}, hardware={}, cost={}cents",
            upgradeId, employeeId, hardwareName, costCents);
    }

    /**
     * 记录每日性能快照。
     */
    public void recordDailySnapshot(String upgradeId, PerformanceSnapshot snapshot) {
        UpgradeTracking tracking = activeTrackings.get(upgradeId);
        if (tracking == null) return;

        activeTrackings.put(upgradeId, tracking.withSnapshot(snapshot));

        // 检查是否到达评估期
        long daysSinceUpgrade = Duration.between(tracking.upgradeTime(), Instant.now()).toDays();
        if (daysSinceUpgrade >= ROI_EVALUATION_PERIOD_DAYS) {
            evaluateRoi(upgradeId);
        }
    }

    private void evaluateRoi(String upgradeId) {
        UpgradeTracking tracking = activeTrackings.get(upgradeId);
        if (tracking == null || tracking.status() != RoiStatus.TRACKING) return;

        double roi = calculateRoi(tracking);
        log.info("P25-A: ROI evaluation for upgrade={}: roi={}, threshold={}",
            upgradeId, String.format("%.2f", roi), NEGATIVE_ROI_THRESHOLD);

        if (roi < NEGATIVE_ROI_THRESHOLD) {
            activeTrackings.put(upgradeId, tracking.withStatus(RoiStatus.NEGATIVE_ROI));
            // 触发 CircuitBreaker，建议回滚
            circuitBreaker.recordFailure(tracking.employeeId);
            log.warn("P25-A: Negative ROI detected for upgrade={}, roi={}, triggering CircuitBreaker for employee={}",
                upgradeId, roi, tracking.employeeId);

            // 如果 ROI 极差，自动执行回滚
            if (roi < ROLLBACK_ROI_THRESHOLD) {
                activeTrackings.put(upgradeId, tracking.withStatus(RoiStatus.ROLLBACK_RECOMMENDED));
                executeRollback(tracking);
            }
        } else {
            activeTrackings.put(upgradeId, tracking.withStatus(RoiStatus.POSITIVE_ROI));
            circuitBreaker.recordSuccess(tracking.employeeId);
            log.info("P25-A: Positive ROI confirmed for upgrade={}, roi={}", upgradeId, roi);
        }
    }

    /**
     * 计算 ROI：性能提升百分比 - 成本摊销比。
     * 正值=收益，负值=亏损。
     */
    private double calculateRoi(UpgradeTracking tracking) {
        PerformanceBaseline baseline = tracking.baseline();
        List<PerformanceSnapshot> snapshots = tracking.dailySnapshots();

        if (snapshots.isEmpty()) return -1.0;

        // 平均性能提升（响应时间降低 + 吞吐提升 + 成功率提升）
        double avgResponseTimeImprovement = 0;
        double avgThroughputImprovement = 0;
        double avgSuccessRateImprovement = 0;
        int count = 0;

        for (PerformanceSnapshot snap : snapshots) {
            if (baseline.avgResponseTimeMs() > 0) {
                avgResponseTimeImprovement += (baseline.avgResponseTimeMs() - snap.avgResponseTimeMs()) / baseline.avgResponseTimeMs();
            }
            if (baseline.avgTokensPerSecond() > 0) {
                avgThroughputImprovement += (snap.avgTokensPerSecond() - baseline.avgTokensPerSecond()) / baseline.avgTokensPerSecond();
            }
            avgSuccessRateImprovement += snap.avgSuccessRate() - baseline.avgSuccessRate();
            count++;
        }

        if (count == 0) return -1.0;

        double performanceGain = (avgResponseTimeImprovement / count * 0.4
            + avgThroughputImprovement / count * 0.4
            + avgSuccessRateImprovement / count * 0.2);

        // 简化成本摊销（假设7天摊销期）
        double costBurden = tracking.costCents() > 0 ? -0.1 : 0;

        return performanceGain + costBurden;
    }

    public Optional<UpgradeTracking> getTracking(String upgradeId) {
        return Optional.ofNullable(activeTrackings.get(upgradeId));
    }

    public List<UpgradeTracking> getActiveTrackings() {
        return activeTrackings.values().stream()
            .filter(t -> t.status() == RoiStatus.TRACKING)
            .toList();
    }

    public List<UpgradeTracking> getNegativeRoiTrackings() {
        return activeTrackings.values().stream()
            .filter(t -> t.status() == RoiStatus.NEGATIVE_ROI || t.status() == RoiStatus.ROLLBACK_RECOMMENDED)
            .toList();
    }

    /**
     * P25: 自动执行硬件升级回滚。
     * 当 ROI 极差（<-50%）时，调用 HardwareUpgradeService.rollbackUpgrade()。
     */
    private void executeRollback(UpgradeTracking tracking) {
        try {
            log.error("P25: Auto-rolling back upgrade {} due to severe negative ROI (employee={})",
                tracking.upgradeId(), tracking.employeeId());

            HardwareUpgradeService.HardwareRollbackResult result =
                upgradeService.rollbackUpgrade(tracking.upgradeId(), tracking.employeeId());

            if (result.success()) {
                activeTrackings.put(tracking.upgradeId(), tracking.withStatus(RoiStatus.ROLLBACK_EXECUTED));
                log.info("P25: Rollback completed for upgrade={}, refunded={} cents",
                    tracking.upgradeId(), result.refundedCents());
            } else {
                log.error("P25: Rollback failed for upgrade={}, reason={}",
                    tracking.upgradeId(), result.message());
            }
        } catch (Exception e) {
            log.error("P25: Rollback execution error for upgrade={}: {}",
                tracking.upgradeId(), e.getMessage());
        }
    }
}
