package com.livingagent.core.autonomy.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.autonomy.*;
import com.livingagent.core.brain.BrainRegistry;
import com.livingagent.core.brain.impl.MainBrain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class LlmAssignmentReadinessEvaluator implements AssignmentReadinessEvaluator {

    private static final Logger log = LoggerFactory.getLogger(LlmAssignmentReadinessEvaluator.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String READINESS_SYSTEM_PROMPT = """
        你是企业主大脑，负责评估任务分派的**执行准备度**。
        
        你的职责仅限于判断：当前分派方案是否具备执行条件。
        你不得重新审视用户的核心需求是否完整，不得追问用户原始意图。
        核心需求的完整性由上游 RequirementReadinessEvaluator 负责，你只关注执行层面的准备度。
        
        你需要判断：
        1. 分派的员工是否匹配任务类型（技能匹配度）
        2. 交付物定义是否足够具体（可执行性）
        3. 验收标准是否可量化（可验证性）
        4. 员工分配是否有空缺（资源完备性）
        
        你不得判断：
        - 用户需求是否完整或需要补充
        - 任务目标是否合理或需要调整
        - 是否需要向用户追问更多信息
        
        你必须只输出一个合法的JSON对象：
        {
          "status": "READY",
          "readinessScore": 0.9,
          "blockingIssues": [],
          "comment": "评估意见"
        }
        
        status 仅可选：READY / PARTIALLY_READY / BLOCKED
        - READY: 所有执行条件具备，可以立即开始
        - PARTIALLY_READY: 大部分条件具备，部分可边执行边完善
        - BLOCKED: 存在执行层面的硬性阻塞（如缺少关键员工、交付物未定义）
        
        readinessScore: 0-1
        blockingIssues: 仅列出执行层面的阻塞项（如"缺少前端开发员工"）
        
        注意：不要输出 clarificationQuestions 字段，执行准备度评估不需要向用户追问。
        """;

    private final BrainRegistry brainRegistry;

    public LlmAssignmentReadinessEvaluator(BrainRegistry brainRegistry) {
        this.brainRegistry = brainRegistry;
    }

    @Override
    public ReadinessEvaluation evaluate(
            MainBrainTaskPlan mainBrainTaskPlan,
            DepartmentTaskPlan departmentTaskPlan,
            List<EmployeeWorkAssignment> assignments) {

        if (assignments == null || assignments.isEmpty()) {
            return new ReadinessEvaluation(
                ReadinessStatus.BLOCKED, 0.0,
                List.of("没有分派任何员工"),
                List.of("请明确需要哪些角色参与此任务"),
                Map.of()
            );
        }

        MainBrain mainBrain = brainRegistry.get(MainBrain.ID)
            .filter(b -> b instanceof MainBrain)
            .map(b -> (MainBrain) b)
            .orElse(null);

        if (mainBrain == null) {
            return ruleBasedEvaluate(mainBrainTaskPlan, departmentTaskPlan, assignments);
        }

        try {
            String userPrompt = buildReadinessPrompt(mainBrainTaskPlan, departmentTaskPlan, assignments);
            String llmResponse = mainBrain.callLlm(READINESS_SYSTEM_PROMPT, userPrompt);

            if (llmResponse == null || llmResponse.isBlank()) {
                return ruleBasedEvaluate(mainBrainTaskPlan, departmentTaskPlan, assignments);
            }

            return parseReadinessResponse(llmResponse);

        } catch (Exception e) {
            log.warn("LLM readiness evaluation failed: {}, using rule-based", e.getMessage());
            return ruleBasedEvaluate(mainBrainTaskPlan, departmentTaskPlan, assignments);
        }
    }

    private ReadinessEvaluation ruleBasedEvaluate(
            MainBrainTaskPlan mainBrainTaskPlan,
            DepartmentTaskPlan departmentTaskPlan,
            List<EmployeeWorkAssignment> assignments) {

        List<String> blockingIssues = new ArrayList<>();

        if (mainBrainTaskPlan.goal() == null || mainBrainTaskPlan.goal().isBlank()) {
            blockingIssues.add("任务目标不明确");
        }

        if (departmentTaskPlan.objective() == null || departmentTaskPlan.objective().isBlank()) {
            blockingIssues.add("部门目标不明确");
        }

        if (assignments.stream().anyMatch(a -> a.employeeNeuronId() == null || a.employeeNeuronId().isBlank())) {
            blockingIssues.add("部分员工缺少神经元ID");
        }

        if (mainBrainTaskPlan.deliverables() == null || mainBrainTaskPlan.deliverables().isEmpty()) {
            blockingIssues.add("交付物未定义");
        }

        ReadinessStatus status;
        double score;

        if (!blockingIssues.isEmpty()) {
            status = ReadinessStatus.BLOCKED;
            score = 0.3;
        } else if (mainBrainTaskPlan.acceptanceCriteria() == null || mainBrainTaskPlan.acceptanceCriteria().isEmpty()) {
            status = ReadinessStatus.PARTIALLY_READY;
            score = 0.7;
        } else {
            status = ReadinessStatus.READY;
            score = 0.9;
        }

        return new ReadinessEvaluation(status, score, blockingIssues, List.of(), Map.of());
    }

    private String buildReadinessPrompt(
            MainBrainTaskPlan mainBrainTaskPlan,
            DepartmentTaskPlan departmentTaskPlan,
            List<EmployeeWorkAssignment> assignments) {
        StringBuilder sb = new StringBuilder();
        sb.append("任务类型: ").append(mainBrainTaskPlan.taskType()).append("\n");
        sb.append("主责部门: ").append(mainBrainTaskPlan.primaryDepartment()).append("\n");
        sb.append("部门目标: ").append(departmentTaskPlan.objective()).append("\n");
        sb.append("交付物: ").append(mainBrainTaskPlan.deliverables() != null ? String.join("、", mainBrainTaskPlan.deliverables()) : "未定义").append("\n");
        sb.append("验收标准: ").append(mainBrainTaskPlan.acceptanceCriteria() != null ? String.join("；", mainBrainTaskPlan.acceptanceCriteria()) : "未定义").append("\n");
        sb.append("分派员工:\n");
        for (EmployeeWorkAssignment a : assignments) {
            sb.append("- ").append(a.employeeCode()).append(": ").append(a.employeeName())
              .append(" (").append(a.role()).append(")\n");
        }
        sb.append("\n请评估上述分派方案的执行准备度（仅关注执行层面，不评估需求完整性）。");
        return sb.toString();
    }

    private ReadinessEvaluation parseReadinessResponse(String llmResponse) {
        String json = extractJson(llmResponse);
        if (json == null) {
            return new ReadinessEvaluation(ReadinessStatus.READY, 0.8, List.of(), List.of(), Map.of());
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(json, Map.class);

            String statusStr = (String) parsed.getOrDefault("status", "READY");
            ReadinessStatus status;
            try {
                status = ReadinessStatus.valueOf(statusStr);
            } catch (IllegalArgumentException e) {
                status = ReadinessStatus.READY;
            }

            double score = parsed.get("readinessScore") instanceof Number n ? n.doubleValue() : 0.8;
            List<String> blockingIssues = parsed.get("blockingIssues") instanceof List<?> list
                ? list.stream().map(Object::toString).toList() : List.of();
            List<String> clarificationQuestions = parsed.get("clarificationQuestions") instanceof List<?> list
                ? list.stream().map(Object::toString).toList() : List.of();

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("evaluator_type", "llm_based");
            String comment = (String) parsed.getOrDefault("comment", null);
            if (comment != null) details.put("comment", comment);

            return new ReadinessEvaluation(status, score, blockingIssues, clarificationQuestions, details);

        } catch (JsonProcessingException e) {
            return new ReadinessEvaluation(ReadinessStatus.READY, 0.8, List.of(), List.of(), Map.of());
        }
    }

    private String extractJson(String response) {
        if (response == null) return null;
        String trimmed = response.trim();
        int braceStart = trimmed.indexOf('{');
        int braceEnd = trimmed.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) return trimmed.substring(braceStart, braceEnd + 1);
        return null;
    }
}
