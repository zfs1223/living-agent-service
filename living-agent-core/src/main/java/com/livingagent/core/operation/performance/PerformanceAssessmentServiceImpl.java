package com.livingagent.core.operation.performance;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * 绩效评估服务实现类
 */
@Service
public class PerformanceAssessmentServiceImpl implements PerformanceAssessmentService {

    @Override
    public PerformanceAssessment assessEmployee(String employeeId, PerformanceAssessment.AssessmentPeriod period) {
        // 简化实现：返回默认评估
        return createDefaultAssessment(employeeId, period);
    }

    @Override
    public PerformanceAssessment assessEmployee(String employeeId, LocalDate startDate, LocalDate endDate) {
        return createDefaultAssessment(employeeId, PerformanceAssessment.AssessmentPeriod.MONTHLY);
    }

    @Override
    public Optional<PerformanceAssessment> getAssessment(String assessmentId) {
        return Optional.empty();
    }

    @Override
    public List<PerformanceAssessment> getEmployeeAssessments(String employeeId) {
        return List.of();
    }

    @Override
    public List<PerformanceAssessment> getEmployeeAssessments(
            String employeeId,
            PerformanceAssessment.AssessmentPeriod period,
            int limit) {
        return List.of();
    }

    @Override
    public List<PerformanceAssessment> getDepartmentAssessments(
            String departmentId,
            PerformanceAssessment.AssessmentPeriod period) {
        return List.of();
    }

    @Override
    public Map<String, Double> getDepartmentAverageScores(String departmentId) {
        return Map.of(
            "overall", 75.0,
            "taskCompletion", 80.0,
            "quality", 78.0,
            "efficiency", 72.0,
            "collaboration", 76.0
        );
    }

    @Override
    public List<EmployeeRanking> getTopPerformers(String departmentId, int limit) {
        return List.of();
    }

    @Override
    public List<EmployeeRanking> getBottomPerformers(String departmentId, int limit) {
        return List.of();
    }

    @Override
    public PerformanceTrend getPerformanceTrend(String employeeId, int periods) {
        List<TrendPoint> points = new ArrayList<>();
        LocalDate now = LocalDate.now();
        
        for (int i = periods - 1; i >= 0; i--) {
            points.add(new TrendPoint(
                now.minusMonths(i),
                70.0 + Math.random() * 20,
                PerformanceAssessment.PerformanceGrade.GOOD
            ));
        }
        
        return new PerformanceTrend(
            employeeId,
            points,
            75.0,
            0.0,
            "Performance trend stable"
        );
    }

    @Override
    public void defineIndicator(IndicatorDefinition definition) {
        // 简化实现
    }

    @Override
    public void setIndicatorWeight(String indicatorId, double weight) {
        // 简化实现
    }

    @Override
    public Map<String, PerformanceIndicator> getDefinedIndicators() {
        return Map.of();
    }

    private PerformanceAssessment createDefaultAssessment(String employeeId, PerformanceAssessment.AssessmentPeriod period) {
        return new PerformanceAssessmentImpl(
            "assessment_" + System.currentTimeMillis(),
            employeeId,
            "Employee",
            period,
            75.0,
            Map.of(
                "taskCompletion", 80.0,
                "quality", 78.0,
                "efficiency", 72.0,
                "collaboration", 76.0
            ),
            List.of(),
            "Default assessment",
            Instant.now()
        );
    }

    /**
     * PerformanceAssessment 的简单实现类
     */
    private record PerformanceAssessmentImpl(
            String assessmentId,
            String employeeId,
            String employeeName,
            PerformanceAssessment.AssessmentPeriod period,
            double overallScore,
            Map<String, Double> dimensionScores,
            List<PerformanceIndicator> indicators,
            String comment,
            Instant assessedAt
    ) implements PerformanceAssessment {

        @Override
        public String getAssessmentId() { return assessmentId; }

        @Override
        public String getEmployeeId() { return employeeId; }

        @Override
        public String getEmployeeName() { return employeeName; }

        @Override
        public AssessmentPeriod getPeriod() { return period; }

        @Override
        public double getOverallScore() { return overallScore; }

        @Override
        public Map<String, Double> getDimensionScores() { return dimensionScores; }

        @Override
        public List<PerformanceIndicator> getIndicators() { return indicators; }

        @Override
        public String getGrade() { return PerformanceGrade.fromScore(overallScore).getCode(); }

        @Override
        public String getComment() { return comment; }

        @Override
        public Instant getAssessedAt() { return assessedAt; }
    }
}
