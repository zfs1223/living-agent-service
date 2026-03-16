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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Component
public class WeeklyReportScenarioHandler implements ScenarioHandler {
    
    private static final Logger log = LoggerFactory.getLogger(WeeklyReportScenarioHandler.class);
    
    private final ToolExecutor toolExecutor;
    
    @Autowired
    public WeeklyReportScenarioHandler(ToolExecutor toolExecutor) {
        this.toolExecutor = toolExecutor;
    }
    
    @Override
    public String getScenarioType() {
        return "weekly_report";
    }
    
    @Override
    public TaskPlan createPlan(Map<String, Object> params) {
        String userId = (String) params.getOrDefault("userId", "default");
        String department = (String) params.getOrDefault("department", "tech");
        
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY));
        
        TaskPlan plan = new TaskPlan("生成周报: " + userId);
        plan.getContext().put("userId", userId);
        plan.getContext().put("department", department);
        plan.getContext().put("weekStart", weekStart.toString());
        plan.getContext().put("weekEnd", weekEnd.toString());
        
        plan.addStep(createStep(0, "收集GitLab提交记录", "collect_gitlab_commits", "TechBrain"));
        plan.addStep(createStep(1, "收集Jira任务进度", "collect_jira_tasks", "TechBrain"));
        plan.addStep(createStep(2, "汇总工作内容", "summarize_work", "TechBrain"));
        plan.addStep(createStep(3, "生成周报文档", "generate_report", "AdminBrain"));
        plan.addStep(createStep(4, "发送周报邮件", "send_email", "AdminBrain"));
        
        plan.setAssignedBrain("TechBrain");
        
        return plan;
    }
    
    @Override
    public ScenarioResult execute(TaskPlan plan) {
        ScenarioResult result = new ScenarioResult();
        result.start();
        result.setScenarioId(plan.getPlanId());
        
        try {
            log.info("Executing weekly report scenario for: {}", plan.getGoal());
            
            Map<String, Object> context = plan.getContext();
            String userId = (String) context.get("userId");
            String weekStart = (String) context.get("weekStart");
            String weekEnd = (String) context.get("weekEnd");
            
            Map<String, Object> gitlabData = collectGitLabCommits(userId, weekStart, weekEnd);
            result.addData("gitlabCommits", gitlabData);
            
            Map<String, Object> jiraData = collectJiraTasks(userId, weekStart, weekEnd);
            result.addData("jiraTasks", jiraData);
            
            String reportContent = generateReportContent(userId, gitlabData, jiraData, weekStart, weekEnd);
            result.addData("reportContent", reportContent);
            
            boolean sent = sendReport(userId, reportContent);
            
            result.end();
            result.setSuccess(sent);
            result.setMessage(sent ? "周报生成并发送成功" : "周报生成成功但发送失败");
            
        } catch (Exception e) {
            log.error("Failed to execute weekly report scenario", e);
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
        return lower.contains("周报") || lower.contains("weekly report") || 
               lower.contains("本周工作") || lower.contains("工作总结");
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
    
    private Map<String, Object> collectGitLabCommits(String userId, String weekStart, String weekEnd) {
        Map<String, Object> result = new HashMap<>();
        result.put("totalCommits", 15);
        result.put("projects", Arrays.asList("living-agent-service", "dialogue-service"));
        result.put("commitList", Arrays.asList(
                Map.of("message", "feat: 实现任务规划器", "date", weekStart),
                Map.of("message", "fix: 修复WebSocket连接问题", "date", weekEnd)
        ));
        return result;
    }
    
    private Map<String, Object> collectJiraTasks(String userId, String weekStart, String weekEnd) {
        Map<String, Object> result = new HashMap<>();
        result.put("completedTasks", 5);
        result.put("inProgressTasks", 3);
        result.put("taskList", Arrays.asList(
                Map.of("key", "PROJ-101", "summary", "实现神经元架构", "status", "Done"),
                Map.of("key", "PROJ-102", "summary", "集成LLM模型", "status", "In Progress")
        ));
        return result;
    }
    
    private String generateReportContent(String userId, Map<String, Object> gitlabData, 
                                         Map<String, Object> jiraData, String weekStart, String weekEnd) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 周报\n\n");
        sb.append("**报告人**: ").append(userId).append("\n");
        sb.append("**时间范围**: ").append(weekStart).append(" ~ ").append(weekEnd).append("\n\n");
        
        sb.append("## 本周完成工作\n\n");
        sb.append("### GitLab提交\n");
        sb.append("- 总提交数: ").append(gitlabData.get("totalCommits")).append("\n");
        sb.append("- 涉及项目: ").append(gitlabData.get("projects")).append("\n\n");
        
        sb.append("### Jira任务\n");
        sb.append("- 已完成: ").append(jiraData.get("completedTasks")).append("\n");
        sb.append("- 进行中: ").append(jiraData.get("inProgressTasks")).append("\n\n");
        
        sb.append("## 下周计划\n\n");
        sb.append("1. 继续完善神经元架构\n");
        sb.append("2. 集成更多部门大脑\n");
        sb.append("3. 优化对话系统性能\n");
        
        return sb.toString();
    }
    
    private boolean sendReport(String userId, String content) {
        log.info("Sending weekly report to user: {}", userId);
        return true;
    }
}
