package com.livingagent.gateway.controller;

import com.livingagent.core.autonomous.bounty.BountyHunterSkill;
import com.livingagent.core.autonomous.bounty.LedgerService;
import com.livingagent.core.autonomous.incentive.CreditAccountService;
import com.livingagent.core.autonomous.incentive.EvolutionTracker;
import com.livingagent.core.autonomous.payout.PayoutService;
import com.livingagent.core.evolution.EvolutionManager.EvolutionTier;
import com.livingagent.core.security.AccessGateService;
import com.livingagent.core.security.AuthContext;
import com.livingagent.core.security.auth.UnifiedAuthService;
import com.livingagent.core.security.auth.UnifiedAuthService.AuthSession;
import com.livingagent.gateway.controller.common.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 经济自治控制器 — 赏金猎取 / 收款管理 / 进化追踪 / 账本查询
 */
@RestController
@RequestMapping("/api/autonomous")
public class AutonomousController {

    private static final Logger log = LoggerFactory.getLogger(AutonomousController.class);

    private final BountyHunterSkill bountyHunterSkill;
    private final PayoutService payoutService;
    private final LedgerService ledgerService;
    private final CreditAccountService creditAccountService;
    private final EvolutionTracker evolutionTracker;
    private final UnifiedAuthService authService;
    private final AccessGateService accessGateService;

    public AutonomousController(
            BountyHunterSkill bountyHunterSkill,
            PayoutService payoutService,
            LedgerService ledgerService,
            CreditAccountService creditAccountService,
            EvolutionTracker evolutionTracker,
            UnifiedAuthService authService,
            AccessGateService accessGateService) {
        this.bountyHunterSkill = bountyHunterSkill;
        this.payoutService = payoutService;
        this.ledgerService = ledgerService;
        this.creditAccountService = creditAccountService;
        this.evolutionTracker = evolutionTracker;
        this.authService = authService;
        this.accessGateService = accessGateService;
    }

    // ==================== 赏金系统 ====================

    @GetMapping("/bounty/opportunities")
    public ResponseEntity<ApiResponse<List<BountyHunterSkill.Opportunity>>> getDiscoveredOpportunities(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty()) return unauthorized();
        if (!accessGateService.canRoute(ctxOpt.get().getEmployeeId(), "brain", "FinanceBrain")) return forbidden();
        return ResponseEntity.ok(ApiResponse.ok(bountyHunterSkill.getDiscoveredOpportunities()));
    }

    @PostMapping("/bounty/discover")
    public ResponseEntity<ApiResponse<List<BountyHunterSkill.Opportunity>>> discoverOpportunities(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) DiscoverRequest config) {
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty()) return unauthorized();
        if (!accessGateService.canRoute(ctxOpt.get().getEmployeeId(), "brain", "FinanceBrain")) return forbidden();

        BountyHunterSkill.DiscoveryConfig discoveryConfig = new BountyHunterSkill.DiscoveryConfig();
        if (config != null) {
            if (config.scanGitHub() != null) discoveryConfig.withGitHub(config.scanGitHub());
            if (config.scanFreelance() != null) discoveryConfig.withFreelance(config.scanFreelance());
            if (config.scanBugBounty() != null) discoveryConfig.withBugBounty(config.scanBugBounty());
        }
        return ResponseEntity.ok(ApiResponse.ok(bountyHunterSkill.discoverOpportunities(discoveryConfig)));
    }

    @GetMapping("/bounty/active-hunts")
    public ResponseEntity<ApiResponse<List<BountyHunterSkill.ActiveHunt>>> getActiveHunts(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty()) return unauthorized();
        if (!accessGateService.canRoute(ctxOpt.get().getEmployeeId(), "brain", "FinanceBrain")) return forbidden();
        return ResponseEntity.ok(ApiResponse.ok(bountyHunterSkill.getActiveHunts()));
    }

    @PostMapping("/bounty/evaluate/{opportunityId}")
    public ResponseEntity<ApiResponse<BountyHunterSkill.ROIResult>> evaluateROI(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String opportunityId) {
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty()) return unauthorized();
        if (!accessGateService.canRoute(ctxOpt.get().getEmployeeId(), "brain", "FinanceBrain")) return forbidden();

        return bountyHunterSkill.getDiscoveredOpportunities().stream()
                .filter(o -> o.opportunityId().equals(opportunityId))
                .findFirst()
                .map(opp -> ResponseEntity.ok(ApiResponse.ok(bountyHunterSkill.evaluateROI(opp))))
                .orElse(ResponseEntity.status(404).body(ApiResponse.err("not_found", "Opportunity not found: " + opportunityId)));
    }

    // ==================== 收款系统 ====================

    @GetMapping("/payout/accounts")
    public ResponseEntity<ApiResponse<List<Object>>> getPayoutAccounts(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty()) return unauthorized();
        String employeeId = ctxOpt.get().getEmployeeId();
        return ResponseEntity.ok(ApiResponse.ok(
                payoutService.getAccountsByOwner(employeeId).stream().map(a -> (Object) a).toList()));
    }

    @PostMapping("/payout/accounts")
    public ResponseEntity<ApiResponse<Object>> createPayoutAccount(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody PayoutService.CreateAccountRequest request) {
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty()) return unauthorized();
        return ResponseEntity.ok(ApiResponse.ok(payoutService.createAccount(request)));
    }

    @GetMapping("/payout/history")
    public ResponseEntity<ApiResponse<List<Object>>> getPayoutHistory(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) Long from,
            @RequestParam(required = false) Long to) {
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty()) return unauthorized();
        String employeeId = ctxOpt.get().getEmployeeId();
        Instant fromInst = from != null ? Instant.ofEpochMilli(from) : Instant.now().minusSeconds(30 * 86400);
        Instant toInst = to != null ? Instant.ofEpochMilli(to) : Instant.now();
        return ResponseEntity.ok(ApiResponse.ok(
                payoutService.getPayoutHistory(employeeId, fromInst, toInst).stream().map(r -> (Object) r).toList()));
    }

    @GetMapping("/payout/summary")
    public ResponseEntity<ApiResponse<PayoutService.PayoutSummary>> getPayoutSummary(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty()) return unauthorized();
        String employeeId = ctxOpt.get().getEmployeeId();
        return ResponseEntity.ok(ApiResponse.ok(payoutService.getSummary(employeeId)));
    }

    @GetMapping("/payout/pending")
    public ResponseEntity<ApiResponse<BigDecimal>> getPendingPayoutAmount(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty()) return unauthorized();
        String employeeId = ctxOpt.get().getEmployeeId();
        return ResponseEntity.ok(ApiResponse.ok(payoutService.getPendingAmount(employeeId)));
    }

    // ==================== 账本查询 ====================

    @GetMapping("/ledger/balance")
    public ResponseEntity<ApiResponse<LedgerSummary>> getLedgerBalance(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty()) return unauthorized();
        String employeeId = ctxOpt.get().getEmployeeId();

        int balance = ledgerService.getBalance(employeeId);
        int totalEarned = ledgerService.getTotalEarned(employeeId);
        return ResponseEntity.ok(ApiResponse.ok(new LedgerSummary(employeeId, balance, totalEarned, Instant.now().toString())));
    }

    @GetMapping("/ledger/history")
    public ResponseEntity<ApiResponse<List<LedgerService.IncomeRecord>>> getLedgerHistory(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "20") int limit) {
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty()) return unauthorized();
        String employeeId = ctxOpt.get().getEmployeeId();
        return ResponseEntity.ok(ApiResponse.ok(ledgerService.getIncomeHistory(employeeId, limit)));
    }

    // ==================== 进化追踪 ====================

    @GetMapping("/evolution/tier")
    public ResponseEntity<ApiResponse<EvolutionTierDto>> getEvolutionTier(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty()) return unauthorized();
        String employeeId = ctxOpt.get().getEmployeeId();

        EvolutionTier tier = evolutionTracker.getCurrentTier(employeeId);
        int accumulatedFunds = evolutionTracker.getAccumulatedFunds(employeeId);
        return ResponseEntity.ok(ApiResponse.ok(new EvolutionTierDto(
                employeeId, tier.name(), tier.getName(), tier.getDescription(), accumulatedFunds, Instant.now().toString())));
    }

    // ==================== 总览 ====================

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<AutonomousOverview>> getOverview(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty()) return unauthorized();
        String employeeId = ctxOpt.get().getEmployeeId();

        int creditBalance = creditAccountService.getBalance(employeeId);
        int totalEarned = creditAccountService.getTotalEarned(employeeId);
        double performanceScore = creditAccountService.getPerformanceScore(employeeId);
        int ledgerBalance = ledgerService.getBalance(employeeId);
        EvolutionTier tier = evolutionTracker.getCurrentTier(employeeId);
        int accumulatedFunds = evolutionTracker.getAccumulatedFunds(employeeId);
        int activeHunts = bountyHunterSkill.getActiveHunts().size();
        int discoveredOpps = bountyHunterSkill.getDiscoveredOpportunities().size();
        BigDecimal pendingPayout = payoutService.getPendingAmount(employeeId);
        PayoutService.PayoutSummary payoutSummary = payoutService.getSummary(employeeId);

        return ResponseEntity.ok(ApiResponse.ok(new AutonomousOverview(
                creditBalance, totalEarned, performanceScore, ledgerBalance,
                tier.name(), tier.getName(), accumulatedFunds,
                activeHunts, discoveredOpps, pendingPayout,
                payoutSummary.totalCollected(), payoutSummary.successfulPayouts(),
                Instant.now().toString())));
    }

    // ==================== 私有方法 ====================

    private Optional<AuthContext> getAuthContext(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) return Optional.empty();
        String sessionId = authorization.substring(7);
        return authService.validateSession(sessionId).map(AuthSession::authContext);
    }

    private <T> ResponseEntity<ApiResponse<T>> unauthorized() {
        return ResponseEntity.status(401).body(ApiResponse.err("unauthorized", "请先登录"));
    }

    private <T> ResponseEntity<ApiResponse<T>> forbidden() {
        return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
    }

    // ==================== DTO Records ====================

    public record DiscoverRequest(Boolean scanGitHub, Boolean scanFreelance, Boolean scanBugBounty) {}

    public record LedgerSummary(String employeeId, int balance, int totalEarned, String queriedAt) {}

    public record EvolutionTierDto(
            String employeeId, String tier, String tierName, String description,
            int accumulatedFunds, String queriedAt) {}

    public record AutonomousOverview(
            int creditBalance, int totalEarned, double performanceScore, int ledgerBalance,
            String tier, String tierName, int accumulatedFunds,
            int activeHunts, int discoveredOpportunities, BigDecimal pendingPayout,
            BigDecimal totalCollected, int successfulPayouts, String generatedAt) {}
}
