package com.livingagent.core.autonomy.impl;

import com.livingagent.core.autonomy.*;
import com.livingagent.core.brain.BrainRegistry;
import com.livingagent.core.brain.impl.MainBrain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class LlmBasedMainBrainResponseComposer implements MainBrainResponseComposer {

    private static final Logger log = LoggerFactory.getLogger(LlmBasedMainBrainResponseComposer.class);

    private static final String COMPOSE_SYSTEM_PROMPT = """
        你是企业主大脑，负责根据任务执行情况生成面向用户的最终回复。
        
        要求：
        1. 根据用户角色、任务类型、执行结果动态调整回复内容
        2. 简洁说明任务目标、执行团队、当前状态和关键产出
        3. 如有风险或待确认事项，明确指出
        4. 不要使用固定模板，用自然语言表达
        5. 如果执行尚未完成，告知预计等待时间或下一步计划
        """;

    private final BrainRegistry brainRegistry;
    private final DefaultMainBrainResponseComposer fallbackComposer;

    public LlmBasedMainBrainResponseComposer(BrainRegistry brainRegistry) {
        this.brainRegistry = brainRegistry;
        this.fallbackComposer = new DefaultMainBrainResponseComposer();
    }

    @Override
    public String composeUserResponse(
            String requestId,
            String department,
            MainBrainTaskPlan mainBrainTaskPlan,
            List<EmployeeWorkAssignment> employeeAssignments,
            String brainRawResponse,
            DepartmentExecutionResult executionResult) {

        MainBrain mainBrain = brainRegistry.get(MainBrain.ID)
            .filter(b -> b instanceof MainBrain)
            .map(b -> (MainBrain) b)
            .orElse(null);

        if (mainBrain == null) {
            log.debug("MainBrain unavailable for response composition, using template fallback");
            return fallbackComposer.composeUserResponse(requestId, department, mainBrainTaskPlan, employeeAssignments, brainRawResponse, executionResult);
        }

        try {
            String userPrompt = buildComposePrompt(department, mainBrainTaskPlan, employeeAssignments, brainRawResponse, executionResult);
            String llmResponse = mainBrain.callLlm(COMPOSE_SYSTEM_PROMPT, userPrompt);

            if (llmResponse != null && !llmResponse.isBlank()) {
                return llmResponse;
            }

            return fallbackComposer.composeUserResponse(requestId, department, mainBrainTaskPlan, employeeAssignments, brainRawResponse, executionResult);

        } catch (Exception e) {
            log.warn("LLM response composition failed: {}, using template fallback", e.getMessage());
            return fallbackComposer.composeUserResponse(requestId, department, mainBrainTaskPlan, employeeAssignments, brainRawResponse, executionResult);
        }
    }

    @Override
    public String composeProgressResponse(
            String requestId,
            String department,
            MainBrainTaskPlan mainBrainTaskPlan,
            List<EmployeeWorkAssignment> employeeAssignments,
            DepartmentExecutionResult executionResult) {

        MainBrain mainBrain = brainRegistry.get(MainBrain.ID)
            .filter(b -> b instanceof MainBrain)
            .map(b -> (MainBrain) b)
            .orElse(null);

        if (mainBrain == null) {
            return fallbackComposer.composeProgressResponse(requestId, department, mainBrainTaskPlan, employeeAssignments, executionResult);
        }

        try {
            StringBuilder sb = new StringBuilder();
            sb.append("这是一个任务进度查询，请简要回复当前执行状态。\n\n");
            sb.append(buildContextSummary(department, mainBrainTaskPlan, employeeAssignments, executionResult));

            String llmResponse = mainBrain.callLlm(COMPOSE_SYSTEM_PROMPT, sb.toString());
            if (llmResponse != null && !llmResponse.isBlank()) {
                return llmResponse;
            }

            return fallbackComposer.composeProgressResponse(requestId, department, mainBrainTaskPlan, employeeAssignments, executionResult);

        } catch (Exception e) {
            return fallbackComposer.composeProgressResponse(requestId, department, mainBrainTaskPlan, employeeAssignments, executionResult);
        }
    }

    private String buildComposePrompt(
            String department,
            MainBrainTaskPlan mainBrainTaskPlan,
            List<EmployeeWorkAssignment> employeeAssignments,
            String brainRawResponse,
            DepartmentExecutionResult executionResult) {

        StringBuilder sb = new StringBuilder();
        sb.append(buildContextSummary(department, mainBrainTaskPlan, employeeAssignments, executionResult));

        if (brainRawResponse != null && !brainRawResponse.isBlank()) {
            sb.append("\n部门大脑原始回复:\n").append(brainRawResponse).append("\n");
        }

        sb.append("\n请根据以上信息生成面向用户的最终回复。");
        return sb.toString();
    }

    private String buildContextSummary(
            String department,
            MainBrainTaskPlan mainBrainTaskPlan,
            List<EmployeeWorkAssignment> employeeAssignments,
            DepartmentExecutionResult executionResult) {

        StringBuilder sb = new StringBuilder();

        if (mainBrainTaskPlan != null) {
            sb.append("任务目标: ").append(mainBrainTaskPlan.goal()).append("\n");
            sb.append("任务类型: ").append(mainBrainTaskPlan.taskType()).append("\n");
        }

        sb.append("部门: ").append(department != null ? department : "未知").append("\n");

        if (employeeAssignments != null && !employeeAssignments.isEmpty()) {
            sb.append("执行团队:\n");
            for (EmployeeWorkAssignment a : employeeAssignments) {
                sb.append("- ").append(a.employeeName()).append("（").append(a.role()).append("）\n");
            }
        }

        if (executionResult != null) {
            sb.append("执行状态: ").append(executionResult.status()).append("\n");
            sb.append("已分配员工: ").append(executionResult.dispatchedAssignments() != null ? executionResult.dispatchedAssignments().size() : 0).append("\n");
        }

        return sb.toString();
    }
}
