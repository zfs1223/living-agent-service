package com.livingagent.core.operation.dashboard;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class CEODashboardService implements CEODashboard {

    private final List<AlertItem> activeAlerts = new ArrayList<>();
    private final List<Recommendation> recommendations = new ArrayList<>();
    private Instant lastUpdated = Instant.now();

    @Override
    public CompanyOverview getCompanyOverview() {
        return new CompanyOverview(
            150,
            120,
            80,
            40,
            8,
            0.85,
            0.92,
            15000,
            230,
            Instant.now()
        );
    }

    @Override
    public List<DepartmentMetrics> getDepartmentMetrics() {
        return List.of(
            new DepartmentMetrics("dept-tech", "技术部", 30, 28, 0.88, 0.95, 3500, 45, 120.5, "HEALTHY", 0.05),
            new DepartmentMetrics("dept-sales", "销售部", 25, 22, 0.82, 0.91, 2800, 38, 95.2, "HEALTHY", 0.03),
            new DepartmentMetrics("dept-hr", "人力资源部", 15, 14, 0.90, 0.93, 1200, 15, 80.0, "HEALTHY", 0.02),
            new DepartmentMetrics("dept-finance", "财务部", 12, 11, 0.92, 0.96, 1500, 20, 70.5, "HEALTHY", 0.04),
            new DepartmentMetrics("dept-legal", "法务部", 8, 7, 0.85, 0.94, 800, 12, 100.0, "HEALTHY", -0.01),
            new DepartmentMetrics("dept-cs", "客服部", 20, 18, 0.78, 0.88, 2200, 35, 150.3, "WARNING", -0.02),
            new DepartmentMetrics("dept-ops", "运营部", 18, 16, 0.86, 0.92, 1800, 28, 90.0, "HEALTHY", 0.01),
            new DepartmentMetrics("dept-mkt", "市场部", 22, 20, 0.80, 0.89, 1200, 37, 110.0, "HEALTHY", 0.02)
        );
    }

    @Override
    public List<EmployeePerformanceSummary> getTopPerformers(int limit) {
        return List.of(
            new EmployeePerformanceSummary("emp-001", "张三", "技术部", 98.5, "S", 520, 0.98, 1, 2.0),
            new EmployeePerformanceSummary("emp-002", "李四", "销售部", 96.2, "S", 480, 0.97, 2, 1.5),
            new EmployeePerformanceSummary("emp-003", "王五", "财务部", 95.8, "S", 350, 0.99, 3, 3.0),
            new EmployeePerformanceSummary("emp-004", "赵六", "技术部", 94.5, "A", 490, 0.96, 4, 0.5),
            new EmployeePerformanceSummary("emp-005", "钱七", "运营部", 93.8, "A", 420, 0.95, 5, 1.2)
        ).stream().limit(limit).collect(Collectors.toList());
    }

    @Override
    public List<AlertItem> getActiveAlerts() {
        return activeAlerts.stream()
            .filter(a -> !a.acknowledged())
            .sorted(Comparator.comparing(AlertItem::triggeredAt).reversed())
            .collect(Collectors.toList());
    }

    @Override
    public List<TrendData> getPerformanceTrends(String metricName, int days) {
        List<DataPoint> dataPoints = new ArrayList<>();
        LocalDate today = LocalDate.now();
        Random random = new Random(42);
        
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            double value = 0.85 + random.nextDouble() * 0.10;
            dataPoints.add(new DataPoint(date, value, Map.of("source", "system")));
        }
        
        double trendDirection = 0.02;
        String trendDescription = "整体呈上升趋势";
        
        return List.of(new TrendData(metricName, dataPoints, trendDirection, trendDescription));
    }

    @Override
    public List<Recommendation> getAIRecommendations() {
        return List.of(
            new Recommendation(
                "rec-001",
                "EFFICIENCY",
                "优化客服部响应时间",
                "客服部平均响应时间较上月增加15%，建议增加数字员工辅助处理常见问题",
                "预计可提升效率20%",
                "HIGH",
                List.of("部署智能客服机器人", "优化工单分配策略", "增加高峰时段人力"),
                Instant.now()
            ),
            new Recommendation(
                "rec-002",
                "RESOURCE",
                "技术部资源利用率优化",
                "技术部夜间资源利用率较低，建议启用自动伸缩策略",
                "预计可节省成本15%",
                "MEDIUM",
                List.of("配置自动伸缩规则", "优化任务调度时间", "启用资源监控告警"),
                Instant.now()
            ),
            new Recommendation(
                "rec-003",
                "GROWTH",
                "销售部数字员工扩容",
                "销售部业务量持续增长，建议增加2个数字员工支持",
                "预计可提升处理能力30%",
                "MEDIUM",
                List.of("评估当前员工负载", "申请新数字员工配额", "配置培训数据"),
                Instant.now()
            )
        );
    }

    @Override
    public Map<String, Object> generateReport(ReportRequest request) {
        Map<String, Object> report = new LinkedHashMap<>();
        
        report.put("reportType", request.reportType());
        report.put("period", Map.of(
            "startDate", request.startDate(),
            "endDate", request.endDate()
        ));
        report.put("generatedAt", Instant.now());
        
        report.put("summary", Map.of(
            "totalTasks", 15000,
            "completedTasks", 14200,
            "successRate", 0.92,
            "averageResponseTime", 95.5
        ));
        
        report.put("departmentBreakdown", getDepartmentMetrics());
        report.put("topPerformers", getTopPerformers(10));
        report.put("alerts", getActiveAlerts());
        report.put("recommendations", getAIRecommendations());
        
        return report;
    }

    @Override
    public List<DepartmentRanking> getDepartmentRankings() {
        return List.of(
            new DepartmentRanking(1, "dept-finance", "财务部", 94.2, 0.92, 0.96, 0.04),
            new DepartmentRanking(2, "dept-hr", "人力资源部", 92.5, 0.90, 0.93, 0.02),
            new DepartmentRanking(3, "dept-tech", "技术部", 91.8, 0.88, 0.95, 0.05),
            new DepartmentRanking(4, "dept-legal", "法务部", 89.5, 0.85, 0.94, -0.01),
            new DepartmentRanking(5, "dept-ops", "运营部", 88.2, 0.86, 0.92, 0.01),
            new DepartmentRanking(6, "dept-sales", "销售部", 86.5, 0.82, 0.91, 0.03),
            new DepartmentRanking(7, "dept-mkt", "市场部", 84.8, 0.80, 0.89, 0.02),
            new DepartmentRanking(8, "dept-cs", "客服部", 82.3, 0.78, 0.88, -0.02)
        );
    }

    @Override
    public ResourceUtilization getResourceUtilization() {
        return new ResourceUtilization(
            0.65,
            0.72,
            0.58,
            85,
            35,
            Map.of(
                "技术部", 0.78,
                "销售部", 0.65,
                "客服部", 0.82,
                "运营部", 0.70,
                "财务部", 0.55,
                "人力资源部", 0.48,
                "法务部", 0.42,
                "市场部", 0.68
            )
        );
    }

    @Override
    public RiskAssessment getRiskAssessment() {
        return new RiskAssessment(
            0.25,
            List.of(
                new RiskItem("risk-001", "PERFORMANCE", "客服部响应时间持续增长", 0.6, 0.7, 0.42, "MONITORING"),
                new RiskItem("risk-002", "RESOURCE", "存储空间使用率接近阈值", 0.4, 0.5, 0.20, "MONITORING"),
                new RiskItem("risk-003", "CAPACITY", "销售部业务量超预期增长", 0.5, 0.6, 0.30, "ACTION_REQUIRED")
            ),
            List.of(
                "增加客服部数字员工支持",
                "清理历史日志释放存储空间",
                "评估销售部扩容需求"
            )
        );
    }
    
    public void addAlert(AlertItem alert) {
        activeAlerts.add(alert);
    }
    
    public void acknowledgeAlert(String alertId) {
        activeAlerts.stream()
            .filter(a -> a.alertId().equals(alertId))
            .findFirst()
            .ifPresent(alert -> {
                activeAlerts.remove(alert);
                activeAlerts.add(new AlertItem(
                    alert.alertId(),
                    alert.type(),
                    alert.severity(),
                    alert.title(),
                    alert.description(),
                    alert.department(),
                    alert.suggestedAction(),
                    alert.triggeredAt(),
                    true
                ));
            });
    }
    
    public void refresh() {
        this.lastUpdated = Instant.now();
    }
}
