package com.livingagent.core.autonomy;

import java.util.List;

/**
 * 部门聚合结果。
 */
public record AggregationResult(
    boolean success,
    DepartmentDeliverable deliverable,
    List<String> uncompletedItems,
    List<String> qualityIssues,
    String message
) {
    public static AggregationResult success(DepartmentDeliverable deliverable) {
        return new AggregationResult(true, deliverable, List.of(), List.of(),
            "部门聚合完成，质量合格");
    }

    public static AggregationResult partial(DepartmentDeliverable deliverable,
                                             List<String> uncompletedItems) {
        return new AggregationResult(false, deliverable, uncompletedItems, List.of(),
            "部门聚合部分完成，存在未完成项: " + String.join(", ", uncompletedItems));
    }

    public static AggregationResult qualityIssues(DepartmentDeliverable deliverable,
                                                   List<String> qualityIssues) {
        return new AggregationResult(false, deliverable, List.of(), qualityIssues,
            "部门聚合完成但存在质量问题: " + String.join(", ", qualityIssues));
    }
}
