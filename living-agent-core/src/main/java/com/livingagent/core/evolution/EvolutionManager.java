package com.livingagent.core.evolution;

import com.livingagent.core.autonomous.bounty.LedgerService;
import com.livingagent.core.model.pool.BrainModelResolver;
import com.livingagent.core.model.pool.ResolvedBrainModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EvolutionManager {

    private static final Logger log = LoggerFactory.getLogger(EvolutionManager.class);

    private static final int THRESHOLD_EVOLVING = 100_000;
    private static final int THRESHOLD_NORMAL = 50_000;
    private static final int THRESHOLD_SAVING = 10_000;

    // 硬编码兜底值，仅在 BrainModelResolver 不可用时使用
    private static final String FALLBACK_MODEL_EVOLVING = "qwen3.5-27b";
    private static final String FALLBACK_MODEL_NORMAL = "qwen3.5-27b";
    private static final String FALLBACK_MODEL_SAVING = "qwen3-0.6b";
    private static final String FALLBACK_MODEL_MINIMAL = "bitnet-1.58-3b";

    private final Map<String, EvolutionTier> employeeTiers = new ConcurrentHashMap<>();
    private final HardwareUpgradeService hardwareUpgradeService;
    private final LedgerService ledgerService;
    private final BrainModelResolver brainModelResolver;

    public EvolutionManager(HardwareUpgradeService hardwareUpgradeService) {
        this.hardwareUpgradeService = hardwareUpgradeService;
        this.ledgerService = null;
        this.brainModelResolver = null;
    }

    public EvolutionManager(HardwareUpgradeService hardwareUpgradeService, LedgerService ledgerService) {
        this(hardwareUpgradeService, ledgerService, null);
    }

    public EvolutionManager(HardwareUpgradeService hardwareUpgradeService, LedgerService ledgerService, BrainModelResolver brainModelResolver) {
        this.hardwareUpgradeService = hardwareUpgradeService;
        this.ledgerService = ledgerService;
        this.brainModelResolver = brainModelResolver;
        log.info("EvolutionManager initialized with unified LedgerService, brainModelResolver={}", brainModelResolver != null);
    }

    /**
     * 通过 BrainModelResolver 动态获取指定用途的模型名称
     * @param purpose 用途标识，如 "evolution-evolving", "evolution-saving", "evolution-minimal"
     * @param fallback 兜底模型名称
     * @return 模型名称
     */
    private String resolveModelForPurpose(String purpose, String fallback) {
        if (brainModelResolver != null) {
            try {
                ResolvedBrainModel resolved = brainModelResolver.resolve(purpose);
                if (resolved != null && resolved.getModelName() != null && !resolved.getModelName().isEmpty()) {
                    return resolved.getModelName();
                }
            } catch (Exception e) {
                log.warn("EvolutionManager failed to resolve model for purpose '{}': {}, using fallback: {}", purpose, e.getMessage(), fallback);
            }
        } else {
            log.warn("EvolutionManager BrainModelResolver is null, using fallback model for purpose '{}': {}", purpose, fallback);
        }
        return fallback;
    }

    public EvolutionTier determineTier(int balanceCents) {
        if (balanceCents >= THRESHOLD_EVOLVING) return EvolutionTier.EVOLVING;
        if (balanceCents >= THRESHOLD_NORMAL) return EvolutionTier.NORMAL;
        if (balanceCents >= THRESHOLD_SAVING) return EvolutionTier.SAVING;
        return EvolutionTier.MINIMAL;
    }

    public void applyTierStrategy(EvolutionTier tier, DigitalEmployeeConfig config) {
        log.info("Applying tier strategy: {} for employee", tier);

        switch (tier) {
            case EVOLVING -> {
                config.setInferenceModel(resolveModelForPurpose("evolution-evolving", FALLBACK_MODEL_EVOLVING));
                config.setHeartbeatInterval(Duration.ofMinutes(5));
                config.setMaxConcurrentTasks(10);
                evaluateHardwareUpgrade(config);
            }
            case NORMAL -> {
                config.setInferenceModel(resolveModelForPurpose("evolution-normal", FALLBACK_MODEL_NORMAL));
                config.setHeartbeatInterval(Duration.ofMinutes(10));
                config.setMaxConcurrentTasks(5);
            }
            case SAVING -> {
                config.setInferenceModel(resolveModelForPurpose("evolution-saving", FALLBACK_MODEL_SAVING));
                config.setHeartbeatInterval(Duration.ofMinutes(30));
                config.setMaxConcurrentTasks(2);
            }
            case MINIMAL -> {
                config.setInferenceModel(resolveModelForPurpose("evolution-minimal", FALLBACK_MODEL_MINIMAL));
                config.setHeartbeatInterval(Duration.ofHours(1));
                config.setMaxConcurrentTasks(1);
                config.setMinPayoutThreshold(50_00);
            }
        }
    }

    public HardwareUpgradePlan evaluateHardwareUpgrade(DigitalEmployeeConfig config) {
        int balance = getAccumulatedFunds(config.getEmployeeId());

        if (balance >= 50_000_00) {
            return new HardwareUpgradePlan(
                UpgradeType.PROFESSIONAL,
                "4× A100 80GB",
                "可运行任意开源大模型",
                50_000_00,
                true
            );
        } else if (balance >= 15_000_00) {
            return new HardwareUpgradePlan(
                UpgradeType.MULTI_GPU,
                "双 RTX 5090",
                "可运行 Qwen3-72B 全精度",
                15_000_00,
                true
            );
        } else if (balance >= 5_000_00) {
            return new HardwareUpgradePlan(
                UpgradeType.GPU,
                "RTX 5090 32GB",
                "可运行 Qwen3-72B 量化",
                5_000_00,
                true
            );
        } else if (balance >= 2_000_00) {
            return new HardwareUpgradePlan(
                UpgradeType.MEMORY,
                "128GB RAM",
                "可处理更长上下文",
                2_000_00,
                true
            );
        }

        return HardwareUpgradePlan.none();
    }

    public void updateEmployeeTier(String employeeId, int balanceCents) {
        EvolutionTier newTier = determineTier(balanceCents);
        EvolutionTier oldTier = employeeTiers.get(employeeId);

        if (oldTier != newTier) {
            employeeTiers.put(employeeId, newTier);
            log.info("Employee {} tier changed: {} -> {} (balance: {} cents)",
                employeeId, oldTier, newTier, balanceCents);

            hardwareUpgradeService.recordTierChange(employeeId, oldTier, newTier, balanceCents);
        }
    }

    public int getAccumulatedFunds(String employeeId) {
        if (ledgerService != null) {
            return ledgerService.getBalance(employeeId);
        }
        return 0;
    }

    public void addFunds(String employeeId, int amountCents) {
        if (ledgerService != null) {
            ledgerService.recordReward(employeeId, amountCents, "Evolution fund addition");
            updateEmployeeTier(employeeId, ledgerService.getBalance(employeeId));
            log.info("Added {} cents to employee {}. New balance: {}", amountCents, employeeId, ledgerService.getBalance(employeeId));
        }
    }

    public boolean deductFunds(String employeeId, int amountCents) {
        if (ledgerService != null) {
            int current = ledgerService.getBalance(employeeId);
            if (current < amountCents) {
                return false;
            }
            updateEmployeeTier(employeeId, current - amountCents);
            log.info("Deducted {} cents from employee {}. New balance: {}", amountCents, employeeId, current - amountCents);
            return true;
        }
        return false;
    }

    public EvolutionTier getTier(String employeeId) {
        return employeeTiers.getOrDefault(employeeId, EvolutionTier.MINIMAL);
    }

    public enum EvolutionTier {
        EVOLVING("进化状态", "资金充足，可升级硬件"),
        NORMAL("正常状态", "标准运行"),
        SAVING("积累状态", "节约模式，加速积累"),
        MINIMAL("基础状态", "最低功耗，持续运行");

        private final String name;
        private final String description;

        EvolutionTier(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
    }

    public enum UpgradeType {
        NONE,
        MEMORY,
        GPU,
        MULTI_GPU,
        PROFESSIONAL
    }

    public record HardwareUpgradePlan(
        UpgradeType type,
        String hardware,
        String benefit,
        int costCents,
        boolean executable
    ) {
        public static HardwareUpgradePlan none() {
            return new HardwareUpgradePlan(UpgradeType.NONE, null, null, 0, false);
        }
    }

    public static class DigitalEmployeeConfig {
        private String employeeId;
        private String inferenceModel = null;
        private Duration heartbeatInterval = Duration.ofMinutes(10);
        private int maxConcurrentTasks = 5;
        private int minPayoutThreshold = 0;

        public String getEmployeeId() { return employeeId; }
        public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
        public String getInferenceModel() { return inferenceModel; }
        public void setInferenceModel(String inferenceModel) { this.inferenceModel = inferenceModel; }
        public Duration getHeartbeatInterval() { return heartbeatInterval; }
        public void setHeartbeatInterval(Duration heartbeatInterval) { this.heartbeatInterval = heartbeatInterval; }
        public int getMaxConcurrentTasks() { return maxConcurrentTasks; }
        public void setMaxConcurrentTasks(int maxConcurrentTasks) { this.maxConcurrentTasks = maxConcurrentTasks; }
        public int getMinPayoutThreshold() { return minPayoutThreshold; }
        public void setMinPayoutThreshold(int minPayoutThreshold) { this.minPayoutThreshold = minPayoutThreshold; }
    }
}
