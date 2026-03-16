package com.livingagent.core.diagnosis;

import java.time.Instant;

public class HealthAlert {
    
    public enum AlertType {
        THRESHOLD_EXCEEDED,
        COMPONENT_DOWN,
        PERFORMANCE_DEGRADATION,
        RESOURCE_EXHAUSTION,
        SECURITY_VIOLATION
    }
    
    private String alertId;
    private AlertType type;
    private String componentName;
    private String metric;
    private double currentValue;
    private double threshold;
    private String message;
    private Instant triggeredAt;
    private Instant acknowledgedAt;
    private boolean acknowledged;
    private String acknowledgedBy;
    
    public HealthAlert() {
        this.alertId = java.util.UUID.randomUUID().toString();
        this.triggeredAt = Instant.now();
        this.acknowledged = false;
    }
    
    public HealthAlert(AlertType type, String componentName, String message) {
        this();
        this.type = type;
        this.componentName = componentName;
        this.message = message;
    }
    
    public void acknowledge(String acknowledgedBy) {
        this.acknowledged = true;
        this.acknowledgedBy = acknowledgedBy;
        this.acknowledgedAt = Instant.now();
    }
    
    public String getAlertId() { return alertId; }
    public void setAlertId(String alertId) { this.alertId = alertId; }
    
    public AlertType getType() { return type; }
    public void setType(AlertType type) { this.type = type; }
    
    public String getComponentName() { return componentName; }
    public void setComponentName(String componentName) { this.componentName = componentName; }
    
    public String getMetric() { return metric; }
    public void setMetric(String metric) { this.metric = metric; }
    
    public double getCurrentValue() { return currentValue; }
    public void setCurrentValue(double currentValue) { this.currentValue = currentValue; }
    
    public double getThreshold() { return threshold; }
    public void setThreshold(double threshold) { this.threshold = threshold; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public HealthIssue getIssue() {
        HealthIssue issue = new HealthIssue(componentName, message, HealthIssue.Severity.HIGH);
        issue.setDescription(message);
        return issue;
    }
    
    public Instant getTriggeredAt() { return triggeredAt; }
    public void setTriggeredAt(Instant triggeredAt) { this.triggeredAt = triggeredAt; }
    
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(Instant acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }
    
    public boolean isAcknowledged() { return acknowledged; }
    public void setAcknowledged(boolean acknowledged) { this.acknowledged = acknowledged; }
    
    public String getAcknowledgedBy() { return acknowledgedBy; }
    public void setAcknowledgedBy(String acknowledgedBy) { this.acknowledgedBy = acknowledgedBy; }
    
    @Override
    public String toString() {
        return "HealthAlert{type=" + type + ", component='" + componentName + "', message='" + message + "'}";
    }
}
