package com.livingagent.core.tool;

import com.livingagent.core.autonomy.EmployeeWorkAssignment;

import java.util.List;
import java.util.Map;

/**
 * 工具调用协议（64-C-2）
 * 借鉴 CLI-Anything 的 utils/<software>_backend.py 模式，
 * 统一工具调用：意图解析 → 调用计划 → 执行 → 格式化输出。
 */
public interface ToolInvocationProtocol {

    /** 解析任务意图到工具调用计划 */
    InvocationPlan resolve(String taskDescription, EmployeeWorkAssignment assignment);

    /** 执行工具调用 */
    InvocationResult invoke(InvocationPlan plan, ToolContext context);

    /** 格式化工具输出为用户可读结果 */
    String formatResult(InvocationResult result);

    /** 工具调用计划 */
    record InvocationPlan(
        String toolName,
        String action,
        Map<String, Object> params,
        List<String> fallbackTools,
        boolean requiresLlmSummary
    ) {
        public static InvocationPlan direct(String toolName, String action, Map<String, Object> params) {
            return new InvocationPlan(toolName, action, params, List.of(), false);
        }

        public static InvocationPlan withLlmSummary(String toolName, String action, Map<String, Object> params) {
            return new InvocationPlan(toolName, action, params, List.of(), true);
        }

        public static InvocationPlan withFallbacks(String toolName, String action,
                                                    Map<String, Object> params, List<String> fallbacks) {
            return new InvocationPlan(toolName, action, params, fallbacks, false);
        }
    }

    /** 工具调用结果 */
    record InvocationResult(
        boolean success,
        Object data,
        String rawOutput,
        Map<String, Object> metadata,
        long durationMs
    ) {
        public static InvocationResult ok(Object data, String rawOutput, long durationMs) {
            return new InvocationResult(true, data, rawOutput, Map.of(), durationMs);
        }

        public static InvocationResult ok(Object data, String rawOutput,
                                           Map<String, Object> metadata, long durationMs) {
            return new InvocationResult(true, data, rawOutput, metadata, durationMs);
        }

        public static InvocationResult fail(String error, long durationMs) {
            return new InvocationResult(false, null, error, Map.of(), durationMs);
        }
    }
}
