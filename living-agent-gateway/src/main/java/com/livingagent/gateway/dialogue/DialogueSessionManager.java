package com.livingagent.gateway.dialogue;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

@Service
public class DialogueSessionManager {

    private static final Logger log = LoggerFactory.getLogger(DialogueSessionManager.class);

    private final ConcurrentHashMap<String, DialogueSession> sessions;
    private final ScheduledExecutorService scheduler;
    private volatile long sessionTimeoutSeconds;
    private volatile int maxHistorySize;
    private final CrossLoopEventBus eventBus;

    private final LongAdder totalSessionsCreated = new LongAdder();
    private final LongAdder totalSessionsExpired = new LongAdder();
    private final LongAdder totalMessagesProcessed = new LongAdder();
    private final LongAdder totalHighLatencyMessages = new LongAdder();

    public DialogueSessionManager() {
        this(null, 30 * 60, 50);
    }

    public DialogueSessionManager(CrossLoopEventBus eventBus, long sessionTimeoutSeconds, int maxHistorySize) {
        this.sessions = new ConcurrentHashMap<>();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.sessionTimeoutSeconds = sessionTimeoutSeconds;
        this.maxHistorySize = maxHistorySize;
        this.eventBus = eventBus;

        startCleanupTask();
        startImprovementTask();
    }

    @Autowired(required = false)
    public void setEventBus(CrossLoopEventBus eventBus) {
        // Support injection after construction if needed
    }

    private void startCleanupTask() {
        scheduler.scheduleAtFixedRate(this::cleanupExpiredSessions, 5, 5, TimeUnit.MINUTES);
    }

    private void startImprovementTask() {
        scheduler.scheduleAtFixedRate(this::evaluateSessionQuality, 30, 30, TimeUnit.MINUTES);
    }

    public DialogueSession createSession(String userId) {
        DialogueSession session = DialogueSession.create(userId);
        sessions.put(session.getSessionId(), session);
        totalSessionsCreated.increment();
        log.info("Created dialogue session: sessionId={}, userId={}", session.getSessionId(), userId);
        return session;
    }

    public DialogueSession createSession(String sessionId, String userId) {
        DialogueSession session = new DialogueSession(sessionId, userId);
        sessions.put(sessionId, session);
        totalSessionsCreated.increment();
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
        return sessions.computeIfAbsent(sessionId, id -> {
            totalSessionsCreated.increment();
            return new DialogueSession(id, userId);
        });
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

    public void recordMessageLatency(String sessionId, long latencyMs) {
        totalMessagesProcessed.increment();
        if (latencyMs > 5000) {
            totalHighLatencyMessages.increment();
            log.warn("[闭环36] 会话高延迟: sessionId={}, latency={}ms", sessionId, latencyMs);
        }
    }

    private void cleanupExpiredSessions() {
        long now = System.currentTimeMillis();
        int removed = 0;

        for (Map.Entry<String, DialogueSession> entry : sessions.entrySet()) {
            DialogueSession session = entry.getValue();
            if (session.getInactiveSeconds() > sessionTimeoutSeconds) {
                sessions.remove(entry.getKey());
                totalSessionsExpired.increment();
                removed++;
            }
        }

        if (removed > 0) {
            log.info("Cleaned up {} expired sessions", removed);
        }
    }

    private void evaluateSessionQuality() {
        long total = totalMessagesProcessed.sum();
        if (total < 10) return;

        double highLatencyRate = (double) totalHighLatencyMessages.sum() / total;

        if (highLatencyRate > 0.2) {
            int newHistorySize = Math.max(20, maxHistorySize - 5);
            if (newHistorySize != maxHistorySize) {
                maxHistorySize = newHistorySize;
                log.info("[闭环36] 高延迟率{}%，缩减历史大小至{}", String.format("%.0f", highLatencyRate * 100), maxHistorySize);
                publishImprovementEvent("history_size_reduced", highLatencyRate);
            }
        } else if (highLatencyRate < 0.05 && maxHistorySize < 100) {
            maxHistorySize = Math.min(100, maxHistorySize + 2);
            log.info("[闭环36] 延迟率低，放宽历史大小至{}", maxHistorySize);
            publishImprovementEvent("history_size_increased", highLatencyRate);
        }

        double expireRate = totalSessionsCreated.sum() > 0
            ? (double) totalSessionsExpired.sum() / totalSessionsCreated.sum() : 0;
        if (expireRate > 0.8 && sessionTimeoutSeconds > 10 * 60) {
            sessionTimeoutSeconds = Math.max(10 * 60, sessionTimeoutSeconds - 5 * 60);
            log.info("[闭环36] 过期率高{}%，缩短超时至{}s", String.format("%.0f", expireRate * 100), sessionTimeoutSeconds);
            publishImprovementEvent("timeout_shortened", expireRate);
        } else if (expireRate < 0.3 && sessionTimeoutSeconds < 60 * 60) {
            sessionTimeoutSeconds = Math.min(60 * 60, sessionTimeoutSeconds + 5 * 60);
            log.info("[闭环36] 过期率低，延长超时至{}s", sessionTimeoutSeconds);
            publishImprovementEvent("timeout_extended", expireRate);
        }
    }

    private void publishImprovementEvent(String action, double metric) {
        if (eventBus != null) {
            eventBus.publish(36, "session_quality_adjusted", CrossLoopEvent.EventPriority.DEGRADATION,
                Map.of("action", action, "metric", metric,
                    "maxHistorySize", maxHistorySize,
                    "sessionTimeoutSeconds", sessionTimeoutSeconds));
        }
    }

    public void trimAllHistories() {
        for (DialogueSession session : sessions.values()) {
            session.trimHistory(maxHistorySize);
        }
    }

    public SessionQualityReport getQualityReport() {
        long total = totalMessagesProcessed.sum();
        return new SessionQualityReport(
            sessions.size(),
            totalSessionsCreated.sum(),
            totalSessionsExpired.sum(),
            total > 0 ? (double) totalHighLatencyMessages.sum() / total : 0,
            sessionTimeoutSeconds,
            maxHistorySize
        );
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

    public record SessionQualityReport(
        int activeSessions, long totalCreated, long totalExpired,
        double highLatencyRate, long sessionTimeoutSeconds, int maxHistorySize) {}
}
