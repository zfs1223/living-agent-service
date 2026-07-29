package com.livingagent.gateway.service;

import com.livingagent.core.distributed.im.ImRedisService;
import com.livingagent.gateway.websocket.IMWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

/**
 * 在途消息重试调度器 — P90 ACK 重试
 *
 * 职责:
 * - 每 30 秒扫描 Redis 中未 ACK 的在途消息
 * - 如果接收者在线，重新推送消息
 * - 在途消息 TTL 60s，超时自动过期，无需手动清理
 *
 * 不负责:
 * - 离线消息推送(由 MessageService.pushOfflineMessages 处理)
 * - ACK 状态持久化(由 ImRedisService 管理)
 */
@Service
public class InflightMessageRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(InflightMessageRetryScheduler.class);

    private static final String INFLIGHT_KEY_PREFIX = "im:inflight:";

    private final ImRedisService imRedisService;
    private final IMWebSocketHandler imWebSocketHandler;

    public InflightMessageRetryScheduler(ImRedisService imRedisService,
                                          IMWebSocketHandler imWebSocketHandler) {
        this.imRedisService = imRedisService;
        this.imWebSocketHandler = imWebSocketHandler;
    }

    /**
     * 每 30 秒扫描一次在途消息，对在线用户重试推送
     */
    @Scheduled(fixedRate = 30000)
    public void retryInflightMessages() {
        Set<String> inflightKeys = imRedisService.scanInflightMessages();
        if (inflightKeys == null || inflightKeys.isEmpty()) return;

        int retried = 0;
        int skipped = 0;

        for (String key : inflightKeys) {
            try {
                String messageId = key.substring(INFLIGHT_KEY_PREFIX.length());
                Object payloadObj = imRedisService.getInflightPayload(messageId);
                if (payloadObj == null) {
                    skipped++;
                    continue;
                }

                // payload 存储为 Map (Jackson 序列化/反序列化)
                String recipientId = null;
                if (payloadObj instanceof Map<?, ?> payload) {
                    Object rid = payload.get("recipientId");
                    if (rid != null) {
                        recipientId = rid.toString();
                    }
                }

                if (recipientId == null) {
                    log.warn("[IM-Retry] 在途消息缺少 recipientId: messageId={}", messageId);
                    skipped++;
                    continue;
                }

                // 如果用户在线，重新推送
                if (imWebSocketHandler.isUserOnline(recipientId)) {
                    imWebSocketHandler.pushToUser(recipientId, payloadObj);
                    retried++;
                    log.debug("[IM-Retry] 重试推送成功: messageId={}, recipientId={}", messageId, recipientId);
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                log.warn("[IM-Retry] 重试在途消息失败: key={}, error={}", key, e.getMessage());
            }
        }

        if (retried > 0 || skipped > 0) {
            log.info("[IM-Retry] 在途消息扫描完成: total={}, retried={}, skipped={}",
                inflightKeys.size(), retried, skipped);
        }
    }
}
