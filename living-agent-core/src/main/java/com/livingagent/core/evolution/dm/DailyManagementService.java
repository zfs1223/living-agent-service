package com.livingagent.core.evolution.dm;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * P11: DBS 日常管理（Daily Management）服务。
 *
 * 三大功能：
 * - P11-1: 部门级日常管理看板
 * - P11-2: 异常标红与健康状态
 * - P11-3: 数字站会机制
 *
 * 关联闭环：
 * - 闭环8（健康监控）→ 健康状态可视化
 * - 闭环24（自愈）→ 异常标红触发自愈
 * - 闭环46（对话质量）→ 对话指标可视化
 * - 闭环65（人类汇报）→ 人类员工状态显示
 */
public interface DailyManagementService {

    /**
     * 获取部门管理看板数据。
     */
    DepartmentDashboard getDashboard(String department);

    /**
     * 获取所有部门看板概览。
     */
    Map<String, DepartmentDashboard> getAllDashboards();

    /**
     * 获取当前异常标红项。
     */
    List<AnomalyItem> getAnomalies(String department);

    /**
     * 生成数字站会摘要。
     */
    StandupSummary generateStandupSummary();

    /**
     * 部门管理看板。
     */
    record DepartmentDashboard(
        String department,
        // 核心指标区
        double taskCompletionRate,
        double avgResponseTimeMs,
        double customerSatisfactionScore,
        // 异常区
        List<AnomalyItem> anomalies,
        // 改善区
        int activeKaizenEvents,
        int completedImprovements,
        // 人员区
        int totalEmployees,
        int activeEmployees,
        Map<String, Integer> competencyDistribution, // level → count
        Instant lastUpdated
    ) {}

    /**
     * 异常标红项。
     */
    record AnomalyItem(
        String itemId,
        String department,
        AnomalyLevel level,
        String component,
        String description,
        int relatedLoopId,
        Instant detectedAt,
        boolean requiresAction
    ) {}

    /**
     * 异常级别。
     */
    enum AnomalyLevel {
        CRITICAL("🔴", "立即响应"),
        HIGH("🟠", "30分钟内响应"),
        MEDIUM("🟡", "当日响应"),
        NORMAL("🟢", "持续监控");

        private final String color;
        private final String responseRequirement;

        AnomalyLevel(String color, String responseRequirement) {
            this.color = color;
            this.responseRequirement = responseRequirement;
        }

        public String getColor() { return color; }
        public String getResponseRequirement() { return responseRequirement; }
    }

    /**
     * 数字站会摘要（≤5项关键指标，异常优先）。
     */
    record StandupSummary(
        Instant generatedAt,
        List<String> departments,
        List<StandupItem> items,
        int anomalyCount,
        int kaizenCount
    ) {}

    /**
     * 站会项。
     */
    record StandupItem(
        String department,
        String type,         // "anomaly" / "kaizen" / "metric"
        String description,
        AnomalyLevel severity,
        String actionRequired
    ) {}
}
