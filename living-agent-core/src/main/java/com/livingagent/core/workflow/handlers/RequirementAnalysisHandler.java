package com.livingagent.core.workflow.handlers;

import com.livingagent.core.workflow.PhaseHandler;
import com.livingagent.core.workflow.WorkflowContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class RequirementAnalysisHandler implements PhaseHandler {

    private static final Logger log = LoggerFactory.getLogger(RequirementAnalysisHandler.class);

    @Override
    public void execute(WorkflowContext context) {
        log.info("Executing requirement analysis for project: {}", context.getProjectName());
        
        Map<String, Object> result = new HashMap<>();
        result.put("status", "in_progress");
        result.put("tasks", new String[]{
            "需求文档解析",
            "功能点拆解",
            "优先级排序",
            "验收标准定义"
        });
        
        context.setData("requirementStarted", true);
        context.completePhase(result);
    }

    @Override
    public String getName() { return "需求分析处理器"; }

    @Override
    public String getDescription() { return "解析需求文档并拆解功能点"; }
}
