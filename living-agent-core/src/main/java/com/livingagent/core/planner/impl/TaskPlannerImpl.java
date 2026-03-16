package com.livingagent.core.planner.impl;

import com.livingagent.core.brain.BrainRegistry;
import com.livingagent.core.planner.TaskPlan;
import com.livingagent.core.planner.TaskPlanner;
import com.livingagent.core.planner.TaskStep;
import com.livingagent.core.model.ModelManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class TaskPlannerImpl implements TaskPlanner {
    
    private static final Logger log = LoggerFactory.getLogger(TaskPlannerImpl.class);
    
    private static final Map<String, String> SCENARIO_TEMPLATES = new HashMap<>();
    
    static {
        SCENARIO_TEMPLATES.put("周报", "weekly_report");
        SCENARIO_TEMPLATES.put("日报", "daily_report");
        SCENARIO_TEMPLATES.put("入职", "employee_onboarding");
        SCENARIO_TEMPLATES.put("请假", "leave_request");
        SCENARIO_TEMPLATES.put("报销", "expense_claim");
        SCENARIO_TEMPLATES.put("会议", "meeting_schedule");
    }
    
    private final ModelManager modelManager;
    private final BrainRegistry brainRegistry;
    
    @Autowired
    public TaskPlannerImpl(ModelManager modelManager, BrainRegistry brainRegistry) {
        this.modelManager = modelManager;
        this.brainRegistry = brainRegistry;
    }
    
    @Override
    public TaskPlan createPlan(String goal, Map<String, Object> context) {
        log.info("Creating plan for goal: {}", goal);
        
        TaskPlan plan = new TaskPlan(goal);
        plan.setContext(context != null ? new HashMap<>(context) : new HashMap<>());
        
        String scenarioType = identifyScenario(goal);
        plan.getContext().put("scenarioType", scenarioType);
        
        double complexity = estimateComplexity(goal);
        plan.setEstimatedComplexity(complexity);
        
        List<TaskStep> steps = decomposeTask(goal, plan.getContext());
        plan.addSteps(steps);
        
        String assignedBrain = assignBrain(goal, scenarioType);
        plan.setAssignedBrain(assignedBrain);
        
        plan.setStatus(TaskPlan.PlanStatus.CREATED);
        
        log.info("Plan created: {} steps, complexity={}, brain={}", 
                steps.size(), complexity, assignedBrain);
        
        return plan;
    }
    
    @Override
    public TaskPlan refinePlan(TaskPlan currentPlan, String feedback) {
        log.info("Refining plan {} based on feedback", currentPlan.getPlanId());
        
        List<TaskStep> newSteps = new ArrayList<>(currentPlan.getSteps());
        
        TaskStep adjustmentStep = new TaskStep(
                "根据反馈调整: " + feedback,
                "adjust_plan"
        );
        adjustmentStep.setAssignedNeuron("MainBrainNeuron");
        newSteps.add(adjustmentStep);
        
        currentPlan.setSteps(newSteps);
        currentPlan.getContext().put("refinementFeedback", feedback);
        
        return currentPlan;
    }
    
    @Override
    public List<TaskStep> decomposeTask(String task, Map<String, Object> context) {
        List<TaskStep> steps = new ArrayList<>();
        
        String scenarioType = (String) context.get("scenarioType");
        
        if (scenarioType != null) {
            steps = createStepsFromTemplate(scenarioType, task, context);
        }
        
        if (steps.isEmpty()) {
            steps = createDefaultSteps(task, context);
        }
        
        return steps;
    }
    
    @Override
    public TaskStep getNextStep(TaskPlan plan, Map<String, Object> currentState) {
        Map<String, TaskStep> completedSteps = new HashMap<>();
        for (TaskStep step : plan.getSteps()) {
            if (step.getStatus() == TaskStep.StepStatus.COMPLETED) {
                completedSteps.put(step.getStepId(), step);
            }
        }
        
        for (TaskStep step : plan.getSteps()) {
            if (step.getStatus() == TaskStep.StepStatus.PENDING && 
                step.canExecute(completedSteps)) {
                return step;
            }
        }
        
        return null;
    }
    
    @Override
    public boolean isPlanComplete(TaskPlan plan) {
        return plan.getSteps().stream()
                .allMatch(s -> s.getStatus() == TaskStep.StepStatus.COMPLETED ||
                              s.getStatus() == TaskStep.StepStatus.SKIPPED);
    }
    
    @Override
    public boolean isStepExecutable(TaskStep step, Map<String, Object> currentState) {
        return step.getStatus() == TaskStep.StepStatus.PENDING;
    }
    
    @Override
    public double estimateComplexity(String goal) {
        double complexity = 1.0;
        
        String lowerGoal = goal.toLowerCase();
        
        if (lowerGoal.contains("多个") || lowerGoal.contains("所有") || lowerGoal.contains("批量")) {
            complexity += 2.0;
        }
        if (lowerGoal.contains("协调") || lowerGoal.contains("协作") || lowerGoal.contains("跨部门")) {
            complexity += 3.0;
        }
        if (lowerGoal.contains("分析") || lowerGoal.contains("报告") || lowerGoal.contains("汇总")) {
            complexity += 1.5;
        }
        if (lowerGoal.contains("审批") || lowerGoal.contains("确认") || lowerGoal.contains("批准")) {
            complexity += 1.0;
        }
        if (lowerGoal.contains("紧急") || lowerGoal.contains("立即") || lowerGoal.contains("马上")) {
            complexity += 0.5;
        }
        
        return Math.min(complexity, 10.0);
    }
    
    @Override
    public List<String> identifyDependencies(List<TaskStep> steps) {
        List<String> dependencies = new ArrayList<>();
        
        for (int i = 1; i < steps.size(); i++) {
            TaskStep current = steps.get(i);
            TaskStep previous = steps.get(i - 1);
            current.addDependency(previous.getStepId());
            dependencies.add(previous.getStepId() + " -> " + current.getStepId());
        }
        
        return dependencies;
    }
    
    private String identifyScenario(String goal) {
        for (Map.Entry<String, String> entry : SCENARIO_TEMPLATES.entrySet()) {
            if (goal.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return "general";
    }
    
    private List<TaskStep> createStepsFromTemplate(String scenarioType, String task, 
                                                    Map<String, Object> context) {
        List<TaskStep> steps = new ArrayList<>();
        
        switch (scenarioType) {
            case "weekly_report":
                steps.add(createStep(0, "收集本周工作数据", "collect_data", "TechBrain"));
                steps.add(createStep(1, "分析工作进度和成果", "analyze_progress", "TechBrain"));
                steps.add(createStep(2, "生成周报内容", "generate_report", "AdminBrain"));
                steps.add(createStep(3, "发送周报", "send_report", "AdminBrain"));
                break;
                
            case "employee_onboarding":
                steps.add(createStep(0, "创建员工账号", "create_account", "HrBrain"));
                steps.add(createStep(1, "分配工位和设备", "assign_resources", "AdminBrain"));
                steps.add(createStep(2, "开通系统权限", "grant_permissions", "TechBrain"));
                steps.add(createStep(3, "发送入职通知", "send_notification", "AdminBrain"));
                steps.add(createStep(4, "安排入职培训", "schedule_training", "HrBrain"));
                break;
                
            case "leave_request":
                steps.add(createStep(0, "验证假期余额", "check_balance", "HrBrain"));
                steps.add(createStep(1, "创建请假申请", "create_request", "HrBrain"));
                steps.add(createStep(2, "发送审批通知", "notify_approver", "AdminBrain"));
                steps.add(createStep(3, "更新考勤记录", "update_attendance", "HrBrain"));
                break;
                
            case "expense_claim":
                steps.add(createStep(0, "验证报销单据", "verify_receipts", "FinanceBrain"));
                steps.add(createStep(1, "创建报销申请", "create_claim", "FinanceBrain"));
                steps.add(createStep(2, "提交审批", "submit_approval", "FinanceBrain"));
                steps.add(createStep(3, "处理付款", "process_payment", "FinanceBrain"));
                break;
                
            case "meeting_schedule":
                steps.add(createStep(0, "检查参会者日程", "check_availability", "AdminBrain"));
                steps.add(createStep(1, "预订会议室", "book_room", "AdminBrain"));
                steps.add(createStep(2, "发送会议邀请", "send_invitation", "AdminBrain"));
                steps.add(createStep(3, "设置会议提醒", "set_reminder", "AdminBrain"));
                break;
                
            default:
                steps.add(createStep(0, "理解任务需求", "understand_task", "MainBrainNeuron"));
                steps.add(createStep(1, "执行任务", "execute_task", "TechBrain"));
                steps.add(createStep(2, "验证结果", "verify_result", "TechBrain"));
        }
        
        return steps;
    }
    
    private List<TaskStep> createDefaultSteps(String task, Map<String, Object> context) {
        List<TaskStep> steps = new ArrayList<>();
        
        steps.add(createStep(0, "分析任务: " + task, "analyze", "MainBrainNeuron"));
        steps.add(createStep(1, "执行任务", "execute", "TechBrain"));
        steps.add(createStep(2, "验证结果", "verify", "TechBrain"));
        
        return steps;
    }
    
    private TaskStep createStep(int index, String description, String action, String neuron) {
        TaskStep step = new TaskStep(description, action);
        step.setStepIndex(index);
        step.setAssignedNeuron(neuron);
        return step;
    }
    
    private String assignBrain(String goal, String scenarioType) {
        if (scenarioType.startsWith("employee") || scenarioType.contains("leave")) {
            return "HrBrain";
        }
        if (scenarioType.contains("expense") || scenarioType.contains("finance")) {
            return "FinanceBrain";
        }
        if (scenarioType.contains("meeting") || scenarioType.contains("report")) {
            return "AdminBrain";
        }
        
        String lowerGoal = goal.toLowerCase();
        if (lowerGoal.contains("代码") || lowerGoal.contains("部署") || lowerGoal.contains("bug")) {
            return "TechBrain";
        }
        if (lowerGoal.contains("客户") || lowerGoal.contains("投诉")) {
            return "CsBrain";
        }
        if (lowerGoal.contains("合同") || lowerGoal.contains("法律")) {
            return "LegalBrain";
        }
        if (lowerGoal.contains("销售") || lowerGoal.contains("客户")) {
            return "SalesBrain";
        }
        
        return "TechBrain";
    }
}
