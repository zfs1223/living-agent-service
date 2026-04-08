package com.livingagent.core.workflow.handlers;

import com.livingagent.core.workflow.PhaseHandler;
import com.livingagent.core.workflow.WorkflowContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class TestingHandler implements PhaseHandler {

    private static final Logger log = LoggerFactory.getLogger(TestingHandler.class);

    @Override
    public void execute(WorkflowContext context) {
        log.info("Executing testing phase for project: {}", context.getProjectName());
        
        Map<String, Object> result = new HashMap<>();
        result.put("status", "in_progress");
        result.put("tasks", new String[]{
            "测试用例生成",
            "自动化测试",
            "性能测试",
            "Bug修复验证"
        });
        
        context.setData("testingStarted", true);
        context.completePhase(result);
    }

    @Override
    public String getName() { return "测试验收处理器"; }

    @Override
    public String getDescription() { return "执行测试用例生成和自动化测试"; }
}
