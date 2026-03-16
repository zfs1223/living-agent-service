package com.livingagent.gateway.dialogue;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

@Service
public class DialogueSessionManager {
    
    private static final Logger log = LoggerFactory.getLogger(DialogueSessionManager.class);
    
    private final ConcurrentHashMap<String, DialogueSession> sessions;
    private final ScheduledExecutorService scheduler;
    private final long sessionTimeoutSeconds;
    private final int maxHistorySize;
    
    public DialogueSessionManager() {
        this(30 * 60, 50);
    }
    
    public DialogueSessionManager(long sessionTimeoutSeconds, int maxHistorySize) {
        this.sessions = new ConcurrentHashMap<>();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.sessionTimeoutSeconds = sessionTimeoutSeconds;
        this.maxHistorySize = maxHistorySize;
        
        startCleanupTask();
    }
    
    private void startCleanupTask() {
        scheduler.scheduleAtFixedRate(this::cleanupExpiredSessions, 5, 5, TimeUnit.MINUTES);
    }
    
    public DialogueSession createSession(String userId) {
        DialogueSession session = DialogueSession.create(userId);
        sessions.put(session.getSessionId(), session);
        log.info("Created dialogue session: sessionId={}, userId={}", session.getSessionId(), userId);
        return session;
    }
    
    public DialogueSession createSession(String sessionId, String userId) {
        DialogueSession session = new DialogueSession(sessionId, userId);
        sessions.put(sessionId, session);
        log.info("Created dialogue session: sessionId={}, userId={}", sessionId, userId);
        return session;
    }
    
    public DialogueSession getSession(String sessionId) {
        DialogueSession session = sessions.get(sessionId);
        if (session != null) {
            session.touch();
        }
        return session;
    }
    
    public DialogueSession getOrCreateSession(String sessionId, String userId) {
        return sessions.computeIfAbsent(sessionId, id -> new DialogueSession(id, userId));
    }
    
    public void removeSession(String sessionId) {
        DialogueSession session = sessions.remove(sessionId);
        if (session != null) {
            log.info("Removed dialogue session: sessionId={}", sessionId);
        }
    }
    
    public boolean hasSession(String sessionId) {
        return sessions.containsKey(sessionId);
    }
    
    public int getSessionCount() {
        return sessions.size();
    }
    
    private void cleanupExpiredSessions() {
        long now = System.currentTimeMillis();
        int removed = 0;
        
        for (Map.Entry<String, DialogueSession> entry : sessions.entrySet()) {
            DialogueSession session = entry.getValue();
            if (session.getInactiveSeconds() > sessionTimeoutSeconds) {
                sessions.remove(entry.getKey());
                removed++;
            }
        }
        
        if (removed > 0) {
            log.info("Cleaned up {} expired sessions", removed);
        }
    }
    
    public void trimAllHistories() {
        for (DialogueSession session : sessions.values()) {
            session.trimHistory(maxHistorySize);
        }
    }
    
    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        sessions.clear();
        log.info("DialogueSessionManager shutdown complete");
    }
}
