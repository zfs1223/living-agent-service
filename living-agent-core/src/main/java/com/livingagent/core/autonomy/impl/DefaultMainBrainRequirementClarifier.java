package com.livingagent.core.autonomy.impl;

import com.livingagent.core.autonomy.MainBrainRequirementClarifier;
import com.livingagent.core.autonomy.RequirementReadinessEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 默认主脑需求澄清器。
 * 基于规则生成澄清消息，不依赖 LLM。
 */
public class DefaultMainBrainRequirementClarifier implements MainBrainRequirementClarifier {

    private static final Logger log = LoggerFactory.getLogger(DefaultMainBrainRequirementClarifier.class);

    @Override
    public CompletableFuture<String> clarify(
            String userMessage,
            RequirementReadinessEvaluator.RequirementReadinessResult readinessResult,
            String department,
            String sessionId) {

        return CompletableFuture.supplyAsync(() -> {
            log.info("Generating clarification for session={}, level={}, missing={}",
                sessionId, readinessResult.level(), readinessResult.missingElements());

            StringBuilder sb = new StringBuilder();
            sb.append("我需要更多信息来帮您完成任务：\n\n");

            List<String> questions = readinessResult.clarificationQuestions();
            if (questions != null && !questions.isEmpty()) {
                for (int i = 0; i < questions.size(); i++) {
                    sb.append(i + 1).append(". ").append(questions.get(i)).append("\n");
                }
            } else {
                // 默认澄清问题
                sb.append("1. 请描述您希望完成的具体任务\n");
                sb.append("2. 请说明期望的产出物类型（网页/文档/数据分析/代码修改等）\n");
                sb.append("3. 请补充使用场景或目标用户信息\n");
            }

            if (readinessResult.missingElements() != null && !readinessResult.missingElements().isEmpty()) {
                sb.append("\n当前缺少的关键信息：");
                sb.append(String.join("、", readinessResult.missingElements()));
            }

            sb.append("\n\n请补充以上信息，我将为您安排最合适的团队来完成任务。");

            return sb.toString();
        });
    }
}
