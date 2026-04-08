package com.livingagent.core.tool.impl.enterprise;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.security.SecurityPolicy;
import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.tool.*;

public class ChairmanFeishuTool implements Tool {
    
    private static final Logger log = LoggerFactory.getLogger(ChairmanFeishuTool.class);
    private static final String NAME = "chairman_feishu";
    private static final String DESCRIPTION = "董事长专属飞书管理工具，具备全部权限，可进行组织架构管理、人员管理、审批管理等所有操作";
    private static final String VERSION = "1.0.0";
    private static final String DEPARTMENT = "enterprise_management";
    
    private final String appId;
    private final String appSecret;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    private String accessToken;
    private long tokenExpireTime;
    private ToolStats stats = ToolStats.empty(NAME);
    
    private final ToolSchema schema;
    
    public ChairmanFeishuTool(String appId, String appSecret) {
        this.appId = appId;
        this.appSecret = appSecret;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        this.objectMapper = new ObjectMapper();
        
        this.schema = ToolSchema.builder()
            .name(NAME)
            .description(DESCRIPTION)
            .parameter("action", "string", 
                "操作类型: " +
                "send_message(发送消息), " +
                "get_user(获取用户), " +
                "get_department(获取部门), " +
                "create_department(创建部门), " +
                "update_department(更新部门), " +
                "delete_department(删除部门), " +
                "create_user(创建用户), " +
                "update_user(更新用户), " +
                "delete_user(删除用户), " +
                "create_approval(发起审批), " +
                "get_approval(查询审批), " +
                "cancel_approval(取消审批), " +
                "get_approval_definition_list(获取审批定义列表), " +
                "create_approval_definition(创建审批定义), " +
                "send_card(发送卡片), " +
                "upload_file(上传文件), " +
                "get_calendar_list(获取日历列表), " +
                "create_event(创建日程), " +
                "get_event_list(获取日程列表), " +
                "create_task(创建任务), " +
                "get_task_list(获取任务列表), " +
                "update_task(更新任务), " +
                "create_chat(创建群聊), " +
                "get_chat(获取群聊), " +
                "add_chat_members(添加群成员), " +
                "get_token_info(获取令牌信息)", 
                true)
            .parameter("receive_id", "string", "接收者ID (用户ID或群ID)", false)
            .parameter("receive_id_type", "string", "接收者类型: open_id, user_id, union_id, chat_id, email", false)
            .parameter("msg_type", "string", "消息类型: text, post, image, file, card", false)
            .parameter("content", "string", "消息内容", false)
            .parameter("user_id", "string", "用户ID", false)
            .parameter("user_ids", "string", "用户ID列表(逗号分隔)", false)
            .parameter("department_id", "string", "部门ID", false)
            .parameter("name", "string", "部门名称/用户名称/群聊名称/任务名称", false)
            .parameter("parent_department_id", "string", "父部门ID", false)
            .parameter("leader_user_id", "string", "部门主管用户ID", false)
            .parameter("order", "string", "部门排序", false)
            .parameter("create_group_chat", "boolean", "是否创建部门群", false)
            .parameter("department_hrbps", "string", "部门HRBP用户ID列表", false)
            .parameter("approval_code", "string", "审批定义代码", false)
            .parameter("form_data", "string", "审批表单数据(JSON)", false)
            .parameter("form_content", "string", "审批表单内容(JSON)", false)
            .parameter("instance_id", "string", "审批实例ID", false)
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
            .parameter("chat_id", "string", "群聊ID", false)
            .parameter("file_path", "string", "文件路径", false)
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
            "messaging",
            "user_management",
            "user_create",
            "user_update", 
            "user_delete",
            "department_management",
            "department_create",
            "department_update",
            "department_delete",
            "approval_management",
            "approval_create",
            "approval_cancel",
            "card_message",
            "file_upload",
            "full_permission"
        );
    }
    
    @Override
    public ToolResult execute(ToolParams params, ToolContext context) {
        long startTime = System.currentTimeMillis();
        String action = params.getString("action");
        if (action == null || action.isEmpty()) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return ToolResult.failure("缺少必要参数: action");
        }
        
        try {
            ensureAccessToken();
            
            ToolResult result = switch (action) {
                case "send_message" -> sendMessage(params);
                case "get_user" -> getUser(params);
                case "get_department" -> getDepartment(params);
                case "create_department" -> createDepartment(params);
                case "update_department" -> updateDepartment(params);
                case "delete_department" -> deleteDepartment(params);
                case "create_user" -> createUser(params);
                case "update_user" -> updateUser(params);
                case "delete_user" -> deleteUser(params);
                case "send_activation" -> sendActivationEmail(params);
                case "create_approval" -> createApproval(params);
                case "get_approval" -> getApproval(params);
                case "cancel_approval" -> cancelApproval(params);
                case "send_card" -> sendCard(params);
                case "upload_file" -> uploadFile(params);
                case "get_token_info" -> getTokenInfo();
                case "get_calendar_list" -> getCalendarList(params);
                case "create_event" -> createEvent(params);
                case "get_event_list" -> getEventList(params);
                case "create_task" -> createTask(params);
                case "get_task_list" -> getTaskList(params);
                case "update_task" -> updateTask(params);
                case "create_chat" -> createChat(params);
                case "get_chat" -> getChat(params);
                case "add_chat_members" -> addChatMembers(params);
                case "get_approval_definition_list" -> getApprovalDefinitionList(params);
                case "create_approval_definition" -> createApprovalDefinition(params);
                default -> ToolResult.failure("不支持的操作: " + action);
            };
            stats = stats.recordCall(result.success(), System.currentTimeMillis() - startTime);
            return result;
        } catch (Exception e) {
            log.error("董事长飞书操作失败: {}", e.getMessage(), e);
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return ToolResult.failure("操作失败: " + e.getMessage());
        }
    }
    
    @Override
    public void validate(ToolParams params) {
        String action = params.getString("action");
        if (action == null || action.isEmpty()) {
            throw new IllegalArgumentException("缺少必要参数: action");
        }
    }
    
    @Override
    public boolean isAllowed(SecurityPolicy policy) {
        return policy.isToolAllowed(NAME);
    }
    
    @Override
    public boolean requiresApproval() {
        return true;
    }
    
    public boolean isActionAllowed(String action, AccessLevel accessLevel) {
        if (accessLevel != AccessLevel.FULL) {
            return false;
        }
        return true;
    }
    
    @Override
    public ToolStats getStats() {
        return stats;
    }
    
    private synchronized void ensureAccessToken() throws Exception {
        if (accessToken != null && System.currentTimeMillis() < tokenExpireTime) {
            return;
        }
        
        String url = "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal";
        
        Map<String, String> body = Map.of(
            "app_id", appId,
            "app_secret", appSecret
        );
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                accessToken = (String) result.get("tenant_access_token");
                Object expireObj = result.get("expire");
                long expire = 7200L;
                if (expireObj instanceof Number) {
                    expire = ((Number) expireObj).longValue();
                }
                tokenExpireTime = System.currentTimeMillis() + expire * 1000L - 60000L;
                log.info("董事长飞书 access_token 获取成功");
            } else {
                throw new RuntimeException("获取access_token失败: " + result.get("msg"));
            }
        } else {
            throw new RuntimeException("获取access_token失败: HTTP " + response.statusCode());
        }
    }
    
    private ToolResult getTokenInfo() {
        return ToolResult.success(Map.of(
            "appId", appId.substring(0, 4) + "****",
            "hasToken", accessToken != null,
            "expiresAt", tokenExpireTime,
            "isExpired", System.currentTimeMillis() >= tokenExpireTime
        ));
    }
    
    private ToolResult sendMessage(ToolParams params) throws Exception {
        String receiveId = params.getString("receive_id");
        String receiveIdType = params.getString("receive_id_type");
        if (receiveIdType == null) receiveIdType = "open_id";
        String msgType = params.getString("msg_type");
        if (msgType == null) msgType = "text";
        String content = params.getString("content");
        
        if (receiveId == null || content == null) {
            return ToolResult.failure("缺少必要参数: receive_id 或 content");
        }
        
        String url = "https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=" + receiveIdType;
        
        Map<String, Object> body = new HashMap<>();
        body.put("receive_id", receiveId);
        body.put("msg_type", msgType);
        
        if ("text".equals(msgType)) {
            body.put("content", objectMapper.writeValueAsString(Map.of("text", content)));
        } else {
            body.put("content", content);
        }
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + accessToken)
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                return ToolResult.success(Map.of(
                    "messageId", data != null ? data.get("message_id") : null,
                    "message", "消息发送成功"
                ));
            } else {
                return ToolResult.failure("发送消息失败: " + result.get("msg"));
            }
        } else {
            return ToolResult.failure("发送消息失败: HTTP " + response.statusCode());
        }
    }
    
    private ToolResult getUser(ToolParams params) throws Exception {
        String userId = params.getString("user_id");
        if (userId == null || userId.isEmpty()) {
            return ToolResult.failure("缺少必要参数: user_id");
        }
        
        String url = "https://open.feishu.cn/open-apis/contact/v3/users/" + userId;
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + accessToken)
            .GET()
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                Map<String, Object> user = (Map<String, Object>) data.get("user");
                
                Map<String, Object> userInfo = new HashMap<>();
                userInfo.put("openId", user.get("open_id"));
                userInfo.put("userId", user.get("user_id"));
                userInfo.put("name", user.get("name"));
                userInfo.put("email", user.get("email"));
                userInfo.put("mobile", user.get("mobile"));
                userInfo.put("status", user.get("status"));
                
                return ToolResult.success(userInfo);
            } else {
                return ToolResult.failure("获取用户信息失败: " + result.get("msg"));
            }
        } else {
            return ToolResult.failure("获取用户信息失败: HTTP " + response.statusCode());
        }
    }
    
    private ToolResult getDepartment(ToolParams params) throws Exception {
        String departmentId = params.getString("department_id");
        if (departmentId == null || departmentId.isEmpty()) {
            departmentId = "0";
        }
        
        Integer pageSizeInt = params.getInteger("page_size");
        int pageSize = pageSizeInt != null ? pageSizeInt : 50;
        
        String url = "https://open.feishu.cn/open-apis/contact/v3/departments/" + departmentId 
            + "/children?department_id_type=open_department_id&page_size=" + pageSize;
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + accessToken)
            .GET()
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
                
                List<Map<String, Object>> departments = new ArrayList<>();
                if (items != null) {
                    for (Map<String, Object> item : items) {
                        Map<String, Object> dept = new HashMap<>();
                        dept.put("openDepartmentId", item.get("open_department_id"));
                        dept.put("name", item.get("name"));
                        dept.put("parentDepartmentId", item.get("parent_department_id"));
                        dept.put("leaderUserId", item.get("leader_user_id"));
                        dept.put("memberCount", item.get("member_count"));
                        departments.add(dept);
                    }
                }
                
                return ToolResult.success(Map.of("departments", departments));
            } else {
                return ToolResult.failure("获取部门信息失败: " + result.get("msg"));
            }
        } else {
            return ToolResult.failure("获取部门信息失败: HTTP " + response.statusCode());
        }
    }
    
    private ToolResult createDepartment(ToolParams params) throws Exception {
        String name = params.getString("name");
        String parentDepartmentId = params.getString("parent_department_id");
        String departmentId = params.getString("department_id");
        String leaderUserId = params.getString("leader_user_id");
        String order = params.getString("order");
        Boolean createGroupChat = params.getBoolean("create_group_chat");
        String departmentHrbps = params.getString("department_hrbps");
        
        if (name == null || name.isEmpty()) {
            return ToolResult.failure("缺少必要参数: name");
        }
        
        if (parentDepartmentId == null || parentDepartmentId.isEmpty()) {
            parentDepartmentId = "0";
        }
        
        String url = "https://open.feishu.cn/open-apis/contact/v3/departments?department_id_type=open_department_id&user_id_type=open_id";
        
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("parent_department_id", parentDepartmentId);
        
        if (departmentId != null && !departmentId.isEmpty()) {
            body.put("department_id", departmentId);
        }
        if (leaderUserId != null && !leaderUserId.isEmpty()) {
            body.put("leader_user_id", leaderUserId);
        }
        if (order != null && !order.isEmpty()) {
            body.put("order", order);
        }
        if (createGroupChat != null) {
            body.put("create_group_chat", createGroupChat);
        }
        if (departmentHrbps != null && !departmentHrbps.isEmpty()) {
            body.put("department_hrbps", List.of(departmentHrbps.split(",")));
        }
        
        String requestBody = objectMapper.writeValueAsString(body);
        log.info("董事长创建部门请求: {}", requestBody);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + accessToken)
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        log.info("董事长创建部门响应: status={}, body={}", response.statusCode(), response.body());
        
        if (response.statusCode() == 200) {
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                Map<String, Object> dept = (Map<String, Object>) data.get("department");
                return ToolResult.success(Map.of(
                    "departmentId", dept != null ? dept.get("open_department_id") : null,
                    "name", name,
                    "message", "部门创建成功"
                ));
            } else {
                return ToolResult.failure("创建部门失败: " + result.get("msg") + " (code: " + result.get("code") + ")");
            }
        } else {
            return ToolResult.failure("创建部门失败: HTTP " + response.statusCode());
        }
    }
    
    private ToolResult updateDepartment(ToolParams params) throws Exception {
        String departmentId = params.getString("department_id");
        String name = params.getString("name");
        String leaderUserId = params.getString("leader_user_id");
        
        if (departmentId == null || departmentId.isEmpty()) {
            return ToolResult.failure("缺少必要参数: department_id");
        }
        
        String url = "https://open.feishu.cn/open-apis/contact/v3/departments/" + departmentId;
        
        Map<String, Object> body = new HashMap<>();
        if (name != null && !name.isEmpty()) {
            body.put("name", name);
        }
        if (leaderUserId != null && !leaderUserId.isEmpty()) {
            body.put("leader_user_id", leaderUserId);
        }
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + accessToken)
            .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                return ToolResult.success(Map.of("message", "部门更新成功"));
            } else {
                return ToolResult.failure("更新部门失败: " + result.get("msg"));
            }
        } else {
            return ToolResult.failure("更新部门失败: HTTP " + response.statusCode());
        }
    }
    
    private ToolResult deleteDepartment(ToolParams params) throws Exception {
        String departmentId = params.getString("department_id");
        
        if (departmentId == null || departmentId.isEmpty()) {
            return ToolResult.failure("缺少必要参数: department_id");
        }
        
        String url = "https://open.feishu.cn/open-apis/contact/v3/departments/" + departmentId;
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + accessToken)
            .DELETE()
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                return ToolResult.success(Map.of("message", "部门删除成功"));
            } else {
                return ToolResult.failure("删除部门失败: " + result.get("msg"));
            }
        } else {
            return ToolResult.failure("删除部门失败: HTTP " + response.statusCode());
        }
    }
    
    private ToolResult createUser(ToolParams params) throws Exception {
        String name = params.getString("name");
        String departmentId = params.getString("department_id");
        String email = params.getString("email");
        String mobile = params.getString("mobile");
        String employeeNo = params.getString("employee_no");
        Integer employeeType = params.getInteger("employee_type");
        Integer gender = params.getInteger("gender");
        
        if (name == null || name.isEmpty()) {
            return ToolResult.failure("缺少必要参数: name");
        }
        
        String url = "https://open.feishu.cn/open-apis/contact/v3/users?user_id_type=open_id&department_id_type=open_department_id";
        
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        
        if (departmentId != null && !departmentId.isEmpty()) {
            body.put("department_ids", List.of(departmentId));
        }
        if (email != null && !email.isEmpty()) {
            body.put("email", email);
        }
        if (mobile != null && !mobile.isEmpty()) {
            body.put("mobile", mobile);
        }
        if (employeeNo != null && !employeeNo.isEmpty()) {
            body.put("employee_no", employeeNo);
        }
        body.put("employee_type", employeeType != null ? employeeType : 1);
        if (gender != null) {
            body.put("gender", gender);
        }
        
        String requestBody = objectMapper.writeValueAsString(body);
        log.info("创建用户请求体: {}", requestBody);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + accessToken)
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        log.info("创建用户响应状态: {}, 响应体: {}", response.statusCode(), response.body());
        
        if (response.statusCode() == 200) {
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                return ToolResult.success(Map.of("message", "用户创建成功", "data", result.get("data")));
            } else {
                return ToolResult.failure("创建用户失败[code=" + code + "]: " + result.get("msg") + ", 响应: " + response.body());
            }
        } else {
            return ToolResult.failure("创建用户失败: HTTP " + response.statusCode() + ", 响应: " + response.body());
        }
    }
    
    private ToolResult updateUser(ToolParams params) throws Exception {
        String userId = params.getString("user_id");
        String name = params.getString("name");
        String departmentId = params.getString("department_id");
        String email = params.getString("email");
        String mobile = params.getString("mobile");
        Integer employeeType = params.getInteger("employee_type");
        
        if (userId == null || userId.isEmpty()) {
            return ToolResult.failure("缺少必要参数: user_id");
        }
        
        String url = "https://open.feishu.cn/open-apis/contact/v3/users/" + userId 
            + "?user_id_type=open_id&department_id_type=open_department_id";
        
        Map<String, Object> body = new HashMap<>();
        if (name != null && !name.isEmpty()) {
            body.put("name", name);
        }
        if (departmentId != null && !departmentId.isEmpty()) {
            body.put("department_ids", List.of(departmentId));
        }
        if (email != null && !email.isEmpty()) {
            body.put("email", email);
        }
        if (mobile != null && !mobile.isEmpty()) {
            body.put("mobile", mobile);
        }
        if (employeeType != null) {
            body.put("employee_type", employeeType);
        }
        
        if (body.isEmpty()) {
            return ToolResult.failure("没有需要更新的字段");
        }
        
        String requestBody = objectMapper.writeValueAsString(body);
        log.info("更新用户请求体: {}", requestBody);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + accessToken)
            .method("PATCH", HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        log.info("更新用户响应状态: {}, 响应体: {}", response.statusCode(), response.body());
        
        if (response.statusCode() == 200) {
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                return ToolResult.success(Map.of("message", "用户更新成功", "data", result.get("data")));
            } else {
                return ToolResult.failure("更新用户失败[code=" + code + "]: " + result.get("msg"));
            }
        } else {
            return ToolResult.failure("更新用户失败: HTTP " + response.statusCode() + ", 响应: " + response.body());
        }
    }
    
    private ToolResult sendActivationEmail(ToolParams params) throws Exception {
        String userId = params.getString("user_id");
        
        if (userId == null || userId.isEmpty()) {
            return ToolResult.failure("缺少必要参数: user_id");
        }
        
        String url = "https://open.feishu.cn/open-apis/contact/v3/users/" + userId 
            + "/resend_invitation?user_id_type=open_id";
        
        Map<String, Object> body = new HashMap<>();
        
        String requestBody = objectMapper.writeValueAsString(body);
        log.info("发送激活邀请请求体: {}", requestBody);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + accessToken)
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        log.info("发送激活邀请响应状态: {}, 响应体: {}", response.statusCode(), response.body());
        
        if (response.statusCode() == 200) {
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                return ToolResult.success(Map.of("message", "激活邀请发送成功"));
            } else {
                return ToolResult.failure("发送激活邀请失败[code=" + code + "]: " + result.get("msg"));
            }
        } else {
            return ToolResult.failure("发送激活邀请失败: HTTP " + response.statusCode() + ", 响应: " + response.body());
        }
    }
    
    private ToolResult deleteUser(ToolParams params) throws Exception {
        String userId = params.getString("user_id");
        
        if (userId == null || userId.isEmpty()) {
            return ToolResult.failure("缺少必要参数: user_id");
        }
        
        String url = "https://open.feishu.cn/open-apis/contact/v3/users/" + userId;
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + accessToken)
            .DELETE()
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                return ToolResult.success(Map.of("message", "用户删除成功"));
            } else {
                return ToolResult.failure("删除用户失败: " + result.get("msg"));
            }
        } else {
            return ToolResult.failure("删除用户失败: HTTP " + response.statusCode());
        }
    }
    
    private ToolResult createApproval(ToolParams params) throws Exception {
        String approvalCode = params.getString("approval_code");
        String formData = params.getString("form_data");
        
        if (approvalCode == null || approvalCode.isEmpty()) {
            return ToolResult.failure("缺少必要参数: approval_code");
        }
        
        String url = "https://open.feishu.cn/open-apis/approval/v4/instances";
        
        Map<String, Object> body = new HashMap<>();
        body.put("approval_code", approvalCode);
        if (formData != null && !formData.isEmpty()) {
            body.put("form", objectMapper.readValue(formData, Map.class));
        }
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + accessToken)
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                return ToolResult.success(Map.of(
                    "instanceId", data != null ? data.get("instance_id") : null,
                    "message", "审批创建成功"
                ));
            } else {
                return ToolResult.failure("创建审批失败: " + result.get("msg"));
            }
        } else {
            return ToolResult.failure("创建审批失败: HTTP " + response.statusCode());
        }
    }
    
    private ToolResult getApproval(ToolParams params) throws Exception {
        String instanceId = params.getString("instance_id");
        
        if (instanceId == null || instanceId.isEmpty()) {
            return ToolResult.failure("缺少必要参数: instance_id");
        }
        
        String url = "https://open.feishu.cn/open-apis/approval/v4/instances/" + instanceId;
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + accessToken)
            .GET()
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                return ToolResult.success(data);
            } else {
                return ToolResult.failure("查询审批失败: " + result.get("msg"));
            }
        } else {
            return ToolResult.failure("查询审批失败: HTTP " + response.statusCode());
        }
    }
    
    private ToolResult cancelApproval(ToolParams params) throws Exception {
        String instanceId = params.getString("instance_id");
        
        if (instanceId == null || instanceId.isEmpty()) {
            return ToolResult.failure("缺少必要参数: instance_id");
        }
        
        String url = "https://open.feishu.cn/open-apis/approval/v4/instances/" + instanceId + "/cancel";
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + accessToken)
            .POST(HttpRequest.BodyPublishers.ofString("{}"))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                return ToolResult.success(Map.of("message", "审批取消成功"));
            } else {
                return ToolResult.failure("取消审批失败: " + result.get("msg"));
            }
        } else {
            return ToolResult.failure("取消审批失败: HTTP " + response.statusCode());
        }
    }
    
    private ToolResult sendCard(ToolParams params) throws Exception {
        String receiveId = params.getString("receive_id");
        String receiveIdType = params.getString("receive_id_type");
        if (receiveIdType == null) receiveIdType = "open_id";
        String cardContent = params.getString("content");
        
        if (receiveId == null || cardContent == null) {
            return ToolResult.failure("缺少必要参数: receive_id 或 content");
        }
        
        String url = "https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=" + receiveIdType;
        
        Map<String, Object> body = new HashMap<>();
        body.put("receive_id", receiveId);
        body.put("msg_type", "interactive");
        body.put("content", cardContent);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + accessToken)
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                return ToolResult.success(Map.of("message", "卡片消息发送成功"));
            } else {
                return ToolResult.failure("发送卡片消息失败: " + result.get("msg"));
            }
        } else {
            return ToolResult.failure("发送卡片消息失败: HTTP " + response.statusCode());
        }
    }
    
    private ToolResult uploadFile(ToolParams params) throws Exception {
        String filePath = params.getString("file_path");
        String fileType = params.getString("file_type");
        String fileName = params.getString("file_name");
        
        if (filePath == null || filePath.isEmpty()) {
            return ToolResult.failure("缺少必要参数: file_path");
        }
        
        if (fileType == null) {
            fileType = "stream";
        }
        
        if (fileName == null) {
            int lastSlash = filePath.lastIndexOf("/");
            if (lastSlash == -1) {
                lastSlash = filePath.lastIndexOf("\\");
            }
            fileName = lastSlash >= 0 ? filePath.substring(lastSlash + 1) : "file";
        }
        
        java.io.File file = new java.io.File(filePath);
        if (!file.exists()) {
            return ToolResult.failure("文件不存在: " + filePath);
        }
        
        String url = "https://open.feishu.cn/open-apis/drive/v1/medias/upload_all";
        
        String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
        
        java.nio.file.Path path = file.toPath();
        byte[] fileBytes = java.nio.file.Files.readAllBytes(path);
        
        StringBuilder sb = new StringBuilder();
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"file_name\"\r\n\r\n");
        sb.append(fileName).append("\r\n");
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"parent_type\"\r\n\r\n");
        sb.append("ccm_import_open").append("\r\n");
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"parent_key\"\r\n\r\n");
        sb.append("ccm_import_open").append("\r\n");
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"size\"\r\n\r\n");
        sb.append(fileBytes.length).append("\r\n");
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"file\"; filename=\"")
          .append(fileName).append("\"\r\n");
        sb.append("Content-Type: application/octet-stream\r\n\r\n");
        
        byte[] headerBytes = sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] footerBytes = ("\r\n--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        
        java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
        outputStream.write(headerBytes);
        outputStream.write(fileBytes);
        outputStream.write(footerBytes);
        byte[] multipartBody = outputStream.toByteArray();
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + accessToken)
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                return ToolResult.success(Map.of(
                    "fileKey", data != null ? data.get("file_key") : null,
                    "fileName", fileName,
                    "fileSize", fileBytes.length,
                    "message", "文件上传成功"
                ));
            } else {
                return ToolResult.failure("文件上传失败: " + result.get("msg"));
            }
        } else {
            return ToolResult.failure("文件上传失败: HTTP " + response.statusCode());
        }
    }
    
    private ToolResult getCalendarList(ToolParams params) throws Exception {
        String url = "https://open.feishu.cn/open-apis/calendar/v4/calendars";
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + accessToken)
            .GET()
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
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
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + accessToken)
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
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
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + accessToken)
            .GET()
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
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
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + accessToken)
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
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
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + accessToken)
            .GET()
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
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
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + accessToken)
            .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                return ToolResult.success(Map.of("message", "任务更新成功"));
            } else {
                return ToolResult.failure("更新任务失败: " + result.get("msg"));
            }
        } else {
            return ToolResult.failure("更新任务失败: HTTP " + response.statusCode());
        }
    }
    
    private ToolResult createChat(ToolParams params) throws Exception {
        String name = params.getString("name");
        String description = params.getString("description");
        String userIds = params.getString("user_ids");
        
        if (name == null || name.isEmpty()) {
            return ToolResult.failure("缺少必要参数: name");
        }
        
        String url = "https://open.feishu.cn/open-apis/im/v1/chats";
        
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        
        if (description != null && !description.isEmpty()) {
            body.put("description", description);
        }
        
        if (userIds != null && !userIds.isEmpty()) {
            List<String> members = new ArrayList<>();
            for (String id : userIds.split(",")) {
                members.add(id.trim());
            }
            body.put("user_id_list", members);
        }
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + accessToken)
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                return ToolResult.success(Map.of(
                    "chatId", data != null ? data.get("chat_id") : null,
                    "name", name,
                    "message", "群聊创建成功"
                ));
            } else {
                return ToolResult.failure("创建群聊失败: " + result.get("msg"));
            }
        } else {
            return ToolResult.failure("创建群聊失败: HTTP " + response.statusCode());
        }
    }
    
    private ToolResult getChat(ToolParams params) throws Exception {
        String chatId = params.getString("chat_id");
        
        if (chatId == null || chatId.isEmpty()) {
            return ToolResult.failure("缺少必要参数: chat_id");
        }
        
        String url = "https://open.feishu.cn/open-apis/im/v1/chats/" + chatId;
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + accessToken)
            .GET()
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                return ToolResult.success(data);
            } else {
                return ToolResult.failure("获取群聊信息失败: " + result.get("msg"));
            }
        } else {
            return ToolResult.failure("获取群聊信息失败: HTTP " + response.statusCode());
        }
    }
    
    private ToolResult addChatMembers(ToolParams params) throws Exception {
        String chatId = params.getString("chat_id");
        String userIds = params.getString("user_ids");
        
        if (chatId == null || chatId.isEmpty()) {
            return ToolResult.failure("缺少必要参数: chat_id");
        }
        
        if (userIds == null || userIds.isEmpty()) {
            return ToolResult.failure("缺少必要参数: user_ids");
        }
        
        String url = "https://open.feishu.cn/open-apis/im/v1/chats/" + chatId + "/members";
        
        List<String> members = new ArrayList<>();
        for (String id : userIds.split(",")) {
            members.add(id.trim());
        }
        
        Map<String, Object> body = new HashMap<>();
        body.put("user_id_list", members);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + accessToken)
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                return ToolResult.success(Map.of("message", "成员添加成功"));
            } else {
                return ToolResult.failure("添加成员失败: " + result.get("msg"));
            }
        } else {
            return ToolResult.failure("添加成员失败: HTTP " + response.statusCode());
        }
    }
    
    private ToolResult getApprovalDefinitionList(ToolParams params) throws Exception {
        Integer pageSizeInt = params.getInteger("page_size");
        int pageSize = pageSizeInt != null ? pageSizeInt : 50;
        
        String url = "https://open.feishu.cn/open-apis/approval/v4/approvals?page_size=" + pageSize;
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + accessToken)
            .GET()
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
                
                List<Map<String, Object>> approvalList = new ArrayList<>();
                if (items != null) {
                    for (Map<String, Object> item : items) {
                        Map<String, Object> approval = new HashMap<>();
                        approval.put("approvalCode", item.get("approval_code"));
                        approval.put("name", item.get("name"));
                        approval.put("description", item.get("description"));
                        approval.put("status", item.get("status"));
                        approvalList.add(approval);
                    }
                }
                
                return ToolResult.success(Map.of("approvals", approvalList));
            } else {
                return ToolResult.failure("获取审批定义列表失败: " + result.get("msg"));
            }
        } else {
            return ToolResult.failure("获取审批定义列表失败: HTTP " + response.statusCode());
        }
    }
    
    private ToolResult createApprovalDefinition(ToolParams params) throws Exception {
        String approvalCode = params.getString("approval_code");
        String name = params.getString("name");
        String description = params.getString("description");
        String formContent = params.getString("form_content");
        
        if (approvalCode == null || approvalCode.isEmpty()) {
            return ToolResult.failure("缺少必要参数: approval_code");
        }
        
        if (name == null || name.isEmpty()) {
            return ToolResult.failure("缺少必要参数: name");
        }
        
        String url = "https://open.feishu.cn/open-apis/approval/v4/approvals";
        
        Map<String, Object> body = new HashMap<>();
        body.put("approval_code", approvalCode);
        body.put("name", name);
        
        if (description != null && !description.isEmpty()) {
            body.put("description", description);
        }
        
        if (formContent != null && !formContent.isEmpty()) {
            body.put("form_content", objectMapper.readValue(formContent, Map.class));
        }
        
        log.info("创建审批定义请求: {}", objectMapper.writeValueAsString(body));
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + accessToken)
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        log.info("创建审批定义响应: status={}, body={}", response.statusCode(), response.body());
        
        if (response.statusCode() == 200) {
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                return ToolResult.success(Map.of(
                    "approvalCode", approvalCode,
                    "name", name,
                    "message", "审批定义创建成功"
                ));
            } else {
                return ToolResult.failure("创建审批定义失败: " + result.get("msg"));
            }
        } else {
            return ToolResult.failure("创建审批定义失败: HTTP " + response.statusCode());
        }
    }
}
