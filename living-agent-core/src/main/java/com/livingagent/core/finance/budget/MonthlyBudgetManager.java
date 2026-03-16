package com.livingagent.core.finance.budget;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MonthlyBudgetManager {

    private static final Logger log = LoggerFactory.getLogger(MonthlyBudgetManager.class);

    @Value("${budget.default-monthly:1000.0}")
    private double defaultMonthlyBudget;

    @Value("${budget.alert-threshold:0.8}")
    private double alertThreshold;

    @Value("${budget.critical-threshold:0.95}")
    private double criticalThreshold;

    private final Map<String, DepartmentBudget> departmentBudgets = new ConcurrentHashMap<>();
    private final Map<String, ProjectBudget> projectBudgets = new ConcurrentHashMap<>();
    private final Map<String, List<BudgetAlert>> alerts = new ConcurrentHashMap<>();
    private final Map<String, List<BudgetTransaction>> transactions = new ConcurrentHashMap<>();

    public DepartmentBudget createDepartmentBudget(String departmentId, String departmentName, 
                                                   YearMonth month, double budget) {
        String budgetId = "dept-" + departmentId + "-" + month.toString();
        
        DepartmentBudget deptBudget = new DepartmentBudget(
            budgetId,
            departmentId,
            departmentName,
            month,
            budget,
            0.0,
            0.0,
            BudgetStatus.ACTIVE,
            Instant.now()
        );
        
        departmentBudgets.put(budgetId, deptBudget);
        log.info("Created department budget: {} for {} - ${}", budgetId, departmentName, budget);
        return deptBudget;
    }

    public ProjectBudget createProjectBudget(String projectId, String projectName, 
                                             double totalBudget, LocalDate startDate, LocalDate endDate) {
        String budgetId = "proj-" + projectId;
        
        ProjectBudget projBudget = new ProjectBudget(
            budgetId,
            projectId,
            projectName,
            totalBudget,
            0.0,
            0.0,
            startDate,
            endDate,
            BudgetStatus.ACTIVE,
            Instant.now()
        );
        
        projectBudgets.put(budgetId, projBudget);
        log.info("Created project budget: {} for {} - ${}", budgetId, projectName, totalBudget);
        return projBudget;
    }

    public BudgetTransaction recordExpense(String budgetId, String description, double amount, 
                                           String category, String recordedBy) {
        BudgetTransaction transaction = new BudgetTransaction(
            UUID.randomUUID().toString(),
            budgetId,
            TransactionType.EXPENSE,
            amount,
            description,
            category,
            Instant.now(),
            recordedBy
        );
        
        transactions.computeIfAbsent(budgetId, k -> new ArrayList<>()).add(transaction);
        
        if (budgetId.startsWith("dept-")) {
            updateDepartmentSpent(budgetId, amount);
        } else if (budgetId.startsWith("proj-")) {
            updateProjectSpent(budgetId, amount);
        }
        
        checkBudgetThreshold(budgetId);
        
        log.info("Recorded expense: {} - ${} for {}", description, amount, budgetId);
        return transaction;
    }

    public BudgetTransaction recordIncome(String budgetId, String description, double amount, 
                                          String category, String recordedBy) {
        BudgetTransaction transaction = new BudgetTransaction(
            UUID.randomUUID().toString(),
            budgetId,
            TransactionType.INCOME,
            amount,
            description,
            category,
            Instant.now(),
            recordedBy
        );
        
        transactions.computeIfAbsent(budgetId, k -> new ArrayList<>()).add(transaction);
        
        if (budgetId.startsWith("proj-")) {
            updateProjectIncome(budgetId, amount);
        }
        
        log.info("Recorded income: {} - ${} for {}", description, amount, budgetId);
        return transaction;
    }

    public Optional<DepartmentBudget> getDepartmentBudget(String departmentId, YearMonth month) {
        String budgetId = "dept-" + departmentId + "-" + month.toString();
        return Optional.ofNullable(departmentBudgets.get(budgetId));
    }

    public Optional<ProjectBudget> getProjectBudget(String projectId) {
        String budgetId = "proj-" + projectId;
        return Optional.ofNullable(projectBudgets.get(budgetId));
    }

    public List<DepartmentBudget> getDepartmentBudgets(YearMonth month) {
        return departmentBudgets.values().stream()
            .filter(b -> b.month().equals(month))
            .sorted(Comparator.comparing(DepartmentBudget::departmentName))
            .toList();
    }

    public List<ProjectBudget> getActiveProjectBudgets() {
        LocalDate today = LocalDate.now();
        return projectBudgets.values().stream()
            .filter(b -> b.status() == BudgetStatus.ACTIVE)
            .filter(b -> !today.isBefore(b.startDate()) && !today.isAfter(b.endDate()))
            .sorted(Comparator.comparing(ProjectBudget::projectName))
            .toList();
    }

    public BudgetSummary getBudgetSummary(String budgetId) {
        List<BudgetTransaction> txList = transactions.getOrDefault(budgetId, List.of());
        
        double totalIncome = txList.stream()
            .filter(t -> t.type() == TransactionType.INCOME)
            .mapToDouble(BudgetTransaction::amount)
            .sum();
        
        double totalExpense = txList.stream()
            .filter(t -> t.type() == TransactionType.EXPENSE)
            .mapToDouble(BudgetTransaction::amount)
            .sum();
        
        Map<String, Double> expensesByCategory = txList.stream()
            .filter(t -> t.type() == TransactionType.EXPENSE)
            .collect(HashMap::new, (m, t) -> m.merge(t.category(), t.amount(), Double::sum), HashMap::putAll);
        
        return new BudgetSummary(
            budgetId,
            totalIncome,
            totalExpense,
            totalIncome - totalExpense,
            txList.size(),
            expensesByCategory
        );
    }

    public List<BudgetAlert> getActiveAlerts(String budgetId) {
        return alerts.getOrDefault(budgetId, List.of()).stream()
            .filter(a -> a.status() == AlertStatus.ACTIVE)
            .sorted(Comparator.comparing(BudgetAlert::timestamp).reversed())
            .toList();
    }

    public List<BudgetAlert> getAllActiveAlerts() {
        return alerts.values().stream()
            .flatMap(List::stream)
            .filter(a -> a.status() == AlertStatus.ACTIVE)
            .sorted(Comparator.comparing(BudgetAlert::timestamp).reversed())
            .toList();
    }

    public void acknowledgeAlert(String alertId, String acknowledgedBy) {
        alerts.values().stream()
            .flatMap(List::stream)
            .filter(a -> a.alertId().equals(alertId))
            .forEach(a -> {
                BudgetAlert acknowledged = new BudgetAlert(
                    a.alertId(),
                    a.budgetId(),
                    a.alertType(),
                    a.message(),
                    a.percentage(),
                    AlertStatus.ACKNOWLEDGED,
                    a.timestamp(),
                    Instant.now(),
                    acknowledgedBy
                );
                
                List<BudgetAlert> alertList = alerts.get(a.budgetId());
                alertList.removeIf(x -> x.alertId().equals(alertId));
                alertList.add(acknowledged);
            });
    }

    public BudgetReport generateMonthlyReport(YearMonth month) {
        List<DepartmentBudget> monthBudgets = getDepartmentBudgets(month);
        
        double totalBudget = monthBudgets.stream()
            .mapToDouble(DepartmentBudget::budget)
            .sum();
        
        double totalSpent = monthBudgets.stream()
            .mapToDouble(DepartmentBudget::spent)
            .sum();
        
        List<DepartmentBudgetStatus> deptStatuses = monthBudgets.stream()
            .map(b -> new DepartmentBudgetStatus(
                b.departmentId(),
                b.departmentName(),
                b.budget(),
                b.spent(),
                b.budget() - b.spent(),
                b.budget() > 0 ? (b.spent() / b.budget()) * 100 : 0
            ))
            .sorted(Comparator.comparing(DepartmentBudgetStatus::utilizationPercent).reversed())
            .toList();
        
        return new BudgetReport(
            month.toString(),
            month,
            totalBudget,
            totalSpent,
            totalBudget - totalSpent,
            totalBudget > 0 ? (totalSpent / totalBudget) * 100 : 0,
            deptStatuses,
            getAllActiveAlerts().stream()
                .filter(a -> {
                    String budgetId = a.budgetId();
                    return budgetId.contains(month.toString());
                })
                .count()
        );
    }

    public BudgetForecast forecastMonthEnd(String budgetId) {
        List<BudgetTransaction> txList = transactions.getOrDefault(budgetId, List.of());
        
        if (txList.isEmpty()) {
            return new BudgetForecast(budgetId, 0, 0, 0, 0);
        }
        
        LocalDate today = LocalDate.now();
        int daysInMonth = today.lengthOfMonth();
        int daysPassed = today.getDayOfMonth();
        int daysRemaining = daysInMonth - daysPassed;
        
        double spentSoFar = txList.stream()
            .filter(t -> t.type() == TransactionType.EXPENSE)
            .mapToDouble(BudgetTransaction::amount)
            .sum();
        
        double dailyAverage = daysPassed > 0 ? spentSoFar / daysPassed : 0;
        double projectedSpend = spentSoFar + (dailyAverage * daysRemaining);
        
        double budget = 0;
        if (budgetId.startsWith("dept-")) {
            budget = Optional.ofNullable(departmentBudgets.get(budgetId))
                .map(DepartmentBudget::budget)
                .orElse(defaultMonthlyBudget);
        } else if (budgetId.startsWith("proj-")) {
            budget = Optional.ofNullable(projectBudgets.get(budgetId))
                .map(ProjectBudget::totalBudget)
                .orElse(0.0);
        }
        
        return new BudgetForecast(
            budgetId,
            spentSoFar,
            dailyAverage,
            projectedSpend,
            budget - projectedSpend
        );
    }

    private void updateDepartmentSpent(String budgetId, double amount) {
        DepartmentBudget current = departmentBudgets.get(budgetId);
        if (current != null) {
            DepartmentBudget updated = new DepartmentBudget(
                current.budgetId(),
                current.departmentId(),
                current.departmentName(),
                current.month(),
                current.budget(),
                current.spent() + amount,
                current.committed() + amount,
                current.status(),
                Instant.now()
            );
            departmentBudgets.put(budgetId, updated);
        }
    }

    private void updateProjectSpent(String budgetId, double amount) {
        ProjectBudget current = projectBudgets.get(budgetId);
        if (current != null) {
            ProjectBudget updated = new ProjectBudget(
                current.budgetId(),
                current.projectId(),
                current.projectName(),
                current.totalBudget(),
                current.spent() + amount,
                current.earned(),
                current.startDate(),
                current.endDate(),
                current.status(),
                Instant.now()
            );
            projectBudgets.put(budgetId, updated);
        }
    }

    private void updateProjectIncome(String budgetId, double amount) {
        ProjectBudget current = projectBudgets.get(budgetId);
        if (current != null) {
            ProjectBudget updated = new ProjectBudget(
                current.budgetId(),
                current.projectId(),
                current.projectName(),
                current.totalBudget(),
                current.spent(),
                current.earned() + amount,
                current.startDate(),
                current.endDate(),
                current.status(),
                Instant.now()
            );
            projectBudgets.put(budgetId, updated);
        }
    }

    private void checkBudgetThreshold(String budgetId) {
        double budget = 0;
        double spent = 0;
        
        if (budgetId.startsWith("dept-")) {
            DepartmentBudget dept = departmentBudgets.get(budgetId);
            if (dept != null) {
                budget = dept.budget();
                spent = dept.spent();
            }
        } else if (budgetId.startsWith("proj-")) {
            ProjectBudget proj = projectBudgets.get(budgetId);
            if (proj != null) {
                budget = proj.totalBudget();
                spent = proj.spent();
            }
        }
        
        if (budget <= 0) return;
        
        double percentage = spent / budget;
        
        if (percentage >= criticalThreshold) {
            createAlert(budgetId, AlertType.CRITICAL, 
                String.format("Budget critical: %.1f%% used (%.2f/%.2f)", percentage * 100, spent, budget),
                percentage);
        } else if (percentage >= alertThreshold) {
            createAlert(budgetId, AlertType.WARNING,
                String.format("Budget warning: %.1f%% used (%.2f/%.2f)", percentage * 100, spent, budget),
                percentage);
        }
    }

    private void createAlert(String budgetId, AlertType type, String message, double percentage) {
        BudgetAlert alert = new BudgetAlert(
            UUID.randomUUID().toString(),
            budgetId,
            type,
            message,
            percentage,
            AlertStatus.ACTIVE,
            Instant.now(),
            null,
            null
        );
        
        alerts.computeIfAbsent(budgetId, k -> new ArrayList<>()).add(alert);
        log.warn("Budget alert: {} - {}", budgetId, message);
    }

    public record DepartmentBudget(
        String budgetId,
        String departmentId,
        String departmentName,
        YearMonth month,
        double budget,
        double spent,
        double committed,
        BudgetStatus status,
        Instant updatedAt
    ) {
        public double remaining() { return budget - spent; }
        public double utilizationPercent() { return budget > 0 ? (spent / budget) * 100 : 0; }
    }

    public record ProjectBudget(
        String budgetId,
        String projectId,
        String projectName,
        double totalBudget,
        double spent,
        double earned,
        LocalDate startDate,
        LocalDate endDate,
        BudgetStatus status,
        Instant updatedAt
    ) {
        public double remaining() { return totalBudget - spent; }
        public double profit() { return earned - spent; }
        public double utilizationPercent() { return totalBudget > 0 ? (spent / totalBudget) * 100 : 0; }
    }

    public record BudgetTransaction(
        String transactionId,
        String budgetId,
        TransactionType type,
        double amount,
        String description,
        String category,
        Instant timestamp,
        String recordedBy
    ) {}

    public record BudgetAlert(
        String alertId,
        String budgetId,
        AlertType alertType,
        String message,
        double percentage,
        AlertStatus status,
        Instant timestamp,
        Instant acknowledgedAt,
        String acknowledgedBy
    ) {}

    public record BudgetSummary(
        String budgetId,
        double totalIncome,
        double totalExpense,
        double netAmount,
        int transactionCount,
        Map<String, Double> expensesByCategory
    ) {}

    public record BudgetReport(
        String reportId,
        YearMonth month,
        double totalBudget,
        double totalSpent,
        double remaining,
        double utilizationPercent,
        List<DepartmentBudgetStatus> departmentStatuses,
        long alertCount
    ) {}

    public record DepartmentBudgetStatus(
        String departmentId,
        String departmentName,
        double budget,
        double spent,
        double remaining,
        double utilizationPercent
    ) {}

    public record BudgetForecast(
        String budgetId,
        double spentSoFar,
        double dailyAverage,
        double projectedMonthEnd,
        double projectedRemaining
    ) {}

    public enum BudgetStatus {
        ACTIVE,
        CLOSED,
        ARCHIVED
    }

    public enum TransactionType {
        INCOME,
        EXPENSE
    }

    public enum AlertType {
        WARNING,
        CRITICAL
    }

    public enum AlertStatus {
        ACTIVE,
        ACKNOWLEDGED,
        RESOLVED
    }
}
