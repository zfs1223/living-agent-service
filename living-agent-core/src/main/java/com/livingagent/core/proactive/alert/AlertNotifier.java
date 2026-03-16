package com.livingagent.core.proactive.alert;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface AlertNotifier {

    String getChannelName();
    
    boolean send(Alert alert);
    
    default boolean sendBatch(List<Alert> alerts) {
        if (alerts == null || alerts.isEmpty()) {
            return true;
        }
        boolean allSuccess = true;
        for (Alert alert : alerts) {
            if (!send(alert)) {
                allSuccess = false;
            }
        }
        return allSuccess;
    }
    
    boolean isAvailable();

    record Alert(
            String alertId,
            String title,
            String content,
            AlertLevel level,
            AlertType type,
            List<String> targetUsers,
            Map<String, Object> data,
            String actionUrl,
            Instant createdAt,
            Instant expiresAt
    ) {
        public static Alert of(String title, String content, AlertLevel level) {
            return new Alert(
                    "alert_" + System.currentTimeMillis(),
                    title,
                    content,
                    level,
                    AlertType.NOTIFICATION,
                    List.of(),
                    Map.of(),
                    null,
                    Instant.now(),
                    Instant.now().plusSeconds(86400)
            );
        }
        
        public static Alert warning(String title, String content) {
            return of(title, content, AlertLevel.WARNING);
        }
        
        public static Alert error(String title, String content) {
            return of(title, content, AlertLevel.ERROR);
        }
        
        public static Alert info(String title, String content) {
            return of(title, content, AlertLevel.INFO);
        }
        
        public static Alert critical(String title, String content) {
            return of(title, content, AlertLevel.CRITICAL);
        }
        
        public Alert withTargetUsers(List<String> users) {
            return new Alert(alertId, title, content, level, type, users, data, actionUrl, createdAt, expiresAt);
        }
        
        public Alert withActionUrl(String url) {
            return new Alert(alertId, title, content, level, type, targetUsers, data, url, createdAt, expiresAt);
        }
        
        public Alert withData(Map<String, Object> data) {
            return new Alert(alertId, title, content, level, type, targetUsers, data, actionUrl, createdAt, expiresAt);
        }
    }

    enum AlertLevel {
        DEBUG(0),
        INFO(1),
        WARNING(2),
        ERROR(3),
        CRITICAL(4);

        private final int severity;

        AlertLevel(int severity) {
            this.severity = severity;
        }

        public int getSeverity() {
            return severity;
        }
        
        public boolean isAtLeast(AlertLevel other) {
            return this.severity >= other.severity;
        }
    }

    enum AlertType {
        NOTIFICATION,
        REMINDER,
        WARNING,
        ERROR,
        TASK,
        REPORT
    }
}
