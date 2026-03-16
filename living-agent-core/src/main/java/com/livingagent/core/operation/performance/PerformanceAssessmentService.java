package com.livingagent.core.operation.performance;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PerformanceAssessmentService {

    PerformanceAssessment assessEmployee(String employeeId, PerformanceAssessment.AssessmentPeriod period);
    
    PerformanceAssessment assessEmployee(String employeeId, LocalDate startDate, LocalDate endDate);
    
    Optional<PerformanceAssessment> getAssessment(String assessmentId);
    
    List<PerformanceAssessment> getEmployeeAssessments(String employeeId);
    
    List<PerformanceAssessment> getEmployeeAssessments(
        String employeeId, 
        PerformanceAssessment.AssessmentPeriod period,
        int limit
    );
    
    List<PerformanceAssessment> getDepartmentAssessments(
        String departmentId,
        PerformanceAssessment.AssessmentPeriod period
    );
    
    Map<String, Double> getDepartmentAverageScores(String departmentId);
    
    List<EmployeeRanking> getTopPerformers(String departmentId, int limit);
    
    List<EmployeeRanking> getBottomPerformers(String departmentId, int limit);
    
    PerformanceTrend getPerformanceTrend(String employeeId, int periods);
    
    void defineIndicator(IndicatorDefinition definition);
    
    void setIndicatorWeight(String indicatorId, double weight);
    
    Map<String, PerformanceIndicator> getDefinedIndicators();
    
    record EmployeeRanking(
        int rank,
        String employeeId,
        String employeeName,
        double score,
        PerformanceAssessment.PerformanceGrade grade,
        double changeFromPrevious
    ) {}
    
    record PerformanceTrend(
        String employeeId,
        List<TrendPoint> points,
        double averageScore,
        double trendDirection,
        String trendDescription
    ) {}
    
    record TrendPoint(
        LocalDate date,
        double score,
        PerformanceAssessment.PerformanceGrade grade
    ) {}
    
    record IndicatorDefinition(
        String indicatorId,
        String name,
        String description,
        PerformanceIndicator.IndicatorCategory category,
        double weight,
        double targetValue,
        String calculationMethod
    ) {}
}
