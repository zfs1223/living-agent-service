package com.livingagent.core.operation.metrics;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface MetricsCollector {

    void record(String metricName, double value, Map<String, String> tags);
    
    void increment(String metricName, Map<String, String> tags);
    
    void increment(String metricName, double delta, Map<String, String> tags);
    
    void recordTiming(String metricName, Duration duration, Map<String, String> tags);
    
    Optional<Double> getValue(String metricName, Map<String, String> tags);
    
    List<OperationMetrics> getMetrics(String metricName, Instant start, Instant end);
    
    List<OperationMetrics> getMetricsByCategory(OperationMetrics.MetricCategory category);
    
    Map<String, Double> getAggregatedValues(String metricName, Duration period);
    
    MetricsSummary getSummary(String metricName, Duration period);
    
    void defineMetric(MetricDefinition definition);
    
    void setAlertThreshold(String metricName, double threshold, OperationMetrics.AlertLevel level);
    
    List<Alert> getActiveAlerts();
    
    void acknowledgeAlert(String alertId);
    
    record MetricDefinition(
        String name,
        String description,
        OperationMetrics.MetricType type,
        OperationMetrics.MetricCategory category,
        String unit,
        double warningThreshold,
        double criticalThreshold
    ) {}
    
    record MetricsSummary(
        String metricName,
        double min,
        double max,
        double avg,
        double sum,
        long count,
        double p50,
        double p95,
        double p99
    ) {}
    
    record Alert(
        String alertId,
        String metricName,
        double value,
        double threshold,
        OperationMetrics.AlertLevel level,
        Instant triggeredAt,
        Map<String, String> tags,
        boolean acknowledged
    ) {}
}
