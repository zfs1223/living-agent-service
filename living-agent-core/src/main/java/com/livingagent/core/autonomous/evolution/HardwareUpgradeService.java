package com.livingagent.core.autonomous.evolution;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface HardwareUpgradeService {

    Optional<EvolutionManager.HardwareUpgradePlan> evaluateUpgrade(String employeeId, int availableFunds);

    HardwareUpgradeResult executeUpgrade(String employeeId, EvolutionManager.HardwareUpgradePlan plan);

    List<HardwareUpgradeRecord> getUpgradeHistory(String employeeId);

    void recordTierChange(String employeeId, EvolutionManager.EvolutionTier fromTier, 
                         EvolutionManager.EvolutionTier toTier, int balanceCents);

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
