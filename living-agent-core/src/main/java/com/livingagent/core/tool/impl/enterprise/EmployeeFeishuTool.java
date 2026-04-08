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

public class EmployeeFeishuTool implements Tool {
    
    private static final Logger log = LoggerFactory.getLogger(EmployeeFeishuTool.class);
    private static final String NAME = "employee_feishu";
    private static final String DESCRIPTION = "普通员工飞书通讯工具，仅具备基础消息发送和查询权限，用于日常工作沟通";
    private static final String VERSION = "1.0.0";
    private static final String DEPARTMENT = "communication";
    
    private final String appId;
    private final String appSecret;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    private String accessToken;
    private long tokenExpireTime;
    private ToolStats stats = ToolStats.empty(NAME);
    
    private final ToolSchema schema;
    
    public EmployeeFeishuTool(String appId, String appSecret) {
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
                "get_user(获取用户信息), " +
                "get_department(获取部门列表), " +
                "send_card(发送卡片消息)", 
                true)
            .parameter("receive_id", "string", "接收者ID (用户ID或群ID)", false)
            .parameter("receive_id_type", "string", "接收者类型: open_id, user_id, union_id, chat_id, email", false)
            .parameter("msg_type", "string", "消息类型: text, post, image, file, card", false)
            .parameter("content", "string", "消息内容", false)
            .parameter("user_id", "string", "用户ID", false)
            .parameter("department_id", "string", "部门ID", false)
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
            "user_query",
            "department_query",
            "card_message"
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
                case "send_card" -> sendCard(params);
                default -> ToolResult.failure("不支持的操作: " + action + "。普通员工工具仅支持: send_message, get_user, get_department, send_card");
            };
            stats = stats.recordCall(result.success(), System.currentTimeMillis() - startTime);
            return result;
        } catch (Exception e) {
            log.error("员工飞书操作失败: {}", e.getMessage(), e);
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
        return false;
    }
    
    public boolean isActionAllowed(String action, AccessLevel accessLevel) {
        return switch (action) {
            case "send_message", "send_card" -> accessLevel != AccessLevel.CHAT_ONLY;
            case "get_user", "get_department" -> true;
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
                log.info("员工飞书 access_token 获取成功");
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
