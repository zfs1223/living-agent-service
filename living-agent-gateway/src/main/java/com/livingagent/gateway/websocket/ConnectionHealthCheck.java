package com.livingagent.gateway.websocket;

import com.livingagent.core.diagnosis.HealthCheck;
import com.livingagent.core.diagnosis.HealthIssue;
import com.livingagent.core.diagnosis.HealthStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * P24-C + P3-A: WebSocket连接健康检查。
 * 周期性检查活跃连接的 lastActivity，检测僵死/超时连接并发布 HealthIssue。
 */
public class ConnectionHealthCheck implements HealthCheck {

    private static final Logger log = LoggerFactory.getLogger(ConnectionHealthCheck.class);

    private static final Duration DEGRADED_THRESHOLD = Duration.ofMinutes(5);
    private static final Duration UNHEALTHY_THRESHOLD = Duration.ofMinutes(30);

    private final ConnectionRegistry connectionRegistry;
    private final ApplicationEventPublisher eventPublisher;

    public ConnectionHealthCheck(ConnectionRegistry connectionRegistry,
                                 ApplicationEventPublisher eventPublisher) {
        this.connectionRegistry = connectionRegistry;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public HealthStatus check() {
        int totalConnections = connectionRegistry.getActiveConnectionCount();
        if (totalConnections == 0) {
            return HealthStatus.healthy("websocket_connections");
        }

        int degradedCount = 0;
        int unhealthyCount = 0;
        Instant now = Instant.now();

        for (String sessionId : getAllSessionIds()) {
            var lastActivityOpt = connectionRegistry.getLastActivity(sessionId);
            if (lastActivityOpt.isEmpty()) continue;

            Duration idle = Duration.between(lastActivityOpt.get(), now);
            if (idle.compareTo(UNHEALTHY_THRESHOLD) > 0) {
                unhealthyCount++;
            } else if (idle.compareTo(DEGRADED_THRESHOLD) > 0) {
                degradedCount++;
            }
        }

        if (unhealthyCount > 0) {
            publishConnectionIssue(unhealthyCount, "UNHEALTHY");
            return HealthStatus.unhealthy("websocket_connections",
                String.format("%d unhealthy connections (idle>%dm) out of %d total",
                    unhealthyCount, UNHEALTHY_THRESHOLD.toMinutes(), totalConnections));
        }

        if (degradedCount > totalConnections / 2) {
            publishConnectionIssue(degradedCount, "DEGRADED");
            return HealthStatus.degraded("websocket_connections",
                String.format("%d degraded connections (idle>%dm) out of %d total",
                    degradedCount, DEGRADED_THRESHOLD.toMinutes(), totalConnections));
        }

        return HealthStatus.healthy("websocket_connections");
    }

    private void publishConnectionIssue(int count, String level) {
        try {
            HealthIssue issue = new HealthIssue(
                "websocket_connections",
                String.format("%s: %d idle WebSocket connections detected", level, count),
                HealthIssue.Severity.HIGH
            );
            issue.setType(HealthIssue.IssueType.CONNECTIVITY);
            issue.setDescription(String.format("%d WebSocket connections idle beyond threshold (%s)", count, level));
            issue.setSuggestedAction("NOTIFY_CLIENT");
            eventPublisher.publishEvent(issue);
            log.info("P24-C: Published connection health issue: {} idle connections ({})", count, level);
        } catch (Exception e) {
            log.warn("P24-C: Failed to publish connection health issue: {}", e.getMessage());
        }
    }

    private List<String> getAllSessionIds() {
        return connectionRegistry.getAllSessionIds();
    }
}
