package com.livingagent.core.planner;

import java.util.List;
import java.util.Map;

public interface TaskPlanner {
    
    TaskPlan createPlan(String goal, Map<String, Object> context);
    
    TaskPlan refinePlan(TaskPlan currentPlan, String feedback);
    
    List<TaskStep> decomposeTask(String task, Map<String, Object> context);
    
    TaskStep getNextStep(TaskPlan plan, Map<String, Object> currentState);
    
    boolean isPlanComplete(TaskPlan plan);
    
    boolean isStepExecutable(TaskStep step, Map<String, Object> currentState);
    
    double estimateComplexity(String goal);
    
    List<String> identifyDependencies(List<TaskStep> steps);
}
