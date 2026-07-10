package com.livingagent.gateway.websocket;

import com.livingagent.core.session.EventQueueService;
import java.util.List;
import java.util.Optional;

/** 支持事件持久化的 ConnectionRegistry 扩展接口 */
public interface PersistentConnectionRegistry extends ConnectionRegistry {
    /** 获取待补发的事件列表 */
    List<EventQueueService.PendingEvent> getPendingEvents(String sessionId);
    /** R6: 获取指定游标之后的事件（重连补发，避免重复） */
    List<EventQueueService.PendingEvent> getPendingEventsAfter(String sessionId, long afterTimestamp);
    /** 标记事件已发送 */
    void markEventSent(String sessionId, String eventId);
    /** 清理已发送的事件 */
    void clearSentEvents(String sessionId);
    /** R6: 获取会话最新事件时间戳（游标） */
    Optional<Long> getLatestEventTimestamp(String sessionId);
}
