package com.livingagent.core.autonomy;

import java.util.List;

/**
 * 执行能力解析请求。
 * 输入：用户消息 + 任务类型 + 意图 + 交付物 + 技能需求 + 部门 + 建议员工
 */
public record ExecutionCapabilityRequest(
    String userMessage,
    String taskType,
    String intent,
    List<String> deliverables,
    List<String> requiredSkills,
    String department,
    List<String> suggestedEmployeeCodes
) {
    public static ExecutionCapabilityRequest of(String userMessage, String taskType, String intent,
            List<String> deliverables, List<String> requiredSkills, String department,
            List<String> suggestedEmployeeCodes) {
        return new ExecutionCapabilityRequest(
            userMessage,
            taskType,
            intent,
            deliverables != null ? List.copyOf(deliverables) : List.of(),
            requiredSkills != null ? List.copyOf(requiredSkills) : List.of(),
            department,
            suggestedEmployeeCodes != null ? List.copyOf(suggestedEmployeeCodes) : List.of()
        );
    }
}
