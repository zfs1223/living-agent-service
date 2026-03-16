package com.livingagent.core.proactive.scenario;

import com.livingagent.core.proactive.alert.AlertNotifier;
import com.livingagent.core.proactive.alert.AlertNotifier.Alert;
import com.livingagent.core.proactive.scheduler.ProactiveTask;
import com.livingagent.core.proactive.scheduler.ProactiveTaskScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

public class WeeklyReportScenarioHandler {

    private static final Logger log = LoggerFactory.getLogger(WeeklyReportScenarioHandler.class);

    private final ProactiveTaskScheduler taskScheduler;
    private final List<AlertNotifier> notifiers;
    private final ZoneId timezone;

    public WeeklyReportScenarioHandler(ProactiveTaskScheduler taskScheduler, List<AlertNotifier> notifiers) {
        this.taskScheduler = taskScheduler;
        this.notifiers = notifiers != null ? new ArrayList<>(notifiers) : new ArrayList<>();
        this.timezone = ZoneId.of("Asia/Shanghai");
    }

    public void scheduleWeeklyReport(WeeklyReportConfig config) {
        log.info("Scheduling weekly report: {}", config.reportName());

        ProactiveTask task = ProactiveTask.create(
                config.reportName(),
                ProactiveTask.TaskType.REPORT,
                ProactiveTask.TaskTrigger.SCHEDULED
        )
        .withBrainDomain(config.brainDomain())
        .withPriority(ProactiveTask.TaskPriority.NORMAL)
        .withParameters(Map.of(
                "reportType", "weekly",
                "reportId", config.reportId(),
                "reportName", config.reportName(),
                "targetUsers", config.targetUsers(),
                "dataSources", config.dataSources(),
                "cronExpression", buildCronExpression(config),
                "template", config.template(),
                "notifyChannels", config.notifyChannels()
        ));

        taskScheduler.scheduleTask(task);
    }

    public WeeklyReportResult generateReport(WeeklyReportConfig config) {
        log.info("Generating weekly report: {}", config.reportName());

        WeeklyReportData data = collectReportData(config);
        String reportContent = generateReportContent(config, data);
        
        if (config.notifyChannels() != null && !config.notifyChannels().isEmpty()) {
            sendReportNotification(config, reportContent);
        }

        return new WeeklyReportResult(
                "report_" + System.currentTimeMillis(),
                config.reportId(),
                config.reportName(),
                Instant.now(),
                reportContent,
                data,
                config.targetUsers(),
                true,
                null
        );
    }

    private WeeklyReportData collectReportData(WeeklyReportConfig config) {
        ZonedDateTime now = ZonedDateTime.now(timezone);
        ZonedDateTime weekStart = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .truncatedTo(ChronoUnit.DAYS);
        ZonedDateTime weekEnd = weekStart.plusDays(7).minusSeconds(1);

        Map<String, Object> gitLabData = collectGitLabData(config, weekStart, weekEnd);
        Map<String, Object> jiraData = collectJiraData(config, weekStart, weekEnd);
        Map<String, Object> jenkinsData = collectJenkinsData(config, weekStart, weekEnd);

        return new WeeklyReportData(
                weekStart.toInstant(),
                weekEnd.toInstant(),
                gitLabData,
                jiraData,
                jenkinsData
        );
    }

    private Map<String, Object> collectGitLabData(WeeklyReportConfig config, ZonedDateTime start, ZonedDateTime end) {
        Map<String, Object> data = new HashMap<>();
        
        data.put("commits", List.of(
                Map.of("author", "张三", "count", 15, "project", "living-agent-service"),
                Map.of("author", "李四", "count", 12, "project", "living-agent-service"),
                Map.of("author", "王五", "count", 8, "project", "dialogue-service")
        ));
        
        data.put("mergeRequests", List.of(
                Map.of("title", "Feature: 新增权限管理", "author", "张三", "status", "merged"),
                Map.of("title", "Fix: 修复登录问题", "author", "李四", "status", "merged"),
                Map.of("title", "Feature: 报表功能", "author", "王五", "status", "open")
        ));
        
        data.put("totalCommits", 35);
        data.put("totalMergeRequests", 3);
        
        return data;
    }

    private Map<String, Object> collectJiraData(WeeklyReportConfig config, ZonedDateTime start, ZonedDateTime end) {
        Map<String, Object> data = new HashMap<>();
        
        data.put("completedTasks", List.of(
                Map.of("key", "PROJ-101", "summary", "实现用户认证", "assignee", "张三"),
                Map.of("key", "PROJ-102", "summary", "优化数据库查询", "assignee", "李四")
        ));
        
        data.put("inProgressTasks", List.of(
                Map.of("key", "PROJ-103", "summary", "开发报表模块", "assignee", "王五")
        ));
        
        data.put("totalCompleted", 2);
        data.put("totalInProgress", 1);
        
        return data;
    }

    private Map<String, Object> collectJenkinsData(WeeklyReportConfig config, ZonedDateTime start, ZonedDateTime end) {
        Map<String, Object> data = new HashMap<>();
        
        data.put("builds", List.of(
                Map.of("job", "living-agent-service", "success", 45, "failed", 2),
                Map.of("job", "dialogue-service", "success", 38, "failed", 1)
        ));
        
        data.put("totalBuilds", 86);
        data.put("successRate", 0.97);
        
        return data;
    }

    private String generateReportContent(WeeklyReportConfig config, WeeklyReportData data) {
        StringBuilder content = new StringBuilder();
        
        content.append("# ").append(config.reportName()).append("\n\n");
        content.append("**报告周期**: ")
                .append(data.weekStart())
                .append(" ~ ")
                .append(data.weekEnd())
                .append("\n\n");
        
        content.append("## 📊 代码提交统计\n\n");
        Map<String, Object> gitLabData = data.gitLabData();
        content.append("- 总提交次数: ").append(gitLabData.get("totalCommits")).append("\n");
        content.append("- 合并请求: ").append(gitLabData.get("totalMergeRequests")).append("\n\n");
        
        content.append("## ✅ 任务完成情况\n\n");
        Map<String, Object> jiraData = data.jiraData();
        content.append("- 已完成: ").append(jiraData.get("totalCompleted")).append("\n");
        content.append("- 进行中: ").append(jiraData.get("totalInProgress")).append("\n\n");
        
        content.append("## 🔧 CI/CD 构建统计\n\n");
        Map<String, Object> jenkinsData = data.jenkinsData();
        content.append("- 总构建次数: ").append(jenkinsData.get("totalBuilds")).append("\n");
        content.append("- 成功率: ").append(String.format("%.1f%%", 
                ((Number) jenkinsData.get("successRate")).doubleValue() * 100)).append("\n\n");
        
        content.append("---\n");
        content.append("*自动生成于 ").append(Instant.now()).append("*");
        
        return content.toString();
    }

    private void sendReportNotification(WeeklyReportConfig config, String reportContent) {
        Alert alert = Alert.info(config.reportName() + " - 周报已生成", reportContent)
                .withTargetUsers(config.targetUsers());

        for (AlertNotifier notifier : notifiers) {
            if (notifier.isAvailable() && 
                    config.notifyChannels().contains(notifier.getChannelName())) {
                try {
                    notifier.send(alert);
                    log.info("Report notification sent via {}", notifier.getChannelName());
                } catch (Exception e) {
                    log.warn("Failed to send report via {}: {}", notifier.getChannelName(), e.getMessage());
                }
            }
        }
    }

    private String buildCronExpression(WeeklyReportConfig config) {
        int hour = config.generateHour() != null ? config.generateHour() : 17;
        int minute = config.generateMinute() != null ? config.generateMinute() : 0;
        DayOfWeek dayOfWeek = config.generateDayOfWeek() != null ? 
                config.generateDayOfWeek() : DayOfWeek.FRIDAY;
        
        return String.format("%d %d ? * %s", minute, hour, 
                dayOfWeek.name().substring(0, 3).toUpperCase());
    }

    public record WeeklyReportConfig(
            String reportId,
            String reportName,
            String brainDomain,
            List<String> targetUsers,
            List<String> dataSources,
            List<String> notifyChannels,
            String template,
            Integer generateHour,
            Integer generateMinute,
            DayOfWeek generateDayOfWeek
    ) {
        public static WeeklyReportConfig create(String reportName, String brainDomain) {
            return new WeeklyReportConfig(
                    "wr_" + System.currentTimeMillis(),
                    reportName,
                    brainDomain,
                    List.of(),
                    List.of("gitlab", "jira", "jenkins"),
                    List.of("dingtalk"),
                    null,
                    17,
                    0,
                    DayOfWeek.FRIDAY
            );
        }
        
        public WeeklyReportConfig withTargetUsers(String... users) {
            return new WeeklyReportConfig(reportId, reportName, brainDomain, 
                    List.of(users), dataSources, notifyChannels, template, 
                    generateHour, generateMinute, generateDayOfWeek);
        }
        
        public WeeklyReportConfig withNotifyChannels(String... channels) {
            return new WeeklyReportConfig(reportId, reportName, brainDomain, 
                    targetUsers, dataSources, List.of(channels), template, 
                    generateHour, generateMinute, generateDayOfWeek);
        }
    }

    public record WeeklyReportData(
            Instant weekStart,
            Instant weekEnd,
            Map<String, Object> gitLabData,
            Map<String, Object> jiraData,
            Map<String, Object> jenkinsData
    ) {}

    public record WeeklyReportResult(
            String resultId,
            String reportId,
            String reportName,
            Instant generatedAt,
            String content,
            WeeklyReportData data,
            List<String> targetUsers,
            boolean success,
            String error
    ) {}
}
