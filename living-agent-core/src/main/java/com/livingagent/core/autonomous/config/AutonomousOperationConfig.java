package com.livingagent.core.autonomous.config;

import com.livingagent.core.autonomous.bounty.*;
import com.livingagent.core.autonomous.bounty.impl.BugBountyScannerImpl;
import com.livingagent.core.autonomous.bounty.impl.CompositeTaskExecutor;
import com.livingagent.core.autonomous.bounty.impl.FreelanceScannerImpl;
import com.livingagent.core.autonomous.evolution.EvolutionManager;
import com.livingagent.core.autonomous.evolution.HardwareUpgradeService;
import com.livingagent.core.autonomous.incentive.CreditAccountService;
import com.livingagent.core.autonomous.incentive.EvolutionTracker;
import com.livingagent.core.autonomous.incentive.IncentiveManager;
import com.livingagent.core.autonomous.platform.impl.GitHubPlatformIntegration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class AutonomousOperationConfig {

    private static final Logger log = LoggerFactory.getLogger(AutonomousOperationConfig.class);

    @Value("${autonomous.bounty-hunter.enabled:true}")
    private boolean bountyHunterEnabled;

    @Value("${autonomous.bounty-hunter.scan-github:true}")
    private boolean scanGitHub;

    @Value("${autonomous.bounty-hunter.scan-freelance:false}")
    private boolean scanFreelance;

    @Bean
    @Primary
    public GitHubScanner gitHubScanner(GitHubPlatformIntegration gitHubPlatformIntegration) {
        log.info("Using GitHubPlatformIntegration as GitHubScanner");
        return gitHubPlatformIntegration;
    }

    @Bean
    public FreelanceScanner freelanceScanner() {
        log.info("Initializing FreelanceScannerImpl");
        return new FreelanceScannerImpl();
    }

    @Bean
    public BugBountyScanner bugBountyScanner() {
        log.info("Initializing BugBountyScannerImpl");
        return new BugBountyScannerImpl();
    }

    @Bean
    public TaskExecutor bountyTaskExecutor() {
        log.info("Initializing CompositeTaskExecutor");
        return new CompositeTaskExecutor();
    }

    @Bean
    public LedgerService ledgerService() {
        log.info("Initializing LedgerService as unified balance source");
        return new InMemoryLedgerService();
    }

    @Bean
    public BountyHunterSkill bountyHunterSkill(
            GitHubScanner gitHubScanner,
            FreelanceScanner freelanceScanner,
            BugBountyScanner bugBountyScanner,
            LedgerService ledgerService,
            TokenCostEstimator costEstimator,
            TaskExecutor bountyTaskExecutor) {
        log.info("Initializing BountyHunterSkill (enabled: {})", bountyHunterEnabled);
        BountyHunterSkill skill = new BountyHunterSkill(gitHubScanner, freelanceScanner, bugBountyScanner, ledgerService, costEstimator);
        skill.setTaskExecutor(bountyTaskExecutor);
        return skill;
    }
    
    @Bean
    public TokenCostEstimator tokenCostEstimator() {
        log.info("Initializing TokenCostEstimator");
        return new TokenCostEstimator();
    }

    @Bean
    public HardwareUpgradeService hardwareUpgradeService() {
        log.info("Initializing HardwareUpgradeService");
        return new InMemoryHardwareUpgradeService();
    }

    @Bean
    public EvolutionManager evolutionManager(
            HardwareUpgradeService hardwareUpgradeService,
            LedgerService ledgerService) {
        log.info("Initializing EvolutionManager with unified LedgerService");
        return new EvolutionManager(hardwareUpgradeService, ledgerService);
    }

    @Bean
    public CreditAccountService creditAccountService(LedgerService ledgerService) {
        log.info("Initializing CreditAccountService with unified LedgerService");
        return new UnifiedCreditAccountService(ledgerService);
    }

    @Bean
    public EvolutionTracker evolutionTracker(LedgerService ledgerService) {
        log.info("Initializing EvolutionTracker with unified LedgerService");
        return new UnifiedEvolutionTracker(ledgerService);
    }

    @Bean
    public IncentiveManager incentiveManager(
            CreditAccountService creditAccountService,
            EvolutionTracker evolutionTracker) {
        log.info("Initializing IncentiveManager");
        return new IncentiveManager(creditAccountService, evolutionTracker);
    }

    private static class InMemoryLedgerService implements LedgerService {
        private final Map<String, Integer> balances = new ConcurrentHashMap<>();
        private final List<IncomeRecord> records = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void recordIncome(String employeeId, String sourceType, String sourceId, int amountCents, String description) {
            balances.merge(employeeId, amountCents, Integer::sum);
            records.add(new IncomeRecord("inc_" + System.currentTimeMillis(), employeeId, sourceType, sourceId, amountCents, "RECEIVED", Instant.now(), Instant.now()));
        }

        @Override
        public void recordPotentialIncome(String employeeId, String sourceType, String sourceId, int amountCents) {
            records.add(new IncomeRecord("inc_" + System.currentTimeMillis(), employeeId, sourceType, sourceId, amountCents, "PENDING", Instant.now(), null));
        }

        @Override
        public void recordReward(String employeeId, int credits, String reason) {
            balances.merge(employeeId, credits, Integer::sum);
        }

        @Override
        public int getBalance(String employeeId) {
            return balances.getOrDefault(employeeId, 0);
        }

        @Override
        public int getTotalEarned(String employeeId) {
            return balances.getOrDefault(employeeId, 0);
        }

        @Override
        public List<IncomeRecord> getIncomeHistory(String employeeId, int limit) {
            return records.stream().filter(r -> r.employeeId().equals(employeeId)).limit(limit).toList();
        }
    }

    private static class InMemoryHardwareUpgradeService implements HardwareUpgradeService {
        private final List<HardwareUpgradeRecord> history = Collections.synchronizedList(new ArrayList<>());

        @Override
        public Optional<EvolutionManager.HardwareUpgradePlan> evaluateUpgrade(String employeeId, int availableFunds) {
            if (availableFunds >= 500000) {
                return Optional.of(new EvolutionManager.HardwareUpgradePlan(
                    EvolutionManager.UpgradeType.GPU, "RTX 5090 32GB", "可运行 Qwen3-72B 量化", 500000, true));
            }
            if (availableFunds >= 200000) {
                return Optional.of(new EvolutionManager.HardwareUpgradePlan(
                    EvolutionManager.UpgradeType.MEMORY, "128GB RAM", "可处理更长上下文", 200000, true));
            }
            return Optional.empty();
        }

        @Override
        public HardwareUpgradeResult executeUpgrade(String employeeId, EvolutionManager.HardwareUpgradePlan plan) {
            return HardwareUpgradeResult.success("upg_" + System.currentTimeMillis(), plan.hardware(), plan.costCents());
        }

        @Override
        public List<HardwareUpgradeRecord> getUpgradeHistory(String employeeId) {
            return history.stream().filter(r -> r.employeeId().equals(employeeId)).toList();
        }

        @Override
        public void recordTierChange(String employeeId, EvolutionManager.EvolutionTier fromTier, EvolutionManager.EvolutionTier toTier, int balanceCents) {
        }
    }

    private static class InMemoryCreditAccountService implements CreditAccountService {
        private final Map<String, Integer> balances = new ConcurrentHashMap<>();
        private final Map<String, Double> performanceScores = new ConcurrentHashMap<>();
        private final List<CreditTransaction> transactions = Collections.synchronizedList(new ArrayList<>());

        @Override
        public int getBalance(String employeeId) {
            return balances.getOrDefault(employeeId, 0);
        }

        @Override
        public void credit(String employeeId, int amountCents) {
            balances.merge(employeeId, amountCents, Integer::sum);
            transactions.add(new CreditTransaction("txn_" + System.currentTimeMillis(), employeeId, "CREDIT", amountCents, "Reward", null, Instant.now()));
        }

        @Override
        public boolean debit(String employeeId, int amountCents) {
            int current = balances.getOrDefault(employeeId, 0);
            if (current < amountCents) return false;
            balances.put(employeeId, current - amountCents);
            transactions.add(new CreditTransaction("txn_" + System.currentTimeMillis(), employeeId, "DEBIT", amountCents, "Exchange", null, Instant.now()));
            return true;
        }

        @Override
        public int getTotalEarned(String employeeId) {
            return transactions.stream()
                .filter(t -> t.employeeId().equals(employeeId) && "CREDIT".equals(t.type()))
                .mapToInt(CreditTransaction::amountCents)
                .sum();
        }

        @Override
        public double getPerformanceScore(String employeeId) {
            return performanceScores.getOrDefault(employeeId, 0.5);
        }

        @Override
        public List<String> getEmployeesByDepartment(String departmentId) {
            return List.of();
        }

        @Override
        public List<CreditTransaction> getTransactionHistory(String employeeId, int limit) {
            return transactions.stream().filter(t -> t.employeeId().equals(employeeId)).limit(limit).toList();
        }

        @Override
        public Map<String, Object> getAccountStats(String employeeId) {
            return Map.of("balance", getBalance(employeeId), "totalEarned", getTotalEarned(employeeId));
        }
    }

    private static class InMemoryEvolutionTracker implements EvolutionTracker {
        private final Map<String, EvolutionManager.EvolutionTier> tiers = new ConcurrentHashMap<>();
        private final Map<String, Integer> funds = new ConcurrentHashMap<>();

        @Override
        public void recordAchievement(String employeeId, IncentiveManager.TaskResult result) {
            funds.merge(employeeId, result.payoutCents(), Integer::sum);
        }

        @Override
        public void updateTier(String employeeId) {
            int fund = funds.getOrDefault(employeeId, 0);
            EvolutionManager.EvolutionTier tier;
            if (fund >= 100000) tier = EvolutionManager.EvolutionTier.EVOLVING;
            else if (fund >= 50000) tier = EvolutionManager.EvolutionTier.NORMAL;
            else if (fund >= 10000) tier = EvolutionManager.EvolutionTier.SAVING;
            else tier = EvolutionManager.EvolutionTier.MINIMAL;
            tiers.put(employeeId, tier);
        }

        @Override
        public EvolutionManager.EvolutionTier getCurrentTier(String employeeId) {
            return tiers.getOrDefault(employeeId, EvolutionManager.EvolutionTier.MINIMAL);
        }

        @Override
        public int getAccumulatedFunds(String employeeId) {
            return funds.getOrDefault(employeeId, 0);
        }
    }

    /**
     * Unified CreditAccountService that delegates to the shared LedgerService.
     * This ensures all balance data comes from a single source of truth.
     */
    private static class UnifiedCreditAccountService implements CreditAccountService {
        private final LedgerService ledgerService;

        public UnifiedCreditAccountService(LedgerService ledgerService) {
            this.ledgerService = ledgerService;
        }

        @Override
        public int getBalance(String employeeId) {
            return ledgerService.getBalance(employeeId);
        }

        @Override
        public void credit(String employeeId, int amountCents) {
            ledgerService.recordReward(employeeId, amountCents, "Credit");
        }

        @Override
        public boolean debit(String employeeId, int amountCents) {
            int current = ledgerService.getBalance(employeeId);
            if (current < amountCents) return false;
            ledgerService.recordReward(employeeId, -amountCents, "Debit");
            return true;
        }

        @Override
        public int getTotalEarned(String employeeId) {
            return ledgerService.getTotalEarned(employeeId);
        }

        @Override
        public double getPerformanceScore(String employeeId) {
            return 0.5; // Default performance score
        }

        @Override
        public List<String> getEmployeesByDepartment(String departmentId) {
            return List.of();
        }

        @Override
        public List<CreditTransaction> getTransactionHistory(String employeeId, int limit) {
            return ledgerService.getIncomeHistory(employeeId, limit).stream()
                .map(r -> new CreditTransaction(r.id(), r.employeeId(), "CREDIT", r.amountCents(), r.sourceType(), r.sourceId(), r.timestamp()))
                .toList();
        }

        @Override
        public Map<String, Object> getAccountStats(String employeeId) {
            return Map.of("balance", getBalance(employeeId), "totalEarned", getTotalEarned(employeeId));
        }
    }

    /**
     * Unified EvolutionTracker that delegates to the shared LedgerService.
     * This ensures all evolution fund data comes from a single source of truth.
     */
    private static class UnifiedEvolutionTracker implements EvolutionTracker {
        private final LedgerService ledgerService;

        public UnifiedEvolutionTracker(LedgerService ledgerService) {
            this.ledgerService = ledgerService;
        }

        @Override
        public void recordAchievement(String employeeId, IncentiveManager.TaskResult result) {
            ledgerService.recordIncome(employeeId, "ACHIEVEMENT", result.taskId(), result.payoutCents(), result.taskDescription());
        }

        @Override
        public void updateTier(String employeeId) {
            // Tier is computed on-demand in getCurrentTier
        }

        @Override
        public EvolutionManager.EvolutionTier getCurrentTier(String employeeId) {
            int fund = ledgerService.getBalance(employeeId);
            if (fund >= 100000) return EvolutionManager.EvolutionTier.EVOLVING;
            if (fund >= 50000) return EvolutionManager.EvolutionTier.NORMAL;
            if (fund >= 10000) return EvolutionManager.EvolutionTier.SAVING;
            return EvolutionManager.EvolutionTier.MINIMAL;
        }

        @Override
        public int getAccumulatedFunds(String employeeId) {
            return ledgerService.getBalance(employeeId);
        }
    }
}
