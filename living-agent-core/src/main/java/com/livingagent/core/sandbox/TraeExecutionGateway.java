package com.livingagent.core.sandbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Trae 执行网关：
 * - 对工具层隐藏 SandboxSession 细节
 * - 作为 SandboxService -> SandboxExecutor 迁移期的兼容适配层
 */
public class TraeExecutionGateway {

    private static final Logger log = LoggerFactory.getLogger(TraeExecutionGateway.class);

    private final SandboxService sandboxService;
    private final ConcurrentMap<String, SandboxSession> sessions = new ConcurrentHashMap<>();

    public TraeExecutionGateway(SandboxService sandboxService) {
        this.sandboxService = sandboxService;
    }

    public CompletableFuture<ExecutionResult> execute(String sessionId, String action, Map<String, Object> params) {
        SandboxSession session = getOrCreateSession(sessionId);
        if (session == null) {
            return CompletableFuture.completedFuture(
                ExecutionResult.error("trae-no-session", "Failed to create sandbox session")
            );
        }
        return session.executeTraeCommand(action, params);
    }

    public void closeSession(String sessionId) {
        SandboxSession session = sessions.remove(sessionId);
        if (session != null) {
            session.close();
        }
    }

    public void closeAllSessions() {
        sessions.values().forEach(SandboxSession::close);
        sessions.clear();
    }

    private SandboxSession getOrCreateSession(String sessionId) {
        String sid = (sessionId == null || sessionId.isBlank()) ? "default" : sessionId;
        return sessions.computeIfAbsent(sid, id -> {
            if (!sandboxService.isAvailable()) {
                log.warn("Sandbox service unavailable for Trae session: {}", id);
                return null;
            }
            Optional<SandboxSession> created = sandboxService.createSession(id, SandboxService.SandboxConfig.TRAE_DEFAULT);
            if (created.isEmpty()) {
                log.warn("Failed to create Trae sandbox session: {}", id);
            }
            return created.orElse(null);
        });
    }
}
