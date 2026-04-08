package com.livingagent.core.workflow.handlers;

import com.livingagent.core.workflow.PhaseHandler;
import com.livingagent.core.workflow.WorkflowContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class MarketAnalysisHandler implements PhaseHandler {

    private static final Logger log = LoggerFactory.getLogger(MarketAnalysisHandler.class);

    @Override
    public void execute(WorkflowContext context) {
        log.info("Executing market analysis for project: {}", context.getProjectName());
        
        Map<String, Object> result = new HashMap<>();
        result.put("status", "in_progress");
        result.put("tasks", new String[]{
            "竞品分析",
            "市场趋势调研",
            "用户需求收集",
            "市场规模评估"
        });
        
        context.setData("analysisStarted", true);
        context.completePhase(result);
    }

    @Override
    public String getName() { return "市场分析处理器"; }

    @Override
    public String getDescription() { return "执行市场调研、竞品分析和用户需求收集"; }
}
