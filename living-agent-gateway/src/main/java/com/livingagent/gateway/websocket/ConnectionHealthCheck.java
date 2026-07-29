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

    // 空闲连接阈值：超过10分钟视为DEGRADED，超过30分钟视为UNHEALTHY
    private static final Duration DEGRADED_THRESHOLD = Duration.ofMinutes(10);
    private static final Duration UNHEALTHY_THRESHOLD = Duration.ofMinutes(30);
    // 绝对阈值：至少有3个空闲连接才触发报警（避免单连接误报）
    private static final int MIN_DEGRADED_COUNT = 3;

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

        // 直接清理超过UNHEALTHY阈值（30分钟）的僵死连接
        // 不依赖自愈编排器，在检测阶段就主动清理
        if (unhealthyCount > 0) {
            try {
                connectionRegistry.cleanupStaleConnections(UNHEALTHY_THRESHOLD.toMillis());
                log.info("P24-C: Cleaned up {} stale connections (idle>{}m)", unhealthyCount, UNHEALTHY_THRESHOLD.toMinutes());
            } catch (Exception e) {
                log.warn("P24-C: Failed to cleanup stale connections: {}", e.getMessage());
            }
        }

        if (unhealthyCount > 0) {
            publishConnectionIssue(unhealthyCount, "UNHEALTHY");
            return HealthStatus.unhealthy("websocket_connections",
                String.format("%d unhealthy connections (idle>%dm) out of %d total",
                    unhealthyCount, UNHEALTHY_THRESHOLD.toMinutes(), totalConnections));
        }

        // 使用绝对阈值：至少有MIN_DEGRADED_COUNT个空闲连接才报警
        // 避免单连接误报（用户暂时离开不应触发告警）
        if (degradedCount >= MIN_DEGRADED_COUNT && degradedCount > totalConnections / 3) {
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
