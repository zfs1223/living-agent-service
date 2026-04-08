package com.livingagent.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

@Service
public class DepartmentNotificationService {

    private static final Logger log = LoggerFactory.getLogger(DepartmentNotificationService.class);

    private final ObjectMapper objectMapper;
    private final DepartmentChatService departmentChatService;

    private final Map<String, Deque<Notification>> notificationQueue = new ConcurrentHashMap<>();
    private final Map<String, Set<NotificationListener>> listeners = new ConcurrentHashMap<>();
    
    private static final int MAX_NOTIFICATIONS_PER_DEPT = 50;

    public DepartmentNotificationService(DepartmentChatService departmentChatService) {
        this.departmentChatService = departmentChatService;
        this.objectMapper = new ObjectMapper();
    }

    public Notification sendNotification(String department, String type, String title, String content, 
                                         String priority, Map<String, Object> metadata) {
        Notification notification = new Notification(
            UUID.randomUUID().toString(),
            department,
            type,
            title,
            content,
            priority != null ? priority : "NORMAL",
            metadata != null ? metadata : new HashMap<>(),
            Instant.now(),
            false
        );

        notificationQueue.computeIfAbsent(department, k -> new ConcurrentLinkedDeque<>())
            .addLast(notification);

        Deque<Notification> queue = notificationQueue.get(department);
        while (queue.size() > MAX_NOTIFICATIONS_PER_DEPT) {
            queue.removeFirst();
        }

        notifyListeners(department, notification);

        log.info("Sent notification: dept={}, type={}, title={}", department, type, title);

        return notification;
    }

    public Notification sendUrgentNotification(String department, String title, String content) {
        return sendNotification(department, "URGENT", title, content, "URGENT", Map.of());
    }

    public Notification sendSystemNotification(String department, String title, String content) {
        return sendNotification(department, "SYSTEM", title, content, "NORMAL", Map.of());
    }

    public Notification sendTaskNotification(String department, String title, String content, 
                                             String taskId, String taskType) {
        return sendNotification(department, "TASK", title, content, "HIGH", 
            Map.of("taskId", taskId, "taskType", taskType));
    }

    public Notification sendMeetingNotification(String department, String title, String content,
                                                Instant meetingTime, String meetingUrl) {
        return sendNotification(department, "MEETING", title, content, "HIGH",
            Map.of("meetingTime", meetingTime.toString(), "meetingUrl", meetingUrl));
    }

    public Notification sendAnnouncementNotification(String department, String title, String content) {
        return sendNotification(department, "ANNOUNCEMENT", title, content, "NORMAL", Map.of());
    }

    public Notification broadcastToAllDepartments(String type, String title, String content, 
                                                   String priority) {
        Notification notification = new Notification(
            UUID.randomUUID().toString(),
            "ALL",
            type,
            title,
            content,
            priority != null ? priority : "NORMAL",
            Map.of(),
            Instant.now(),
            false
        );

        for (String dept : getAllDepartments()) {
            notificationQueue.computeIfAbsent(dept, k -> new ConcurrentLinkedDeque<>())
                .addLast(notification);
            notifyListeners(dept, notification);
        }

        log.info("Broadcast notification to all departments: type={}, title={}", type, title);
        return notification;
    }

    public List<Notification> getNotifications(String department, int limit, boolean unreadOnly) {
        Deque<Notification> queue = notificationQueue.get(department);
        if (queue == null) {
            return List.of();
        }

        return queue.stream()
            .filter(n -> !unreadOnly || !n.read())
            .skip(Math.max(0, queue.size() - limit))
            .collect(Collectors.toList());
    }

    public List<Notification> getUnreadNotifications(String department) {
        return getNotifications(department, MAX_NOTIFICATIONS_PER_DEPT, true);
    }

    public void markAsRead(String department, String notificationId) {
        Deque<Notification> queue = notificationQueue.get(department);
        if (queue == null) return;

        for (Notification n : queue) {
            if (n.notificationId().equals(notificationId)) {
                Notification read = new Notification(
                    n.notificationId(), n.department(), n.type(), n.title(),
                    n.content(), n.priority(), n.metadata(), n.timestamp(), true
                );
                queue.remove(n);
                queue.add(read);
                break;
            }
        }
    }

    public void markAllAsRead(String department) {
        Deque<Notification> queue = notificationQueue.get(department);
        if (queue == null) return;

        Deque<Notification> updated = new ConcurrentLinkedDeque<>();
        for (Notification n : queue) {
            updated.add(new Notification(
                n.notificationId(), n.department(), n.type(), n.title(),
                n.content(), n.priority(), n.metadata(), n.timestamp(), true
            ));
        }
        notificationQueue.put(department, updated);
    }

    public int getUnreadCount(String department) {
        Deque<Notification> queue = notificationQueue.get(department);
        if (queue == null) return 0;
        
        return (int) queue.stream().filter(n -> !n.read()).count();
    }

    public void clearNotifications(String department) {
        notificationQueue.remove(department);
        log.info("Cleared notifications for department: {}", department);
    }

    public void addListener(String department, NotificationListener listener) {
        listeners.computeIfAbsent(department, k -> ConcurrentHashMap.newKeySet())
            .add(listener);
    }

    public void removeListener(String department, NotificationListener listener) {
        Set<NotificationListener> deptListeners = listeners.get(department);
        if (deptListeners != null) {
            deptListeners.remove(listener);
        }
    }

    private void notifyListeners(String department, Notification notification) {
        Set<NotificationListener> deptListeners = listeners.get(department);
        if (deptListeners != null) {
            for (NotificationListener listener : deptListeners) {
                try {
                    listener.onNotification(notification);
                } catch (Exception e) {
                    log.warn("Error notifying listener: {}", e.getMessage());
                }
            }
        }
    }

    private Set<String> getAllDepartments() {
        return Set.of("tech", "hr", "finance", "sales", "admin", "cs", "legal", "ops");
    }

    public NotificationSummary getSummary(String department) {
        Deque<Notification> queue = notificationQueue.get(department);
        int total = queue != null ? queue.size() : 0;
        int unread = getUnreadCount(department);
        
        Map<String, Long> byType = new HashMap<>();
        if (queue != null) {
            byType = queue.stream()
                .collect(Collectors.groupingBy(Notification::type, Collectors.counting()));
        }

        return new NotificationSummary(department, total, unread, byType);
    }

    public interface NotificationListener {
        void onNotification(Notification notification);
    }

    public record Notification(
        String notificationId,
        String department,
        String type,
        String title,
        String content,
        String priority,
        Map<String, Object> metadata,
        Instant timestamp,
        boolean read
    ) {
        public boolean isUrgent() {
            return "URGENT".equals(priority);
        }
    }

    public record NotificationSummary(
        String department,
        int totalNotifications,
        int unreadCount,
        Map<String, Long> countByType
    ) {}
}
