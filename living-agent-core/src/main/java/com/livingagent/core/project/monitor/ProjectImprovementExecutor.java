package com.livingagent.core.project.monitor;

import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ProjectImprovementExecutor {

    private static final Logger log = LoggerFactory.getLogger(ProjectImprovementExecutor.class);

    private final ProjectHealthMonitor healthMonitor;
    private final ProjectDeviationDetector deviationDetector;
    private final CrossLoopEventBus eventBus;

    private volatile double deviationWarningThreshold = 0.20;

    @Autowired(required = false)
    private ProjectRetroService retroService;

    public ProjectImprovementExecutor(ProjectHealthMonitor healthMonitor,
                                       ProjectDeviationDetector deviationDetector,
                                       CrossLoopEventBus eventBus) {
        this.healthMonitor = healthMonitor;
        this.deviationDetector = deviationDetector;
        this.eventBus = eventBus;
    }

    @Scheduled(fixedRate = 30 * 60 * 1000)
    public void evaluateProjectHealth() {
        ProjectHealthMonitor.ProjectHealthSummary summary = healthMonitor.getHealthSummary();
        if (summary == null || summary.totalProjects() == 0) return;

        double deviationRate = (double) summary.deviationProjects() / summary.totalProjects();

        if (deviationRate > deviationWarningThreshold) {
            deviationWarningThreshold = Math.max(0.10, deviationWarningThreshold - 0.02);
            log.info("[闭环40] 项目偏差率{}%，降低预警阈值至{}%",
                String.format("%.0f", deviationRate * 100),
                String.format("%.0f", deviationWarningThreshold * 100));

            eventBus.publish(40, "project_deviation_adjusted",
                CrossLoopEvent.EventPriority.SELF_HEALING,
                Map.of("deviationRate", deviationRate,
                    "action", "lower_threshold",
                    "newThreshold", deviationWarningThreshold));
        } else if (deviationRate < 0.05 && deviationWarningThreshold < 0.30) {
            deviationWarningThreshold = Math.min(0.30, deviationWarningThreshold + 0.01);
        }

        for (Map.Entry<String, ProjectDeviationDetector.DeviationAnalysis> entry :
                deviationDetector.getAllAnalyses().entrySet()) {
            String projectId = entry.getKey();
            ProjectDeviationDetector.DeviationAnalysis analysis = entry.getValue();

            if ("SEVERE_DEVIATION".equals(analysis.pattern())) {
                eventBus.publish(40, "project_severe_deviation",
                    CrossLoopEvent.EventPriority.SELF_HEALING,
                    Map.of("projectId", projectId,
                        "deviation", analysis.recentAvgDeviation(),
                        "action", "trigger_replanning"));
            }
        }
    }

    public double getDeviationWarningThreshold() {
        return deviationWarningThreshold;
    }
}
