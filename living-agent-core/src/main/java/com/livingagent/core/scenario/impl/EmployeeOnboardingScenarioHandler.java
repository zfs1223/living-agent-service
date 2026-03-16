package com.livingagent.core.scenario.impl;

import com.livingagent.core.planner.TaskPlan;
import com.livingagent.core.planner.TaskStep;
import com.livingagent.core.scenario.ScenarioHandler;
import com.livingagent.core.scenario.ScenarioResult;
import com.livingagent.core.tool.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class EmployeeOnboardingScenarioHandler implements ScenarioHandler {
    
    private static final Logger log = LoggerFactory.getLogger(EmployeeOnboardingScenarioHandler.class);
    
    private final ToolExecutor toolExecutor;
    
    @Autowired
    public EmployeeOnboardingScenarioHandler(ToolExecutor toolExecutor) {
        this.toolExecutor = toolExecutor;
    }
    
    @Override
    public String getScenarioType() {
        return "employee_onboarding";
    }
    
    @Override
    public TaskPlan createPlan(Map<String, Object> params) {
        String employeeName = (String) params.getOrDefault("name", "新员工");
        String department = (String) params.getOrDefault("department", "tech");
        String position = (String) params.getOrDefault("position", "工程师");
        Date startDate = (Date) params.getOrDefault("startDate", new Date());
        
        TaskPlan plan = new TaskPlan("新员工入职: " + employeeName);
        plan.getContext().put("employeeName", employeeName);
        plan.getContext().put("department", department);
        plan.getContext().put("position", position);
        plan.getContext().put("startDate", startDate);
        
        plan.addStep(createStep(0, "创建员工档案", "create_employee_record", "HrBrain"));
        plan.addStep(createStep(1, "创建企业账号", "create_account", "TechBrain"));
        plan.addStep(createStep(2, "分配工位", "assign_desk", "AdminBrain"));
        plan.addStep(createStep(3, "分配设备", "assign_equipment", "AdminBrain"));
        plan.addStep(createStep(4, "开通系统权限", "grant_permissions", "TechBrain"));
        plan.addStep(createStep(5, "添加到钉钉群", "add_to_dingtalk", "AdminBrain"));
        plan.addStep(createStep(6, "发送入职通知", "send_notification", "AdminBrain"));
        plan.addStep(createStep(7, "安排入职培训", "schedule_training", "HrBrain"));
        
        plan.setAssignedBrain("HrBrain");
        
        return plan;
    }
    
    @Override
    public ScenarioResult execute(TaskPlan plan) {
        ScenarioResult result = new ScenarioResult();
        result.start();
        result.setScenarioId(plan.getPlanId());
        
        try {
            log.info("Executing employee onboarding scenario: {}", plan.getGoal());
            
            Map<String, Object> context = plan.getContext();
            String employeeName = (String) context.get("employeeName");
            String department = (String) context.get("department");
            String position = (String) context.get("position");
            
            String employeeId = createEmployeeRecord(employeeName, department, position);
            result.addData("employeeId", employeeId);
            result.addMetric("step1", "员工档案创建成功");
            
            String account = createAccount(employeeId, employeeName);
            result.addData("account", account);
            result.addMetric("step2", "企业账号创建成功");
            
            String desk = assignDesk(employeeId, department);
            result.addData("desk", desk);
            result.addMetric("step3", "工位分配成功: " + desk);
            
            String equipment = assignEquipment(employeeId, position);
            result.addData("equipment", equipment);
            result.addMetric("step4", "设备分配成功: " + equipment);
            
            List<String> permissions = grantPermissions(employeeId, department, position);
            result.addData("permissions", permissions);
            result.addMetric("step5", "权限开通成功: " + permissions.size() + "个系统");
            
            List<String> groups = addToDingTalk(employeeId, employeeName, department);
            result.addData("dingtalkGroups", groups);
            result.addMetric("step6", "钉钉群添加成功: " + groups.size() + "个群");
            
            boolean notified = sendNotification(employeeId, employeeName, account, desk);
            result.addMetric("step7", "入职通知发送: " + (notified ? "成功" : "失败"));
            
            String training = scheduleTraining(employeeId, employeeName, department);
            result.addData("trainingSchedule", training);
            result.addMetric("step8", "入职培训安排成功");
            
            result.end();
            result.setSuccess(true);
            result.setMessage("员工入职流程完成: " + employeeName);
            
        } catch (Exception e) {
            log.error("Failed to execute employee onboarding scenario", e);
            result.end();
            result.setSuccess(false);
            result.setError(e.getMessage());
        }
        
        return result;
    }
    
    @Override
    public boolean canHandle(String goal) {
        if (goal == null) return false;
        String lower = goal.toLowerCase();
        return lower.contains("入职") || lower.contains("onboarding") || 
               lower.contains("新员工") || lower.contains("办理入职");
    }
    
    @Override
    public int getPriority() {
        return 100;
    }
    
    private TaskStep createStep(int index, String description, String action, String neuron) {
        TaskStep step = new TaskStep(description, action);
        step.setStepIndex(index);
        step.setAssignedNeuron(neuron);
        return step;
    }
    
    private String createEmployeeRecord(String name, String department, String position) {
        String employeeId = "EMP" + System.currentTimeMillis();
        log.info("Created employee record: {} for {} in {} as {}", employeeId, name, department, position);
        return employeeId;
    }
    
    private String createAccount(String employeeId, String name) {
        String account = name.toLowerCase().replace(" ", ".");
        log.info("Created account: {} for employee: {}", account, employeeId);
        return account;
    }
    
    private String assignDesk(String employeeId, String department) {
        String desk = department + "-D" + (int)(Math.random() * 100);
        log.info("Assigned desk: {} to employee: {}", desk, employeeId);
        return desk;
    }
    
    private String assignEquipment(String employeeId, String position) {
        String equipment = "MacBook Pro, 显示器, 键盘, 鼠标";
        log.info("Assigned equipment: {} to employee: {}", equipment, employeeId);
        return equipment;
    }
    
    private List<String> grantPermissions(String employeeId, String department, String position) {
        List<String> permissions = new ArrayList<>();
        permissions.add("GitLab");
        permissions.add("Jira");
        permissions.add("Confluence");
        permissions.add("Jenkins");
        if ("tech".equals(department)) {
            permissions.add("AWS Console");
            permissions.add("Kubernetes");
        }
        log.info("Granted permissions: {} to employee: {}", permissions, employeeId);
        return permissions;
    }
    
    private List<String> addToDingTalk(String employeeId, String name, String department) {
        List<String> groups = new ArrayList<>();
        groups.add("全体员工群");
        groups.add(department + "部门群");
        groups.add("技术交流群");
        log.info("Added employee {} to DingTalk groups: {}", employeeId, groups);
        return groups;
    }
    
    private boolean sendNotification(String employeeId, String name, String account, String desk) {
        log.info("Sending onboarding notification to {} (account: {}, desk: {})", name, account, desk);
        return true;
    }
    
    private String scheduleTraining(String employeeId, String name, String department) {
        String training = "入职培训: 公司文化、制度介绍、安全培训";
        log.info("Scheduled training for employee {}: {}", employeeId, training);
        return training;
    }
}
