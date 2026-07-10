package com.livingagent.core.session;

import java.util.List;
import java.util.Optional;

/** WebSocket 事件队列服务接口 */
public interface EventQueueService {
    /** 入队事件（用于断线期间的事件缓存） */
    void enqueueEvent(String sessionId, String eventType, String payload);
    /** 获取待补发的事件列表 */
    List<PendingEvent> getPendingEvents(String sessionId);
    /** R6: 获取指定游标之后的事件（用于重连补发，避免重复发送） */
    List<PendingEvent> getPendingEventsAfter(String sessionId, long afterTimestamp);
    /** 标记事件已发送 */
    void markEventSent(String sessionId, String eventId);
    /** 清理已发送的事件 */
    void clearSentEvents(String sessionId);
    /** 获取未发送事件数量 */
    int getPendingCount(String sessionId);
    /** 删除会话的所有事件 */
    void deleteSessionEvents(String sessionId);
    /** R6: 获取会话最新事件的时间戳（作为游标返回给客户端） */
    Optional<Long> getLatestEventTimestamp(String sessionId);

    record PendingEvent(String eventId, String sessionId, String eventType, String payload, long timestamp) {}
}
