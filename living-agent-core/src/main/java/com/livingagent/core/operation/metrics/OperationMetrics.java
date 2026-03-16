package com.livingagent.core.operation.metrics;

import java.time.Instant;
import java.util.Map;

public interface OperationMetrics {

    String getMetricId();
    
    String getName();
    
    MetricCategory getCategory();
    
    MetricType getType();
    
    double getValue();
    
    String getUnit();
    
    Instant getTimestamp();
    
    Map<String, String> getDimensions();
    
    double getThreshold();
    
    AlertLevel getAlertLevel();
    
    boolean isAlert();
    
    enum MetricCategory {
        PERFORMANCE,
        AVAILABILITY,
        EFFICIENCY,
        QUALITY,
        COST,
        GROWTH,
        SATISFACTION,
        SECURITY
    }
    
    enum MetricType {
        GAUGE,
        COUNTER,
        HISTOGRAM,
        SUMMARY
    }
    
    enum AlertLevel {
        NORMAL,
        WARNING,
        CRITICAL,
        EMERGENCY
    }
}
