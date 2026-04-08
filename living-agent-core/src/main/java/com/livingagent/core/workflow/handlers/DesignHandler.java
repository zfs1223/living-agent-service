package com.livingagent.core.workflow.handlers;

import com.livingagent.core.workflow.PhaseHandler;
import com.livingagent.core.workflow.WorkflowContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class DesignHandler implements PhaseHandler {

    private static final Logger log = LoggerFactory.getLogger(DesignHandler.class);

    @Override
    public void execute(WorkflowContext context) {
        log.info("Executing design phase for project: {}", context.getProjectName());
        
        Map<String, Object> result = new HashMap<>();
        result.put("status", "in_progress");
        result.put("tasks", new String[]{
            "架构设计",
            "技术选型",
            "接口设计",
            "数据库设计"
        });
        
        context.setData("designStarted", true);
        context.completePhase(result);
    }

    @Override
    public String getName() { return "方案设计处理器"; }

    @Override
    public String getDescription() { return "进行架构设计、技术选型和接口设计"; }
}
