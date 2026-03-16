package com.livingagent.core.proactive.predictor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RiskPredictor {

    private static final Logger log = LoggerFactory.getLogger(RiskPredictor.class);

    private static final double HIGH_RISK_THRESHOLD = 0.7;
    private static final double MEDIUM_RISK_THRESHOLD = 0.4;
    private static final long MONITORING_WINDOW_HOURS = 24;

    private final Map<String, RiskIndicator> indicators = new ConcurrentHashMap<>();
    private final Map<String, List<RiskAlert>> activeAlerts = new ConcurrentHashMap<>();
    private final Map<String, RiskAssessment> assessments = new ConcurrentHashMap<>();

    public RiskPredictor() {
        initializeDefaultIndicators();
    }

    private void initializeDefaultIndicators() {
        registerIndicator(new RiskIndicator(
                "error_rate",
                "系统错误率",
                "系统错误率超过阈值",
                0.1,
                0.3,
                RiskLevel.HIGH,
                "errors",
                "total_requests"
        ));

        registerIndicator(new RiskIndicator(
                "response_time",
                "响应时间",
                "平均响应时间超过阈值",
                2000.0,
                5000.0,
                RiskLevel.MEDIUM,
                "avg_response_ms"
        ));

        registerIndicator(new RiskIndicator(
                "cpu_usage",
                "CPU使用率",
                "CPU使用率持续过高",
                0.8,
                0.95,
                RiskLevel.HIGH,
                "cpu_percent"
        ));

        registerIndicator(new RiskIndicator(
                "memory_usage",
                "内存使用率",
                "内存使用率过高",
                0.85,
                0.95,
                RiskLevel.HIGH,
                "memory_percent"
        ));

        registerIndicator(new RiskIndicator(
                "disk_usage",
                "磁盘使用率",
                "磁盘空间不足",
                0.8,
                0.9,
                RiskLevel.MEDIUM,
                "disk_percent"
        ));

        registerIndicator(new RiskIndicator(
                "project_deadline",
                "项目截止日期",
                "项目即将或已经延期",
                7.0,
                0.0,
                RiskLevel.HIGH,
                "days_remaining"
        ));

        registerIndicator(new RiskIndicator(
                "task_completion",
                "任务完成率",
                "任务完成率低于预期",
                0.7,
                0.5,
                RiskLevel.MEDIUM,
                "completed_tasks",
                "total_tasks"
        ));
    }

    public void registerIndicator(RiskIndicator indicator) {
        indicators.put(indicator.indicatorId(), indicator);
        log.info("Registered risk indicator: {}", indicator.name());
    }

    public void updateMetric(String indicatorId, double value) {
        updateMetric(indicatorId, value, Map.of());
    }

    public void updateMetric(String indicatorId, double value, Map<String, Object> context) {
        RiskIndicator indicator = indicators.get(indicatorId);
        if (indicator == null) {
            log.warn("Unknown indicator: {}", indicatorId);
            return;
        }

        RiskAssessment assessment = assessRisk(indicator, value, context);
        assessments.put(indicatorId, assessment);

        if (assessment.level() != RiskLevel.LOW) {
            generateAlert(assessment);
        }

        log.debug("Updated metric {}: value={}, level={}", indicatorId, value, assessment.level());
    }

    private RiskAssessment assessRisk(RiskIndicator indicator, double value, Map<String, Object> context) {
        RiskLevel level;
        double probability;

        if (indicator.isPercentage()) {
            if (value >= indicator.criticalThreshold()) {
                level = RiskLevel.CRITICAL;
                probability = Math.min(1.0, (value - indicator.criticalThreshold()) / 0.1 + 0.9);
            } else if (value >= indicator.warningThreshold()) {
                level = RiskLevel.HIGH;
                probability = (value - indicator.warningThreshold()) / (indicator.criticalThreshold() - indicator.warningThreshold());
            } else if (value >= indicator.warningThreshold() * 0.8) {
                level = RiskLevel.MEDIUM;
                probability = (value - indicator.warningThreshold() * 0.8) / (indicator.warningThreshold() * 0.2);
            } else {
                level = RiskLevel.LOW;
                probability = value / (indicator.warningThreshold() * 0.8);
            }
        } else {
            if (value <= indicator.criticalThreshold()) {
                level = RiskLevel.CRITICAL;
                probability = 0.95;
            } else if (value <= indicator.warningThreshold()) {
                level = RiskLevel.HIGH;
                probability = 0.7;
            } else if (value <= indicator.warningThreshold() * 1.5) {
                level = RiskLevel.MEDIUM;
                probability = 0.4;
            } else {
                level = RiskLevel.LOW;
                probability = 0.1;
            }
        }

        return new RiskAssessment(
                "assess_" + System.currentTimeMillis(),
                indicator.indicatorId(),
                indicator.name(),
                value,
                level,
                probability,
                generateRecommendation(indicator, level, value),
                Instant.now(),
                context
        );
    }

    private String generateRecommendation(RiskIndicator indicator, RiskLevel level, double value) {
        return switch (level) {
            case CRITICAL -> String.format("【紧急】%s 已达临界值 (%.2f)，需立即处理", indicator.name(), value);
            case HIGH -> String.format("【警告】%s 超过预警阈值 (%.2f)，建议尽快处理", indicator.name(), value);
            case MEDIUM -> String.format("【注意】%s 接近预警阈值 (%.2f)，建议关注", indicator.name(), value);
            case LOW -> String.format("%s 正常 (%.2f)", indicator.name(), value);
        };
    }

    private void generateAlert(RiskAssessment assessment) {
        RiskAlert alert = new RiskAlert(
                "alert_" + System.currentTimeMillis(),
                assessment.indicatorId(),
                assessment.indicatorName(),
                assessment.level(),
                assessment.probability(),
                assessment.recommendation(),
                assessment.context(),
                Instant.now(),
                AlertStatus.ACTIVE
        );

        activeAlerts.computeIfAbsent(assessment.indicatorId(), k -> new ArrayList<>()).add(alert);

        log.warn("Risk alert generated: {} - {}", assessment.indicatorName(), assessment.recommendation());
    }

    public List<RiskAlert> getActiveAlerts() {
        return activeAlerts.values().stream()
                .flatMap(List::stream)
                .filter(a -> a.status() == AlertStatus.ACTIVE)
                .sorted(Comparator.comparing(a -> a.level().getSeverity(), Comparator.reverseOrder()))
                .toList();
    }

    public List<RiskAlert> getAlertsByLevel(RiskLevel level) {
        return activeAlerts.values().stream()
                .flatMap(List::stream)
                .filter(a -> a.level() == level && a.status() == AlertStatus.ACTIVE)
                .toList();
    }

    public void acknowledgeAlert(String alertId) {
        activeAlerts.values().stream()
                .flatMap(List::stream)
                .filter(a -> a.alertId().equals(alertId))
                .findFirst()
                .ifPresent(alert -> {
                    alert = new RiskAlert(
                            alert.alertId(),
                            alert.indicatorId(),
                            alert.indicatorName(),
                            alert.level(),
                            alert.probability(),
                            alert.recommendation(),
                            alert.context(),
                            alert.triggeredAt(),
                            AlertStatus.ACKNOWLEDGED
                    );
                    log.info("Alert acknowledged: {}", alertId);
                });
    }

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalIndicators", indicators.size());
        stats.put("totalAlerts", getActiveAlerts().size());
        
        Map<String, Integer> byLevel = new HashMap<>();
        for (RiskLevel level : RiskLevel.values()) {
            byLevel.put(level.name(), getAlertsByLevel(level).size());
        }
        stats.put("alertsByLevel", byLevel);
        
        Map<String, Object> indicatorStats = new HashMap<>();
        assessments.forEach((k, v) -> indicatorStats.put(k, v.toMap()));
        stats.put("assessments", indicatorStats);
        
        return stats;
    }

    public record RiskIndicator(
            String indicatorId,
            String name,
            String description,
            double warningThreshold,
            double criticalThreshold,
            RiskLevel defaultLevel,
            String... metricKeys
    ) {
        public boolean isPercentage() {
            return warningThreshold <= 1.0 && criticalThreshold <= 1.0;
        }
    }

    public record RiskAssessment(
            String assessmentId,
            String indicatorId,
            String indicatorName,
            double currentValue,
            RiskLevel level,
            double probability,
            String recommendation,
            Instant assessedAt,
            Map<String, Object> context
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("indicatorId", indicatorId);
            map.put("indicatorName", indicatorName);
            map.put("currentValue", currentValue);
            map.put("level", level.name());
            map.put("probability", probability);
            map.put("recommendation", recommendation);
            return map;
        }
    }

    public record RiskAlert(
            String alertId,
            String indicatorId,
            String indicatorName,
            RiskLevel level,
            double probability,
            String recommendation,
            Map<String, Object> context,
            Instant triggeredAt,
            AlertStatus status
    ) {}

    public enum RiskLevel {
        LOW(1), MEDIUM(2), HIGH(3), CRITICAL(4);

        private final int severity;

        RiskLevel(int severity) {
            this.severity = severity;
        }

        public int getSeverity() {
            return severity;
        }
    }

    public enum AlertStatus {
        ACTIVE, ACKNOWLEDGED, RESOLVED, IGNORED
    }
}
