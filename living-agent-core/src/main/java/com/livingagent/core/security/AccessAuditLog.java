package com.livingagent.core.security;

import java.time.Instant;

public class AccessAuditLog {
    
    private String logId;
    private String employeeId;
    private String employeeName;
    private String resource;
    private String action;
    private boolean granted;
    private String reason;
    private long timestamp;
    private String sessionId;
    private String ipAddress;

    public AccessAuditLog() {
        this.logId = "log_" + System.currentTimeMillis();
        this.timestamp = System.currentTimeMillis();
    }

    public String getLogId() { return logId; }
    public void setLogId(String logId) { this.logId = logId; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

    public String getResource() { return resource; }
    public void setResource(String resource) { this.resource = resource; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public boolean isGranted() { return granted; }
    public void setGranted(boolean granted) { this.granted = granted; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    @Override
    public String toString() {
        return String.format("AccessAuditLog{employee=%s, resource=%s, action=%s, granted=%s}",
            employeeId, resource, action, granted);
    }
}
