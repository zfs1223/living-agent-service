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
import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.security.SecurityPolicy;
import com.livingagent.core.tool.*;

public class FeishuTool implements Tool {
    
    private static final Logger log = LoggerFactory.getLogger(FeishuTool.class);
    private static final String NAME = "feishu";
    private static final String DESCRIPTION = "飞书/Lark企业通讯工具，用于发送消息、获取用户信息和管理群组";
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
    
    public FeishuTool(String appId, String appSecret) {
        this.appId = appId;
        this.appSecret = appSecret;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        this.objectMapper = new ObjectMapper();
        
        this.schema = ToolSchema.builder()
            .name(NAME)
            .description(DESCRIPTION)
            .parameter("action", "string", "操作类型: send_message, get_user, get_department, create_department, send_card, upload_file", true)
            .parameter("receive_id", "string", "接收者ID (用户ID或群ID)", false)
            .parameter("receive_id_type", "string", "接收者类型: open_id, user_id, union_id, chat_id, email", false)
            .parameter("msg_type", "string", "消息类型: text, post, image, file, card", false)
            .parameter("content", "string", "消息内容", false)
            .parameter("user_id", "string", "用户ID", false)
            .parameter("department_id", "string", "部门ID (创建时为自定义ID)", false)
            .parameter("name", "string", "部门名称 (创建部门时使用)", false)
            .parameter("parent_department_id", "string", "父部门ID (创建部门时使用，默认为根部门0)", false)
            .parameter("leader_user_id", "string", "部门主管用户ID (创建部门时使用)", false)
            .parameter("order", "string", "部门排序 (数值越小越靠前)", false)
            .parameter("create_group_chat", "boolean", "是否创建部门群 (创建部门时使用)", false)
            .parameter("department_hrbps", "string", "部门HRBP用户ID列表，逗号分隔", false)
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
        return List.of("messaging", "user_management", "department_management", "department_create", "card_message", "file_upload");
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
                case "send_card" -> sendCard(params);
                case "upload_file" -> uploadFile(params);
                default -> ToolResult.failure("不支持的操作: " + action);
            };
            stats = stats.recordCall(result.success(), System.currentTimeMillis() - startTime);
            return result;
        } catch (Exception e) {
            log.error("飞书操作失败: {}", e.getMessage(), e);
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
        return switch (action) {
            case "create_department" -> accessLevel == AccessLevel.FULL;
            case "send_message", "send_card" -> accessLevel != AccessLevel.CHAT_ONLY;
            case "get_user", "get_department" -> true;
            case "upload_file" -> accessLevel != AccessLevel.CHAT_ONLY;
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
        
        log.info("创建部门响应状态: {}, 响应内容: {}", response.statusCode(), response.body());
        
        if (response.statusCode() == 200) {
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                accessToken = (String) result.get("tenant_access_token");
                Integer expire = (Integer) result.get("expire");
                tokenExpireTime = System.currentTimeMillis() + (expire != null ? expire * 1000L - 60000L : 7000000L);
                log.info("飞书access_token获取成功");
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
                userInfo.put("enName", user.get("en_name"));
                userInfo.put("nickname", user.get("nickname"));
                userInfo.put("email", user.get("email"));
                userInfo.put("mobile", user.get("mobile"));
                userInfo.put("gender", user.get("gender"));
                userInfo.put("avatarUrl", user.get("avatar_url"));
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
        log.info("创建部门请求: {}", requestBody);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + accessToken)
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        log.info("创建部门响应: status={}, body={}", response.statusCode(), response.body());
        
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
}
