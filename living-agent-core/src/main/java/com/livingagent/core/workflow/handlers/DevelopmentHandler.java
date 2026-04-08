package com.livingagent.core.workflow.handlers;

import com.livingagent.core.workflow.PhaseHandler;
import com.livingagent.core.workflow.WorkflowContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class DevelopmentHandler implements PhaseHandler {

    private static final Logger log = LoggerFactory.getLogger(DevelopmentHandler.class);

    @Override
    public void execute(WorkflowContext context) {
        log.info("Executing development phase for project: {}", context.getProjectName());
        
        Map<String, Object> result = new HashMap<>();
        result.put("status", "in_progress");
        result.put("tasks", new String[]{
            "代码编写",
            "单元测试",
            "代码审查",
            "文档编写"
        });
        
        context.setData("developmentStarted", true);
        context.completePhase(result);
    }

    @Override
    public String getName() { return "开发实施处理器"; }

    @Override
    public String getDescription() { return "执行代码编写和单元测试"; }
}
