package com.livingagent.core.database.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 客户端设备注册表实体
 * 用于存储桌面端设备信息，确保 clientId 与设备指纹绑定的唯一性
 */
@Entity
@Table(name = "client_device_registry")
public class ClientDeviceEntity {

    @Id
    @Column(name = "client_id", length = 100)
    private String clientId;

    @Column(name = "hostname", length = 100, nullable = false)
    private String hostname;

    @Column(name = "platform", length = 20, nullable = false)
    private String platform;

    @Column(name = "os_user", length = 100)
    private String osUser;

    @Column(name = "mac_address", length = 50)
    private String macAddress;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "app_version", length = 20)
    private String appVersion;

    @Column(name = "first_seen_at", nullable = false)
    private LocalDateTime firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    @Column(name = "status", length = 20)
    private String status = "active";

    @Column(name = "node_id", length = 100)
    private String nodeId;

    @Column(name = "tenant_id", length = 100)
    private String tenantId;

    @Column(name = "applications", columnDefinition = "TEXT")
    private String applications;

    // Getters and Setters
    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getOsUser() {
        return osUser;
    }

    public void setOsUser(String osUser) {
        this.osUser = osUser;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    public LocalDateTime getFirstSeenAt() {
        return firstSeenAt;
    }

    public void setFirstSeenAt(LocalDateTime firstSeenAt) {
        this.firstSeenAt = firstSeenAt;
    }

    public LocalDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(LocalDateTime lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getApplications() {
        return applications;
    }

    public void setApplications(String applications) {
        this.applications = applications;
    }
}
