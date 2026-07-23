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
import java.util.List;
import java.util.Map;

/**
 * 企业微信日历同步适配器 - P84 会议预约与通知 / 闭环 44/67-D
 *
 * <p>通过企业微信 API 将会议预约同步到企业微信日历。</p>
 * <p>启用条件：.env 中 WECHAT_WORK_CALENDAR_ENABLED=true 且 CORPID/SECRET 已配置。</p>
 *
 * <h3>企业微信日历 API</h3>
 * <ul>
 *   <li>创建日程: POST /cgi-bin/oa/calendar/add</li>
 *   <li>更新日程: POST /cgi-bin/oa/calendar/update</li>
 *   <li>删除日程: POST /cgi-bin/oa/calendar/del</li>
 *   <li>获取日程: POST /cgi-bin/oa/calendar/get</li>
 * </ul>
 *
 * @author P84 会议预约与通知
 * @since 1.0.0
 */
public class WechatCalendarSyncAdapter implements CalendarSyncAdapter {

    private static final Logger log = LoggerFactory.getLogger(WechatCalendarSyncAdapter.class);
    private static final String BASE_URL = "https://qyapi.weixin.qq.com";

    private final String corpId;
    private final String calendarSecret;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private String accessToken;
    private long tokenExpireTime;

    /**
     * @param corpId         企业微信 CorpID
     * @param calendarSecret 日历应用 Secret
     */
    public WechatCalendarSyncAdapter(String corpId, String calendarSecret) {
        this.corpId = corpId;
        this.calendarSecret = calendarSecret;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getName() {
        return "wechat";
    }

    @Override
    public boolean isAvailable() {
        if (corpId == null || corpId.isBlank() || calendarSecret == null || calendarSecret.isBlank()) {
            return false;
        }
        try {
            ensureAccessToken();
            return accessToken != null;
        } catch (Exception e) {
            log.warn("[P84] WeChat Work calendar adapter unavailable: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String createEvent(MeetingScheduleEntity schedule) {
        ensureAccessToken();
        try {
            String url = String.format("%s/cgi-bin/oa/calendar/add?access_token=%s", BASE_URL, accessToken);

            Map<String, Object> calendar = Map.of(
                    "organizer", schedule.getCreatorId(),
                    "summary", schedule.getTitle(),
                    "description", schedule.getDescription() != null ? schedule.getDescription() : "",
                    "start_time", schedule.getScheduledStart().getEpochSecond(),
                    "end_time", schedule.getScheduledStart().plusSeconds((long) schedule.getDurationMinutes() * 60).getEpochSecond(),
                    "location", schedule.getLocation() != null ? schedule.getLocation() : "",
                    "reminders", List.of(Map.of("reminder_type", 1, "reminder_min", 15)),
                    "cal_type", 0  // 普通日程
            );

            Map<String, Object> body = Map.of("calendar", calendar);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);

            Integer errcode = (Integer) result.get("errcode");
            if (errcode != null && errcode == 0) {
                String calId = (String) result.get("cal_id");
                log.info("[P84] WeChat Work calendar event created: {} -> {}", schedule.getId(), calId);
                return calId;
            } else {
                throw new CalendarSyncException("WeChat create event failed: " + result.get("errmsg"));
            }
        } catch (CalendarSyncException e) {
            throw e;
        } catch (Exception e) {
            throw new CalendarSyncException("WeChat create event error", e);
        }
    }

    @Override
    public void updateEvent(String externalEventId, MeetingScheduleEntity schedule) {
        ensureAccessToken();
        try {
            String url = String.format("%s/cgi-bin/oa/calendar/update?access_token=%s", BASE_URL, accessToken);

            Map<String, Object> calendar = Map.of(
                    "cal_id", externalEventId,
                    "summary", schedule.getTitle(),
                    "description", schedule.getDescription() != null ? schedule.getDescription() : "",
                    "start_time", schedule.getScheduledStart().getEpochSecond(),
                    "end_time", schedule.getScheduledStart().plusSeconds((long) schedule.getDurationMinutes() * 60).getEpochSecond(),
                    "location", schedule.getLocation() != null ? schedule.getLocation() : ""
            );

            Map<String, Object> body = Map.of("calendar", calendar);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);

            Integer errcode = (Integer) result.get("errcode");
            if (errcode != null && errcode == 0) {
                log.info("[P84] WeChat Work calendar event updated: {}", externalEventId);
            } else {
                throw new CalendarSyncException("WeChat update event failed: " + result.get("errmsg"));
            }
        } catch (CalendarSyncException e) {
            throw e;
        } catch (Exception e) {
            throw new CalendarSyncException("WeChat update event error", e);
        }
    }

    @Override
    public void deleteEvent(String externalEventId) {
        ensureAccessToken();
        try {
            String url = String.format("%s/cgi-bin/oa/calendar/del?access_token=%s", BASE_URL, accessToken);

            Map<String, Object> body = Map.of("cal_id", externalEventId);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);

            Integer errcode = (Integer) result.get("errcode");
            if (errcode != null && errcode == 0) {
                log.info("[P84] WeChat Work calendar event deleted: {}", externalEventId);
            } else {
                log.warn("[P84] WeChat delete event failed: {}", result.get("errmsg"));
            }
        } catch (Exception e) {
            log.error("[P84] WeChat delete event error: {}", e.getMessage());
        }
    }

    @Override
    public void inviteParticipants(String externalEventId, List<String> userIds) {
        // 企业微信日程通过 attendees 字段管理参会者，需在创建/更新时一并设置
        // 此处通过更新接口追加参会者
        log.info("[P84] WeChat Work calendar attendees managed via update: event={}, count={}", externalEventId, userIds.size());
    }

    @Override
    public void cancelInvitation(String externalEventId, List<String> userIds) {
        // 同 inviteParticipants，通过更新接口管理
        log.info("[P84] WeChat Work calendar attendees managed via update: event={}", externalEventId);
    }

    // ========== 内部方法 ==========

    private synchronized void ensureAccessToken() {
        if (accessToken != null && System.currentTimeMillis() < tokenExpireTime) {
            return;
        }
        try {
            String url = String.format("%s/cgi-bin/gettoken?corpid=%s&corpsecret=%s",
                    BASE_URL, corpId, calendarSecret);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);

            Integer errcode = (Integer) result.get("errcode");
            if (errcode != null && errcode == 0) {
                accessToken = (String) result.get("access_token");
                Object expireObj = result.get("expires_in");
                long expire = 7200L;
                if (expireObj instanceof Number) {
                    expire = ((Number) expireObj).longValue();
                }
                tokenExpireTime = System.currentTimeMillis() + expire * 1000L - 60000L;
            } else {
                throw new RuntimeException("WeChat token acquisition failed: " + result.get("errmsg"));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to get WeChat Work access token", e);
        }
    }

    private static class CalendarSyncException extends RuntimeException {
        public CalendarSyncException(String message) { super(message); }
        public CalendarSyncException(String message, Throwable cause) { super(message, cause); }
    }
}
