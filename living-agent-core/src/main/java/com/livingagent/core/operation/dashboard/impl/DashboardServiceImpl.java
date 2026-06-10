package com.livingagent.core.operation.dashboard.impl;

import com.livingagent.core.brain.Brain;
import com.livingagent.core.brain.BrainRegistry;
import com.livingagent.core.database.entity.DepartmentEntity;
import com.livingagent.core.database.repository.DepartmentRepository;
import com.livingagent.core.diagnosis.HealthMonitor;
import com.livingagent.core.diagnosis.HealthIssue;
import com.livingagent.core.employee.Employee;
import com.livingagent.core.employee.EmployeeService;
import com.livingagent.core.employee.EmployeeStatus;
import com.livingagent.core.employee.registry.FixedEmployeeRegistry;
import com.livingagent.core.neuron.Neuron;
import com.livingagent.core.neuron.NeuronRegistry;
import com.livingagent.core.operation.dashboard.DashboardDTOs;
import com.livingagent.core.operation.dashboard.DashboardService;
import com.livingagent.core.ops.scheduler.TaskCheckout;
import com.livingagent.core.ops.scheduler.TaskCheckout.TaskStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DashboardServiceImpl implements DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardServiceImpl.class);

    private final EmployeeService employeeService;
    private final BrainRegistry brainRegistry;
    private final NeuronRegistry neuronRegistry;
    private final TaskCheckout taskCheckout;
    private final HealthMonitor healthMonitor;
    private final DepartmentRepository departmentRepository;
    private final FixedEmployeeRegistry fixedEmployeeRegistry;

    public DashboardServiceImpl(
            EmployeeService employeeService,
            BrainRegistry brainRegistry,
            NeuronRegistry neuronRegistry,
            TaskCheckout taskCheckout,
            HealthMonitor healthMonitor,
            DepartmentRepository departmentRepository,
            FixedEmployeeRegistry fixedEmployeeRegistry) {
        this.employeeService = employeeService;
        this.brainRegistry = brainRegistry;
        this.neuronRegistry = neuronRegistry;
        this.taskCheckout = taskCheckout;
        this.healthMonitor = healthMonitor;
        this.departmentRepository = departmentRepository;
        this.fixedEmployeeRegistry = fixedEmployeeRegistry;
    }

    @Override
    public DashboardDTOs.EnterpriseSummary getEnterpriseSummary(String employeeId) {
        return new DashboardDTOs.EnterpriseSummary(
                Instant.now(),
                buildSystemHealth(),
                buildEmployeeMetrics(),
                buildTaskMetrics(),
                buildCostAnalysis(),
                buildDepartmentHealth(),
                buildRiskAlerts(),
                buildStrategicSuggestions()
        );
    }

    @Override
    public List<DashboardDTOs.DepartmentHealth> getDepartmentHealth() {
        return buildDepartmentHealth();
    }

    @Override
    public List<DashboardDTOs.DepartmentSummary> getDepartmentSummaries() {
        return departmentRepository.findAll().stream()
                .map(this::toDepartmentSummary)
                .collect(Collectors.toList());
    }

    @Override
    public List<DashboardDTOs.RiskAlert> getRiskAlerts() {
        return buildRiskAlerts();
    }

    @Override
    public DashboardDTOs.CostAnalysis getCostAnalysis() {
        return buildCostAnalysis();
    }

    @Override
    public DashboardDTOs.DepartmentSummary getDepartmentSummary(String code) {
        return departmentRepository.findByCode(code)
                .map(this::toDepartmentSummary)
                .orElse(null);
    }

    @Override
    public DashboardDTOs.WorkspaceSummary getWorkspaceSummary(String employeeId) {
        TaskStatistics taskStats = taskCheckout.getStatistics();

        List<DashboardDTOs.MyTask> recentTasks = taskCheckout.getCompletedTasks(10).stream()
                .filter(t -> employeeId.equals(t.assignedTo()))
                .map(t -> new DashboardDTOs.MyTask(
                        t.taskId(),
                        t.description(),
                        t.status().name(),
                        String.valueOf(t.priority()),
                        t.createdAt()
                ))
                .collect(Collectors.toList());

        List<DashboardDTOs.AccessibleAgent> accessibleAgents = new ArrayList<>();
        if (brainRegistry != null) {
            brainRegistry.getAll().forEach(brain -> {
                accessibleAgents.add(new DashboardDTOs.AccessibleAgent(
                        brain.getClass().getSimpleName(),
                        brain.getClass().getSimpleName(),
                        "running",
                        "Department Brain"
                ));
            });
        }

        return new DashboardDTOs.WorkspaceSummary(
                employeeId,
                employeeService.getEmployee(employeeId).map(Employee::getName).orElse("Unknown"),
                taskStats.pendingCount(),
                taskStats.completedCount(),
                recentTasks,
                accessibleAgents
        );
    }

    private DashboardDTOs.SystemHealth buildSystemHealth() {
        var healthStatus = healthMonitor.checkHealth();
        var components = healthMonitor.getAllComponentStatus();
        var issues = healthMonitor.detectIssues();

        double healthScore = 100.0 - (issues.size() * 10.0);
        healthScore = Math.max(0, Math.min(100, healthScore));

        List<DashboardDTOs.ComponentStatus> componentStatuses = components.entrySet().stream()
                .map(e -> new DashboardDTOs.ComponentStatus(
                        e.getKey(),
                        e.getValue().getStatus().name(),
                        e.getValue().getScore()
                ))
                .collect(Collectors.toList());

        return new DashboardDTOs.SystemHealth(
                healthScore,
                healthStatus.getStatus().name(),
                (int) components.values().stream().filter(c -> c.getStatus() == com.livingagent.core.diagnosis.HealthStatus.Status.HEALTHY).count(),
                components.size(),
                componentStatuses
        );
    }

    private DashboardDTOs.EmployeeMetrics buildEmployeeMetrics() {
        var allEmployees = employeeService.listEmployees(new EmployeeService.EmployeeQuery(null, null, null, null, 1000, 0));
        var activeEmployees = employeeService.listEmployees(new EmployeeService.EmployeeQuery(null, null, EmployeeStatus.ACTIVE, null, 1000, 0));

        int total = allEmployees.size();
        int active = activeEmployees.size();
        int risk = (int) allEmployees.stream()
                .filter(e -> e.getStatus() == EmployeeStatus.BUSY || e.getStatus() == EmployeeStatus.OFFLINE)
                .count();

        int digital = (int) allEmployees.stream().filter(Employee::isDigital).count();
        int human = (int) allEmployees.stream().filter(Employee::isHuman).count();

        double activationRate = total > 0 ? (double) active / total * 100.0 : 0.0;

        return new DashboardDTOs.EmployeeMetrics(total, active, risk, activationRate, digital, human);
    }

    private DashboardDTOs.TaskMetrics buildTaskMetrics() {
        TaskStatistics stats = taskCheckout.getStatistics();
        var completedTasks = taskCheckout.getCompletedTasks(100);

        long completedToday = completedTasks.stream()
                .filter(t -> t.completedAt() != null &&
                        t.completedAt().isAfter(Instant.now().minusSeconds(86400)))
                .count();

        int totalTasks = stats.pendingCount() + stats.checkedOutCount() + stats.completedCount();
        double completionRate = totalTasks > 0 ? (double) stats.completedCount() / totalTasks * 100.0 : 0.0;

        return new DashboardDTOs.TaskMetrics(totalTasks, stats.pendingCount(), (int) completedToday, 0, completionRate, 0);
    }

    private DashboardDTOs.CostAnalysis buildCostAnalysis() {
        return new DashboardDTOs.CostAnalysis(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0.0,
                List.of()
        );
    }

    private List<DashboardDTOs.DepartmentHealth> buildDepartmentHealth() {
        return departmentRepository.findAll().stream()
                .map(dept -> {
                    String code = dept.getCode();
                    int memberCount = dept.getMemberCount();

                    var activeMembers = employeeService.listEmployees(
                            new EmployeeService.EmployeeQuery(null, code, EmployeeStatus.ACTIVE, null, 1000, 0));

                    var todayTasks = taskCheckout.getCompletedTasks(100).stream()
                            .filter(t -> t.completedAt() != null &&
                                    t.completedAt().isAfter(Instant.now().minusSeconds(86400)))
                            .count();

                    double healthScore = memberCount > 0 ? (double) activeMembers.size() / memberCount * 100.0 : 0;
                    String status = healthScore >= 80 ? "HEALTHY" : healthScore >= 50 ? "WARNING" : "CRITICAL";

                    return new DashboardDTOs.DepartmentHealth(
                            code,
                            dept.getName(),
                            memberCount,
                            activeMembers.size(),
                            (int) todayTasks,
                            0,
                            healthScore,
                            status,
                            0
                    );
                })
                .sorted(Comparator.comparingDouble(DashboardDTOs.DepartmentHealth::healthScore).reversed())
                .collect(Collectors.toList());
    }

    private List<DashboardDTOs.RiskAlert> buildRiskAlerts() {
        List<DashboardDTOs.RiskAlert> alerts = new ArrayList<>();
        var issues = healthMonitor.detectIssues();

        for (int i = 0; i < issues.size(); i++) {
            HealthIssue issue = issues.get(i);
            
            String level = mapSeverityToLevel(issue.getSeverity());
            String department = mapComponentToDepartment(issue.getComponentName());
            String action = issue.getSuggestedAction() != null && !issue.getSuggestedAction().isEmpty()
                ? issue.getSuggestedAction()
                : generateSuggestedAction(issue);

            alerts.add(new DashboardDTOs.RiskAlert(
                    "alert-" + issue.getIssueId(),
                    level,
                    issue.getTitle(),
                    issue.getDescription() != null ? issue.getDescription() : issue.getTitle(),
                    department,
                    null,
                    generateImpactDescription(issue),
                    issue.getDetectedAt() != null ? issue.getDetectedAt() : Instant.now()
            ));
        }

        return alerts;
    }

    private String mapSeverityToLevel(HealthIssue.Severity severity) {
        if (severity == null) return "WARNING";
        return switch (severity) {
            case CRITICAL -> "CRITICAL";
            case HIGH -> "WARNING";
            case MEDIUM -> "WARNING";
            case LOW -> "INFO";
        };
    }

    private String mapComponentToDepartment(String componentName) {
        if (componentName == null) return "system";
        String lower = componentName.toLowerCase();
        if (lower.contains("brain")) {
            if (lower.contains("tech")) return "tech";
            if (lower.contains("hr")) return "hr";
            if (lower.contains("finance")) return "finance";
            if (lower.contains("sales")) return "sales";
            if (lower.contains("admin")) return "admin";
            if (lower.contains("cs") || lower.contains("customer")) return "cs";
            if (lower.contains("legal")) return "legal";
            if (lower.contains("ops")) return "ops";
        }
        return "system";
    }

    private String generateSuggestedAction(HealthIssue issue) {
        if (issue.getType() == null) return "检查系统状态并联系管理员";
        return switch (issue.getType()) {
            case PERFORMANCE -> "优化相关组件的性能配置，考虑增加资源分配";
            case CONNECTIVITY -> "检查网络连接，确认服务端口可达";
            case RESOURCE -> "检查系统资源使用率，必要时扩容";
            case CONFIGURATION -> "检查配置文件，确认参数正确";
            case SECURITY -> "检查安全策略，确认无未授权访问";
            case LOGIC -> "检查业务逻辑，确认流程正常";
        };
    }

    private String generateImpactDescription(HealthIssue issue) {
        String typeDesc = issue.getType() != null ? switch (issue.getType()) {
            case PERFORMANCE -> "性能下降";
            case CONNECTIVITY -> "连接不稳定";
            case RESOURCE -> "资源不足";
            case CONFIGURATION -> "配置异常";
            case SECURITY -> "安全风险";
            case LOGIC -> "业务逻辑异常";
        } : "系统异常";
        return String.format("[%s] %s - 组件: %s", typeDesc, issue.getTitle(), issue.getComponentName());
    }

    private List<DashboardDTOs.StrategicSuggestion> buildStrategicSuggestions() {
        List<DashboardDTOs.StrategicSuggestion> suggestions = new ArrayList<>();
        
        var allEmployees = employeeService.listEmployees(new EmployeeService.EmployeeQuery(null, null, null, null, 1000, 0));
        var activeEmployees = employeeService.listEmployees(new EmployeeService.EmployeeQuery(null, null, EmployeeStatus.ACTIVE, null, 1000, 0));
        TaskStatistics taskStats = taskCheckout.getStatistics();
        var issues = healthMonitor.detectIssues();

        int total = allEmployees.size();
        int active = activeEmployees.size();
        double activationRate = total > 0 ? (double) active / total * 100.0 : 0.0;
        int criticalIssues = (int) issues.stream().filter(i -> i.getSeverity() == HealthIssue.Severity.CRITICAL).count();
        int totalTasks = taskStats.pendingCount() + taskStats.checkedOutCount() + taskStats.completedCount();
        double completionRate = totalTasks > 0 ? (double) taskStats.completedCount() / totalTasks * 100.0 : 0.0;

        if (activationRate < 70 && total > 0) {
            suggestions.add(new DashboardDTOs.StrategicSuggestion(
                "sugg-001",
                "人员",
                "员工活跃度偏低",
                String.format("当前员工活跃度为 %.1f%%，低于 70%% 的健康阈值。建议关注员工工作状态和任务分配。", activationRate),
                2,
                "review_employees",
                Map.of("activationRate", activationRate, "threshold", 70.0)
            ));
        }

        if (completionRate < 60 && totalTasks > 10) {
            suggestions.add(new DashboardDTOs.StrategicSuggestion(
                "sugg-002",
                "任务",
                "任务完成率需要提升",
                String.format("当前任务完成率为 %.1f%%，有 %d 个待处理任务。建议优化任务分配策略。", completionRate, taskStats.pendingCount()),
                1,
                "optimize_tasks",
                Map.of("completionRate", completionRate, "pendingTasks", taskStats.pendingCount())
            ));
        }

        if (criticalIssues > 0) {
            suggestions.add(new DashboardDTOs.StrategicSuggestion(
                "sugg-003",
                "风险",
                "存在严重系统问题",
                String.format("检测到 %d 个严重级别的系统问题，建议优先处理。", criticalIssues),
                1,
                "resolve_issues",
                Map.of("criticalIssues", criticalIssues)
            ));
        }

        int digital = (int) allEmployees.stream().filter(Employee::isDigital).count();
        if (digital == 0 && total > 5) {
            suggestions.add(new DashboardDTOs.StrategicSuggestion(
                "sugg-004",
                "人员",
                "建议引入数字员工",
                "当前没有数字员工。建议为重复性工作引入数字员工，提升效率。",
                3,
                "add_digital_employee",
                Map.of("humanEmployees", total - digital)
            ));
        }

        if (suggestions.isEmpty()) {
            suggestions.add(new DashboardDTOs.StrategicSuggestion(
                "sugg-005",
                "运营",
                "系统运行良好",
                "当前各项指标均在正常范围内。建议继续保持监控，关注趋势变化。",
                5,
                "continue_monitoring",
                Map.of("healthScore", "good")
            ));
        }

        return suggestions;
    }

    private DashboardDTOs.DepartmentSummary toDepartmentSummary(DepartmentEntity dept) {
        String code = dept.getCode();
        int memberCount = dept.getMemberCount();

        var activeMembers = employeeService.listEmployees(
                new EmployeeService.EmployeeQuery(null, code, EmployeeStatus.ACTIVE, null, 1000, 0));

        double healthScore = memberCount > 0 ? (double) activeMembers.size() / memberCount * 100.0 : 0;
        String status = healthScore >= 80 ? "HEALTHY" : healthScore >= 50 ? "WARNING" : "CRITICAL";

        return new DashboardDTOs.DepartmentSummary(
                code,
                dept.getName(),
                memberCount,
                activeMembers.size(),
                0,
                healthScore,
                status,
                dept.getTargetBrain()
        );
    }
}
