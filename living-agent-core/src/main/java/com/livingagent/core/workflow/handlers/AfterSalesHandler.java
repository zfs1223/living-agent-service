package com.livingagent.core.workflow.handlers;

import com.livingagent.core.workflow.PhaseHandler;
import com.livingagent.core.workflow.WorkflowContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class AfterSalesHandler implements PhaseHandler {

    private static final Logger log = LoggerFactory.getLogger(AfterSalesHandler.class);

    @Override
    public void execute(WorkflowContext context) {
        log.info("Executing after-sales phase for project: {}", context.getProjectName());
        
        Map<String, Object> result = new HashMap<>();
        result.put("status", "in_progress");
        result.put("tasks", new String[]{
            "用户反馈收集",
            "问题排查",
            "迭代改进",
            "文档更新"
        });
        
        context.setData("afterSalesStarted", true);
        context.completePhase(result);
    }

    @Override
    public String getName() { return "售后服务处理器"; }

    @Override
    public String getDescription() { return "处理用户反馈和进行迭代改进"; }
}
