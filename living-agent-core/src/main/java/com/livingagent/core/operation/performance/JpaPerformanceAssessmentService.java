package com.livingagent.core.operation.performance;

import com.livingagent.core.database.entity.PerformanceAssessmentEntity;
import com.livingagent.core.database.entity.PerformanceIndicatorEntity;
import com.livingagent.core.database.entity.PerformanceTrendSnapshotEntity;
import com.livingagent.core.database.repository.PerformanceAssessmentRepository;
import com.livingagent.core.database.repository.PerformanceIndicatorRepository;
import com.livingagent.core.database.repository.PerformanceTrendRepository;
import com.livingagent.core.employee.Employee;
import com.livingagent.core.employee.EmployeeCompensationService;
import com.livingagent.core.employee.EmployeeService;
import com.livingagent.core.employee.EmployeeStatus;
import com.livingagent.core.ops.scheduler.TaskCheckout;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@Primary
public class JpaPerformanceAssessmentService implements PerformanceAssessmentService {

    private final EmployeeService employeeService;
    private final TaskCheckout taskCheckout;
    private final EmployeeCompensationService compensationService;
    private final PerformanceAssessmentRepository assessmentRepository;
    private final PerformanceIndicatorRepository indicatorRepository;
    private final PerformanceTrendRepository trendRepository;

    public JpaPerformanceAssessmentService(EmployeeService employeeService,
                                           TaskCheckout taskCheckout,
                                           EmployeeCompensationService compensationService,
                                           PerformanceAssessmentRepository assessmentRepository,
                                           PerformanceIndicatorRepository indicatorRepository,
                                           PerformanceTrendRepository trendRepository) {
        this.employeeService = employeeService;
        this.taskCheckout = taskCheckout;
        this.compensationService = compensationService;
        this.assessmentRepository = assessmentRepository;
        this.indicatorRepository = indicatorRepository;
        this.trendRepository = trendRepository;
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

        PerformanceAssessmentEntity entity = PerformanceAssessmentEntity.fromDomain(assessment);
        assessmentRepository.save(entity);
        trendRepository.save(toTrendSnapshot(entity));
        return assessment;
    }

    @Override
    public PerformanceAssessment assessEmployee(String employeeId, LocalDate startDate, LocalDate endDate) {
        return assessEmployee(employeeId, PerformanceAssessment.AssessmentPeriod.MONTHLY);
    }

    @Override
    public Optional<PerformanceAssessment> getAssessment(String assessmentId) {
        return assessmentRepository.findByAssessmentId(assessmentId).map(PerformanceAssessmentEntity::toDomain);
    }

    @Override
    public List<PerformanceAssessment> getEmployeeAssessments(String employeeId) {
        return assessmentRepository.findByEmployeeIdOrderByAssessedAtDesc(employeeId).stream()
                .map(PerformanceAssessmentEntity::toDomain)
                .toList();
    }

    @Override
    public List<PerformanceAssessment> getEmployeeAssessments(String employeeId, PerformanceAssessment.AssessmentPeriod period, int limit) {
        return assessmentRepository.findByEmployeeIdAndPeriodTypeOrderByAssessedAtDesc(employeeId, period.name()).stream()
                .limit(limit)
                .map(PerformanceAssessmentEntity::toDomain)
                .toList();
    }

    @Override
    public List<PerformanceAssessment> getDepartmentAssessments(String departmentId, PerformanceAssessment.AssessmentPeriod period) {
        return employeeService.listEmployees(new EmployeeService.EmployeeQuery(null, departmentId, null, null, 1000, 0))
                .stream()
                .map(employee -> assessEmployee(employee.getEmployeeId(), period))
                .toList();
    }

    @Override
    public Map<String, Double> getDepartmentAverageScores(String departmentId) {
        List<PerformanceAssessment> assessments = getDepartmentAssessments(departmentId, PerformanceAssessment.AssessmentPeriod.MONTHLY);
        if (assessments.isEmpty()) {
            return Map.of("overall", 0.0, "taskCompletion", 0.0, "quality", 0.0, "efficiency", 0.0, "collaboration", 0.0);
        }
        double overall = assessments.stream().mapToDouble(PerformanceAssessment::getOverallScore).average().orElse(0.0);
        return Map.of(
                "overall", overall,
                "taskCompletion", overall,
                "quality", overall,
                "efficiency", overall,
                "collaboration", overall
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
        List<PerformanceTrendSnapshotEntity> snapshots = trendRepository.findByEmployeeIdOrderByDateDesc(employeeId);
        List<TrendPoint> points = snapshots.stream()
                .limit(periods)
                .sorted(Comparator.comparing(PerformanceTrendSnapshotEntity::getDate))
                .map(snapshot -> new TrendPoint(
                        snapshot.getDate(),
                        snapshot.getScore() != null ? snapshot.getScore() : 0.0,
                        snapshot.getGrade() != null ? PerformanceAssessment.PerformanceGrade.valueOf(snapshot.getGrade()) : PerformanceAssessment.PerformanceGrade.GOOD
                ))
                .toList();
        double average = points.stream().mapToDouble(TrendPoint::score).average().orElse(0.0);
        return new PerformanceTrend(employeeId, points, average, 0.0, "persisted trend");
    }

    @Override
    public void defineIndicator(IndicatorDefinition definition) {
        PerformanceIndicatorEntity entity = new PerformanceIndicatorEntity();
        entity.setIndicatorId(definition.indicatorId());
        entity.setName(definition.name());
        entity.setDescription(definition.description());
        entity.setCategory(definition.category() != null ? definition.category().name() : null);
        entity.setWeight(definition.weight());
        entity.setTargetValue(definition.targetValue());
        entity.setCalculationMethod(definition.calculationMethod());
        entity.setEnabled(true);
        indicatorRepository.save(entity);
    }

    @Override
    public void setIndicatorWeight(String indicatorId, double weight) {
        indicatorRepository.findByIndicatorId(indicatorId).ifPresent(indicator -> {
            indicator.setWeight(weight);
            indicatorRepository.save(indicator);
        });
    }

    @Override
    public Map<String, PerformanceIndicator> getDefinedIndicators() {
        return indicatorRepository.findByEnabledTrueOrderByUpdatedAtDesc().stream()
                .map(this::toIndicator)
                .collect(Collectors.toMap(PerformanceIndicator::getIndicatorId, indicator -> indicator, (a, b) -> a, LinkedHashMap::new));
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

    private PerformanceTrendSnapshotEntity toTrendSnapshot(PerformanceAssessmentEntity assessment) {
        PerformanceTrendSnapshotEntity snapshot = new PerformanceTrendSnapshotEntity();
        snapshot.setEmployeeId(assessment.getEmployeeId());
        snapshot.setDate(assessment.getAssessedAt() != null ? assessment.getAssessedAt().atZone(ZoneId.systemDefault()).toLocalDate() : LocalDate.now());
        snapshot.setScore(assessment.getOverallScore());
        snapshot.setGrade(assessment.getGrade());
        snapshot.setPeriod(assessment.getPeriodType());
        return snapshot;
    }

    private PerformanceIndicator toIndicator(PerformanceIndicatorEntity entity) {
        return new PerformanceIndicator() {
            @Override public String getIndicatorId() { return entity.getIndicatorId(); }
            @Override public String getName() { return entity.getName(); }
            @Override public String getDescription() { return entity.getDescription(); }
            @Override public IndicatorCategory getCategory() { return entity.getCategory() != null ? IndicatorCategory.valueOf(entity.getCategory()) : IndicatorCategory.TASK_COMPLETION; }
            @Override public double getWeight() { return entity.getWeight() != null ? entity.getWeight() : 0.0; }
            @Override public double getTargetValue() { return entity.getTargetValue() != null ? entity.getTargetValue() : 0.0; }
            @Override public double getActualValue() { return entity.getTargetValue() != null ? entity.getTargetValue() : 0.0; }
            @Override public double getScore() { return 0.0; }
            @Override public double getAchievementRate() { return 0.0; }
            @Override public Map<String, Object> getDetails() { return Map.of("enabled", entity.getEnabled(), "method", entity.getCalculationMethod()); }
        };
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
}
