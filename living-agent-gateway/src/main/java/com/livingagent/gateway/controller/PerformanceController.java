package com.livingagent.gateway.controller;

import com.livingagent.core.operation.performance.PerformanceAssessment;
import com.livingagent.core.operation.performance.PerformanceAssessmentService;
import com.livingagent.core.operation.performance.PerformanceIndicator;
import com.livingagent.core.security.AccessGateService;
import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.security.AuthContext;
import com.livingagent.core.security.auth.UnifiedAuthService;
import com.livingagent.core.security.auth.UnifiedAuthService.AuthSession;
import com.livingagent.gateway.service.PerformanceDashboardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/performance")
public class PerformanceController {

    private static final Logger log = LoggerFactory.getLogger(PerformanceController.class);

    private final PerformanceAssessmentService assessmentService;
    private final UnifiedAuthService authService;
    private final AccessGateService accessGateService;
    private final PerformanceDashboardService dashboardService;

    public PerformanceController(
            PerformanceAssessmentService assessmentService,
            UnifiedAuthService authService,
            AccessGateService accessGateService,
            PerformanceDashboardService dashboardService) {
        this.assessmentService = assessmentService;
        this.authService = authService;
        this.accessGateService = accessGateService;
        this.dashboardService = dashboardService;
    }

    @GetMapping("/my-assessment")
    public ResponseEntity<ApiResponse<AssessmentDto>> getMyAssessment(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false, defaultValue = "MONTHLY") String period,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.error("forbidden", "Access denied before routing"));
        }
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty()) {
            return ResponseEntity.status(401).body(ApiResponse.error("unauthorized", "请先登录"));
        }

        AuthContext ctx = ctxOpt.get();
        String currentEmployeeId = ctx.getEmployeeId();
        if (!accessGateService.canRoute(currentEmployeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.error("forbidden", "Access denied before routing"));
        }

        try {
            PerformanceAssessment.AssessmentPeriod assessmentPeriod = PerformanceAssessment.AssessmentPeriod.valueOf(period.toUpperCase());
            PerformanceAssessment assessment = assessmentService.assessEmployee(currentEmployeeId, assessmentPeriod);
            return ResponseEntity.ok(ApiResponse.success(toDto(assessment)));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.success(createEmptyAssessment(currentEmployeeId, ctx.getName())));
        }
    }

    @GetMapping("/assessments/{employeeId}")
    public ResponseEntity<ApiResponse<AssessmentDto>> getEmployeeAssessment(
            @PathVariable String employeeId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false, defaultValue = "MONTHLY") String period,
            @RequestHeader(value = "X-Employee-Id", required = false) String headerEmployeeId) {
        if (headerEmployeeId != null && !headerEmployeeId.isBlank() && !accessGateService.canRoute(headerEmployeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.error("forbidden", "Access denied before routing"));
        }
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty()) {
            return ResponseEntity.status(401).body(ApiResponse.error("unauthorized", "请先登录"));
        }

        AuthContext ctx = ctxOpt.get();
        if (!accessGateService.canRoute(ctx.getEmployeeId(), "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.error("forbidden", "Access denied before routing"));
        }
        if (!canViewEmployee(ctx, employeeId)) {
            return ResponseEntity.status(403).body(ApiResponse.error("forbidden", "无权查看该员工的绩效"));
        }

        try {
            PerformanceAssessment.AssessmentPeriod assessmentPeriod = PerformanceAssessment.AssessmentPeriod.valueOf(period.toUpperCase());
            PerformanceAssessment assessment = assessmentService.assessEmployee(employeeId, assessmentPeriod);
            return ResponseEntity.ok(ApiResponse.success(toDto(assessment)));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.success(createEmptyAssessment(employeeId, "Unknown")));
        }
    }

    @GetMapping("/rankings")
    public ResponseEntity<ApiResponse<RankingListDto>> getRankings(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String departmentId,
            @RequestParam(required = false, defaultValue = "10") int limit,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.error("forbidden", "Access denied before routing"));
        }
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty()) {
            return ResponseEntity.status(401).body(ApiResponse.error("unauthorized", "请先登录"));
        }

        AuthContext ctx = ctxOpt.get();
        if (!accessGateService.canRoute(ctx.getEmployeeId(), "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.error("forbidden", "Access denied before routing"));
        }

        String targetDept = departmentId != null ? departmentId : ctx.getDepartment();
        if (departmentId == null && ctx.getAccessLevel() == AccessLevel.FULL) {
            targetDept = null;
        }

        try {
            List<PerformanceAssessmentService.EmployeeRanking> topPerformers = targetDept != null ? assessmentService.getTopPerformers(targetDept, limit) : getCompanyTopPerformers(limit);
            List<RankingItemDto> rankings = topPerformers.stream().map(this::toRankingDto).collect(Collectors.toList());
            RankingListDto result = new RankingListDto(targetDept, rankings, LocalDate.now().toString());
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.success(new RankingListDto(targetDept, List.of(), LocalDate.now().toString())));
        }
    }

    @GetMapping("/trends/{employeeId}")
    public ResponseEntity<ApiResponse<TrendDto>> getPerformanceTrend(
            @PathVariable String employeeId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false, defaultValue = "6") int periods,
            @RequestHeader(value = "X-Employee-Id", required = false) String headerEmployeeId) {
        if (headerEmployeeId != null && !headerEmployeeId.isBlank() && !accessGateService.canRoute(headerEmployeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.error("forbidden", "Access denied before routing"));
        }
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty()) {
            return ResponseEntity.status(401).body(ApiResponse.error("unauthorized", "请先登录"));
        }

        AuthContext ctx = ctxOpt.get();
        if (!accessGateService.canRoute(ctx.getEmployeeId(), "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.error("forbidden", "Access denied before routing"));
        }
        if (!canViewEmployee(ctx, employeeId)) {
            return ResponseEntity.status(403).body(ApiResponse.error("forbidden", "无权查看该员工的绩效趋势"));
        }

        try {
            PerformanceAssessmentService.PerformanceTrend trend = assessmentService.getPerformanceTrend(employeeId, periods);
            TrendDto result = new TrendDto(employeeId, trend.points().stream().map(p -> new TrendPointDto(p.date().toString(), p.score(), p.grade().getCode())).collect(Collectors.toList()), trend.averageScore(), trend.trendDirection(), trend.trendDescription());
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.success(createEmptyTrend(employeeId)));
        }
    }

    @GetMapping("/departments/{deptId}")
    public ResponseEntity<ApiResponse<DepartmentPerformanceDto>> getDepartmentPerformance(
            @PathVariable String deptId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.error("forbidden", "Access denied before routing"));
        }
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty()) {
            return ResponseEntity.status(401).body(ApiResponse.error("unauthorized", "请先登录"));
        }

        AuthContext ctx = ctxOpt.get();
        if (!accessGateService.canRoute(ctx.getEmployeeId(), "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.error("forbidden", "Access denied before routing"));
        }
        if (!canViewDepartment(ctx, deptId)) {
            return ResponseEntity.status(403).body(ApiResponse.error("forbidden", "无权查看该部门绩效"));
        }

        try {
            Map<String, Double> avgScores = assessmentService.getDepartmentAverageScores(deptId);
            DepartmentPerformanceDto result = new DepartmentPerformanceDto(deptId, avgScores.getOrDefault("overall", 0.0), avgScores, LocalDate.now().toString());
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.success(new DepartmentPerformanceDto(deptId, 0.0, Map.of(), LocalDate.now().toString())));
        }
    }

    @GetMapping("/company-rankings")
    public ResponseEntity<ApiResponse<RankingListDto>> getCompanyRankings(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false, defaultValue = "10") int limit,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.error("forbidden", "Access denied before routing"));
        }
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty()) {
            return ResponseEntity.status(401).body(ApiResponse.error("unauthorized", "请先登录"));
        }
        AuthContext ctx = ctxOpt.get();
        if (!accessGateService.canRoute(ctx.getEmployeeId(), "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.error("forbidden", "Access denied before routing"));
        }
        List<PerformanceAssessmentService.EmployeeRanking> top = getCompanyTopPerformers(limit);
        List<RankingItemDto> rankings = top.stream().map(this::toRankingDto).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(new RankingListDto(null, rankings, LocalDate.now().toString())));
    }

    @GetMapping("/company-bottom-rankings")
    public ResponseEntity<ApiResponse<RankingListDto>> getCompanyBottomRankings(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false, defaultValue = "10") int limit,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.error("forbidden", "Access denied before routing"));
        }
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty()) {
            return ResponseEntity.status(401).body(ApiResponse.error("unauthorized", "请先登录"));
        }
        AuthContext ctx = ctxOpt.get();
        if (!accessGateService.canRoute(ctx.getEmployeeId(), "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.error("forbidden", "Access denied before routing"));
        }
        List<PerformanceAssessmentService.EmployeeRanking> bottom = getCompanyBottomPerformers(limit);
        List<RankingItemDto> rankings = bottom.stream().map(this::toRankingDto).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(new RankingListDto(null, rankings, LocalDate.now().toString())));
    }

    private Optional<AuthContext> getAuthContext(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return Optional.empty();
        }
        String sessionId = authorization.substring(7);
        Optional<AuthSession> sessionOpt = authService.validateSession(sessionId);
        return sessionOpt.map(AuthSession::authContext);
    }

    private boolean canViewEmployee(AuthContext ctx, String employeeId) { return employeeId.equals(ctx.getEmployeeId()) || ctx.getAccessLevel() == AccessLevel.FULL; }
    private boolean canViewDepartment(AuthContext ctx, String deptId) { return ctx.getAccessLevel() == AccessLevel.FULL || deptId.equalsIgnoreCase(ctx.getDepartment()); }

    private List<PerformanceAssessmentService.EmployeeRanking> getCompanyTopPerformers(int limit) {
        if (assessmentService instanceof com.livingagent.core.operation.performance.InMemoryPerformanceAssessmentService memory) {
            return memory.getCompanyTopPerformers(limit);
        }
        return List.of();
    }

    private List<PerformanceAssessmentService.EmployeeRanking> getCompanyBottomPerformers(int limit) {
        if (assessmentService instanceof com.livingagent.core.operation.performance.InMemoryPerformanceAssessmentService memory) {
            return memory.getCompanyBottomPerformers(limit);
        }
        return List.of();
    }

    private AssessmentDto toDto(PerformanceAssessment assessment) { return new AssessmentDto(assessment.getAssessmentId(), assessment.getEmployeeId(), assessment.getEmployeeName(), assessment.getPeriod().name(), assessment.getOverallScore(), assessment.getGrade(), assessment.getDimensionScores(), assessment.getIndicators().stream().map(this::toIndicatorDto).collect(Collectors.toList()), assessment.getComment(), assessment.getAssessedAt().toString()); }
    private IndicatorDto toIndicatorDto(PerformanceIndicator indicator) { return new IndicatorDto(indicator.getIndicatorId(), indicator.getName(), indicator.getCategory().name(), indicator.getWeight(), indicator.getTargetValue(), indicator.getActualValue(), indicator.getScore(), indicator.getAchievementRate()); }
    private RankingItemDto toRankingDto(PerformanceAssessmentService.EmployeeRanking ranking) { return new RankingItemDto(ranking.rank(), ranking.employeeId(), ranking.employeeName(), ranking.score(), ranking.grade().getCode(), ranking.changeFromPrevious()); }
    private AssessmentDto createEmptyAssessment(String employeeId, String employeeName) { return new AssessmentDto("assessment_" + System.currentTimeMillis(), employeeId, employeeName, "MONTHLY", 0.0, "N/A", Map.of(), List.of(), "暂无绩效数据", java.time.Instant.now().toString()); }
    private TrendDto createEmptyTrend(String employeeId) { return new TrendDto(employeeId, List.of(), 0.0, 0.0, "暂无趋势数据"); }

    public record ApiResponse<T>(boolean success, T data, String error, String errorDescription) {
        public static <T> ApiResponse<T> success(T data) { return new ApiResponse<>(true, data, null, null); }
        public static <T> ApiResponse<T> error(String error, String description) { return new ApiResponse<>(false, null, error, description); }
    }
    public record AssessmentDto(String assessmentId, String employeeId, String employeeName, String period, double overallScore, String grade, Map<String, Double> dimensionScores, List<IndicatorDto> indicators, String comment, String assessedAt) {}
    public record IndicatorDto(String indicatorId, String name, String category, double weight, double targetValue, double actualValue, double score, double achievementRate) {}
    public record RankingItemDto(int rank, String employeeId, String employeeName, double score, String grade, double changeFromPrevious) {}
    public record RankingListDto(String departmentId, List<RankingItemDto> rankings, String generatedAt) {}
    public record TrendDto(String employeeId, List<TrendPointDto> points, double averageScore, double trendDirection, String trendDescription) {}
    public record TrendPointDto(String date, double score, String grade) {}
    public record DepartmentPerformanceDto(String departmentId, double overallAverage, Map<String, Double> dimensionAverages, String generatedAt) {}
}
