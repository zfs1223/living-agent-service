package com.livingagent.core.evolution;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface HardwareUpgradeService {

    Optional<EvolutionManager.HardwareUpgradePlan> evaluateUpgrade(String employeeId, int availableFunds);

    HardwareUpgradeResult executeUpgrade(String employeeId, EvolutionManager.HardwareUpgradePlan plan);

    List<HardwareUpgradeRecord> getUpgradeHistory(String employeeId);

    void recordTierChange(String employeeId, EvolutionManager.EvolutionTier fromTier,
                         EvolutionManager.EvolutionTier toTier, int balanceCents);

    /**
     * P25: 回滚硬件升级（当 ROI 极差时自动执行）。
     * @param upgradeId 升级 ID
     * @param employeeId 员工 ID
     * @return 回滚结果
     */
    HardwareRollbackResult rollbackUpgrade(String upgradeId, String employeeId);

    record HardwareRollbackResult(
        String upgradeId,
        boolean success,
        String message,
        int refundedCents,
        Instant rolledBackAt
    ) {
        public static HardwareRollbackResult success(String upgradeId, int refunded) {
            return new HardwareRollbackResult(upgradeId, true, "Rollback completed", refunded, Instant.now());
        }

        public static HardwareRollbackResult failed(String upgradeId, String reason) {
            return new HardwareRollbackResult(upgradeId, false, reason, 0, Instant.now());
        }

        public static HardwareRollbackResult notFound(String upgradeId) {
            return new HardwareRollbackResult(upgradeId, false, "Upgrade not found", 0, Instant.now());
        }
    }

    record HardwareUpgradeResult(
        String upgradeId,
        boolean success,
        String hardwareName,
        int costCents,
        String message,
        Instant completedAt
    ) {
        public static HardwareUpgradeResult success(String upgradeId, String hardware, int cost) {
            return new HardwareUpgradeResult(upgradeId, true, hardware, cost, "Upgrade completed", Instant.now());
        }

        public static HardwareUpgradeResult insufficientFunds() {
            return new HardwareUpgradeResult(null, false, null, 0, "Insufficient funds", Instant.now());
        }

        public static HardwareUpgradeResult failed(String reason) {
            return new HardwareUpgradeResult(null, false, null, 0, reason, Instant.now());
        }
    }

    record HardwareUpgradeRecord(
        String upgradeId,
        String employeeId,
        EvolutionManager.UpgradeType upgradeType,
        String hardwareName,
        int costCents,
        String benefit,
        String status,
        Instant createdAt,
        Instant completedAt
    ) {}
}
