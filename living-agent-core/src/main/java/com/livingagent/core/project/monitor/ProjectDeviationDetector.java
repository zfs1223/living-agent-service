package com.livingagent.core.project.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Instant;

/**
 * 闭环40-P40-B: 项目偏差模式识别
 * 基于历史数据识别项目偏差模式，自动建议纠偏策略
 */
public class ProjectDeviationDetector {

    private static final Logger log = LoggerFactory.getLogger(ProjectDeviationDetector.class);

    private final Map<String, List<DeviationRecord>> deviationHistory = new ConcurrentHashMap<>();
    private final Map<String, String> correctionStrategies = new ConcurrentHashMap<>();

    public void recordDeviation(ProjectHealthMonitor.ProjectHealthReport report) {
        deviationHistory.computeIfAbsent(report.projectId(), k -> new ArrayList<>())
            .add(new DeviationRecord(
                report.projectId(), report.deviation(),
                report.timeProgress(), report.taskProgress(),
                report.status().name(), Instant.now()
            ));

        // 只保留最近20条记录
        List<DeviationRecord> records = deviationHistory.get(report.projectId());
        if (records.size() > 20) {
            records.subList(0, records.size() - 20).clear();
        }
    }

    public DeviationAnalysis analyzeDeviation(String projectId) {
        List<DeviationRecord> records = deviationHistory.get(projectId);
        if (records == null || records.isEmpty()) {
            return new DeviationAnalysis(projectId, "NO_DATA", 0, "无偏差数据", Collections.emptyList());
        }

        // 检测偏差趋势
        double recentAvgDeviation = records.stream()
            .skip(Math.max(0, records.size() - 5))
            .mapToDouble(DeviationRecord::deviation)
            .average().orElse(0);

        double overallAvgDeviation = records.stream()
            .mapToDouble(DeviationRecord::deviation)
            .average().orElse(0);

        boolean isIncreasing = recentAvgDeviation > overallAvgDeviation;

        String pattern;
        List<String> suggestions;

        if (recentAvgDeviation > 0.5) {
            pattern = "SEVERE_DEVIATION";
            suggestions = List.of(
                "建议重新评估项目范围和优先级",
                "考虑增加人力资源或减少非核心需求",
                "建议启动项目风险评审会议"
            );
        } else if (recentAvgDeviation > 0.2) {
            pattern = isIncreasing ? "DEVIATION_INCREASING" : "DEVIATION_STABLE";
            suggestions = isIncreasing
                ? List.of("偏差持续扩大，建议立即调整任务优先级", "检查是否有阻塞任务需要解决")
                : List.of("偏差稳定但偏高，建议优化任务分配效率", "关注关键路径任务进展");
        } else {
            pattern = "MINOR_DEVIATION";
            suggestions = List.of("偏差在可控范围内，持续监控即可");
        }

        String strategy = suggestions.isEmpty() ? "CONTINUE_MONITORING" : suggestions.get(0);
        correctionStrategies.put(projectId, strategy);

        return new DeviationAnalysis(
            projectId, pattern, recentAvgDeviation,
            String.join("; ", suggestions), suggestions
        );
    }

    public String getCorrectionStrategy(String projectId) {
        return correctionStrategies.getOrDefault(projectId, "CONTINUE_MONITORING");
    }

    public Map<String, DeviationAnalysis> getAllAnalyses() {
        Map<String, DeviationAnalysis> result = new java.util.HashMap<>();
        for (String projectId : deviationHistory.keySet()) {
            result.put(projectId, analyzeDeviation(projectId));
        }
        return Map.copyOf(result);
    }

    public record DeviationRecord(
        String projectId, double deviation,
        double timeProgress, double taskProgress,
        String status, Instant recordedAt
    ) {}

    public record DeviationAnalysis(
        String projectId, String pattern,
        double recentAvgDeviation,
        String recommendedAction,
        List<String> suggestions
    ) {}
}
