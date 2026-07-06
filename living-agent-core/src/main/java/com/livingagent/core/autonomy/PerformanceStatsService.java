package com.livingagent.core.autonomy;

import java.util.List;
import java.util.Map;

/**
 * NP2-3: 员工绩效统计服务。
 * 聚合 LedgerService 中的经济数据，生成结构化绩效指标。
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
    }
}
