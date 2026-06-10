package com.livingagent.core.tool.impl.enterprise;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

import com.livingagent.core.tool.*;

/**
 * 飞书日历与任务工具 — 日历、日程、任务管理相关 action。
 * <p>
 * Actions: get_calendar_list, create_event, get_event_list,
 *          create_task, get_task_list, update_task
 */
public class FeishuCalendarTool extends AbstractFeishuTool {

    private static final String NAME = "feishu_calendar";
    private static final String DESCRIPTION = "飞书日历与任务管理工具，支持日历查询、日程创建、任务管理";
    private static final String VERSION = "1.0.0";
    private static final String DEPARTMENT = "enterprise_management";

    private final ToolSchema schema;

    public FeishuCalendarTool(String appId, String appSecret) {
        super(appId, appSecret, NAME);
        this.schema = ToolSchema.builder()
            .name(NAME)
            .description(DESCRIPTION)
            .parameter("action", "string",
                "操作类型: get_calendar_list(获取日历列表), create_event(创建日程), get_event_list(获取日程列表), " +
                "create_task(创建任务), get_task_list(获取任务列表), update_task(更新任务)",
                true)
            .parameter("calendar_id", "string", "日历ID", false)
            .parameter("summary", "string", "日程/任务摘要", false)
            .parameter("description", "string", "描述", false)
            .parameter("start_time", "string", "开始时间(时间戳)", false)
            .parameter("end_time", "string", "结束时间(时间戳)", false)
            .parameter("due_time", "string", "截止时间(时间戳)", false)
            .parameter("attendee_ids", "string", "参与者用户ID列表(逗号分隔)", false)
            .parameter("assignee_ids", "string", "任务负责人用户ID列表(逗号分隔)", false)
            .parameter("task_id", "string", "任务ID", false)
            .parameter("status", "string", "状态", false)
            .parameter("user_id", "string", "用户ID", false)
            .parameter("page_size", "integer", "分页大小", false)
            .build();
    }

    @Override
    public String getName() { return NAME; }

    @Override
    public String getDescription() { return DESCRIPTION; }

    @Override
    public String getVersion() { return VERSION; }

    @Override
    public String getDepartment() { return DEPARTMENT; }

    @Override
    public ToolSchema getSchema() { return schema; }

    @Override
    public List<String> getCapabilities() {
        return List.of(
            "calendar_management",
            "task_management",
            "full_permission"
        );
    }

    @Override
    protected ToolResult doExecute(String action, ToolParams params) throws Exception {
        return switch (action) {
            case "get_calendar_list" -> getCalendarList(params);
            case "create_event" -> createEvent(params);
            case "get_event_list" -> getEventList(params);
            case "create_task" -> createTask(params);
            case "get_task_list" -> getTaskList(params);
            case "update_task" -> updateTask(params);
            default -> ToolResult.failure("不支持的操作: " + action);
        };
    }

    private ToolResult getCalendarList(ToolParams params) throws Exception {
        String url = "https://open.feishu.cn/open-apis/calendar/v4/calendars";

        var response = sendRequest(buildGetRequest(url));

        if (response.statusCode() == 200) {
            Map<String, Object> result = parseResponse(response);
            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                List<Map<String, Object>> calendars = (List<Map<String, Object>>) data.get("calendars");

                List<Map<String, Object>> calendarList = new ArrayList<>();
                if (calendars != null) {
                    for (Map<String, Object> cal : calendars) {
                        Map<String, Object> calendar = new HashMap<>();
                        calendar.put("calendarId", cal.get("calendar_id"));
                        calendar.put("summary", cal.get("summary"));
                        calendar.put("description", cal.get("description"));
                        calendar.put("permissions", cal.get("permissions"));
                        calendarList.add(calendar);
                    }
                }

                return ToolResult.success(Map.of("calendars", calendarList));
            } else {
                return ToolResult.failure("获取日历列表失败: " + result.get("msg"));
            }
        } else {
            return ToolResult.failure("获取日历列表失败: HTTP " + response.statusCode());
        }
    }

    private ToolResult createEvent(ToolParams params) throws Exception {
        String calendarId = params.getString("calendar_id");
        String summary = params.getString("summary");
        String startTime = params.getString("start_time");
        String endTime = params.getString("end_time");
        String description = params.getString("description");
        String attendeeIds = params.getString("attendee_ids");

        if (summary == null || summary.isEmpty()) {
            return ToolResult.failure("缺少必要参数: summary");
        }

        if (calendarId == null || calendarId.isEmpty()) {
            calendarId = "primary";
        }

        String url = "https://open.feishu.cn/open-apis/calendar/v4/calendars/" + calendarId + "/events";

        Map<String, Object> body = new HashMap<>();
        body.put("summary", summary);

        if (startTime != null && !startTime.isEmpty()) {
            Map<String, Object> start = new HashMap<>();
            start.put("timestamp", Long.parseLong(startTime));
            body.put("start_time", start);
        }

        if (endTime != null && !endTime.isEmpty()) {
            Map<String, Object> end = new HashMap<>();
            end.put("timestamp", Long.parseLong(endTime));
            body.put("end_time", end);
        }

        if (description != null && !description.isEmpty()) {
            body.put("description", description);
        }

        if (attendeeIds != null && !attendeeIds.isEmpty()) {
            List<Map<String, String>> attendees = new ArrayList<>();
            for (String id : attendeeIds.split(",")) {
                Map<String, String> attendee = new HashMap<>();
                attendee.put("user_id", id.trim());
                attendees.add(attendee);
            }
            body.put("attendees", attendees);
        }

        var response = sendRequest(buildPostRequest(url, body));

        if (response.statusCode() == 200) {
            Map<String, Object> result = parseResponse(response);
            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                Map<String, Object> event = (Map<String, Object>) data.get("event");
                return ToolResult.success(Map.of(
                    "eventId", event != null ? event.get("event_id") : null,
                    "summary", summary,
                    "message", "日程创建成功"
                ));
            } else {
                return ToolResult.failure("创建日程失败: " + result.get("msg"));
            }
        } else {
            return ToolResult.failure("创建日程失败: HTTP " + response.statusCode());
        }
    }

    private ToolResult getEventList(ToolParams params) throws Exception {
        String calendarId = params.getString("calendar_id");
        String startTime = params.getString("start_time");
        String endTime = params.getString("end_time");

        if (calendarId == null || calendarId.isEmpty()) {
            calendarId = "primary";
        }

        StringBuilder urlBuilder = new StringBuilder("https://open.feishu.cn/open-apis/calendar/v4/calendars/");
        urlBuilder.append(calendarId).append("/events?");

        if (startTime != null && !startTime.isEmpty()) {
            urlBuilder.append("start_time=").append(startTime).append("&");
        }
        if (endTime != null && !endTime.isEmpty()) {
            urlBuilder.append("end_time=").append(endTime).append("&");
        }

        String url = urlBuilder.toString();
        if (url.endsWith("&")) {
            url = url.substring(0, url.length() - 1);
        }

        var response = sendRequest(buildGetRequest(url));

        if (response.statusCode() == 200) {
            Map<String, Object> result = parseResponse(response);
            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                List<Map<String, Object>> events = (List<Map<String, Object>>) data.get("events");

                List<Map<String, Object>> eventList = new ArrayList<>();
                if (events != null) {
                    for (Map<String, Object> evt : events) {
                        Map<String, Object> event = new HashMap<>();
                        event.put("eventId", evt.get("event_id"));
                        event.put("summary", evt.get("summary"));
                        event.put("description", evt.get("description"));
                        event.put("startTime", evt.get("start_time"));
                        event.put("endTime", evt.get("end_time"));
                        event.put("status", evt.get("status"));
                        eventList.add(event);
                    }
                }

                return ToolResult.success(Map.of("events", eventList));
            } else {
                return ToolResult.failure("获取日程列表失败: " + result.get("msg"));
            }
        } else {
            return ToolResult.failure("获取日程列表失败: HTTP " + response.statusCode());
        }
    }

    private ToolResult createTask(ToolParams params) throws Exception {
        String summary = params.getString("summary");
        String description = params.getString("description");
        String dueTime = params.getString("due_time");
        String assigneeIds = params.getString("assignee_ids");

        if (summary == null || summary.isEmpty()) {
            return ToolResult.failure("缺少必要参数: summary");
        }

        String url = "https://open.feishu.cn/open-apis/task/v1/tasks";

        Map<String, Object> body = new HashMap<>();
        body.put("summary", summary);

        if (description != null && !description.isEmpty()) {
            body.put("description", description);
        }

        if (dueTime != null && !dueTime.isEmpty()) {
            Map<String, Object> due = new HashMap<>();
            due.put("timestamp", Long.parseLong(dueTime));
            body.put("due", due);
        }

        if (assigneeIds != null && !assigneeIds.isEmpty()) {
            List<Map<String, String>> assignees = new ArrayList<>();
            for (String id : assigneeIds.split(",")) {
                Map<String, String> assignee = new HashMap<>();
                assignee.put("user_id", id.trim());
                assignees.add(assignee);
            }
            body.put("assignees", assignees);
        }

        var response = sendRequest(buildPostRequest(url, body));

        if (response.statusCode() == 200) {
            Map<String, Object> result = parseResponse(response);
            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                Map<String, Object> task = (Map<String, Object>) data.get("task");
                return ToolResult.success(Map.of(
                    "taskId", task != null ? task.get("task_id") : null,
                    "summary", summary,
                    "message", "任务创建成功"
                ));
            } else {
                return ToolResult.failure("创建任务失败: " + result.get("msg"));
            }
        } else {
            return ToolResult.failure("创建任务失败: HTTP " + response.statusCode());
        }
    }

    private ToolResult getTaskList(ToolParams params) throws Exception {
        String userId = params.getString("user_id");
        Integer pageSizeInt = params.getInteger("page_size");
        int pageSize = pageSizeInt != null ? pageSizeInt : 50;

        StringBuilder urlBuilder = new StringBuilder("https://open.feishu.cn/open-apis/task/v1/tasks?page_size=");
        urlBuilder.append(pageSize);

        if (userId != null && !userId.isEmpty()) {
            urlBuilder.append("&user_id=").append(userId);
        }

        String url = urlBuilder.toString();

        var response = sendRequest(buildGetRequest(url));

        if (response.statusCode() == 200) {
            Map<String, Object> result = parseResponse(response);
            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");

                List<Map<String, Object>> taskList = new ArrayList<>();
                if (items != null) {
                    for (Map<String, Object> item : items) {
                        Map<String, Object> task = new HashMap<>();
                        task.put("taskId", item.get("task_id"));
                        task.put("summary", item.get("summary"));
                        task.put("description", item.get("description"));
                        task.put("status", item.get("status"));
                        task.put("due", item.get("due"));
                        taskList.add(task);
                    }
                }

                return ToolResult.success(Map.of("tasks", taskList));
            } else {
                return ToolResult.failure("获取任务列表失败: " + result.get("msg"));
            }
        } else {
            return ToolResult.failure("获取任务列表失败: HTTP " + response.statusCode());
        }
    }

    private ToolResult updateTask(ToolParams params) throws Exception {
        String taskId = params.getString("task_id");
        String summary = params.getString("summary");
        String status = params.getString("status");

        if (taskId == null || taskId.isEmpty()) {
            return ToolResult.failure("缺少必要参数: task_id");
        }

        String url = "https://open.feishu.cn/open-apis/task/v1/tasks/" + taskId;

        Map<String, Object> body = new HashMap<>();
        if (summary != null && !summary.isEmpty()) {
            body.put("summary", summary);
        }
        if (status != null && !status.isEmpty()) {
            body.put("status", status);
        }

        var response = sendRequest(buildPutRequest(url, body));
        return handleResponseWithMessage(response, "任务更新成功", "更新任务失败");
    }
}
