package com.livingagent.core.diagnosis;

import java.time.Instant;

public class HealthStatus {
    
    public enum Status {
        HEALTHY,
        DEGRADED,
        UNHEALTHY,
        UNKNOWN
    }
    
    private String componentName;
    private Status status;
    private String message;
    private Instant checkedAt;
    private double score;
    private boolean responsive;
    private long responseTimeMs;
    
    public HealthStatus() {
        this.checkedAt = Instant.now();
        this.status = Status.UNKNOWN;
        this.score = 100.0;
        this.responsive = true;
    }
    
    public static HealthStatus healthy(String componentName) {
        HealthStatus hs = new HealthStatus();
        hs.componentName = componentName;
        hs.status = Status.HEALTHY;
        hs.message = "Component is healthy";
        hs.score = 100.0;
        return hs;
    }
    
    public static HealthStatus degraded(String componentName, String message) {
        HealthStatus hs = new HealthStatus();
        hs.componentName = componentName;
        hs.status = Status.DEGRADED;
        hs.message = message;
        hs.score = 70.0;
        return hs;
    }
    
    public static HealthStatus unhealthy(String componentName, String message) {
        HealthStatus hs = new HealthStatus();
        hs.componentName = componentName;
        hs.status = Status.UNHEALTHY;
        hs.message = message;
        hs.score = 0.0;
        hs.responsive = false;
        return hs;
    }
    
    public String getComponentName() { return componentName; }
    public void setComponentName(String componentName) { this.componentName = componentName; }
    
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public Instant getCheckedAt() { return checkedAt; }
    public void setCheckedAt(Instant checkedAt) { this.checkedAt = checkedAt; }
    
    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
    
    public boolean isResponsive() { return responsive; }
    public void setResponsive(boolean responsive) { this.responsive = responsive; }
    
    public long getResponseTimeMs() { return responseTimeMs; }
    public void setResponseTimeMs(long responseTimeMs) { this.responseTimeMs = responseTimeMs; }
    
    @Override
    public String toString() {
        return "HealthStatus{component='" + componentName + "', status=" + status + ", score=" + score + "}";
    }
}
