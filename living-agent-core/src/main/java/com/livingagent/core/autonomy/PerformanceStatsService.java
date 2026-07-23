package com.livingagent.core.autonomy;

import java.util.List;
import java.util.Map;

/**
 * NP2-3: 员工绩效统计服务。
 * 聚合 LedgerService 中的经济数据，生成结构化绩效指标。
 *
 * P6-1: DBS 人才发展 — 数字员工能力等级
 * 基于绩效评分 + 任务完成数 + 进化历史，计算能力等级（Novice → Competent → Expert → BlackBelt）
 */
public interface PerformanceStatsService {

    /**
     * 获取单个员工的绩效统计。
     */
    EmployeePerformanceStats getStats(String employeeCode);

    /**
     * 批量获取员工绩效统计。
     */
    Map<String, EmployeePerformanceStats> getStatsBatch(List<String> employeeCodes);

    /**
     * 获取部门内绩效排名（按总积分降序）。
     */
    List<EmployeePerformanceStats> getDepartmentRanking(String department, int limit);

    /**
     * P28-A: 调整员工分派权重（审核结果联动）。
     *
     * @param employeeCode 员工代码
     * @param delta 权重变化量（正数增加，负数减少）
     */
    void adjustWeight(String employeeCode, double delta);

    /**
     * P28-A: 获取员工当前分派权重。
     * 默认1.0，最低0.0。
     *
     * @param employeeCode 员工代码
     * @return 当前分派权重
     */
    double getDispatchWeight(String employeeCode);

    /**
     * P6-1: 获取员工能力等级。
     * 基于绩效评分 + 任务完成数计算。
     *
     * @param employeeCode 员工代码
     * @return 能力等级
     */
    default CompetencyLevel getCompetencyLevel(String employeeCode) {
        EmployeePerformanceStats stats = getStats(employeeCode);
        if (stats == null) return CompetencyLevel.NOVICE;
        return CompetencyLevel.fromStats(stats.normalizedScore(), stats.taskCompletionCount());
    }

    /**
     * P6-1: 数字员工能力等级（DBS 人才发展）
     *
     * 等级定义：
     * - NOVICE（新手）: 绩效<20 或 任务<5
     * - COMPETENT（胜任）: 绩效20-50 且 任务5-20
     * - EXPERT（专家）: 绩效50-80 且 任务20-50
     * - BLACK_BELT（黑带）: 绩效>80 且 任务>50
     */
    enum CompetencyLevel {
        NOVICE(1.0, "新手", "初入岗位，正在学习基本流程"),
        COMPETENT(1.2, "胜任", "掌握核心技能，可独立完成任务"),
        EXPERT(1.5, "专家", "精通领域知识，可指导他人"),
        BLACK_BELT(2.0, "黑带", "顶级能力，可驱动系统级改进");

        private final double dispatchMultiplier;
        private final String label;
        private final String description;

        CompetencyLevel(double dispatchMultiplier, String label, String description) {
            this.dispatchMultiplier = dispatchMultiplier;
            this.label = label;
            this.description = description;
        }

        public double getDispatchMultiplier() { return dispatchMultiplier; }
        public String getLabel() { return label; }
        public String getDescription() { return description; }

        /**
         * 从绩效数据计算能力等级。
         */
        public static CompetencyLevel fromStats(double normalizedScore, int taskCompletionCount) {
            if (normalizedScore > 80 && taskCompletionCount > 50) return BLACK_BELT;
            if (normalizedScore > 50 && taskCompletionCount > 20) return EXPERT;
            if (normalizedScore > 20 && taskCompletionCount > 5) return COMPETENT;
            return NOVICE;
        }
    }

    /**
     * 员工绩效统计结果。
     */
    record EmployeePerformanceStats(
        String employeeCode,
        int totalCredits,
        int taskCompletionCount,
        int taskParticipationCount,
        double averageRewardPerTask,
        double performanceScore
    ) {
        /**
         * 绩效评分 = 任务完成数 * 1.0 + 参与数 * 0.3 + 平均奖励 * 0.01
         * 归一化到 0-100 范围
         */
        public double normalizedScore() {
            double raw = taskCompletionCount * 1.0 + taskParticipationCount * 0.3 + averageRewardPerTask * 0.01;
            return Math.min(raw * 10, 100.0);
        }

        /**
         * P6-1: 获取能力等级。
         */
        public CompetencyLevel competencyLevel() {
            return CompetencyLevel.fromStats(normalizedScore(), taskCompletionCount);
        }
    }
}
