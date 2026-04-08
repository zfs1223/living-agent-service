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

public class HrFeishuTool implements Tool {
    
    private static final Logger log = LoggerFactory.getLogger(HrFeishuTool.class);
    private static final String NAME = "hr_feishu";
    private static final String DESCRIPTION = "HR专属飞书管理工具，具备通讯录管理权限，可进行员工信息查询、部门管理、入职离职办理等HR相关操作";
    private static final String VERSION = "1.0.0";
    private static final String DEPARTMENT = "human_resources";
    
    private final String appId;
    private final String appSecret;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    private String accessToken;
    private long tokenExpireTime;
    private ToolStats stats = ToolStats.empty(NAME);
    
    private final ToolSchema schema;
    
    public HrFeishuTool(String appId, String appSecret) {
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
                "get_user_list(获取用户列表), " +
                "create_user(创建用户), " +
                "update_user(更新用户), " +
                "send_card(发送卡片)", 
                true)
            .parameter("receive_id", "string", "接收者ID", false)
            .parameter("receive_id_type", "string", "接收者类型: open_id, user_id, chat_id", false)
            .parameter("msg_type", "string", "消息类型: text, post, card", false)
            .parameter("content", "string", "消息内容", false)
            .parameter("user_id", "string", "用户ID", false)
            .parameter("department_id", "string", "部门ID", false)
            .parameter("name", "string", "部门名称/用户名称", false)
            .parameter("parent_department_id", "string", "父部门ID", false)
            .parameter("leader_user_id", "string", "部门主管用户ID", false)
            .parameter("email", "string", "用户邮箱", false)
            .parameter("mobile", "string", "用户手机号", false)
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
            "user_query",
            "department_management",
            "department_create",
            "department_update",
            "department_query",
            "hr_operations"
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
                case "get_user_list" -> getUserList(params);
                case "get_department" -> getDepartment(params);
                case "create_department" -> createDepartment(params);
                case "update_department" -> updateDepartment(params);
                case "create_user" -> createUser(params);
                case "update_user" -> updateUser(params);
                case "send_card" -> sendCard(params);
                default -> ToolResult.failure("不支持的操作: " + action + "。HR工具支持: send_message, get_user, get_user_list, get_department, create_department, update_department, create_user, update_user, send_card");
            };
            stats = stats.recordCall(result.success(), System.currentTimeMillis() - startTime);
            return result;
        } catch (Exception e) {
            log.error("HR飞书操作失败: {}", e.getMessage(), e);
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
        if (accessLevel == AccessLevel.CHAT_ONLY) {
            return false;
        }
        return switch (action) {
            case "send_message", "send_card" -> true;
            case "get_user", "get_user_list", "get_department" -> true;
            case "create_department", "update_department" -> accessLevel == AccessLevel.DEPARTMENT || accessLevel == AccessLevel.FULL;
            case "create_user", "update_user" -> accessLevel == AccessLevel.DEPARTMENT || accessLevel == AccessLevel.FULL;
            default -> false;
        };
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
                log.info("HR飞书 access_token 获取成功");
            } else {
                throw new RuntimeException("获取access_token失败: " + result.get("msg"));
            }
        } else {
            throw new RuntimeException("获取access_token失败: HTTP " + response.statusCode());
        }
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
                return ToolResult.success(Map.of("message", "消息发送成功"));
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
                userInfo.put("departmentIds", user.get("department_ids"));
                
                return ToolResult.success(userInfo);
            } else {
                return ToolResult.failure("获取用户信息失败: " + result.get("msg"));
            }
        } else {
            return ToolResult.failure("获取用户信息失败: HTTP " + response.statusCode());
        }
    }
    
    private ToolResult getUserList(ToolParams params) throws Exception {
        String departmentId = params.getString("department_id");
        if (departmentId == null || departmentId.isEmpty()) {
            departmentId = "0";
        }
        
        Integer pageSizeInt = params.getInteger("page_size");
        int pageSize = pageSizeInt != null ? pageSizeInt : 50;
        
        String url = "https://open.feishu.cn/open-apis/contact/v3/users/find_by_department?department_id=" + departmentId + "&page_size=" + pageSize;
        
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
                
                List<Map<String, Object>> users = new ArrayList<>();
                if (items != null) {
                    for (Map<String, Object> item : items) {
                        Map<String, Object> user = new HashMap<>();
                        user.put("openId", item.get("open_id"));
                        user.put("userId", item.get("user_id"));
                        user.put("name", item.get("name"));
                        user.put("email", item.get("email"));
                        user.put("mobile", item.get("mobile"));
                        user.put("status", item.get("status"));
                        users.add(user);
                    }
                }
                
                return ToolResult.success(Map.of("users", users, "total", users.size()));
            } else {
                return ToolResult.failure("获取用户列表失败: " + result.get("msg"));
            }
        } else {
            return ToolResult.failure("获取用户列表失败: HTTP " + response.statusCode());
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
        String leaderUserId = params.getString("leader_user_id");
        
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
        
        if (leaderUserId != null && !leaderUserId.isEmpty()) {
            body.put("leader_user_id", leaderUserId);
        }
        
        log.info("HR创建部门请求: {}", objectMapper.writeValueAsString(body));
        
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
                Map<String, Object> dept = (Map<String, Object>) data.get("department");
                return ToolResult.success(Map.of(
                    "departmentId", dept != null ? dept.get("open_department_id") : null,
                    "name", name,
                    "message", "部门创建成功"
                ));
            } else {
                return ToolResult.failure("创建部门失败: " + result.get("msg"));
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
    
    private ToolResult createUser(ToolParams params) throws Exception {
        String name = params.getString("name");
        String departmentId = params.getString("department_id");
        String email = params.getString("email");
        String mobile = params.getString("mobile");
        
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
        
        log.info("HR创建用户请求: {}", objectMapper.writeValueAsString(body));
        
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
                return ToolResult.success(Map.of("message", "用户创建成功"));
            } else {
                return ToolResult.failure("创建用户失败: " + result.get("msg"));
            }
        } else {
            return ToolResult.failure("创建用户失败: HTTP " + response.statusCode());
        }
    }
    
    private ToolResult updateUser(ToolParams params) throws Exception {
        String userId = params.getString("user_id");
        String name = params.getString("name");
        String departmentId = params.getString("department_id");
        
        if (userId == null || userId.isEmpty()) {
            return ToolResult.failure("缺少必要参数: user_id");
        }
        
        String url = "https://open.feishu.cn/open-apis/contact/v3/users/" + userId;
        
        Map<String, Object> body = new HashMap<>();
        if (name != null && !name.isEmpty()) {
            body.put("name", name);
        }
        if (departmentId != null && !departmentId.isEmpty()) {
            body.put("department_ids", List.of(departmentId));
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
                return ToolResult.success(Map.of("message", "用户更新成功"));
            } else {
                return ToolResult.failure("更新用户失败: " + result.get("msg"));
            }
        } else {
            return ToolResult.failure("更新用户失败: HTTP " + response.statusCode());
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
}
