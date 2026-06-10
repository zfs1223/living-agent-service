package com.livingagent.core.operation.dashboard;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class DashboardDTOs {

    public record EnterpriseSummary(
        Instant generatedAt,
        SystemHealth systemHealth,
        EmployeeMetrics employeeMetrics,
        TaskMetrics taskMetrics,
        CostAnalysis costAnalysis,
        List<DepartmentHealth> departmentHealth,
        List<RiskAlert> riskAlerts,
        List<StrategicSuggestion> strategicSuggestions
    ) {}

    public record SystemHealth(
        double healthScore,
        String status,
        int activeComponents,
        int totalComponents,
        List<ComponentStatus> components
    ) {}

    public record ComponentStatus(
        String name,
        String status,
        double healthScore
    ) {}

    public record EmployeeMetrics(
        int totalEmployees,
        int activeEmployees,
        int riskEmployees,
        double activationRate,
        int digitalEmployees,
        int humanEmployees
    ) {}

    public record TaskMetrics(
        int totalTasks,
        int pendingTasks,
        int completedToday,
        int failedTasks,
        double completionRate,
        long totalTokensToday
    ) {}

    public record CostAnalysis(
        BigDecimal totalCosts,
        BigDecimal internalCosts,
        BigDecimal externalBounties,
        BigDecimal pendingBounties,
        BigDecimal costPerTask,
        double outsourcingRate,
        List<CostBreakdown> breakdowns
    ) {}

    public record CostBreakdown(
        String category,
        BigDecimal amount,
        double percentage,
        String trend
    ) {}

    public record DepartmentHealth(
        String code,
        String name,
        int memberCount,
        int activeMembers,
        int todayTasks,
        long todayTokens,
        double healthScore,
        String status,
        int riskCount
    ) {}

    public record RiskAlert(
        String alertId,
        String level,
        String title,
        String message,
        String department,
        String employeeId,
        String impact,
        Instant detectedAt
    ) {}

    public record StrategicSuggestion(
        String suggestionId,
        String category,
        String title,
        String description,
        int priority,
        String action,
        Map<String, Object> context
    ) {}

    public record DepartmentSummary(
        String code,
        String name,
        int memberCount,
        int activeMembers,
        int todayTasks,
        double healthScore,
        String status,
        String brainCode
    ) {}

    public record WorkspaceSummary(
        String employeeId,
        String name,
        int pendingTasks,
        int completedTasks,
        List<MyTask> recentTasks,
        List<AccessibleAgent> accessibleAgents
    ) {}

    public record MyTask(
        String taskId,
        String title,
        String status,
        String priority,
        Instant createdAt
    ) {}

    public record AccessibleAgent(
        String id,
        String name,
        String status,
        String roleDescription
    ) {}
}
