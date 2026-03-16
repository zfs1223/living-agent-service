package com.livingagent.core.proactive.event;

import java.time.Instant;
import java.util.Map;

public record HookEvent(
        String eventId,
        String eventType,
        String source,
        Map<String, Object> payload,
        Instant timestamp,
        String correlationId
) {
    public static HookEvent of(String eventType, String source, Map<String, Object> payload) {
        return new HookEvent(
                "evt_" + System.currentTimeMillis(),
                eventType,
                source,
                payload,
                Instant.now(),
                null
        );
    }
    
    public static HookEvent of(String eventType, Map<String, Object> payload) {
        return of(eventType, "system", payload);
    }
    
    public HookEvent withCorrelationId(String correlationId) {
        return new HookEvent(eventId, eventType, source, payload, timestamp, correlationId);
    }
    
    public Object get(String key) {
        return payload != null ? payload.get(key) : null;
    }
    
    public String getString(String key) {
        Object value = get(key);
        return value != null ? value.toString() : null;
    }
}
