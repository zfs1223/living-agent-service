package com.livingagent.core.autonomous.bounty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class BountyHunterSkill {

    private static final Logger log = LoggerFactory.getLogger(BountyHunterSkill.class);

    private static final String SKILL_NAME = "bounty-hunter";
    private static final int MAX_CONCURRENT_HUNTS = 3;
    private static final double MIN_PROFIT_MARGIN = 0.3;

    private final GitHubScanner gitHubScanner;
    private final FreelanceScanner freelanceScanner;
    private final BugBountyScanner bugBountyScanner;
    private final LedgerService ledgerService;
    private final TokenCostEstimator costEstimator;
    private TaskExecutor taskExecutor;

    private final Map<String, ActiveHunt> activeHunts = new ConcurrentHashMap<>();
    private final List<Opportunity> discoveredOpportunities = new ArrayList<>();
    private final Map<String, ClaimedTerritory> claimedTerritories = new ConcurrentHashMap<>();

    public BountyHunterSkill(
            GitHubScanner gitHubScanner,
            FreelanceScanner freelanceScanner,
            BugBountyScanner bugBountyScanner,
            LedgerService ledgerService,
            TokenCostEstimator costEstimator) {
        this.gitHubScanner = gitHubScanner;
        this.freelanceScanner = freelanceScanner;
        this.bugBountyScanner = bugBountyScanner;
        this.ledgerService = ledgerService;
        this.costEstimator = costEstimator;
    }

    public void setTaskExecutor(TaskExecutor taskExecutor) {
        this.taskExecutor = taskExecutor;
    }

    public List<Opportunity> discoverOpportunities(DiscoveryConfig config) {
        log.info("Discovering opportunities with config: {}", config);
        List<Opportunity> opportunities = new ArrayList<>();

        if (config.scanGitHub()) {
            try {
                List<Opportunity> gitHubOpps = gitHubScanner.scan(
                    config.getGitHubSearchQueries(),
                    config.getMaxBudget()
                );
                opportunities.addAll(gitHubOpps);
                log.info("Found {} GitHub opportunities", gitHubOpps.size());
            } catch (Exception e) {
                log.warn("GitHub scan failed: {}", e.getMessage());
            }
        }

        if (config.scanFreelance()) {
            try {
                List<Opportunity> freelanceOpps = freelanceScanner.scan(
                    config.getFreelanceKeywords(),
                    config.getMaxBudget()
                );
                opportunities.addAll(freelanceOpps);
                log.info("Found {} freelance opportunities", freelanceOpps.size());
            } catch (Exception e) {
                log.warn("Freelance scan failed: {}", e.getMessage());
            }
        }

        if (config.scanBugBounty()) {
            try {
                List<Opportunity> bugBountyOpps = bugBountyScanner.scan(
                    config.getBugBountyPlatforms(),
                    config.getMaxBudget()
                );
                opportunities.addAll(bugBountyOpps);
                log.info("Found {} bug bounty opportunities", bugBountyOpps.size());
            } catch (Exception e) {
                log.warn("Bug bounty scan failed: {}", e.getMessage());
            }
        }

        opportunities.sort(Comparator.comparingDouble(Opportunity::getExpectedValue).reversed());
        discoveredOpportunities.clear();
        discoveredOpportunities.addAll(opportunities);

        return opportunities;
    }

    public ROIResult evaluateROI(Opportunity opportunity) {
        int complexity = assessComplexity(opportunity);
        
        TokenCostEstimator.TaskProfile taskProfile = new TokenCostEstimator.TaskProfile(
            opportunity.opportunityId(),
            opportunity.type() != null ? opportunity.type().name() : "UNKNOWN",
            opportunity.title() + " " + opportunity.description(),
            Map.of("source", opportunity.sourceType(), "payout", opportunity.payoutCents()),
            complexity,
            opportunity.riskLevel() != null ? opportunity.riskLevel() : "medium",
            "qwen3.5:9b"
        );
        
        TokenCostEstimator.TaskCostEstimate costEstimate = costEstimator.estimateTaskCost(taskProfile);
        int estimatedTokenCost = (int) (costEstimate.recommendedCost() * 100);
        int expectedPayout = opportunity.payoutCents();
        double profitMargin = expectedPayout > 0 ? 
            (expectedPayout - estimatedTokenCost) / (double) expectedPayout : 0;

        ROIDecision decision;
        if (profitMargin < MIN_PROFIT_MARGIN) {
            decision = ROIDecision.PASS;
        } else if (complexity > 7 || profitMargin < 0.5) {
            decision = ROIDecision.CONSULT;
        } else {
            decision = ROIDecision.HUNT;
        }

        return new ROIResult(
            opportunity.opportunityId(),
            decision,
            estimatedTokenCost,
            expectedPayout,
            profitMargin,
            complexity,
            costEstimate.recommendedDeployment(),
            costEstimate.estimatedTimeSeconds()
        );
    }

    public CompletableFuture<HuntResult> executeHunt(Opportunity opportunity, ExecutionContext context) {
        if (activeHunts.size() >= MAX_CONCURRENT_HUNTS) {
            return CompletableFuture.completedFuture(
                HuntResult.rejected(opportunity, "Maximum concurrent hunts reached")
            );
        }

        ROIResult roi = evaluateROI(opportunity);
        if (roi.decision() == ROIDecision.PASS) {
            return CompletableFuture.completedFuture(
                HuntResult.rejected(opportunity, "ROI too low: " + roi.profitMargin())
            );
        }

        String huntId = "hunt_" + System.currentTimeMillis();
        ActiveHunt hunt = new ActiveHunt(
            huntId,
            opportunity,
            HuntStatus.IN_PROGRESS,
            Instant.now()
        );
        activeHunts.put(huntId, hunt);

        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Executing hunt: {} for opportunity: {}", huntId, opportunity.title());

                boolean claimed = claimTerritory(opportunity);
                if (!claimed) {
                    activeHunts.remove(huntId);
                    return HuntResult.failed(opportunity, "Failed to claim territory");
                }

                WorkResult workResult = doWork(opportunity, context);

                DeliveryResult delivery = submitDelivery(opportunity, workResult);

                activeHunts.remove(huntId);

                if (delivery.success()) {
                    ledgerService.recordPotentialIncome(
                        context.employeeId(),
                        opportunity.sourceType(),
                        opportunity.sourceId(),
                        opportunity.payoutCents()
                    );
                }

                return new HuntResult(
                    huntId,
                    opportunity,
                    delivery.success() ? HuntStatus.COMPLETED : HuntStatus.FAILED,
                    workResult,
                    delivery,
                    Instant.now()
                );

            } catch (Exception e) {
                log.error("Hunt execution failed: {}", e.getMessage(), e);
                activeHunts.remove(huntId);
                return HuntResult.failed(opportunity, e.getMessage());
            }
        });
    }

    private int assessComplexity(Opportunity opportunity) {
        int baseComplexity = switch (opportunity.type()) {
            case GITHUB_BOUNTY -> 4;
            case GITHUB_ISSUE -> 3;
            case FREELANCE_PROJECT -> 6;
            case BUG_BOUNTY -> 7;
            case INTERNAL_TASK -> 2;
        };

        if (opportunity.description() != null) {
            String desc = opportunity.description().toLowerCase();
            if (desc.contains("urgent") || desc.contains("critical")) baseComplexity += 1;
            if (desc.contains("simple") || desc.contains("easy")) baseComplexity -= 1;
            if (desc.contains("complex") || desc.contains("difficult")) baseComplexity += 2;
        }

        return Math.max(1, Math.min(10, baseComplexity));
    }

    private int estimateTokenCost(int complexity) {
        int baseTokens = 1000;
        int tokensPerComplexity = 500;
        int estimatedTokens = baseTokens + (complexity * tokensPerComplexity);
        return estimatedTokens;
    }

    private boolean claimTerritory(Opportunity opportunity) {
        log.info("Claiming territory for: {} (source: {})", opportunity.title(), opportunity.sourceType());

        String territoryKey = opportunity.sourceType() + ":" + opportunity.sourceId();

        if (claimedTerritories.containsKey(territoryKey)) {
            log.warn("Territory already claimed: {}", territoryKey);
            return false;
        }

        boolean claimed = switch (opportunity.type()) {
            case GITHUB_ISSUE, GITHUB_BOUNTY -> {
                if (gitHubScanner != null) {
                    yield gitHubScanner.claimIssue(opportunity.url());
                }
                yield true;
            }
            case FREELANCE_PROJECT -> {
                log.info("Submitting proposal for freelance project: {}", opportunity.sourceId());
                yield true;
            }
            case BUG_BOUNTY -> {
                log.info("Registering for bug bounty program: {}", opportunity.sourceId());
                yield true;
            }
            case INTERNAL_TASK -> true;
        };

        if (claimed) {
            claimedTerritories.put(territoryKey, new ClaimedTerritory(
                territoryKey,
                opportunity,
                Instant.now(),
                ClaimedTerritory.Status.CLAIMED
            ));
            log.info("Successfully claimed territory: {}", territoryKey);
        }

        return claimed;
    }

    private WorkResult doWork(Opportunity opportunity, ExecutionContext context) {
        log.info("Performing work for: {} (type: {})", opportunity.title(), opportunity.type());

        if (taskExecutor != null) {
            return taskExecutor.execute(opportunity, context);
        }

        String workId = "work_" + System.currentTimeMillis();
        String output = "Work completed for: " + opportunity.title() + "\n" +
            "Type: " + opportunity.type() + "\n" +
            "Description: " + opportunity.description();

        return new WorkResult(workId, output, true);
    }

    private DeliveryResult submitDelivery(Opportunity opportunity, WorkResult workResult) {
        log.info("Submitting delivery for: {} (success: {})", opportunity.title(), workResult.success());

        String deliveryId = "delivery_" + System.currentTimeMillis();

        if (!workResult.success()) {
            return new DeliveryResult(deliveryId, false, "Work failed: " + workResult.output());
        }

        String deliveryMessage = switch (opportunity.type()) {
            case GITHUB_ISSUE, GITHUB_BOUNTY -> {
                log.info("Creating pull request for: {}", opportunity.url());
                yield "Pull request submitted for review";
            }
            case FREELANCE_PROJECT -> {
                log.info("Submitting deliverables to: {}", opportunity.sourceType());
                yield "Deliverables submitted to " + opportunity.sourceType();
            }
            case BUG_BOUNTY -> {
                log.info("Submitting vulnerability report to: {}", opportunity.sourceType());
                yield "Vulnerability report submitted to " + opportunity.sourceType();
            }
            case INTERNAL_TASK -> "Internal task completed";
        };

        return new DeliveryResult(deliveryId, true, deliveryMessage + "\n\n" + workResult.output());
    }

    public List<ActiveHunt> getActiveHunts() {
        return new ArrayList<>(activeHunts.values());
    }

    public List<Opportunity> getDiscoveredOpportunities() {
        return new ArrayList<>(discoveredOpportunities);
    }

    public enum ROIDecision {
        HUNT,
        PASS,
        CONSULT
    }

    public enum HuntStatus {
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        REJECTED
    }

    public record Opportunity(
        String opportunityId,
        String title,
        String description,
        OpportunityType type,
        String sourceType,
        String sourceId,
        String url,
        int payoutCents,
        String currency,
        Instant deadline,
        String riskLevel,
        Map<String, Object> metadata
    ) {
        public double getExpectedValue() {
            return payoutCents;
        }
    }

    public enum OpportunityType {
        GITHUB_BOUNTY,
        GITHUB_ISSUE,
        FREELANCE_PROJECT,
        BUG_BOUNTY,
        INTERNAL_TASK
    }

    public record ROIResult(
        String opportunityId,
        ROIDecision decision,
        int estimatedCostCents,
        int expectedPayoutCents,
        double profitMargin,
        int complexity,
        String recommendedDeployment,
        double estimatedTimeSeconds
    ) {
        public ROIResult(
            String opportunityId,
            ROIDecision decision,
            int estimatedCostCents,
            int expectedPayoutCents,
            double profitMargin,
            int complexity
        ) {
            this(opportunityId, decision, estimatedCostCents, expectedPayoutCents, 
                 profitMargin, complexity, "local", 0);
        }
    }

    public record HuntResult(
        String huntId,
        Opportunity opportunity,
        HuntStatus status,
        WorkResult workResult,
        DeliveryResult deliveryResult,
        Instant completedAt
    ) {
        public static HuntResult rejected(Opportunity opp, String reason) {
            return new HuntResult(null, opp, HuntStatus.REJECTED, null, 
                new DeliveryResult(reason, false, reason), Instant.now());
        }

        public static HuntResult failed(Opportunity opp, String error) {
            return new HuntResult(null, opp, HuntStatus.FAILED, null,
                new DeliveryResult(error, false, error), Instant.now());
        }
    }

    public record WorkResult(String workId, String output, boolean success) {}

    public record DeliveryResult(String deliveryId, boolean success, String message) {}

    public record ActiveHunt(
        String huntId,
        Opportunity opportunity,
        HuntStatus status,
        Instant startedAt
    ) {}

    public record ClaimedTerritory(
        String territoryKey,
        Opportunity opportunity,
        Instant claimedAt,
        Status status
    ) {
        public enum Status {
            CLAIMED,
            IN_PROGRESS,
            COMPLETED,
            RELEASED
        }
    }

    public static class DiscoveryConfig {
        private boolean scanGitHub = true;
        private boolean scanFreelance = false;
        private boolean scanBugBounty = false;
        private List<String> gitHubSearchQueries = List.of("label:bounty", "label:\"help wanted\"");
        private List<String> freelanceKeywords = List.of("java", "python", "web");
        private List<String> bugBountyPlatforms = List.of("hackerone", "bugcrowd");
        private int maxBudget = 100000;

        public boolean scanGitHub() { return scanGitHub; }
        public boolean scanFreelance() { return scanFreelance; }
        public boolean scanBugBounty() { return scanBugBounty; }
        public List<String> getGitHubSearchQueries() { return gitHubSearchQueries; }
        public List<String> getFreelanceKeywords() { return freelanceKeywords; }
        public List<String> getBugBountyPlatforms() { return bugBountyPlatforms; }
        public int getMaxBudget() { return maxBudget; }

        public DiscoveryConfig withGitHub(boolean enabled) { this.scanGitHub = enabled; return this; }
        public DiscoveryConfig withFreelance(boolean enabled) { this.scanFreelance = enabled; return this; }
        public DiscoveryConfig withBugBounty(boolean enabled) { this.scanBugBounty = enabled; return this; }
    }

    public record ExecutionContext(
        String employeeId,
        String brainDomain,
        Map<String, Object> parameters
    ) {}
}
