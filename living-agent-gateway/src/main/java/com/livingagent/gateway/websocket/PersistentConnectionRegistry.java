package com.livingagent.gateway.websocket;

import com.livingagent.core.session.EventQueueService;
import java.util.List;

/** 支持事件持久化的 ConnectionRegistry 扩展接口 */
public interface PersistentConnectionRegistry extends ConnectionRegistry {
    /** 获取待补发的事件列表 */
    List<EventQueueService.PendingEvent> getPendingEvents(String sessionId);
    /** 标记事件已发送 */
    void markEventSent(String sessionId, String eventId);
    /** 清理已发送的事件 */
    void clearSentEvents(String sessionId);
}
