package com.livingagent.core.diagnosis;

import java.util.List;
import java.util.Map;

public interface HealthMonitor {
    
    HealthStatus checkHealth();
    
    HealthStatus checkComponent(String componentName);
    
    List<HealthIssue> detectIssues();
    
    void registerCheck(String name, HealthCheck check);
    
    void unregisterCheck(String name);
    
    Map<String, HealthStatus> getAllComponentStatus();
    
    void setAlertThreshold(String metric, double threshold);
    
    List<HealthAlert> getActiveAlerts();
    
    void acknowledgeAlert(String alertId);
}
