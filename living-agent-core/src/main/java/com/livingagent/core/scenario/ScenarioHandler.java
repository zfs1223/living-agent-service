package com.livingagent.core.scenario;

import com.livingagent.core.planner.TaskPlan;
import java.util.Map;

public interface ScenarioHandler {
    
    String getScenarioType();
    
    TaskPlan createPlan(Map<String, Object> params);
    
    ScenarioResult execute(TaskPlan plan);
    
    boolean canHandle(String goal);
    
    int getPriority();
}
