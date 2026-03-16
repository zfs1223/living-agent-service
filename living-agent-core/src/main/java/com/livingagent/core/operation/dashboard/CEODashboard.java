package com.livingagent.core.operation.dashboard;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface CEODashboard {

    CompanyOverview getCompanyOverview();
    
    List<DepartmentMetrics> getDepartmentMetrics();
    
    List<EmployeePerformanceSummary> getTopPerformers(int limit);
    
    List<AlertItem> getActiveAlerts();
    
    List<TrendData> getPerformanceTrends(String metricName, int days);
    
    List<Recommendation> getAIRecommendations();
    
    Map<String, Object> generateReport(ReportRequest request);
    
    List<DepartmentRanking> getDepartmentRankings();
    
    ResourceUtilization getResourceUtilization();
    
    RiskAssessment getRiskAssessment();
    
    record CompanyOverview(
        int totalEmployees,
        int activeEmployees,
        int digitalEmployees,
        int humanEmployees,
        int totalDepartments,
        double overallEfficiency,
        double averageSuccessRate,
        long totalTasksCompleted,
        long totalTasksInProgress,
        Instant lastUpdated
    ) {}
    
    record DepartmentMetrics(
        String departmentId,
        String departmentName,
        int employeeCount,
        int activeCount,
        double efficiency,
        double successRate,
        long tasksCompleted,
        long tasksInProgress,
        double averageResponseTime,
        String status,
        double changeFromLastPeriod
    ) {}
    
    record EmployeePerformanceSummary(
        String employeeId,
        String employeeName,
        String department,
        double score,
        String grade,
        long tasksCompleted,
        double successRate,
        int rank,
        double changeFromPrevious
    ) {}
    
    record AlertItem(
        String alertId,
        String type,
        String severity,
        String title,
        String description,
        String department,
        String suggestedAction,
        Instant triggeredAt,
        boolean acknowledged
    ) {}
    
    record TrendData(
        String metricName,
        List<DataPoint> dataPoints,
        double trendDirection,
        String trendDescription
    ) {}
    
    record DataPoint(
        LocalDate date,
        double value,
        Map<String, Object> metadata
    ) {}
    
    record Recommendation(
        String recommendationId,
        String category,
        String title,
        String description,
        String impact,
        String priority,
        List<String> actionItems,
        Instant generatedAt
    ) {}
    
    record ReportRequest(
        String reportType,
        LocalDate startDate,
        LocalDate endDate,
        List<String> departments,
        List<String> metrics,
        String format
    ) {}
    
    record DepartmentRanking(
        int rank,
        String departmentId,
        String departmentName,
        double score,
        double efficiency,
        double successRate,
        double changeFromPrevious
    ) {}
    
    record ResourceUtilization(
        double cpuUtilization,
        double memoryUtilization,
        double storageUtilization,
        int activeNeurons,
        int idleNeurons,
        Map<String, Double> departmentUtilization
    ) {}
    
    record RiskAssessment(
        double overallRiskScore,
        List<RiskItem> risks,
        List<String> mitigationSuggestions
    ) {}
    
    record RiskItem(
        String riskId,
        String category,
        String description,
        double probability,
        double impact,
        double riskScore,
        String status
    ) {}
}
