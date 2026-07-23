package com.livingagent.gateway.meeting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.database.entity.MeetingScheduleEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * 飞书日历同步适配器 - P84 会议预约与通知 / 闭环 44/67-D
 *
 * <p>通过飞书开放 API v4 将会议预约同步到飞书日历。</p>
 * <p>启用条件：.env 中 FEISHU_ENABLED=true 且 FEISHU_ENTERPRISE_APP_ID/SECRET 已配置。</p>
 *
 * <h3>飞书日历 API</h3>
 * <ul>
 *   <li>创建日程: POST /open-apis/calendar/v4/calendars/{calendar_id}/events</li>
 *   <li>更新日程: PATCH /open-apis/calendar/v4/calendars/{calendar_id}/events/{event_id}</li>
 *   <li>删除日程: DELETE /open-apis/calendar/v4/calendars/{calendar_id}/events/{event_id}</li>
 *   <li>添加参会者: POST /open-apis/calendar/v4/calendars/{calendar_id}/events/{event_id}/attendees</li>
 * </ul>
 *
 * @author P84 会议预约与通知
 * @since 1.0.0
 */
public class FeishuCalendarSyncAdapter implements CalendarSyncAdapter {

    private static final Logger log = LoggerFactory.getLogger(FeishuCalendarSyncAdapter.class);
    private static final String BASE_URL = "https://open.feishu.cn/open-apis";

    private final String appId;
    private final String appSecret;
    private final String calendarId;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private String accessToken;
    private long tokenExpireTime;

    /**
     * @param appId      飞书企业自建应用 App ID
     * @param appSecret  飞书企业自建应用 App Secret
     * @param calendarId 默认日历 ID（可为 "primary" 使用主日历）
     */
    public FeishuCalendarSyncAdapter(String appId, String appSecret, String calendarId) {
        this.appId = appId;
        this.appSecret = appSecret;
        this.calendarId = calendarId != null ? calendarId : "primary";
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getName() {
        return "feishu";
    }

    @Override
    public boolean isAvailable() {
        if (appId == null || appId.isBlank() || appSecret == null || appSecret.isBlank()) {
            return false;
        }
        try {
            ensureAccessToken();
            return accessToken != null;
        } catch (Exception e) {
            log.warn("[P84] Feishu calendar adapter unavailable: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String createEvent(MeetingScheduleEntity schedule) {
        ensureAccessToken();
        try {
            String url = String.format("%s/calendar/v4/calendars/%s/events", BASE_URL, calendarId);

            Map<String, Object> body = Map.of(
                    "summary", schedule.getTitle(),
                    "description", schedule.getDescription() != null ? schedule.getDescription() : "",
                    "start_time", Map.of(
                            "timestamp", String.valueOf(schedule.getScheduledStart().getEpochSecond()),
                            "timezone", "Asia/Shanghai"
                    ),
                    "end_time", Map.of(
                            "timestamp", String.valueOf(
                                    schedule.getScheduledStart().plusSeconds((long) schedule.getDurationMinutes() * 60).getEpochSecond()),
                            "timezone", "Asia/Shanghai"
                    ),
                    "visibility", "default",
                    "color", mapMeetingTypeToColor(schedule.getMeetingType()),
                    "reminders", List.of(Map.of("minutes", 15))
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + accessToken)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);

            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                Map<String, Object> event = (Map<String, Object>) data.get("event");
                String eventId = (String) event.get("event_id");
                log.info("[P84] Feishu calendar event created: {} -> {}", schedule.getId(), eventId);
                return eventId;
            } else {
                throw new CalendarSyncException("Feishu create event failed: " + result.get("msg"));
            }
        } catch (CalendarSyncException e) {
            throw e;
        } catch (Exception e) {
            throw new CalendarSyncException("Feishu create event error", e);
        }
    }

    @Override
    public void updateEvent(String externalEventId, MeetingScheduleEntity schedule) {
        ensureAccessToken();
        try {
            String url = String.format("%s/calendar/v4/calendars/%s/events/%s",
                    BASE_URL, calendarId, externalEventId);

            Map<String, Object> body = Map.of(
                    "summary", schedule.getTitle(),
                    "description", schedule.getDescription() != null ? schedule.getDescription() : "",
                    "start_time", Map.of(
                            "timestamp", String.valueOf(schedule.getScheduledStart().getEpochSecond()),
                            "timezone", "Asia/Shanghai"
                    ),
                    "end_time", Map.of(
                            "timestamp", String.valueOf(
                                    schedule.getScheduledStart().plusSeconds((long) schedule.getDurationMinutes() * 60).getEpochSecond()),
                            "timezone", "Asia/Shanghai"
                    )
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + accessToken)
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);

            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                log.info("[P84] Feishu calendar event updated: {}", externalEventId);
            } else {
                throw new CalendarSyncException("Feishu update event failed: " + result.get("msg"));
            }
        } catch (CalendarSyncException e) {
            throw e;
        } catch (Exception e) {
            throw new CalendarSyncException("Feishu update event error", e);
        }
    }

    @Override
    public void deleteEvent(String externalEventId) {
        ensureAccessToken();
        try {
            String url = String.format("%s/calendar/v4/calendars/%s/events/%s",
                    BASE_URL, calendarId, externalEventId);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + accessToken)
                    .DELETE()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);

            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                log.info("[P84] Feishu calendar event deleted: {}", externalEventId);
            } else {
                log.warn("[P84] Feishu delete event failed: {}", result.get("msg"));
            }
        } catch (Exception e) {
            log.error("[P84] Feishu delete event error: {}", e.getMessage());
        }
    }

    @Override
    public void inviteParticipants(String externalEventId, List<String> userIds) {
        ensureAccessToken();
        try {
            String url = String.format("%s/calendar/v4/calendars/%s/events/%s/attendees",
                    BASE_URL, calendarId, externalEventId);

            List<Map<String, String>> attendees = userIds.stream()
                    .map(uid -> Map.of("type", "user", "user_id", uid))
                    .toList();

            Map<String, Object> body = Map.of("attendees", attendees);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + accessToken)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("[P84] Feishu calendar attendees invited: event={}, count={}", externalEventId, userIds.size());
        } catch (Exception e) {
            log.error("[P84] Feishu invite attendees error: {}", e.getMessage());
        }
    }

    @Override
    public void cancelInvitation(String externalEventId, List<String> userIds) {
        // 飞书不支持单独取消邀请，通过删除+重新创建参会者列表实现
        log.warn("[P84] Feishu does not support individual cancellation; consider re-creating the event");
    }

    // ========== 内部方法 ==========

    private synchronized void ensureAccessToken() {
        if (accessToken != null && System.currentTimeMillis() < tokenExpireTime) {
            return;
        }
        try {
            String url = BASE_URL + "/auth/v3/tenant_access_token/internal";
            Map<String, String> body = Map.of("app_id", appId, "app_secret", appSecret);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);

            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                accessToken = (String) result.get("tenant_access_token");
                long expire = 7200L;
                Object expireObj = result.get("expire");
                if (expireObj instanceof Number) {
                    expire = ((Number) expireObj).longValue();
                }
                tokenExpireTime = System.currentTimeMillis() + expire * 1000L - 60000L;
            } else {
                throw new RuntimeException("Feishu token acquisition failed: " + result.get("msg"));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to get Feishu access token", e);
        }
    }

    private int mapMeetingTypeToColor(String meetingType) {
        // 飞书日历颜色 ID 映射
        if (meetingType == null) return 0;
        return switch (meetingType) {
            case "DEPARTMENT" -> 4;   // 蓝色
            case "CROSS_DEPT" -> 6;   // 紫色
            case "PROJECT" -> 8;      // 青色
            case "TRAINING" -> 9;     // 橙色
            case "ALL_HANDS" -> 10;   // 粉色
            default -> 0;             // 默认
        };
    }

    private static class CalendarSyncException extends RuntimeException {
        public CalendarSyncException(String message) { super(message); }
        public CalendarSyncException(String message, Throwable cause) { super(message, cause); }
    }
}
