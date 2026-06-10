package com.livingagent.core.autonomy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.database.entity.TraceEventEntity;
import com.livingagent.core.database.repository.TraceEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
                        traceEventRepository.save(entity);
                    } catch (Exception e) {
                        log.warn("Failed to persist trace event (async): requestId={}, stage={}, error={}",
                            event.requestId(), event.stage(), e.getMessage());
                    }
                });
            } else {
                traceEventRepository.save(entity);
            }
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize trace event data for persistence: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("Failed to persist trace event: requestId={}, stage={}, error={}",
                event.requestId(), event.stage(), e.getMessage());
        }
    }

    public List<AutonomyTraceEvent> getTraceByRequestId(String requestId) {
        // 优先从内存获取
        List<AutonomyTraceEvent> memoryResults = traceEvents.stream()
            .filter(e -> e.requestId().equals(requestId))
            .toList();

        // 如果内存有结果，直接返回
        if (!memoryResults.isEmpty()) {
            return memoryResults;
        }

        // 内存没有，从数据库查询
        if (traceEventRepository != null) {
            try {
                return traceEventRepository.findByRequestIdOrderByTimestampAsc(requestId).stream()
                    .map(this::entityToEvent)
                    .collect(Collectors.toList());
            } catch (Exception e) {
                log.warn("Failed to query trace events from database for requestId={}: {}", requestId, e.getMessage());
            }
        }

        return List.of();
    }

    public List<AutonomyTraceEvent> getRecentTraces(int limit) {
        int fromIndex = Math.max(0, traceEvents.size() - limit);
        List<AutonomyTraceEvent> memoryResults = traceEvents.subList(fromIndex, traceEvents.size());

        // 如果内存足够，直接返回
        if (memoryResults.size() >= limit) {
            return memoryResults;
        }

        // 否则从数据库补充
        if (traceEventRepository != null) {
            try {
                List<TraceEventEntity> dbResults = traceEventRepository.findTop1000ByOrderByTimestampDesc();
                return dbResults.stream()
                    .map(this::entityToEvent)
                    .limit(limit)
                    .collect(Collectors.toList());
            } catch (Exception e) {
                log.warn("Failed to query recent trace events from database: {}", e.getMessage());
            }
        }

        return memoryResults;
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
     */
    public List<AutonomyTraceEvent> getTraceByTaskKey(String taskKey) {
        if (taskKey == null || taskKey.isBlank()) {
            return List.of();
        }
        // 优先从内存获取
        List<AutonomyTraceEvent> memoryResults = traceEvents.stream()
            .filter(e -> taskKey.equals(e.taskKey()))
            .toList();

        if (!memoryResults.isEmpty()) {
            return memoryResults;
        }

        // 内存没有，从数据库查询
        if (traceEventRepository != null) {
            try {
                return traceEventRepository.findByTaskKeyOrderByTimestampAsc(taskKey).stream()
                    .map(this::entityToEvent)
                    .collect(Collectors.toList());
            } catch (Exception e) {
                log.warn("Failed to query trace events by taskKey={}: {}", taskKey, e.getMessage());
            }
        }

        return List.of();
    }

    /**
     * P1-6.2: 通过 executionId 查询 Trace 事件，实现与 RuntimeEventStore 的交叉查询
     */
    public List<AutonomyTraceEvent> getTraceByExecutionId(String executionId) {
        if (executionId == null || executionId.isBlank()) {
            return List.of();
        }
        // 优先从内存获取
        List<AutonomyTraceEvent> memoryResults = traceEvents.stream()
            .filter(e -> executionId.equals(e.executionId()))
            .toList();

        if (!memoryResults.isEmpty()) {
            return memoryResults;
        }

        // 内存没有，从数据库查询
        if (traceEventRepository != null) {
            try {
                return traceEventRepository.findByExecutionIdOrderByTimestampAsc(executionId).stream()
                    .map(this::entityToEvent)
                    .collect(Collectors.toList());
            } catch (Exception e) {
                log.warn("Failed to query trace events by executionId={}: {}", executionId, e.getMessage());
            }
        }

        return List.of();
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
