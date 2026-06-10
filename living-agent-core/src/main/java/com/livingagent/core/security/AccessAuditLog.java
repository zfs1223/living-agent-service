package com.livingagent.core.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "access_audit_logs", indexes = {
    @Index(name = "idx_audit_employee_id", columnList = "employee_id"),
    @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
    @Index(name = "idx_audit_resource_action", columnList = "resource,action")
})
public class AccessAuditLog {
    
    @Id
    @Column(name = "log_id", length = 50)
    private String logId;
    
    @Column(name = "employee_id", length = 255)
    private String employeeId;
    
    @Column(name = "employee_name", length = 255)
    private String employeeName;
    
    @Column(name = "resource", length = 255)
    private String resource;
    
    @Column(name = "action", length = 255)
    private String action;
    
    @Column(name = "granted")
    private boolean granted;
    
    @Column(name = "reason", length = 500)
    private String reason;
    
    @Column(name = "timestamp")
    private long timestamp;
    
    @Column(name = "session_id", length = 100)
    private String sessionId;
    
    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    public AccessAuditLog() {
        this.logId = "log_" + UUID.randomUUID().toString().substring(0, 8);
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
