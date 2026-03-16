package com.livingagent.core.operation.performance;

import java.util.Map;

public interface PerformanceIndicator {

    String getIndicatorId();
    
    String getName();
    
    String getDescription();
    
    IndicatorCategory getCategory();
    
    double getWeight();
    
    double getTargetValue();
    
    double getActualValue();
    
    double getScore();
    
    double getAchievementRate();
    
    Map<String, Object> getDetails();
    
    enum IndicatorCategory {
        TASK_COMPLETION,
        QUALITY,
        EFFICIENCY,
        COLLABORATION,
        INNOVATION,
        LEARNING,
        COMMUNICATION,
        RELIABILITY
    }
}
