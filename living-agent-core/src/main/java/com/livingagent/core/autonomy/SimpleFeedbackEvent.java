package com.livingagent.core.autonomy;

import java.util.Map;

/**
 * 简单反馈事件实现
 */
record SimpleFeedbackEvent(
    String eventId,
    String requestId,
    FeedbackEvent.FeedbackType type,
    String source,
    String message,
    FeedbackEvent.Severity severity,
    Map<String, Object> metadata
) implements FeedbackEvent {}
