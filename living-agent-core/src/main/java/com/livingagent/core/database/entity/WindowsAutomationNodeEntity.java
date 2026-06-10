package com.livingagent.core.database.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** Windows 自动化节点注册实体 */
@Entity
@Table(name = "windows_automation_nodes", indexes = {
    @Index(name = "idx_wan_tenant", columnList = "tenant_id"),
    @Index(name = "idx_wan_status", columnList = "status"),
    @Index(name = "idx_wan_user", columnList = "user_id")
})
public class WindowsAutomationNodeEntity {

    @Id
    @Column(name = "node_id", length = 64)
    private String nodeId;

    @Column(name = "ip_address", length = 64, nullable = false)
    private String ipAddress;

    @Column(name = "port")
    private Integer port = 8765;

    @Column(name = "hostname", length = 128)
    private String hostname;

    @Column(name = "cpu_count")
    private Integer cpuCount;

    @Column(name = "memory_gb")
    private Double memoryGb;

    @Column(name = "applications", columnDefinition = "TEXT")
    private String applications;

    @Column(name = "description", length = 256)
    private String description;

    @Column(name = "status", length = 16)
    private String status = "offline";

    @Column(name = "last_heartbeat")
    private Instant lastHeartbeat;

    @Column(name = "registered_at")
    private Instant registeredAt;

    @Column(name = "tenant_id", length = 64)
    private String tenantId;

    @Column(name = "user_id", length = 64)
    private String userId;

    @Column(name = "enabled")
    private Boolean enabled = true;

    // Getters and Setters
    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }

    public String getHostname() { return hostname; }
    public void setHostname(String hostname) { this.hostname = hostname; }

    public Integer getCpuCount() { return cpuCount; }
    public void setCpuCount(Integer cpuCount) { this.cpuCount = cpuCount; }

    public Double getMemoryGb() { return memoryGb; }
    public void setMemoryGb(Double memoryGb) { this.memoryGb = memoryGb; }

    public String getApplications() { return applications; }
    public void setApplications(String applications) { this.applications = applications; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getLastHeartbeat() { return lastHeartbeat; }
    public void setLastHeartbeat(Instant lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }

    public Instant getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(Instant registeredAt) { this.registeredAt = registeredAt; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
