package com.livingagent.core.autonomy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 反馈事件总线 - 发布/订阅模式
 *
 * 编排流程中的各组件通过 FeedbackEventBus 发布反馈事件，
 * 订阅者可以监听并响应（如触发重试、人工介入、质量改进等）。
 */
public class FeedbackEventBus {

    private final List<Consumer<FeedbackEvent>> subscribers = new CopyOnWriteArrayList<>();
    private final AutonomyTraceService traceService;

    public FeedbackEventBus() {
        this.traceService = null;
    }

    public FeedbackEventBus(AutonomyTraceService traceService) {
        this.traceService = traceService;
    }

    /**
     * 订阅反馈事件
     */
    public void subscribe(Consumer<FeedbackEvent> subscriber) {
        subscribers.add(subscriber);
    }

    /**
     * 取消订阅
     */
    public void unsubscribe(Consumer<FeedbackEvent> subscriber) {
        subscribers.remove(subscriber);
    }

    /**
     * 发布反馈事件
     */
    public void publish(FeedbackEvent event) {
        if (traceService != null) {
            traceService.recordEvent(AutonomyTraceEvent.of(
                event.requestId(),
                "feedback_" + event.type().name().toLowerCase(),
                event.source(),
                event.message(),
                Map.of(
                    "feedbackType", event.type().name(),
                    "severity", event.severity().name(),
                    "eventId", event.eventId()
                )
            ));
        }

        for (Consumer<FeedbackEvent> subscriber : subscribers) {
            try {
                subscriber.accept(event);
            } catch (Exception e) {
                // 订阅者异常不影响其他订阅者
            }
        }
    }
}
