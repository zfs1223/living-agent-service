package com.livingagent.core.session.impl;

import com.livingagent.core.database.repository.PendingEventRepository;
import com.livingagent.core.session.EventQueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class EventQueueServiceImpl implements EventQueueService {
    private static final Logger log = LoggerFactory.getLogger(EventQueueServiceImpl.class);
    
    private final PendingEventRepository repository;

    public EventQueueServiceImpl(PendingEventRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void enqueueEvent(String sessionId, String eventType, String payload) {
        var entity = new com.livingagent.core.database.entity.PendingEventEntity();
        entity.setSessionId(sessionId);
        entity.setEventId(UUID.randomUUID().toString());
        entity.setEventType(eventType);
        entity.setPayload(payload);
        entity.setTimestamp(System.currentTimeMillis());
        entity.setSent(false);
        repository.save(entity);
        log.debug("Event enqueued: sessionId={}, eventType={}", sessionId, eventType);
    }

    @Override
    public List<PendingEvent> getPendingEvents(String sessionId) {
        return repository.findPendingEvents(sessionId).stream()
            .map(e -> new PendingEvent(e.getEventId(), e.getSessionId(), e.getEventType(), e.getPayload(), e.getTimestamp()))
            .toList();
    }

    @Override
    @Transactional
    public void markEventSent(String sessionId, String eventId) {
        repository.markAsSent(eventId, Instant.now());
        log.debug("Event marked as sent: sessionId={}, eventId={}", sessionId, eventId);
    }

    @Override
    @Transactional
    public void clearSentEvents(String sessionId) {
        repository.deleteSentEvents(sessionId);
    }

    @Override
    public int getPendingCount(String sessionId) {
        return repository.countBySessionIdAndSentFalse(sessionId);
    }

    @Override
    @Transactional
    public void deleteSessionEvents(String sessionId) {
        repository.deleteBySessionId(sessionId);
    }
}
