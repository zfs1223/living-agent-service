package com.livingagent.core.autonomy;

/**
 * 执行能力解析器接口。
 * 职责：将 LLM 开放输出的任务意图归一到有限的 executionCapability/artifactType/executionMode。
 *
 * 设计原则：任务意图可以开放，执行能力必须收敛。
 * 无法归一时进入澄清或人工介入，不允许直接抛错或乱执行。
 */
public interface ExecutionCapabilityResolver {

    /**
     * 解析执行能力。
     * 实现策略：规则兜底优先 → LLM 判断补充 → 枚举校验 → 置信度检查 → 无法归一则 NEEDS_CLARIFICATION / HUMAN_HANDOFF
     */
    ExecutionCapabilityResolution resolve(ExecutionCapabilityRequest request);
}
