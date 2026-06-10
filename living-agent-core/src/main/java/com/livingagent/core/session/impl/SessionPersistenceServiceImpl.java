package com.livingagent.core.session.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.database.entity.SessionContextEntity;
import com.livingagent.core.database.repository.SessionContextRepository;
import com.livingagent.core.session.SessionPersistenceService;
import com.livingagent.core.session.ConnectionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SessionPersistenceServiceImpl implements SessionPersistenceService {
    private static final Logger log = LoggerFactory.getLogger(SessionPersistenceServiceImpl.class);
    
    private final SessionContextRepository repository;
    private final ObjectMapper objectMapper;

    public SessionPersistenceServiceImpl(SessionContextRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void saveSession(String sessionId, ConnectionContext context) {
        SessionContextEntity entity = repository.findById(sessionId)
            .orElse(new SessionContextEntity());
        entity.setSessionId(sessionId);
        entity.setUserId(context.userId());
        entity.setTenantId(context.tenantId());
        entity.setDepartmentCode(context.departmentCode());
        entity.setTaskKey(context.taskKey());
        entity.setExecutionId(context.executionId());
        entity.setProjectId(context.projectId());
        entity.setProjectKey(context.projectKey());
        entity.setConversationId(context.conversationId());
        entity.setConnectedAt(context.connectedAt());
        entity.setLastActivity(Instant.now());
        try {
            entity.setAttributesJson(objectMapper.writeValueAsString(context.attributes()));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize session attributes: {}", e.getMessage());
        }
        repository.save(entity);
    }

    @Override
    public Optional<ConnectionContext> getSession(String sessionId) {
        return repository.findById(sessionId).map(this::toContext);
    }

    @Override
    @Transactional
    public void deleteSession(String sessionId) {
        repository.deleteById(sessionId);
    }

    @Override
    public List<String> getRecentSessionsByUser(String userId, int limit) {
        return repository.findRecentByUserId(userId, limit).stream()
            .map(SessionContextEntity::getSessionId)
            .toList();
    }

    @Override
    public int countActiveSessionsByTenant(String tenantId) {
        return (int) repository.countByTenantId(tenantId);
    }

    @Override
    @Transactional
    public void cleanupExpiredSessions(long maxIdleMs) {
        Instant threshold = Instant.now().minusMillis(maxIdleMs);
        repository.deleteByLastActivityBefore(threshold);
    }

    @SuppressWarnings("unchecked")
    private ConnectionContext toContext(SessionContextEntity entity) {
        Map<String, Object> attributes = Map.of();
        if (entity.getAttributesJson() != null) {
            try {
                attributes = objectMapper.readValue(entity.getAttributesJson(), Map.class);
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize session attributes: {}", e.getMessage());
            }
        }
        return new ConnectionContext(
            entity.getSessionId(), entity.getUserId(), entity.getTenantId(),
            entity.getDepartmentCode(), entity.getTaskKey(), entity.getExecutionId(),
            entity.getProjectId(), entity.getProjectKey(), entity.getConversationId(),
            entity.getConnectedAt(), entity.getLastActivity(), attributes
        );
    }
}
