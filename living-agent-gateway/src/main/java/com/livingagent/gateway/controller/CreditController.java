package com.livingagent.gateway.controller;

import com.livingagent.core.autonomous.incentive.CreditAccountService;
import com.livingagent.core.autonomous.incentive.IncentiveManager;
import com.livingagent.core.security.AccessGateService;
import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.security.AuthContext;
import com.livingagent.gateway.controller.common.ApiResponse;
import com.livingagent.core.security.auth.UnifiedAuthService;
import com.livingagent.core.security.auth.UnifiedAuthService.AuthSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 积分账户控制器
 * 提供积分查询、历史记录、排行榜等功能
 */
@RestController
@RequestMapping("/api/credits")
public class CreditController {

    private static final Logger log = LoggerFactory.getLogger(CreditController.class);

    private final CreditAccountService creditAccountService;
    private final IncentiveManager incentiveManager;
    private final UnifiedAuthService authService;
    private final AccessGateService accessGateService;

    public CreditController(
            CreditAccountService creditAccountService,
            IncentiveManager incentiveManager,
            UnifiedAuthService authService,
            AccessGateService accessGateService) {
        this.creditAccountService = creditAccountService;
        this.incentiveManager = incentiveManager;
        this.authService = authService;
        this.accessGateService = accessGateService;
    }

    /**
     * 获取当前用户的积分余额
     */
    @GetMapping("/balance")
    public ResponseEntity<ApiResponse<CreditBalanceDto>> getMyBalance(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty()) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.err("unauthorized", "请先登录"));
        }

        AuthContext ctx = ctxOpt.get();
        String employeeId = ctx.getEmployeeId();

        if (!accessGateService.canRoute(employeeId, "brain", "FinanceBrain")) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        
        try {
            int balance = creditAccountService.getBalance(employeeId);
            int totalEarned = creditAccountService.getTotalEarned(employeeId);
            double performanceScore = creditAccountService.getPerformanceScore(employeeId);
            
            CreditBalanceDto result = new CreditBalanceDto(
                employeeId,
                ctx.getName(),
                balance,
                totalEarned,
                performanceScore,
                Instant.now().toString()
            );
            
            return ResponseEntity.ok(ApiResponse.ok(result));
        } catch (Exception e) {
            log.error("Failed to get balance for employee {}: {}", employeeId, e.getMessage());
            return ResponseEntity.ok(ApiResponse.ok(
                new CreditBalanceDto(employeeId, ctx.getName(), 0, 0, 0.0, Instant.now().toString())
            ));
        }
    }

    /**
     * 获取指定员工的积分余额（需要权限）
     */
    @GetMapping("/balance/{employeeId}")
    public ResponseEntity<ApiResponse<CreditBalanceDto>> getEmployeeBalance(
            @PathVariable String employeeId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Employee-Id", required = false) String headerEmployeeId) {
        
        if (headerEmployeeId != null && !headerEmployeeId.isBlank() && !accessGateService.canRoute(headerEmployeeId, "brain", "FinanceBrain")) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty()) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.err("unauthorized", "请先登录"));
        }

        AuthContext ctx = ctxOpt.get();
        
        // 权限检查
        if (!canViewEmployee(ctx, employeeId)) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.err("forbidden", "无权查看该员工的积分"));
        }

        try {
            int balance = creditAccountService.getBalance(employeeId);
            int totalEarned = creditAccountService.getTotalEarned(employeeId);
            double performanceScore = creditAccountService.getPerformanceScore(employeeId);
            
            CreditBalanceDto result = new CreditBalanceDto(
                employeeId,
                "Employee", // 实际应该查询员工名称
                balance,
                totalEarned,
                performanceScore,
                Instant.now().toString()
            );
            
            return ResponseEntity.ok(ApiResponse.ok(result));
        } catch (Exception e) {
            log.error("Failed to get balance for employee {}: {}", employeeId, e.getMessage());
            return ResponseEntity.ok(ApiResponse.ok(
                new CreditBalanceDto(employeeId, "Unknown", 0, 0, 0.0, Instant.now().toString())
            ));
        }
    }

    /**
     * 获取积分交易历史
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<CreditHistoryDto>> getMyTransactionHistory(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false, defaultValue = "20") int limit,
            @RequestHeader(value = "X-Employee-Id", required = false) String headerEmployeeId) {
        
        if (headerEmployeeId != null && !headerEmployeeId.isBlank() && !accessGateService.canRoute(headerEmployeeId, "brain", "FinanceBrain")) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty()) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.err("unauthorized", "请先登录"));
        }

        AuthContext ctx = ctxOpt.get();
        String employeeId = ctx.getEmployeeId();
        
        try {
            List<CreditAccountService.CreditTransaction> transactions = 
                creditAccountService.getTransactionHistory(employeeId, limit);
            
            List<TransactionDto> transactionDtos = transactions.stream()
                .map(this::toTransactionDto)
                .collect(Collectors.toList());
            
            CreditHistoryDto result = new CreditHistoryDto(
                employeeId,
                transactionDtos,
                transactions.size(),
                Instant.now().toString()
            );
            
            return ResponseEntity.ok(ApiResponse.ok(result));
        } catch (Exception e) {
            log.error("Failed to get history for employee {}: {}", employeeId, e.getMessage());
            return ResponseEntity.ok(ApiResponse.ok(
                new CreditHistoryDto(employeeId, List.of(), 0, Instant.now().toString())
            ));
        }
    }

    /**
     * 获取积分排行榜
     */
    @GetMapping("/leaderboard")
    public ResponseEntity<ApiResponse<LeaderboardDto>> getLeaderboard(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String departmentId,
            @RequestParam(required = false, defaultValue = "10") int limit,
            @RequestHeader(value = "X-Employee-Id", required = false) String headerEmployeeId) {
        
        if (headerEmployeeId != null && !headerEmployeeId.isBlank() && !accessGateService.canRoute(headerEmployeeId, "brain", "FinanceBrain")) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty()) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.err("unauthorized", "请先登录"));
        }

        AuthContext ctx = ctxOpt.get();
        String targetDept = departmentId != null ? departmentId : ctx.getDepartment();
        
        // 董事长可以查看全公司
        if (departmentId == null && ctx.getAccessLevel() == AccessLevel.FULL) {
            targetDept = null;
        }

        try {
            List<String> employees;
            if (targetDept != null) {
                employees = creditAccountService.getEmployeesByDepartment(targetDept);
            } else {
                // 全公司：简化处理，返回空列表
                employees = List.of();
            }
            
            // 构建排行榜
            List<LeaderboardItemDto> items = employees.stream()
                .map(empId -> {
                    int balance = creditAccountService.getBalance(empId);
                    double score = creditAccountService.getPerformanceScore(empId);
                    return new LeaderboardItemDto(
                        empId,
                        "Employee", // 实际应该查询员工名称
                        balance,
                        score,
                        0 // 排名稍后计算
                    );
                })
                .sorted(Comparator.comparingInt(LeaderboardItemDto::balance).reversed())
                .limit(limit)
                .collect(Collectors.toList());
            
            // 重新计算排名
            List<LeaderboardItemDto> rankedItems = new java.util.ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                LeaderboardItemDto item = items.get(i);
                rankedItems.add(new LeaderboardItemDto(
                    item.employeeId(),
                    item.employeeName(),
                    item.balance(),
                    item.performanceScore(),
                    i + 1
                ));
            }
            
            LeaderboardDto result = new LeaderboardDto(
                targetDept,
                rankedItems,
                Instant.now().toString()
            );
            
            return ResponseEntity.ok(ApiResponse.ok(result));
        } catch (Exception e) {
            log.error("Failed to get leaderboard: {}", e.getMessage());
            return ResponseEntity.ok(ApiResponse.ok(
                new LeaderboardDto(targetDept, List.of(), Instant.now().toString())
            ));
        }
    }

    /**
     * 获取账户统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<CreditStatsDto>> getMyStats(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Employee-Id", required = false) String headerEmployeeId) {
        
        if (headerEmployeeId != null && !headerEmployeeId.isBlank() && !accessGateService.canRoute(headerEmployeeId, "brain", "FinanceBrain")) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty()) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.err("unauthorized", "请先登录"));
        }

        AuthContext ctx = ctxOpt.get();
        String employeeId = ctx.getEmployeeId();
        
        try {
            Map<String, Object> stats = creditAccountService.getAccountStats(employeeId);
            
            CreditStatsDto result = new CreditStatsDto(
                employeeId,
                stats.getOrDefault("totalTransactions", 0),
                stats.getOrDefault("creditsThisMonth", 0),
                stats.getOrDefault("creditsThisWeek", 0),
                stats.getOrDefault("averagePerTask", 0.0),
                stats.getOrDefault("tasksCompleted", 0),
                Instant.now().toString()
            );
            
            return ResponseEntity.ok(ApiResponse.ok(result));
        } catch (Exception e) {
            log.error("Failed to get stats for employee {}: {}", employeeId, e.getMessage());
            return ResponseEntity.ok(ApiResponse.ok(
                new CreditStatsDto(employeeId, 0, 0, 0, 0.0, 0, Instant.now().toString())
            ));
        }
    }

    // ==================== 私有方法 ====================

    private Optional<AuthContext> getAuthContext(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return Optional.empty();
        }
        
        String sessionId = authorization.substring(7);
        Optional<AuthSession> sessionOpt = authService.validateSession(sessionId);
        
        return sessionOpt.map(AuthSession::authContext);
    }

    private boolean canViewEmployee(AuthContext ctx, String employeeId) {
        if (employeeId.equals(ctx.getEmployeeId())) {
            return true;
        }
        if (ctx.getAccessLevel() == AccessLevel.FULL) {
            return true;
        }
        return false;
    }

    private TransactionDto toTransactionDto(CreditAccountService.CreditTransaction tx) {
        return new TransactionDto(
            tx.transactionId(),
            tx.type(),
            tx.amountCents(),
            tx.description(),
            tx.relatedTaskId(),
            tx.createdAt().toString()
        );
    }

    // ==================== DTO Records ====================

    public record CreditBalanceDto(
            String employeeId,
            String employeeName,
            int balance,
            int totalEarned,
            double performanceScore,
            String queriedAt
    ) {}

    public record CreditHistoryDto(
            String employeeId,
            List<TransactionDto> transactions,
            int totalCount,
            String queriedAt
    ) {}

    public record TransactionDto(
            String transactionId,
            String type,
            int amountCents,
            String description,
            String relatedTaskId,
            String createdAt
    ) {}

    public record LeaderboardDto(
            String departmentId,
            List<LeaderboardItemDto> items,
            String generatedAt
    ) {}

    public record LeaderboardItemDto(
            String employeeId,
            String employeeName,
            int balance,
            double performanceScore,
            int rank
    ) {}

    public record CreditStatsDto(
            String employeeId,
            Object totalTransactions,
            Object creditsThisMonth,
            Object creditsThisWeek,
            Object averagePerTask,
            Object tasksCompleted,
            String generatedAt
    ) {}
}
