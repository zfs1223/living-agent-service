package com.livingagent.core.database.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "autonomy_trace_events", indexes = {
    @Index(name = "idx_trace_request_id", columnList = "request_id"),
    @Index(name = "idx_trace_stage", columnList = "stage"),
    @Index(name = "idx_trace_actor", columnList = "actor"),
    @Index(name = "idx_trace_timestamp", columnList = "timestamp"),
    @Index(name = "idx_trace_task_key", columnList = "task_key"),
    @Index(name = "idx_trace_execution_id", columnList = "execution_id")
})
public class TraceEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "request_id", nullable = false, length = 100)
    private String requestId;

    @Column(name = "stage", nullable = false, length = 64)
    private String stage;

    @Column(name = "actor", length = 128)
    private String actor;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "data", columnDefinition = "TEXT")
    private String data;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // P1-6.2: 关联键字段，统一 AutonomyTraceService 和 RuntimeEventStore 的交叉查询
    @Column(name = "task_key", length = 100)
    private String taskKey;

    @Column(name = "execution_id", length = 500)
    private String executionId;

    public TraceEventEntity() {
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }

    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getData() { return data; }
    public void setData(String data) { this.data = data; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getTaskKey() { return taskKey; }
    public void setTaskKey(String taskKey) { this.taskKey = taskKey; }

    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }
}
