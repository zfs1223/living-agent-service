package com.livingagent.core.autonomy;

import java.util.List;
import java.util.Map;

/**
 * 执行评审结果
 * 用于阶段6：执行结果验收层
 */
public record ExecutionReviewResult(
    /** 评审是否通过 */
    boolean passed,
    /** 评审状态：PASSED / NEEDS_REWORK / FAILED */
    String status,
    /** 评审总结 */
    String summary,
    /** 发现的问题列表 */
    List<String> issues,
    /** 返工建议列表 */
    List<String> reworkSuggestions,
    /** 是否建议二次派发 */
    boolean needsRedispatch,
    /** 验收依据来源：programmatic_rules / llm_semantic / both */
    String reviewSource,
    /** 附加元数据 */
    Map<String, Object> metadata
) {
    public static ExecutionReviewResult passed(String summary, String source) {
        return new ExecutionReviewResult(true, "PASSED", summary, List.of(), List.of(), false, source, Map.of());
    }

    public static ExecutionReviewResult needsRework(String summary, List<String> issues, List<String> suggestions, boolean needsRedispatch) {
        return new ExecutionReviewResult(false, "NEEDS_REWORK", summary, issues, suggestions, needsRedispatch, "programmatic_rules", Map.of());
    }

    public static ExecutionReviewResult failed(String summary, List<String> issues) {
        return new ExecutionReviewResult(false, "FAILED", summary, issues, List.of(), false, "programmatic_rules", Map.of());
    }
}
