package com.livingagent.core.autonomy.impl;

import com.livingagent.core.autonomy.RequirementReadinessEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 规则版需求就绪评估器。
 *
 * <p>不依赖 LLM，通过确定性规则判断需求是否足够明确。
 * 适用于低延迟场景或 LLM 不可用时的降级方案。
 */
public class DefaultRequirementReadinessEvaluator implements RequirementReadinessEvaluator {

    private static final Logger log = LoggerFactory.getLogger(DefaultRequirementReadinessEvaluator.class);

    /** 明确的动作关键词 */
    private static final Pattern ACTION_PATTERN = Pattern.compile(
        "(开发|分析|总结|检查|修复|设计|部署|查询|创建|删除|修改|配置|测试|编写|实现|生成|提取|转换|迁移|优化|重构|审查|评估|对比|计算|翻译|解释|说明|列出|查找|搜索|推荐|安排|预约|审批|提交|发布|回滚|重启|监控|告警|备份|恢复)"
    );

    /** 疑问词 */
    private static final Pattern QUESTION_PATTERN = Pattern.compile(
        "(如何|怎么|什么|为什么|哪|是否|能否|可以|能不能|吗|呢|\\?)"
    );

    /** 请求/指令词 */
    private static final Pattern REQUEST_PATTERN = Pattern.compile(
        "(帮|请|要|需要|希望|想要|能不能|可以.*吗|请帮我|帮我|麻烦)"
    );

    @Override
    public RequirementReadinessResult evaluate(String userMessage, String department, String sessionId) {
        // 规则1：空消息
        if (userMessage == null || userMessage.isBlank()) {
            log.debug("Rule-based readiness: INSUFFICIENT (empty message)");
            return RequirementReadinessResult.insufficient(
                List.of("请描述您需要完成的任务"), "Empty message");
        }

        String trimmed = userMessage.trim();

        // 规则2：过短消息
        if (trimmed.length() < 3) {
            log.debug("Rule-based readiness: INSUFFICIENT (too short: {})", trimmed.length());
            return RequirementReadinessResult.insufficient(
                List.of("请提供更详细的任务描述"), "Message too short");
        }

        // 规则3：包含明确动作词
        if (ACTION_PATTERN.matcher(trimmed).find()) {
            log.debug("Rule-based readiness: SUFFICIENT (action keyword detected)");
            return RequirementReadinessResult.sufficient(0.9, "Action keyword detected");
        }

        // 规则4：包含疑问词
        if (QUESTION_PATTERN.matcher(trimmed).find()) {
            log.debug("Rule-based readiness: SUFFICIENT (question pattern detected)");
            return RequirementReadinessResult.sufficient(0.85, "Question pattern detected");
        }

        // 规则5：包含请求/指令词
        if (REQUEST_PATTERN.matcher(trimmed).find()) {
            log.debug("Rule-based readiness: SUFFICIENT (request pattern detected)");
            return RequirementReadinessResult.sufficient(0.85, "Request pattern detected");
        }

        // 规则6：较长消息（>=10字符）默认认为需求明确
        if (trimmed.length() >= 10) {
            log.debug("Rule-based readiness: SUFFICIENT (sufficient length: {})", trimmed.length());
            return RequirementReadinessResult.sufficient(0.75, "Sufficient message length");
        }

        // 规则7：中等长度（3-9字符）无明确关键词
        log.debug("Rule-based readiness: PARTIALLY_SUFFICIENT (ambiguous short message)");
        return RequirementReadinessResult.partiallySufficient(
            0.5,
            List.of("任务描述不够详细"),
            List.of("请补充您希望完成的具体目标"),
            "Short message without clear intent"
        );
    }
}
