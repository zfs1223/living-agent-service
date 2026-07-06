package com.livingagent.core.evolution.orchestrator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * P31-A: 跨闭环事件总线。
 * 发布/订阅 L3 闭环事件，支持去重和冷却期。
 */
@Component
public class CrossLoopEventBus {

    private static final Logger log = LoggerFactory.getLogger(CrossLoopEventBus.class);

    private final ApplicationEventPublisher publisher;
    private final Map<String, Instant> recentEvents = new ConcurrentHashMap<>();

    public CrossLoopEventBus(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    /**
     * 发布跨闭环事件。相同 dedupeKey 在冷却期内不重复发布。
     */
    public void publish(int sourceLoop, String eventType, CrossLoopEvent.EventPriority priority,
                        Map<String, Object> payload, int cooldownSeconds) {
        String dedupeKey = sourceLoop + ":" + eventType;

        Instant lastPublish = recentEvents.get(dedupeKey);
        if (lastPublish != null && lastPublish.plusSeconds(cooldownSeconds).isAfter(Instant.now())) {
            log.debug("CrossLoopEvent deduped: {} (cooldown {}s)", dedupeKey, cooldownSeconds);
            return;
        }

        recentEvents.put(dedupeKey, Instant.now());
        CrossLoopEvent event = new CrossLoopEvent(this, sourceLoop, eventType, priority, payload, cooldownSeconds);
        publisher.publishEvent(event);
        log.info("Published CrossLoopEvent: loop={} type={} priority={}", sourceLoop, eventType, priority);
    }

    public void publish(int sourceLoop, String eventType, CrossLoopEvent.EventPriority priority,
                        Map<String, Object> payload) {
        publish(sourceLoop, eventType, priority, payload, 60);
    }
}
