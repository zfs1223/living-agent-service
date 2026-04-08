package com.livingagent.core.workflow.handlers;

import com.livingagent.core.workflow.PhaseHandler;
import com.livingagent.core.workflow.WorkflowContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class OperationHandler implements PhaseHandler {

    private static final Logger log = LoggerFactory.getLogger(OperationHandler.class);

    @Override
    public void execute(WorkflowContext context) {
        log.info("Executing operation phase for project: {}", context.getProjectName());
        
        Map<String, Object> result = new HashMap<>();
        result.put("status", "in_progress");
        result.put("tasks", new String[]{
            "监控配置",
            "告警设置",
            "日志分析",
            "性能优化"
        });
        
        context.setData("operationStarted", true);
        context.completePhase(result);
    }

    @Override
    public String getName() { return "运营维护处理器"; }

    @Override
    public String getDescription() { return "配置监控告警和进行性能优化"; }
}
