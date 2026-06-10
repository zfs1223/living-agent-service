package com.livingagent.core.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class RuntimeEventStore {

    private final DataNamespaceService namespaceService;
    private final ObjectMapper objectMapper;

    public RuntimeEventStore() {
        this(new DataNamespaceService());
    }

    public RuntimeEventStore(DataNamespaceService namespaceService) {
        this.namespaceService = namespaceService;
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);
    }

    public void appendTaskEvent(String tenantId, String taskKey, String eventType, Map<String, Object> data) {
        String eventsPath = namespaceService.getTaskEventsPath(tenantId, taskKey);
        appendEvent(eventsPath, eventType, data);
    }

    public void appendProjectEvent(String tenantId, String projectId, String eventType, Map<String, Object> data) {
        String eventsPath = namespaceService.getProjectEventsPath(tenantId, projectId);
        appendEvent(eventsPath, eventType, data);
    }

    public void appendConversationEvent(String tenantId, String userId, String taskKey, String executionId, 
                                        String eventType, Map<String, Object> data) {
        String eventsPath = namespaceService.getConversationEventsPath(tenantId, userId, taskKey, executionId);
        appendEvent(eventsPath, eventType, data);
    }

    public void appendConversationIdEvent(String tenantId, String conversationId,
                                          String eventType, Map<String, Object> data) {
        String eventsPath = namespaceService.getConversationIdEventsPath(tenantId, conversationId);
        appendEvent(eventsPath, eventType, data);
    }

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

    private void appendEvent(String eventsPath, String eventType, Map<String, Object> data) {
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
            System.err.println("Failed to append event to " + eventsPath + ": " + e.getMessage());
        }
    }

    private void writeJson(String filePath, Map<String, Object> data) {
        try {
            Path path = Path.of(filePath);
            Files.createDirectories(path.getParent());
            objectMapper.writeValue(path.toFile(), data);
        } catch (IOException e) {
            System.err.println("Failed to write JSON to " + filePath + ": " + e.getMessage());
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
            System.err.println("Failed to read JSON from " + filePath + ": " + e.getMessage());
        }
        return Optional.empty();
    }
}
