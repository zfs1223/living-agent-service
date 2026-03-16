package com.livingagent.core.proactive.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class EventHookManager {

    private static final Logger log = LoggerFactory.getLogger(EventHookManager.class);

    private final ApplicationEventPublisher eventPublisher;
    private final List<HookHandler> handlers;
    private final ExecutorService executorService;
    private final Map<String, List<HookHandler>> handlerCache = new ConcurrentHashMap<>();
    private final Map<String, EventStatistics> statistics = new ConcurrentHashMap<>();

    public EventHookManager(ApplicationEventPublisher eventPublisher, List<HookHandler> handlers) {
        this.eventPublisher = eventPublisher;
        this.handlers = handlers != null ? new ArrayList<>(handlers) : new ArrayList<>();
        this.executorService = Executors.newFixedThreadPool(4);
        
        buildHandlerCache();
        log.info("EventHookManager initialized with {} handlers", this.handlers.size());
    }

    private void buildHandlerCache() {
        handlerCache.clear();
        for (HookHandler handler : handlers) {
            if (!handler.isEnabled()) {
                continue;
            }
            for (String eventType : handler.supportedEvents()) {
                handlerCache.computeIfAbsent(eventType, k -> new ArrayList<>()).add(handler);
            }
        }
        
        for (List<HookHandler> handlerList : handlerCache.values()) {
            handlerList.sort(Comparator.comparingInt(HookHandler::getOrder));
        }
    }

    public void publishEvent(HookEvent event) {
        log.debug("Publishing event: {} from {}", event.eventType(), event.source());
        
        recordEvent(event);
        
        List<HookHandler> matchingHandlers = findHandlers(event.eventType());
        
        if (matchingHandlers.isEmpty()) {
            log.debug("No handlers found for event type: {}", event.eventType());
            return;
        }
        
        for (HookHandler handler : matchingHandlers) {
            executorService.submit(() -> {
                try {
                    long start = System.currentTimeMillis();
                    handler.handle(event);
                    long duration = System.currentTimeMillis() - start;
                    
                    recordHandlerExecution(handler, event, true, duration);
                    
                    log.debug("Handler {} processed event {} in {}ms", 
                            handler.getClass().getSimpleName(), event.eventType(), duration);
                    
                } catch (Exception e) {
                    log.error("Handler {} failed to process event {}: {}", 
                            handler.getClass().getSimpleName(), event.eventType(), e.getMessage());
                    recordHandlerExecution(handler, event, false, 0);
                }
            });
        }
    }

    public void publishEvent(String eventType, String source, Map<String, Object> payload) {
        publishEvent(HookEvent.of(eventType, source, payload));
    }

    public void publishEvent(String eventType, Map<String, Object> payload) {
        publishEvent(HookEvent.of(eventType, payload));
    }

    public void registerHandler(HookHandler handler) {
        if (handler == null || handlers.contains(handler)) {
            return;
        }
        
        handlers.add(handler);
        buildHandlerCache();
        log.info("Registered handler: {}", handler.getClass().getSimpleName());
    }

    public void unregisterHandler(HookHandler handler) {
        if (handlers.remove(handler)) {
            buildHandlerCache();
            log.info("Unregistered handler: {}", handler.getClass().getSimpleName());
        }
    }

    public List<HookHandler> findHandlers(String eventType) {
        List<HookHandler> result = new ArrayList<>();
        
        List<HookHandler> specificHandlers = handlerCache.get(eventType);
        if (specificHandlers != null) {
            result.addAll(specificHandlers);
        }
        
        List<HookHandler> wildcardHandlers = handlerCache.get("*");
        if (wildcardHandlers != null) {
            result.addAll(wildcardHandlers);
        }
        
        result.sort(Comparator.comparingInt(HookHandler::getOrder));
        
        return result;
    }

    private void recordEvent(HookEvent event) {
        statistics.computeIfAbsent(event.eventType(), k -> new EventStatistics())
                .recordEvent();
    }

    private void recordHandlerExecution(HookHandler handler, HookEvent event, boolean success, long durationMs) {
        String key = event.eventType() + ":" + handler.getClass().getSimpleName();
        statistics.computeIfAbsent(key, k -> new EventStatistics())
                .recordExecution(success, durationMs);
    }

    public Map<String, Object> getStatistics() {
        Map<String, Object> result = new HashMap<>();
        result.put("totalHandlers", handlers.size());
        result.put("enabledHandlers", handlers.stream().filter(HookHandler::isEnabled).count());
        result.put("eventTypes", handlerCache.keySet());
        
        Map<String, Object> eventStats = new HashMap<>();
        statistics.forEach((k, v) -> eventStats.put(k, v.toMap()));
        result.put("statistics", eventStats);
        
        return result;
    }

    public void refreshHandlers() {
        buildHandlerCache();
        log.info("Handlers refreshed");
    }

    public void shutdown() {
        executorService.shutdown();
        log.info("EventHookManager shutdown");
    }

    private static class EventStatistics {
        private long totalEvents = 0;
        private long successfulExecutions = 0;
        private long failedExecutions = 0;
        private long totalDurationMs = 0;
        private long lastEventTime = 0;

        synchronized void recordEvent() {
            totalEvents++;
            lastEventTime = System.currentTimeMillis();
        }

        synchronized void recordExecution(boolean success, long durationMs) {
            if (success) {
                successfulExecutions++;
            } else {
                failedExecutions++;
            }
            totalDurationMs += durationMs;
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("totalEvents", totalEvents);
            map.put("successfulExecutions", successfulExecutions);
            map.put("failedExecutions", failedExecutions);
            map.put("averageDurationMs", totalEvents > 0 ? totalDurationMs / totalEvents : 0);
            map.put("lastEventTime", lastEventTime);
            return map;
        }
    }
}
