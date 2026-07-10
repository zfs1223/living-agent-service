package com.livingagent.core.project.monitor;

import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import com.livingagent.core.project.Project;
import com.livingagent.core.project.ProjectStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 闭环40-P40-A: 项目健康监控
 * 定期检查项目进度偏差(计划vs实际)，偏差>20%触发预警
 */
public class ProjectHealthMonitor {

    private static final Logger log = LoggerFactory.getLogger(ProjectHealthMonitor.class);

    private static final double DEVIATION_ALERT_THRESHOLD = 0.20;

    private final CrossLoopEventBus eventBus;
    private final Map<String, ProjectBaseline> baselines = new ConcurrentHashMap<>();

    public ProjectHealthMonitor(CrossLoopEventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * 项目创建/启动时记录计划基线
     */
    public void recordBaseline(String projectId, Instant deadline, int expectedTaskCount) {
        baselines.put(projectId, new ProjectBaseline(
            projectId, deadline, expectedTaskCount, Instant.now()
        ));
        log.info("[闭环40] 项目基线已记录: id={}, deadline={}, expectedTasks={}",
            projectId, deadline, expectedTaskCount);
    }

    /**
     * 检查项目健康状态
     */
    public ProjectHealthReport checkHealth(Project project, int completedTasks, int totalTasks) {
        ProjectBaseline baseline = baselines.get(project.getProjectId());
        if (baseline == null) return null;

        // 计算进度偏差
        double timeProgress = calculateTimeProgress(baseline);
        double taskProgress = totalTasks > 0 ? (double) completedTasks / totalTasks : 0;
        double deviation = timeProgress - taskProgress; // 正值=落后，负值=超前

        // 计算时间紧迫度
        Duration timeRemaining = Duration.between(Instant.now(), baseline.deadline());
        boolean isOverdue = timeRemaining.isNegative() || timeRemaining.isZero();
        double urgencyScore = isOverdue ? 1.0 :
            Math.max(0, 1.0 - (double) timeRemaining.toHours() /
                Duration.between(baseline.createdAt(), baseline.deadline()).toHours());

        ProjectHealthStatus status;
        if (isOverdue && taskProgress < 1.0) {
            status = ProjectHealthStatus.CRITICAL;
        } else if (deviation > DEVIATION_ALERT_THRESHOLD) {
            status = ProjectHealthStatus.WARNING;
        } else if (deviation > DEVIATION_ALERT_THRESHOLD / 2) {
            status = ProjectHealthStatus.CAUTION;
        } else {
            status = ProjectHealthStatus.HEALTHY;
        }

        ProjectHealthReport report = new ProjectHealthReport(
            project.getProjectId(), project.getName(),
            status, deviation, timeProgress, taskProgress,
            timeRemaining, urgencyScore,
            completedTasks, totalTasks,
            baseline.expectedTaskCount(),
            Instant.now()
        );

        // 触发预警
        if (status == ProjectHealthStatus.WARNING || status == ProjectHealthStatus.CRITICAL) {
            publishDeviationAlert(report);
        }

        return report;
    }

    private double calculateTimeProgress(ProjectBaseline baseline) {
        Duration totalDuration = Duration.between(baseline.createdAt(), baseline.deadline());
        Duration elapsed = Duration.between(baseline.createdAt(), Instant.now());
        if (totalDuration.isZero() || totalDuration.isNegative()) return 1.0;
        double progress = (double) elapsed.toMillis() / totalDuration.toMillis();
        return Math.min(1.0, Math.max(0, progress));
    }

    private void publishDeviationAlert(ProjectHealthReport report) {
        if (eventBus != null) {
            eventBus.publish(40, "project_health_alert", CrossLoopEvent.EventPriority.SELF_HEALING,
                Map.of("content", String.format("项目 %s 进度偏差 %.1f%% (状态: %s)",
                        report.projectName(), report.deviation() * 100, report.status()),
                    "projectId", report.projectId(), "deviation", report.deviation(),
                    "status", report.status().name(), "timeProgress", report.timeProgress(),
                    "taskProgress", report.taskProgress()));
        }
        log.warn("[闭环40] 项目健康预警: id={}, deviation={}/{}",
            report.projectId(),
            String.format("%.1f%%", report.deviation() * 100),
            String.format("%.0f%%", DEVIATION_ALERT_THRESHOLD * 100));
    }

    public void removeBaseline(String projectId) {
        baselines.remove(projectId);
    }

    public enum ProjectHealthStatus {
        HEALTHY, CAUTION, WARNING, CRITICAL
    }

    public record ProjectBaseline(
        String projectId, Instant deadline, int expectedTaskCount, Instant createdAt
    ) {}

    public record ProjectHealthReport(
        String projectId, String projectName,
        ProjectHealthStatus status, double deviation,
        double timeProgress, double taskProgress,
        Duration timeRemaining, double urgencyScore,
        int completedTasks, int totalTasks,
        int expectedTaskCount,
        Instant capturedAt
    ) {}
}
