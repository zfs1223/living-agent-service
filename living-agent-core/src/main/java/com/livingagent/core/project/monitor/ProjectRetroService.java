package com.livingagent.core.project.monitor;

import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import com.livingagent.core.project.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 闭环40-P40-C: 项目复盘服务
 * 项目完成时自动复盘(偏差分析/效率评估/经验沉淀)
 */
public class ProjectRetroService {

    private static final Logger log = LoggerFactory.getLogger(ProjectRetroService.class);

    private final ProjectHealthMonitor healthMonitor;
    private final ProjectDeviationDetector deviationDetector;
    private final CrossLoopEventBus eventBus;
    private final Map<String, RetroReport> retroReports = new ConcurrentHashMap<>();

    public ProjectRetroService(ProjectHealthMonitor healthMonitor,
                                ProjectDeviationDetector deviationDetector,
                                CrossLoopEventBus eventBus) {
        this.healthMonitor = healthMonitor;
        this.deviationDetector = deviationDetector;
        this.eventBus = eventBus;
    }

    /**
     * 项目完成时触发复盘
     */
    public RetroReport generateRetro(Project project, int completedTasks,
                                      int totalTasks, Instant startedAt, Instant completedAt) {
        Duration actualDuration = Duration.between(startedAt, completedAt);

        // 获取偏差分析
        ProjectDeviationDetector.DeviationAnalysis deviationAnalysis =
            deviationDetector.analyzeDeviation(project.getProjectId());

        // 计算效率指标
        double taskCompletionRate = totalTasks > 0 ? (double) completedTasks / totalTasks : 0;
        double tasksPerDay = actualDuration.toDays() > 0
            ? (double) completedTasks / actualDuration.toDays() : completedTasks;

        // 生成复盘报告
        String lessonsLearned = generateLessonsLearned(deviationAnalysis, taskCompletionRate, actualDuration);

        RetroReport report = new RetroReport(
            project.getProjectId(), project.getName(),
            actualDuration, completedTasks, totalTasks,
            taskCompletionRate, tasksPerDay,
            deviationAnalysis.pattern(), deviationAnalysis.recentAvgDeviation(),
            deviationAnalysis.suggestions(), lessonsLearned,
            Instant.now()
        );

        retroReports.put(project.getProjectId(), report);

        // 沉淀经验到进化系统
        publishRetroExperience(report);

        // 清理监控数据
        healthMonitor.removeBaseline(project.getProjectId());

        log.info("[闭环40] 项目复盘完成: id={}, duration={}d, completionRate={}/{}",
            project.getProjectId(), actualDuration.toDays(),
            String.format("%.0f%%", taskCompletionRate * 100), totalTasks);

        return report;
    }

    private String generateLessonsLearned(
            ProjectDeviationDetector.DeviationAnalysis deviationAnalysis,
            double completionRate, Duration actualDuration) {

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("项目耗时%d天，任务完成率%.0f%%。",
            actualDuration.toDays(), completionRate * 100));

        if (deviationAnalysis.recentAvgDeviation() > 0.3) {
            sb.append(" 偏差较大，建议后续项目加强需求分析和任务拆分。");
        } else if (deviationAnalysis.recentAvgDeviation() > 0.1) {
            sb.append(" 存在一定偏差，建议优化任务估算和进度跟踪频率。");
        } else {
            sb.append(" 进度控制良好。");
        }

        if (completionRate < 0.9) {
            sb.append(" 任务完成率不足90%，建议关注未完成任务的风险评估。");
        }

        return sb.toString();
    }

    private void publishRetroExperience(RetroReport report) {
        if (eventBus != null) {
            eventBus.publish(40, "project_retro_experience", CrossLoopEvent.EventPriority.KNOWLEDGE,
                Map.of("content", String.format("项目复盘经验: %s - %s", report.projectName(), report.lessonsLearned()),
                    "projectId", report.projectId(), "durationDays", report.actualDuration().toDays(),
                    "completionRate", report.taskCompletionRate(), "deviationPattern", report.deviationPattern()));
        }
    }

    public RetroReport getRetroReport(String projectId) {
        return retroReports.get(projectId);
    }

    public record RetroReport(
        String projectId, String projectName,
        Duration actualDuration, int completedTasks, int totalTasks,
        double taskCompletionRate, double tasksPerDay,
        String deviationPattern, double avgDeviation,
        java.util.List<String> deviationSuggestions, String lessonsLearned,
        Instant generatedAt
    ) {}
}
