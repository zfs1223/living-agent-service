package com.livingagent.core.database.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 服务初始化状态实体
 * <p>记录 ServiceAdminBootstrap 各步骤的执行状态，支持幂等重试。
 * <p>关联文档：docs/core/MAINBRAIN_ADMIN_BRIDGE_PLAN.md
 */
@Entity
@Table(name = "service_admin_bootstrap_state",
    uniqueConstraints = @UniqueConstraint(columnNames = {"service_type", "step_name"}))
public class ServiceAdminBootstrapStateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_type", length = 32, nullable = false)
    private String serviceType;

    @Column(name = "step_name", length = 128, nullable = false)
    private String stepName;

    @Column(name = "status", length = 32, nullable = false)
    private String status;

    @Column(name = "detail")
    private String detail;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public ServiceAdminBootstrapStateEntity() {
    }

    public ServiceAdminBootstrapStateEntity(String serviceType, String stepName, String status) {
        this.serviceType = serviceType;
        this.stepName = stepName;
        this.status = status;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }
    public String getStepName() { return stepName; }
    public void setStepName(String stepName) { this.stepName = stepName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
