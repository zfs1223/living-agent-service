package com.livingagent.core.evolution.orchestrator;

import org.springframework.context.ApplicationEvent;

import java.time.Instant;
import java.util.Map;

/**
 * P31-A: 跨闭环事件。
 * 用于 L3 各闭环之间的协同通信。
 */
public class CrossLoopEvent extends ApplicationEvent {

    public enum EventPriority {
        SECURITY(1),
        SELF_HEALING(2),
        DEGRADATION(3),
        RECEIPT(4),
        ECONOMY(5),
        KNOWLEDGE(6),
        PERSONALITY(7);

        private final int order;

        EventPriority(int order) {
            this.order = order;
        }

        public int getOrder() {
            return order;
        }
    }

    private final int sourceLoop;
    private final String eventType;
    private final EventPriority priority;
    private final Map<String, Object> payload;
    private final Instant coolingUntil;

    public CrossLoopEvent(Object source, int sourceLoop, String eventType,
                          EventPriority priority, Map<String, Object> payload, int cooldownSeconds) {
        super(source);
        this.sourceLoop = sourceLoop;
        this.eventType = eventType;
        this.priority = priority;
        this.payload = payload;
        this.coolingUntil = cooldownSeconds > 0 ? Instant.now().plusSeconds(cooldownSeconds) : null;
    }

    public int getSourceLoop() { return sourceLoop; }
    public String getEventType() { return eventType; }
    public EventPriority getPriority() { return priority; }
    public Map<String, Object> getPayload() { return payload; }
    public Instant getCoolingUntil() { return coolingUntil; }

    public String dedupeKey() {
        return sourceLoop + ":" + eventType;
    }

    @Override
    public String toString() {
        return "CrossLoopEvent{loop=" + sourceLoop + ", type=" + eventType + ", priority=" + priority + "}";
    }
}
