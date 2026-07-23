package com.livingagent.core.evolution.verification;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * P14: DBS 验证表增强服务。
 *
 * P14-1: 第八章验证表增加 DBS 维度
 *   - 每个闭环验证表增加 DBS 工具关联列
 *   - DBS 八大工具覆盖度统计
 *
 * P14-2: AI 七大浪费检测维度
 *   - 过度生产 → 过度推理（不必要的LLM调用）
 *   - 等待 → 模型响应延迟
 *   - 运输 → 跨服务数据搬运
 *   - 过度加工 → 过度复杂的决策链路
 *   - 库存 → 未消费的对话历史
 *   - 动作 → 重复的状态查询
 *   - 缺陷 → 错误的LLM输出
 */
public interface DbsVerificationService {

    /**
     * P14-1: 生成闭环验证表（含 DBS 维度）。
     */
    List<LoopVerificationEntry> generateVerificationTable();

    /**
     * P14-1: 获取 DBS 八大工具覆盖度统计。
     */
    DbsToolCoverage getToolCoverage();

    /**
     * P14-2: 执行 AI 七大浪费检测。
     */
    List<WasteDetectionResult> detectWaste();

    /**
     * P14-2: 获取浪费检测汇总。
     */
    WasteSummary getWasteSummary();

    // === 数据模型 ===

    record LoopVerificationEntry(
        int loopId,
        String loopName,
        String loopLevel,
        boolean implemented,
        String dbsTool,          // 关联的 DBS 工具
        String dbsSkill,         // 关联的主脑 DBS 技能
        String verificationNote
    ) {}

    record DbsToolCoverage(
        Map<String, Double> toolCoverage,  // 工具名 → 覆盖率(0-1)
        double overallCoverage,
        List<String> uncoveredTools,
        Instant assessedAt
    ) {}

    record WasteDetectionResult(
        String wasteType,        // 传统浪费类型
        String aiMapping,       // AI系统映射
        String detectionMetric, // 检测指标
        double currentValue,    // 当前值
        double targetValue,     // 目标值
        boolean isWaste,        // 是否为浪费
        String relatedLoop      // 关联闭环
    ) {}

    record WasteSummary(
        int totalWasteTypes,
        int detectedWasteCount,
        double wasteScore,      // 0=无浪费, 1=全部浪费
        List<WasteDetectionResult> results,
        Instant generatedAt
    ) {}
}
