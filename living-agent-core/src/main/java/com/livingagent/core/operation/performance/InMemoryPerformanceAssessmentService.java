package com.livingagent.core.operation.performance;

import com.livingagent.core.employee.Employee;
import com.livingagent.core.employee.EmployeeCompensationService;
import com.livingagent.core.employee.EmployeeService;
import com.livingagent.core.employee.EmployeeStatus;
import com.livingagent.core.ops.scheduler.TaskCheckout;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Profile("dev")
public class InMemoryPerformanceAssessmentService implements PerformanceAssessmentService {

    private final EmployeeService employeeService;
    private final TaskCheckout taskCheckout;
    private final EmployeeCompensationService compensationService;

    private final Map<String, PerformanceAssessment> assessments = new ConcurrentHashMap<>();
    private final Map<String, PerformanceIndicator> indicators = new ConcurrentHashMap<>();

    public InMemoryPerformanceAssessmentService(EmployeeService employeeService,
                                                TaskCheckout taskCheckout,
                                                EmployeeCompensationService compensationService) {
        this.employeeService = employeeService;
        this.taskCheckout = taskCheckout;
        this.compensationService = compensationService;
    }

    @Override
    public PerformanceAssessment assessEmployee(String employeeId, PerformanceAssessment.AssessmentPeriod period) {
        double taskScore = Math.min(100.0, taskCheckout.getStatistics().completedCount() * 10.0 + 50.0);
        double balanceScore = Math.min(100.0, Math.max(0, compensationService.getBalance(employeeId)) + 50.0);
        double statusScore = employeeService.getEmployee(employeeId)
                .map(e -> e.getStatus() == EmployeeStatus.ACTIVE ? 100.0 : 60.0)
                .orElse(50.0);
        double overall = (taskScore * 0.45) + (balanceScore * 0.25) + (statusScore * 0.30);

        Map<String, Double> dims = new LinkedHashMap<>();
        dims.put("taskCompletion", taskScore);
        dims.put("balance", balanceScore);
        dims.put("status", statusScore);

        PerformanceAssessment assessment = new SimpleAssessment(
                "assessment_" + System.currentTimeMillis(),
                employeeId,
                employeeService.getEmployee(employeeId).map(Employee::getName).orElse("Employee"),
                period,
                overall,
                dims,
                List.of(),
                "Auto-generated assessment",
                Instant.now()
        );
        assessments.put(assessment.getAssessmentId(), assessment);
        return assessment;
    }

    @Override
    public PerformanceAssessment assessEmployee(String employeeId, LocalDate startDate, LocalDate endDate) {
        return assessEmployee(employeeId, PerformanceAssessment.AssessmentPeriod.MONTHLY);
    }

    @Override
    public Optional<PerformanceAssessment> getAssessment(String assessmentId) {
        return Optional.ofNullable(assessments.get(assessmentId));
    }

    @Override
    public List<PerformanceAssessment> getEmployeeAssessments(String employeeId) {
        return assessments.values().stream().filter(a -> employeeId.equals(a.getEmployeeId())).toList();
    }

    @Override
    public List<PerformanceAssessment> getEmployeeAssessments(String employeeId, PerformanceAssessment.AssessmentPeriod period, int limit) {
        return getEmployeeAssessments(employeeId).stream().filter(a -> a.getPeriod() == period).limit(limit).toList();
    }

    @Override
    public List<PerformanceAssessment> getDepartmentAssessments(String departmentId, PerformanceAssessment.AssessmentPeriod period) {
        return List.of();
    }

    @Override
    public Map<String, Double> getDepartmentAverageScores(String departmentId) {
        return Map.of(
                "overall", 80.0,
                "taskCompletion", 82.0,
                "quality", 79.0,
                "efficiency", 77.0,
                "collaboration", 81.0
        );
    }

    @Override
    public List<EmployeeRanking> getTopPerformers(String departmentId, int limit) {
        return buildRankings(departmentId, limit, true);
    }

    @Override
    public List<EmployeeRanking> getBottomPerformers(String departmentId, int limit) {
        return buildRankings(departmentId, limit, false);
    }

    @Override
    public PerformanceTrend getPerformanceTrend(String employeeId, int periods) {
        List<TrendPoint> points = new ArrayList<>();
        LocalDate now = LocalDate.now();
        for (int i = periods - 1; i >= 0; i--) {
            points.add(new TrendPoint(now.minusMonths(i), 75.0 + (i % 3), PerformanceAssessment.PerformanceGrade.GOOD));
        }
        return new PerformanceTrend(employeeId, points, 76.0, 0.2, "slightly improving");
    }

    @Override
    public void defineIndicator(IndicatorDefinition definition) {
        indicators.put(definition.indicatorId(), new SimpleIndicator(definition));
    }

    @Override
    public void setIndicatorWeight(String indicatorId, double weight) {
        PerformanceIndicator indicator = indicators.get(indicatorId);
        if (indicator instanceof SimpleIndicator simple) {
            simple.setWeight(weight);
        }
    }

    @Override
    public Map<String, PerformanceIndicator> getDefinedIndicators() {
        return Map.copyOf(indicators);
    }

    public List<EmployeeRanking> getCompanyTopPerformers(int limit) {
        return buildRankings(null, limit, true);
    }

    public List<EmployeeRanking> getCompanyBottomPerformers(int limit) {
        return buildRankings(null, limit, false);
    }

    private List<EmployeeRanking> buildRankings(String departmentId, int limit, boolean top) {
        List<EmployeeRanking> rankings = employeeService.listEmployees(new EmployeeService.EmployeeQuery(null, departmentId, null, null, 1000, 0))
                .stream()
                .map(employee -> {
                    PerformanceAssessment assessment = assessEmployee(employee.getEmployeeId(), PerformanceAssessment.AssessmentPeriod.MONTHLY);
                    return new EmployeeRanking(
                            0,
                            employee.getEmployeeId(),
                            employee.getName(),
                            assessment.getOverallScore(),
                            PerformanceAssessment.PerformanceGrade.fromScore(assessment.getOverallScore()),
                            0.0
                    );
                })
                .sorted(Comparator.comparingDouble(EmployeeRanking::score).reversed())
                .toList();

        List<EmployeeRanking> selected = new ArrayList<>();
        if (top) {
            for (int i = 0; i < rankings.size() && i < limit; i++) {
                EmployeeRanking r = rankings.get(i);
                selected.add(new EmployeeRanking(i + 1, r.employeeId(), r.employeeName(), r.score(), r.grade(), r.changeFromPrevious()));
            }
        } else {
            for (int i = rankings.size() - 1, rank = 1; i >= 0 && selected.size() < limit; i--, rank++) {
                EmployeeRanking r = rankings.get(i);
                selected.add(new EmployeeRanking(rank, r.employeeId(), r.employeeName(), r.score(), r.grade(), r.changeFromPrevious()));
            }
        }
        return selected;
    }

    private record SimpleAssessment(String assessmentId, String employeeId, String employeeName, AssessmentPeriod period, double overallScore, Map<String, Double> dimensionScores, List<PerformanceIndicator> indicators, String comment, Instant assessedAt) implements PerformanceAssessment {
        @Override public String getAssessmentId() { return assessmentId; }
        @Override public String getEmployeeId() { return employeeId; }
        @Override public String getEmployeeName() { return employeeName; }
        @Override public AssessmentPeriod getPeriod() { return period; }
        @Override public double getOverallScore() { return overallScore; }
        @Override public Map<String, Double> getDimensionScores() { return dimensionScores; }
        @Override public List<PerformanceIndicator> getIndicators() { return indicators; }
        @Override public String getGrade() { return PerformanceGrade.fromScore(overallScore).getCode(); }
        @Override public String getComment() { return comment; }
        @Override public Instant getAssessedAt() { return assessedAt; }
    }

    private static final class SimpleIndicatorDetails extends java.util.HashMap<String, Object> {
        SimpleIndicatorDetails(String indicatorId, String category, double weight, double targetValue) {
            put("indicatorId", indicatorId);
            put("category", category);
            put("weight", weight);
            put("targetValue", targetValue);
        }
    }

    private static class SimpleIndicator implements PerformanceIndicator {
        private final String id;
        private final String name;
        private final String description;
        private final IndicatorCategory category;
        private double weight;
        private final double targetValue;
        private double actualValue;
        private double score;

        SimpleIndicator(IndicatorDefinition definition) {
            this.id = definition.indicatorId();
            this.name = definition.name();
            this.description = definition.description();
            this.category = definition.category();
            this.weight = definition.weight();
            this.targetValue = definition.targetValue();
        }

        void setWeight(double weight) { this.weight = weight; }
        @Override public String getIndicatorId() { return id; }
        @Override public String getName() { return name; }
        @Override public String getDescription() { return description; }
        @Override public IndicatorCategory getCategory() { return category; }
        @Override public double getWeight() { return weight; }
        @Override public double getTargetValue() { return targetValue; }
        @Override public double getActualValue() { return actualValue; }
        @Override public double getScore() { return score; }
        @Override public double getAchievementRate() { return targetValue == 0 ? 0 : actualValue / targetValue; }
        @Override public Map<String, Object> getDetails() { return new SimpleIndicatorDetails(id, category.name(), weight, targetValue); }
    }
}
