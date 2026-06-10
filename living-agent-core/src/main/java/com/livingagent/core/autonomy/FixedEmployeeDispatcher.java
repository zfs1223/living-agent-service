package com.livingagent.core.autonomy;

import com.livingagent.core.knowledge.KnowledgeEntry;
import com.livingagent.core.knowledge.KnowledgeManager;

import java.util.List;

public interface FixedEmployeeDispatcher {

    List<EmployeeWorkAssignment> planAssignments(
        MainBrainTaskPlan mainBrainTaskPlan,
        DepartmentTaskPlan departmentTaskPlan,
        String sessionId,
        String userId
    );

    /**
     * 带知识注入的分派方法：在分派时注入相关知识到员工上下文
     */
    default List<EmployeeWorkAssignment> planAssignmentsWithKnowledge(
            MainBrainTaskPlan mainBrainTaskPlan,
            DepartmentTaskPlan departmentTaskPlan,
            String sessionId,
            String userId,
            KnowledgeManager knowledgeManager) {
        List<EmployeeWorkAssignment> assignments = planAssignments(
            mainBrainTaskPlan, departmentTaskPlan, sessionId, userId);

        if (knowledgeManager != null) {
            List<EmployeeWorkAssignment> enriched = new java.util.ArrayList<>();
            for (EmployeeWorkAssignment assignment : assignments) {
                try {
                    List<KnowledgeEntry> relevant = knowledgeManager.search(
                        assignment.objective(), 3);
                    if (!relevant.isEmpty()) {
                        StringBuilder knowledgeContext = new StringBuilder();
                        for (KnowledgeEntry entry : relevant) {
                            knowledgeContext.append("- ").append(entry.getKey())
                                .append(": ").append(entry.getContent()).append("\n");
                        }
                        enriched.add(assignment.addContext("relevant_knowledge", knowledgeContext.toString()));
                    } else {
                        enriched.add(assignment);
                    }
                } catch (Exception e) {
                    enriched.add(assignment);
                }
            }
            return enriched;
        }

        return assignments;
    }

    /**
     * 换人重派：排除已失败的员工，重新选择替代员工
     * @param plan 原始任务计划
     * @param failedEmployeeCodes 已失败的员工代码列表
     * @return 重新分派的员工工作分配
     */
    default List<EmployeeWorkAssignment> reassign(MainBrainTaskPlan plan, List<String> failedEmployeeCodes) {
        return List.of();
    }
}
