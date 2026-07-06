package com.livingagent.core.autonomy;

import java.time.Instant;
import java.util.List;

/**
 * 部门交付物。
 *
 * <p>部门大脑聚合分析后打包的交付成果，交付给主脑。
 */
public record DepartmentDeliverable(
    String deliverableId,
    String department,
    String planId,
    String objective,
    AggregationStatus status,
    List<DeliverableItem> items,
    String summary,
    List<String> issues,
    double overallQualityScore,
    Instant deliveredAt
) {
    public enum AggregationStatus {
        COMPLETE,           // 所有子任务完成，质量合格
        PARTIAL,            // 部分子任务完成
        INCOMPLETE,         // 未完成
        QUALITY_ISSUES      // 完成但有质量问题
    }

    public record DeliverableItem(
        String employeeCode,
        String employeeName,
        String taskType,
        String summary,
        boolean reviewPassed,
        double qualityScore,
        List<String> artifactPaths
    ) {}
}
