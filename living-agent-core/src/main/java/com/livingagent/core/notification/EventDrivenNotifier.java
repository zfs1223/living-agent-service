package com.livingagent.core.notification;

import com.livingagent.core.database.entity.NotificationEntity;
import com.livingagent.core.database.repository.NotificationRepository;
import com.livingagent.core.notification.feedback.NotificationMetricsService;
import com.livingagent.core.notification.feedback.NotificationStrategyOptimizer;
import com.livingagent.core.proactive.alert.AlertNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 事件驱动通知器 - 统一门面类
 * 整合 DingTalk/Feishu/Webhook 等渠道，提供统一的通知接口
 * 对应技能: event-driven-notifier (贾维斯模式核心技能)
 */
public class EventDrivenNotifier {

    private static final Logger log = LoggerFactory.getLogger(EventDrivenNotifier.class);

    private final Map<String, AlertNotifier> channels = new ConcurrentHashMap<>();
    private final Map<NotificationPriority, List<String>> priorityRouting = new EnumMap<>(NotificationPriority.class);
    private final Map<String, NotificationSubscription> subscriptions = new ConcurrentHashMap<>();
    private final NotificationMetricsService metricsService;
    private final NotificationStrategyOptimizer strategyOptimizer;
    private final NotificationRepository notificationRepository;
    private final ExecutorService asyncExecutor;

    public EventDrivenNotifier(NotificationMetricsService metricsService,
                               NotificationStrategyOptimizer strategyOptimizer,
                               NotificationRepository notificationRepository) {
        this.metricsService = metricsService;
        this.strategyOptimizer = strategyOptimizer;
        this.notificationRepository = notificationRepository;
        this.asyncExecutor = Executors.newFixedThreadPool(4);
        initDefaultRouting();
    }

    private void initDefaultRouting() {
        priorityRouting.put(NotificationPriority.CRITICAL, List.of("websocket", "dingtalk", "feishu"));
        priorityRouting.put(NotificationPriority.HIGH, List.of("websocket", "dingtalk"));
        priorityRouting.put(NotificationPriority.MEDIUM, List.of("websocket"));
        priorityRouting.put(NotificationPriority.LOW, List.of("system_message"));
    }

    public void registerChannel(AlertNotifier notifier) {
        channels.put(notifier.getChannelName(), notifier);
        log.info("Registered notification channel: {} (available={})", notifier.getChannelName(), notifier.isAvailable());
    }

    public void setPriorityRouting(NotificationPriority priority, List<String> channelNames) {
        priorityRouting.put(priority, channelNames);
    }

    // --- 推送通知 ---

    public NotificationResult pushNotification(Notification notification) {
        List<String> targetChannels = resolveChannels(notification);
        notification = enrichNotification(notification);

        persistNotification(notification);

        List<ChannelResult> results = new ArrayList<>();
        for (String channelName : targetChannels) {
            AlertNotifier channel = channels.get(channelName);
            if (channel == null || !channel.isAvailable()) {
                results.add(new ChannelResult(channelName, false, "channel unavailable"));
                continue;
            }
            try {
                boolean sent = channel.send(toAlert(notification));
                metricsService.recordSent(channelName);
                if (sent) {
                    metricsService.recordDelivered(channelName);
                }
                results.add(new ChannelResult(channelName, sent, sent ? "ok" : "send failed"));
            } catch (Exception e) {
                log.error("Failed to send notification via {}: {}", channelName, e.getMessage());
                results.add(new ChannelResult(channelName, false, e.getMessage()));
            }
        }

        boolean anySuccess = results.stream().anyMatch(ChannelResult::success);
        log.info("Notification pushed: id={}, title={}, channels={}, success={}",
            notification.notificationId(), notification.title(), targetChannels, anySuccess);

        return new NotificationResult(notification.notificationId(), anySuccess, results);
    }

    public void pushNotificationAsync(Notification notification) {
        asyncExecutor.submit(() -> pushNotification(notification));
    }

    public List<NotificationResult> pushBatch(List<Notification> notifications) {
        List<NotificationResult> results = new ArrayList<>();
        for (Notification notification : notifications) {
            results.add(pushNotification(notification));
        }
        return results;
    }

    // --- 事件订阅 ---

    public void subscribe(String eventType, List<String> channels, NotificationPriority priority) {
        subscribe(eventType, channels, priority, null, false);
    }

    public void subscribe(String eventType, List<String> channels, NotificationPriority priority,
                          String templateId, boolean requiresAck) {
        subscriptions.put(eventType, new NotificationSubscription(eventType, channels, priority, templateId, requiresAck));
        log.info("Subscribed to event: {} -> channels={}, priority={}", eventType, channels, priority);
    }

    public void unsubscribe(String eventType) {
        subscriptions.remove(eventType);
        log.info("Unsubscribed from event: {}", eventType);
    }

    public NotificationResult notifyEvent(String eventType, String userId, String title, String content,
                                          Map<String, Object> data) {
        NotificationSubscription sub = subscriptions.get(eventType);
        if (sub == null) {
            log.debug("No subscription for event: {}, using default routing", eventType);
            return pushNotification(Notification.builder()
                .userId(userId).title(title).content(content)
                .type(NotificationType.ALERT).priority(NotificationPriority.MEDIUM)
                .data(data).build());
        }

        String renderedContent = renderTemplate(sub.templateId(), data, content);

        Notification notification = Notification.builder()
            .userId(userId).title(title).content(renderedContent)
            .type(mapEventTypeToType(eventType)).priority(sub.priority())
            .channels(sub.channels()).data(data).build();

        return pushNotification(notification);
    }

    // --- 查询 ---

    public List<NotificationEntity> getUnreadNotifications(String department) {
        if (notificationRepository == null) return List.of();
        return notificationRepository.findByDepartmentAndReadFalseOrderByTimestampDesc(department);
    }

    public long getUnreadCount(String department) {
        if (notificationRepository == null) return 0;
        return notificationRepository.countByDepartmentAndReadFalse(department);
    }

    public void markAsRead(String notificationId) {
        if (notificationRepository == null) return;
        notificationRepository.findByNotificationId(notificationId).ifPresent(entity -> {
            entity.setRead(true);
            notificationRepository.save(entity);
            metricsService.recordRead("system");
        });
    }

    public NotificationMetricsService.NotificationMetricsReport getMetricsReport() {
        return metricsService.getReport();
    }

    public void optimizeStrategy() {
        strategyOptimizer.optimize();
    }

    public Map<String, Boolean> getChannelAvailability() {
        Map<String, Boolean> availability = new LinkedHashMap<>();
        channels.forEach((name, notifier) -> availability.put(name, notifier.isAvailable()));
        return availability;
    }

    // --- 内部方法 ---

    private List<String> resolveChannels(Notification notification) {
        if (notification.channels() != null && !notification.channels().isEmpty()) {
            return notification.channels();
        }
        return priorityRouting.getOrDefault(notification.priority(), List.of("system_message"));
    }

    private Notification enrichNotification(Notification notification) {
        if (notification.notificationId() == null) {
            notification = notification.withNotificationId("notif_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8));
        }
        if (notification.createdAt() == null) {
            notification = notification.withCreatedAt(Instant.now());
        }
        return notification;
    }

    private void persistNotification(Notification notification) {
        if (notificationRepository == null) return;
        try {
            NotificationEntity entity = new NotificationEntity(
                notification.notificationId(),
                notification.department() != null ? notification.department() : "default",
                notification.type().name(),
                notification.title(),
                notification.content(),
                notification.priority().name(),
                notification.data() != null ? notification.data().toString() : null,
                notification.createdAt() != null ? notification.createdAt() : Instant.now(),
                false
            );
            notificationRepository.save(entity);
        } catch (Exception e) {
            log.warn("Failed to persist notification: {}", e.getMessage());
        }
    }

    private AlertNotifier.Alert toAlert(Notification notification) {
        return new AlertNotifier.Alert(
            notification.notificationId(),
            notification.title(),
            notification.content(),
            mapPriorityToAlertLevel(notification.priority()),
            mapTypeToAlertType(notification.type()),
            notification.userId() != null ? List.of(notification.userId()) : List.of(),
            notification.data() != null ? notification.data() : Map.of(),
            notification.actionUrl(),
            notification.createdAt() != null ? notification.createdAt() : Instant.now(),
            Instant.now().plusSeconds(86400)
        );
    }

    private AlertNotifier.AlertLevel mapPriorityToAlertLevel(NotificationPriority priority) {
        return switch (priority) {
            case CRITICAL -> AlertNotifier.AlertLevel.CRITICAL;
            case HIGH -> AlertNotifier.AlertLevel.ERROR;
            case MEDIUM -> AlertNotifier.AlertLevel.WARNING;
            case LOW -> AlertNotifier.AlertLevel.INFO;
        };
    }

    private AlertNotifier.AlertType mapTypeToAlertType(NotificationType type) {
        return switch (type) {
            case ALERT -> AlertNotifier.AlertType.WARNING;
            case REMINDER -> AlertNotifier.AlertType.REMINDER;
            case SUGGESTION -> AlertNotifier.AlertType.NOTIFICATION;
            case REPORT -> AlertNotifier.AlertType.REPORT;
            case SYSTEM -> AlertNotifier.AlertType.NOTIFICATION;
        };
    }

    private NotificationType mapEventTypeToType(String eventType) {
        if (eventType.contains("RISK") || eventType.contains("ALERT")) return NotificationType.ALERT;
        if (eventType.contains("REMINDER")) return NotificationType.REMINDER;
        if (eventType.contains("REPORT")) return NotificationType.REPORT;
        return NotificationType.SYSTEM;
    }

    private String renderTemplate(String templateId, Map<String, Object> data, String defaultContent) {
        if (templateId == null || data == null) return defaultContent;
        String content = defaultContent;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            content = content.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
        }
        return content;
    }

    public void shutdown() {
        asyncExecutor.shutdown();
    }

    // --- 内部类型 ---

    public enum NotificationPriority {
        CRITICAL, HIGH, MEDIUM, LOW
    }

    public enum NotificationType {
        ALERT, REMINDER, SUGGESTION, REPORT, SYSTEM
    }

    public static final class Notification {
        private final String notificationId;
        private final String userId;
        private final String department;
        private final String title;
        private final String content;
        private final NotificationType type;
        private final NotificationPriority priority;
        private final List<String> channels;
        private final String actionUrl;
        private final Map<String, Object> data;
        private final Instant createdAt;

        private Notification(String notificationId, String userId, String department, String title,
                             String content, NotificationType type, NotificationPriority priority,
                             List<String> channels, String actionUrl, Map<String, Object> data,
                             Instant createdAt) {
            this.notificationId = notificationId;
            this.userId = userId;
            this.department = department;
            this.title = title;
            this.content = content;
            this.type = type;
            this.priority = priority;
            this.channels = channels;
            this.actionUrl = actionUrl;
            this.data = data;
            this.createdAt = createdAt;
        }

        public static Builder builder() { return new Builder(); }

        public String notificationId() { return notificationId; }
        public String userId() { return userId; }
        public String department() { return department; }
        public String title() { return title; }
        public String content() { return content; }
        public NotificationType type() { return type; }
        public NotificationPriority priority() { return priority; }
        public List<String> channels() { return channels; }
        public String actionUrl() { return actionUrl; }
        public Map<String, Object> data() { return data; }
        public Instant createdAt() { return createdAt; }

        public Notification withNotificationId(String id) {
            return new Notification(id, userId, department, title, content, type, priority, channels, actionUrl, data, createdAt);
        }
        public Notification withCreatedAt(Instant at) {
            return new Notification(notificationId, userId, department, title, content, type, priority, channels, actionUrl, data, at);
        }

        public static class Builder {
            private String notificationId;
            private String userId;
            private String department;
            private String title;
            private String content;
            private NotificationType type = NotificationType.SYSTEM;
            private NotificationPriority priority = NotificationPriority.MEDIUM;
            private List<String> channels;
            private String actionUrl;
            private Map<String, Object> data;
            private Instant createdAt;

            public Builder notificationId(String v) { notificationId = v; return this; }
            public Builder userId(String v) { userId = v; return this; }
            public Builder department(String v) { department = v; return this; }
            public Builder title(String v) { title = v; return this; }
            public Builder content(String v) { content = v; return this; }
            public Builder type(NotificationType v) { type = v; return this; }
            public Builder priority(NotificationPriority v) { priority = v; return this; }
            public Builder channels(List<String> v) { channels = v; return this; }
            public Builder actionUrl(String v) { actionUrl = v; return this; }
            public Builder data(Map<String, Object> v) { data = v; return this; }
            public Builder createdAt(Instant v) { createdAt = v; return this; }
            public Notification build() {
                return new Notification(notificationId, userId, department, title, content, type, priority, channels, actionUrl, data, createdAt);
            }
        }
    }

    public record NotificationResult(
        String notificationId, boolean success, List<ChannelResult> channelResults
    ) {}

    public record ChannelResult(
        String channel, boolean success, String message
    ) {}

    public record NotificationSubscription(
        String eventType, List<String> channels, NotificationPriority priority,
        String templateId, boolean requiresAck
    ) {}
}
