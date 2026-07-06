package com.livingagent.core.database.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "runtime_events", indexes = {
    @Index(name = "idx_re_scope", columnList = "scope"),
    @Index(name = "idx_re_scope_key", columnList = "scope,scope_key"),
    @Index(name = "idx_re_tenant", columnList = "tenant_id"),
    @Index(name = "idx_re_type", columnList = "event_type"),
    @Index(name = "idx_re_timestamp", columnList = "timestamp")
})
public class RuntimeEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "scope", nullable = false, length = 32)
    private String scope;

    @Column(name = "scope_key", nullable = false, length = 255)
    private String scopeKey;

    @Column(name = "tenant_id", length = 64)
    private String tenantId;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(name = "data", columnDefinition = "TEXT")
    private String data;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    public RuntimeEventEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public String getScopeKey() { return scopeKey; }
    public void setScopeKey(String scopeKey) { this.scopeKey = scopeKey; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getData() { return data; }
    public void setData(String data) { this.data = data; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
