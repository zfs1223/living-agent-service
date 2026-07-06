package com.livingagent.core.autonomy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.database.entity.TraceEventEntity;
import com.livingagent.core.database.repository.TraceEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class AutonomyTraceService {

    private static final Logger log = LoggerFactory.getLogger(AutonomyTraceService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final List<AutonomyTraceEvent> traceEvents = new CopyOnWriteArrayList<>();
    private final int maxEvents;
    private final TraceEventRepository traceEventRepository;
    private final ExecutorService traceExecutor;

    public AutonomyTraceService() {
        this(1000, null, null);
    }

    public AutonomyTraceService(int maxEvents) {
        this(maxEvents, null, null);
    }

    public AutonomyTraceService(TraceEventRepository traceEventRepository) {
        this(1000, traceEventRepository, null);
    }

    public AutonomyTraceService(int maxEvents, TraceEventRepository traceEventRepository) {
        this(maxEvents, traceEventRepository, null);
    }

    public AutonomyTraceService(int maxEvents, TraceEventRepository traceEventRepository, ExecutorService traceExecutor) {
        this.maxEvents = maxEvents;
        this.traceEventRepository = traceEventRepository;
        this.traceExecutor = traceExecutor;
    }

    @Transactional
    public void recordEvent(AutonomyTraceEvent event) {
        traceEvents.add(event);

        if (traceEvents.size() > maxEvents) {
            traceEvents.remove(0);
        }

        logStructuredEvent(event);

        // 持久化到数据库
        persistEvent(event);
    }

    private void logStructuredEvent(AutonomyTraceEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("[AutonomyTrace] requestId=").append(event.requestId());
        sb.append(" stage=").append(event.stage());
        sb.append(" actor=").append(event.actor());

        if (event.data() != null && !event.data().isEmpty()) {
            event.data().forEach((key, value) -> sb.append(" ").append(key).append("=").append(value));
        }

        if (event.summary() != null && !event.summary().isEmpty()) {
            sb.append(" summary=\"").append(event.summary()).append("\"");
        }

        log.info(sb.toString());
    }

    private void persistEvent(AutonomyTraceEvent event) {
        if (traceEventRepository == null) {
            return;
        }
        try {
            TraceEventEntity entity = new TraceEventEntity();
            entity.setTraceId(event.traceId());
            entity.setRequestId(event.requestId());
            entity.setStage(event.stage());
            entity.setActor(event.actor());
            entity.setSummary(event.summary());
            entity.setTimestamp(event.timestamp());
            entity.setTaskKey(event.taskKey());
            entity.setExecutionId(event.executionId());
            if (event.data() != null && !event.data().isEmpty()) {
                entity.setData(objectMapper.writeValueAsString(event.data()));
            }
            if (traceExecutor != null) {
                traceExecutor.submit(() -> {
                    try {
                        traceEventRepository.saveAndVerify(entity);
                    } catch (Exception e) {
                        log.warn("Failed to persist trace event (async): requestId={}, stage={}, error={}",
                            event.requestId(), event.stage(), e.getMessage());
                    }
                });
            } else {
                traceEventRepository.saveAndVerify(entity);
            }
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize trace event data for persistence: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("Failed to persist trace event: requestId={}, stage={}, error={}",
                event.requestId(), event.stage(), e.getMessage());
        }
    }

    public List<AutonomyTraceEvent> getTraceByRequestId(String requestId) {
        // B-0-4: DB 优先查询，内存作为兜底/补充。
        // 原代码内存优先，会丢失已落库但尚未载入内存的事件（如服务重启后内存清空）。
        if (traceEventRepository != null) {
            try {
                List<AutonomyTraceEvent> dbResults = traceEventRepository.findByRequestIdOrderByTimestampAsc(requestId).stream()
                    .map(this::entityToEvent)
                    .collect(Collectors.toList());
                if (!dbResults.isEmpty()) {
                    return dbResults;
                }
            } catch (Exception e) {
                log.warn("Failed to query trace events from database for requestId={}: {}", requestId, e.getMessage());
            }
        }

        // DB 没有则回退到内存（兜底，未配置 repository 时仍可用）
        return traceEvents.stream()
            .filter(e -> e.requestId().equals(requestId))
            .toList();
    }

    public List<AutonomyTraceEvent> getRecentTraces(int limit) {
        // B-0-4: DB 优先查询
        if (traceEventRepository != null) {
            try {
                List<TraceEventEntity> dbResults = traceEventRepository.findTop1000ByOrderByTimestampDesc();
                List<AutonomyTraceEvent> events = dbResults.stream()
                    .map(this::entityToEvent)
                    .limit(limit)
                    .collect(Collectors.toList());
                if (!events.isEmpty()) {
                    return events;
                }
            } catch (Exception e) {
                log.warn("Failed to query recent trace events from database: {}", e.getMessage());
            }
        }

        // 兜底：使用内存
        int fromIndex = Math.max(0, traceEvents.size() - limit);
        return new java.util.ArrayList<>(traceEvents.subList(fromIndex, traceEvents.size()));
    }

    public void clearTraces() {
        traceEvents.clear();
        log.info("Autonomy traces cleared (in-memory only, database records preserved)");
    }

    @jakarta.annotation.PreDestroy
    public void destroy() {
        if (traceExecutor != null) {
            traceExecutor.shutdown();
            try {
                if (!traceExecutor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    traceExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                traceExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("AutonomyTraceService shutdown complete");
        }
    }

    /**
     * P1-6.2: 通过 taskKey 查询 Trace 事件，实现与 RuntimeEventStore 的交叉查询
     * B-0-4: DB 优先查询，内存兜底
     */
    public List<AutonomyTraceEvent> getTraceByTaskKey(String taskKey) {
        if (taskKey == null || taskKey.isBlank()) {
            return List.of();
        }
        // B-0-4: DB 优先查询
        if (traceEventRepository != null) {
            try {
                List<AutonomyTraceEvent> dbResults = traceEventRepository.findByTaskKeyOrderByTimestampAsc(taskKey).stream()
                    .map(this::entityToEvent)
                    .collect(Collectors.toList());
                if (!dbResults.isEmpty()) {
                    return dbResults;
                }
            } catch (Exception e) {
                log.warn("Failed to query trace events by taskKey={}: {}", taskKey, e.getMessage());
            }
        }

        // 兜底：内存
        return traceEvents.stream()
            .filter(e -> taskKey.equals(e.taskKey()))
            .toList();
    }

    /**
     * P1-6.2: 通过 executionId 查询 Trace 事件，实现与 RuntimeEventStore 的交叉查询
     * B-0-4: DB 优先查询，内存兜底
     */
    public List<AutonomyTraceEvent> getTraceByExecutionId(String executionId) {
        if (executionId == null || executionId.isBlank()) {
            return List.of();
        }
        // B-0-4: DB 优先查询
        if (traceEventRepository != null) {
            try {
                List<AutonomyTraceEvent> dbResults = traceEventRepository.findByExecutionIdOrderByTimestampAsc(executionId).stream()
                    .map(this::entityToEvent)
                    .collect(Collectors.toList());
                if (!dbResults.isEmpty()) {
                    return dbResults;
                }
            } catch (Exception e) {
                log.warn("Failed to query trace events by executionId={}: {}", executionId, e.getMessage());
            }
        }

        // 兜底：内存
        return traceEvents.stream()
            .filter(e -> executionId.equals(e.executionId()))
            .toList();
    }

    /**
     * 清理过期的数据库记录
     */
    public void cleanupOldTraces(Instant cutoff) {
        if (traceEventRepository == null) {
            return;
        }
        try {
            traceEventRepository.deleteByTimestampBefore(cutoff);
            log.info("Cleaned up trace events before {}", cutoff);
        } catch (Exception e) {
            log.warn("Failed to cleanup old trace events: {}", e.getMessage());
        }
    }

    private AutonomyTraceEvent entityToEvent(TraceEventEntity entity) {
        Map<String, Object> data = null;
        if (entity.getData() != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = objectMapper.readValue(entity.getData(), Map.class);
                data = parsed;
            } catch (JsonProcessingException e) {
                log.debug("Failed to deserialize trace event data: {}", e.getMessage());
            }
        }
        return new AutonomyTraceEvent(
            entity.getTraceId(),
            entity.getRequestId(),
            entity.getStage(),
            entity.getActor(),
            entity.getSummary(),
            data != null ? data : Map.of(),
            entity.getTimestamp(),
            entity.getTaskKey(),
            entity.getExecutionId()
        );
    }
}
