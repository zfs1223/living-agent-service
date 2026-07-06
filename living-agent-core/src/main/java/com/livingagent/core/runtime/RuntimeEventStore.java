package com.livingagent.core.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.livingagent.core.database.entity.RuntimeEventEntity;
import com.livingagent.core.database.repository.RuntimeEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.*;

/**
 * 运行时事件存储。B-0-4: DB 优先持久化，文件系统作为降级回退。
 * 事件写入 runtime_events 表，summary/index 写入文件系统（DataNamespaceService）。
 */
public class RuntimeEventStore {

    private static final Logger log = LoggerFactory.getLogger(RuntimeEventStore.class);

    private final RuntimeEventRepository eventRepository;
    private final DataNamespaceService namespaceService;
    private final ObjectMapper objectMapper;

    public RuntimeEventStore() {
        this(null, new DataNamespaceService());
    }

    public RuntimeEventStore(RuntimeEventRepository eventRepository,
                             DataNamespaceService namespaceService) {
        this.eventRepository = eventRepository;
        this.namespaceService = namespaceService;
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);
    }

    public void appendTaskEvent(String tenantId, String taskKey, String eventType, Map<String, Object> data) {
        appendEventToDb("task", taskKey, tenantId, eventType, data);
        // 降级：同时写文件（仅在 DB 不可用时作为回退日志）
        if (eventRepository == null) {
            String eventsPath = namespaceService.getTaskEventsPath(tenantId, taskKey);
            appendEventToFile(eventsPath, eventType, data);
        }
    }

    public void appendProjectEvent(String tenantId, String projectId, String eventType, Map<String, Object> data) {
        appendEventToDb("project", projectId, tenantId, eventType, data);
        if (eventRepository == null) {
            String eventsPath = namespaceService.getProjectEventsPath(tenantId, projectId);
            appendEventToFile(eventsPath, eventType, data);
        }
    }

    public void appendConversationEvent(String tenantId, String userId, String taskKey, String executionId,
                                         String eventType, Map<String, Object> data) {
        String scopeKey = userId + "/" + taskKey + "/" + executionId;
        appendEventToDb("conversation", scopeKey, tenantId, eventType, data);
        if (eventRepository == null) {
            String eventsPath = namespaceService.getConversationEventsPath(tenantId, userId, taskKey, executionId);
            appendEventToFile(eventsPath, eventType, data);
        }
    }

    public void appendConversationIdEvent(String tenantId, String conversationId,
                                           String eventType, Map<String, Object> data) {
        appendEventToDb("conversation_id", conversationId, tenantId, eventType, data);
        if (eventRepository == null) {
            String eventsPath = namespaceService.getConversationIdEventsPath(tenantId, conversationId);
            appendEventToFile(eventsPath, eventType, data);
        }
    }

    // ========== Summary/Index 仍使用文件系统 ==========

    public void writeTaskSummary(String tenantId, String taskKey, Map<String, Object> summary) {
        String summaryPath = namespaceService.getTaskSummaryPath(tenantId, taskKey);
        writeJson(summaryPath, summary);
    }

    public void writeProjectSummary(String tenantId, String projectId, Map<String, Object> summary) {
        String summaryPath = namespaceService.getProjectSummaryPath(tenantId, projectId);
        writeJson(summaryPath, summary);
    }

    public void writeConversationSession(String tenantId, String userId, String taskKey, String executionId,
                                          Map<String, Object> session) {
        String sessionPath = namespaceService.getConversationSessionPath(tenantId, userId, taskKey, executionId);
        writeJson(sessionPath, session);
    }

    public void writeConversationSummary(String tenantId, String userId, String taskKey, String executionId,
                                          Map<String, Object> summary) {
        String summaryPath = namespaceService.getConversationSummaryPath(tenantId, userId, taskKey, executionId);
        writeJson(summaryPath, summary);
    }

    public Optional<Map<String, Object>> readTaskSummary(String tenantId, String taskKey) {
        String summaryPath = namespaceService.getTaskSummaryPath(tenantId, taskKey);
        return readJson(summaryPath);
    }

    public Optional<Map<String, Object>> readProjectSummary(String tenantId, String projectId) {
        String summaryPath = namespaceService.getProjectSummaryPath(tenantId, projectId);
        return readJson(summaryPath);
    }

    public Optional<Map<String, Object>> readConversationSession(String tenantId, String userId,
                                                                   String taskKey, String executionId) {
        String sessionPath = namespaceService.getConversationSessionPath(tenantId, userId, taskKey, executionId);
        return readJson(sessionPath);
    }

    public void updateUserIndex(String userId, Map<String, Object> indexData) {
        String indexPath = namespaceService.getUserIndexPath(userId);
        writeJson(indexPath, indexData);
    }

    public void updateProjectIndex(String projectId, Map<String, Object> indexData) {
        String indexPath = namespaceService.getProjectIndexPath(projectId);
        writeJson(indexPath, indexData);
    }

    public void updateTaskIndex(String taskKey, Map<String, Object> indexData) {
        String indexPath = namespaceService.getTaskIndexPath(taskKey);
        writeJson(indexPath, indexData);
    }

    public void updateExecutionIndex(String executionId, Map<String, Object> indexData) {
        String indexPath = namespaceService.getExecutionIndexPath(executionId);
        writeJson(indexPath, indexData);
    }

    // ========== DB 事件查询 ==========

    public List<RuntimeEventEntity> getEventsByScope(String scope, String scopeKey) {
        if (eventRepository != null) {
            try {
                return eventRepository.findByScopeAndScopeKeyOrderByTimestampDesc(scope, scopeKey);
            } catch (Exception e) {
                log.warn("Failed to query runtime events from DB: {}", e.getMessage());
            }
        }
        return List.of();
    }

    // ========== 内部方法 ==========

    private void appendEventToDb(String scope, String scopeKey, String tenantId,
                                  String eventType, Map<String, Object> data) {
        if (eventRepository == null) return;
        try {
            RuntimeEventEntity entity = new RuntimeEventEntity();
            entity.setScope(scope);
            entity.setScopeKey(scopeKey);
            entity.setTenantId(tenantId);
            entity.setEventType(eventType);
            entity.setData(serializeMap(data));
            entity.setTimestamp(Instant.now());
            eventRepository.save(entity);
        } catch (Exception e) {
            log.warn("Failed to persist runtime event to DB (scope={}, key={}, type={}): {}",
                scope, scopeKey, eventType, e.getMessage());
        }
    }

    private void appendEventToFile(String eventsPath, String eventType, Map<String, Object> data) {
        try {
            Path path = Path.of(eventsPath);
            Files.createDirectories(path.getParent());

            Map<String, Object> event = new HashMap<>();
            event.put("timestamp", Instant.now().toString());
            event.put("type", eventType);
            event.put("data", data);

            String jsonLine = objectMapper.writeValueAsString(event) + "\n";
            Files.writeString(path, jsonLine, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn("Failed to append event to file {}: {}", eventsPath, e.getMessage());
        }
    }

    private void writeJson(String filePath, Map<String, Object> data) {
        try {
            Path path = Path.of(filePath);
            Files.createDirectories(path.getParent());
            objectMapper.writeValue(path.toFile(), data);
        } catch (IOException e) {
            log.warn("Failed to write JSON to {}: {}", filePath, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Optional<Map<String, Object>> readJson(String filePath) {
        try {
            Path path = Path.of(filePath);
            if (Files.exists(path)) {
                Map<String, Object> data = objectMapper.readValue(path.toFile(), Map.class);
                return Optional.of(data);
            }
        } catch (IOException e) {
            log.warn("Failed to read JSON from {}: {}", filePath, e.getMessage());
        }
        return Optional.empty();
    }

    private String serializeMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize event data: {}", e.getMessage());
            return null;
        }
    }
}
