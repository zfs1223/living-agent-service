package com.livingagent.core.tool.impl;

import com.livingagent.core.security.SecurityPolicy;
import com.livingagent.core.tool.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BudgetManagementTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(BudgetManagementTool.class);

    private static final String NAME = "budget_management";
    private static final String DESCRIPTION = "预算管理工具，月度预算管理、超支预警、预算分析";
    private static final String VERSION = "1.0.0";
    private static final String DEPARTMENT = "finance";

    private final ObjectMapper objectMapper;
    private final Map<String, Budget> budgets = new ConcurrentHashMap<>();
    private final Map<String, BudgetUsage> usages = new ConcurrentHashMap<>();
    private ToolStats stats = ToolStats.empty(NAME);

    public BudgetManagementTool() {
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getName() { return NAME; }

    @Override
    public String getDescription() { return DESCRIPTION; }

    @Override
    public String getVersion() { return VERSION; }

    @Override
    public String getDepartment() { return DEPARTMENT; }

    @Override
    public ToolSchema getSchema() {
        return ToolSchema.builder()
                .name(NAME)
                .description(DESCRIPTION)
                .parameter("action", "string", "操作类型: create_budget, record_usage, check_status, get_report, set_alert", true)
                .parameter("budget_id", "string", "预算ID", false)
                .parameter("department", "string", "部门", false)
                .parameter("period", "string", "预算周期 (格式: 2026-03)", false)
                .parameter("total_amount", "number", "预算总额", false)
                .parameter("categories", "object", "分类预算", false)
                .parameter("category", "string", "支出分类", false)
                .parameter("amount", "number", "金额", false)
                .parameter("description", "string", "描述", false)
                .parameter("alert_threshold", "number", "预警阈值 (0-1)", false)
                .build();
    }

    @Override
    public List<String> getCapabilities() {
        return List.of("budget_creation", "usage_tracking", "alert_management", "reporting");
    }

    @Override
    public ToolResult execute(ToolParams params, ToolContext context) {
        long startTime = System.currentTimeMillis();
        String action = params.getString("action");
        
        try {
            Object result = switch (action) {
                case "create_budget" -> createBudget(params);
                case "record_usage" -> recordUsage(params);
                case "check_status" -> checkStatus(params);
                case "get_report" -> getReport(params);
                case "set_alert" -> setAlert(params);
                case "list_budgets" -> listBudgets(params);
                default -> throw new IllegalArgumentException("未知操作: " + action);
            };
            
            stats = stats.recordCall(true, System.currentTimeMillis() - startTime);
            return ToolResult.success(result);
        } catch (Exception e) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            log.error("预算管理操作失败: {}", e.getMessage(), e);
            return ToolResult.failure("预算管理操作失败: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> createBudget(ToolParams params) {
        String department = params.getString("department");
        String period = params.getString("period");
        String amountStr = params.getString("total_amount");
        BigDecimal totalAmount = amountStr != null ? new BigDecimal(amountStr) : BigDecimal.ZERO;
        Object catObj = params.get("categories");
        Map<String, Object> categories = new HashMap<>();
        if (catObj instanceof Map) {
            categories = new HashMap<>((Map<String, Object>) catObj);
        }
        
        String budgetId = "BUD-" + period + "-" + department.toUpperCase();
        
        Map<String, BigDecimal> categoryBudgets = new HashMap<>();
        categories.forEach((k, v) -> categoryBudgets.put(k, new BigDecimal(v.toString())));
        
        Budget budget = new Budget(budgetId, department, period, totalAmount, categoryBudgets);
        budgets.put(budgetId, budget);
        
        return Map.of(
            "budget_id", budgetId,
            "department", department,
            "period", period,
            "total_amount", totalAmount,
            "created", true
        );
    }

    private Map<String, Object> recordUsage(ToolParams params) {
        String budgetId = params.getString("budget_id");
        String category = params.getString("category");
        String amountStr = params.getString("amount");
        BigDecimal amount = amountStr != null ? new BigDecimal(amountStr) : BigDecimal.ZERO;
        String description = params.getString("description");
        if (description == null) description = "";
        
        Budget budget = budgets.get(budgetId);
        if (budget == null) {
            throw new IllegalArgumentException("预算不存在: " + budgetId);
        }
        
        String usageId = UUID.randomUUID().toString();
        BudgetUsage usage = new BudgetUsage(usageId, budgetId, category, amount, description, System.currentTimeMillis());
        usages.put(usageId, usage);
        
        BigDecimal used = calculateUsedAmount(budgetId);
        BigDecimal usageRate = used.divide(budget.totalAmount(), 4, RoundingMode.HALF_UP);
        
        String alertLevel = "normal";
        if (usageRate.doubleValue() > 0.85) {
            alertLevel = "critical";
        } else if (usageRate.doubleValue() > 0.70) {
            alertLevel = "warning";
        }
        
        return Map.of(
            "usage_id", usageId,
            "recorded", true,
            "used_amount", used,
            "remaining_amount", budget.totalAmount().subtract(used),
            "usage_rate", usageRate,
            "alert_level", alertLevel
        );
    }

    private Map<String, Object> checkStatus(ToolParams params) {
        String budgetId = params.getString("budget_id");
        
        Budget budget = budgets.get(budgetId);
        if (budget == null) {
            throw new IllegalArgumentException("预算不存在: " + budgetId);
        }
        
        BigDecimal used = calculateUsedAmount(budgetId);
        BigDecimal remaining = budget.totalAmount().subtract(used);
        double usageRate = used.divide(budget.totalAmount(), 4, RoundingMode.HALF_UP).doubleValue();
        
        String alertLevel = "normal";
        if (usageRate > 0.85) {
            alertLevel = "critical";
        } else if (usageRate > 0.70) {
            alertLevel = "warning";
        }
        
        return Map.of(
            "budget_id", budgetId,
            "department", budget.department(),
            "period", budget.period(),
            "total_amount", budget.totalAmount(),
            "used_amount", used,
            "remaining_amount", remaining,
            "usage_rate", usageRate,
            "alert_level", alertLevel
        );
    }

    private Map<String, Object> getReport(ToolParams params) {
        String department = params.getString("department");
        String period = params.getString("period");
        
        List<Map<String, Object>> budgetReports = budgets.values().stream()
            .filter(b -> department == null || b.department().equals(department))
            .filter(b -> period == null || b.period().equals(period))
            .map(b -> {
                BigDecimal used = calculateUsedAmount(b.budgetId());
                return Map.<String, Object>of(
                    "budget_id", b.budgetId(),
                    "department", b.department(),
                    "period", b.period(),
                    "total_amount", b.totalAmount(),
                    "used_amount", used,
                    "usage_rate", used.divide(b.totalAmount(), 4, RoundingMode.HALF_UP)
                );
            })
            .toList();
        
        return Map.of(
            "report_date", System.currentTimeMillis(),
            "budgets", budgetReports,
            "total_budgets", budgetReports.size()
        );
    }

    private Map<String, Object> setAlert(ToolParams params) {
        String budgetId = params.getString("budget_id");
        Object thresholdObj = params.get("threshold");
        double alertThreshold = 0.7;
        if (thresholdObj instanceof Number) {
            alertThreshold = ((Number) thresholdObj).doubleValue();
        }
        
        Budget budget = budgets.get(budgetId);
        if (budget == null) {
            throw new IllegalArgumentException("预算不存在: " + budgetId);
        }
        
        return Map.of(
            "budget_id", budgetId,
            "alert_threshold", alertThreshold,
            "set", true
        );
    }

    private List<Map<String, Object>> listBudgets(ToolParams params) {
        String department = params.getString("department");
        
        return budgets.values().stream()
            .filter(b -> department == null || b.department().equals(department))
            .map(b -> Map.<String, Object>of(
                "budget_id", b.budgetId(),
                "department", b.department(),
                "period", b.period(),
                "total_amount", b.totalAmount()
            ))
            .toList();
    }

    private BigDecimal calculateUsedAmount(String budgetId) {
        return usages.values().stream()
            .filter(u -> u.budgetId().equals(budgetId))
            .map(BudgetUsage::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public void validate(ToolParams params) {
        if (params.getString("action") == null) {
            throw new IllegalArgumentException("action 参数不能为空");
        }
    }

    @Override
    public boolean isAllowed(SecurityPolicy policy) { return true; }

    @Override
    public boolean requiresApproval() { return false; }

    @Override
    public ToolStats getStats() { return stats; }

    private record Budget(
        String budgetId, String department, String period,
        BigDecimal totalAmount, Map<String, BigDecimal> categories
    ) {}

    private record BudgetUsage(
        String usageId, String budgetId, String category,
        BigDecimal amount, String description, long timestamp
    ) {}
}
