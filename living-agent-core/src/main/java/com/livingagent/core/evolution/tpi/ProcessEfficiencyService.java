package com.livingagent.core.evolution.tpi;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * P9-1: 流程效率度量服务（DBS TPI 工具代码实现）。
 *
 * 事务流程改进（Transaction Process Improvement）核心度量：
 * - 节拍时间(Takt Time): 用户可接受的最大响应时间
 * - 周期时间(Cycle Time): 实际处理时间
 * - 增值时间比(VA Ratio): 增值时间/总时间
 * - 首次通过率(FTY): 一次成功完成的请求比例
 *
 * 关联闭环：
 * - 闭环1（WS连接）: 连接建立瓶颈
 * - 闭环3（对话处理）: 对话处理瓶颈
 * - 闭环25（经济系统）: 成本效率分析
 * - 闭环27（降级模式）: 瓶颈应对降级
 */
public interface ProcessEfficiencyService {

    /**
     * 获取指定流程的效率指标。
     *
     * @param processName 流程名称（如 "conversation_processing", "task_dispatch"）
     * @return 流程效率指标
     */
    ProcessEfficiencyMetrics getMetrics(String processName);

    /**
     * 批量获取流程效率指标。
     */
    Map<String, ProcessEfficiencyMetrics> getMetricsBatch(List<String> processNames);

    /**
     * 记录一次流程执行的耗时数据。
     *
     * @param processName 流程名称
     * @param stepName 步骤名称
     * @param isValueAdded 是否增值步骤
     * @param durationMs 耗时（毫秒）
     * @param success 是否成功
     */
    void recordStep(String processName, String stepName, boolean isValueAdded, long durationMs, boolean success);

    /**
     * 获取所有流程效率概览。
     */
    ProcessEfficiencyOverview getOverview();

    /**
     * 流程效率指标。
     */
    record ProcessEfficiencyMetrics(
        String processName,
        double taktTimeMs,         // 节拍时间（目标最大响应时间）
        double cycleTimeMs,        // 实际平均周期时间
        double vaRatio,            // 增值时间比
        double fty,                // 首次通过率
        int totalSteps,            // 总步骤数
        int valueAddedSteps,       // 增值步骤数
        int sampleCount,           // 样本数
        Instant lastUpdated
    ) {
        /**
         * 效率评分：综合 VA 比 + FTY + 周期时间达标率
         */
        public double efficiencyScore() {
            double cycleScore = cycleTimeMs <= taktTimeMs ? 1.0 : taktTimeMs / cycleTimeMs;
            return vaRatio * 0.4 + fty * 0.3 + cycleScore * 0.3;
        }

        /**
         * 是否为瓶颈流程（VA比<40% 或 周期时间>2倍节拍时间）
         */
        public boolean isBottleneck() {
            return vaRatio < 0.40 || cycleTimeMs > taktTimeMs * 2;
        }
    }

    /**
     * 流程效率概览。
     */
    record ProcessEfficiencyOverview(
        int totalProcesses,
        int bottleneckCount,
        double averageVaRatio,
        double averageFty,
        List<String> bottleneckProcesses,
        Instant generatedAt
    ) {}
}
