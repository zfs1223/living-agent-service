package com.livingagent.core.autonomous.incentive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class IncentiveManager {

    private static final Logger log = LoggerFactory.getLogger(IncentiveManager.class);

    private final CreditAccountService creditAccountService;
    private final EvolutionTracker evolutionTracker;
    private final Map<String, List<IncentiveReward>> pendingRewards = new ConcurrentHashMap<>();

    public IncentiveManager(CreditAccountService creditAccountService, 
                           EvolutionTracker evolutionTracker) {
        this.creditAccountService = creditAccountService;
        this.evolutionTracker = evolutionTracker;
    }

    public IncentiveReward calculateReward(TaskResult result, String employeeId) {
        int baseCredits = result.payoutCents();

        double qualityMultiplier = calculateQualityMultiplier(result.successRate());
        double timelinessMultiplier = calculateTimelinessMultiplier(result.completionTimeMs());

        int totalCredits = (int) (baseCredits * qualityMultiplier * timelinessMultiplier);

        return new IncentiveReward(
            "reward_" + System.currentTimeMillis(),
            employeeId,
            baseCredits,
            qualityMultiplier,
            timelinessMultiplier,
            totalCredits,
            Instant.now()
        );
    }

    public void distributeReward(TaskResult result, String employeeId) {
        IncentiveReward reward = calculateReward(result, employeeId);

        creditAccountService.credit(employeeId, reward.totalCredits());
        log.info("Distributed reward: {} credits to employee {}", reward.totalCredits(), employeeId);

        evolutionTracker.recordAchievement(employeeId, result);
        evolutionTracker.updateTier(employeeId);

        checkHardwareUpgradeEligibility(employeeId);
    }

    private double calculateQualityMultiplier(double successRate) {
        if (successRate >= 0.98) return 1.5;
        if (successRate >= 0.95) return 1.3;
        if (successRate >= 0.90) return 1.2;
        if (successRate >= 0.80) return 1.0;
        return 0.8;
    }

    private double calculateTimelinessMultiplier(long completionTimeMs) {
        if (completionTimeMs < 1000) return 1.3;
        if (completionTimeMs < 5000) return 1.1;
        if (completionTimeMs < 30000) return 1.0;
        return 0.8;
    }

    private void checkHardwareUpgradeEligibility(String employeeId) {
        int totalFunds = creditAccountService.getBalance(employeeId);
        log.debug("Checking hardware upgrade eligibility for {}: {} cents", employeeId, totalFunds);
    }

    public void distributeDepartmentBonus(String departmentId, int totalBonus) {
        List<String> employees = creditAccountService.getEmployeesByDepartment(departmentId);

        Map<String, Double> performanceScores = calculatePerformanceDistribution(employees);

        for (String employeeId : employees) {
            double share = performanceScores.getOrDefault(employeeId, 0.0);
            int bonus = (int) (totalBonus * share);
            if (bonus > 0) {
                creditAccountService.credit(employeeId, bonus);
                log.info("Distributed department bonus: {} cents to employee {}", bonus, employeeId);
            }
        }
    }

    private Map<String, Double> calculatePerformanceDistribution(List<String> employees) {
        Map<String, Double> scores = new ConcurrentHashMap<>();
        double totalScore = 0.0;

        for (String employeeId : employees) {
            double score = creditAccountService.getPerformanceScore(employeeId);
            scores.put(employeeId, score);
            totalScore += score;
        }

        if (totalScore > 0) {
            for (String employeeId : employees) {
                scores.put(employeeId, scores.get(employeeId) / totalScore);
            }
        }

        return scores;
    }

    public record IncentiveReward(
        String rewardId,
        String employeeId,
        int baseCredits,
        double qualityMultiplier,
        double timelinessMultiplier,
        int totalCredits,
        Instant awardedAt
    ) {}

    public record TaskResult(
        String taskId,
        String employeeId,
        int payoutCents,
        boolean success,
        double successRate,
        long completionTimeMs,
        Instant completedAt
    ) {}
}
