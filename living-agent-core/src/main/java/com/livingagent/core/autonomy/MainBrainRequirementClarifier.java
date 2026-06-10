package com.livingagent.core.autonomy;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 主脑需求澄清器。
 * 职责：当需求不明确时，由主脑统一生成澄清消息返回给用户。
 *
 * 设计原则：
 * - 澄清由主脑统一负责，不直接派给员工
 * - 澄清消息应简洁明确，引导用户提供缺失信息
 * - 澄清完成后，重新进入需求评估流程
 */
public interface MainBrainRequirementClarifier {

    /**
     * 生成澄清消息。
     *
     * @param userMessage 用户原始消息
     * @param readinessResult 需求就绪评估结果
     * @param department 当前部门
     * @param sessionId 会话ID
     * @return 澄清消息内容
     */
    CompletableFuture<String> clarify(
        String userMessage,
        RequirementReadinessEvaluator.RequirementReadinessResult readinessResult,
        String department,
        String sessionId
    );
}
