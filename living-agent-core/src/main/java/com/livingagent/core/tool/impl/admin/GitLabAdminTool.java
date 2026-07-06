package com.livingagent.core.tool.impl.admin;

import com.livingagent.core.admin.AdminOperationResult;
import com.livingagent.core.admin.EmployeeExternalAccount;
import com.livingagent.core.database.entity.EmployeeExternalAccountEntity;
import com.livingagent.core.database.repository.EmployeeExternalAccountRepository;
import com.livingagent.core.security.SecurityPolicy;
import com.livingagent.core.tool.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * GitLab 管理工具
 * <p>主脑以管理员身份完成 GitLab 的初始配置：创建 Group/Project/User/Token/Member。
 * <p>实现 Tool 接口，注册到 ToolRegistry，部门为 "admin_management"，MainBrain 可访问，其他大脑不可访问。
 * <p>关联文档：docs/core/MAINBRAIN_ADMIN_BRIDGE_PLAN.md, docs/core/MAINBRAIN_SERVICE_MANAGEMENT.md
 */
public class GitLabAdminTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(GitLabAdminTool.class);

    private static final String NAME = "gitlab_admin";
    private static final String DESCRIPTION = "GitLab 管理工具 - 创建 Group/Project/User/Token/Member（仅 MainBrain 可访问）";
    private static final String VERSION = "1.0.0";
    private static final String DEPARTMENT = "admin_management";

    private final HttpClient httpClient;
    private final EmployeeExternalAccountRepository accountRepository;
    private String gitlabUrl;
    private String rootToken;

    private final AtomicLong totalCalls = new AtomicLong(0);
    private final AtomicLong successfulCalls = new AtomicLong(0);
    private final AtomicLong failedCalls = new AtomicLong(0);
    private final AtomicLong totalDurationMs = new AtomicLong(0);

    private final ToolSchema schema;

    public GitLabAdminTool(EmployeeExternalAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

        Map<String, ToolSchema.Property> properties = new HashMap<>();
        properties.put("action", ToolSchema.Property.string("管理操作类型", List.of(
            "create_group", "create_project", "create_user",
            "add_group_member", "create_token", "block_user", "unblock_user"
        )));
        properties.put("group_name", ToolSchema.Property.string("Group 名称"));
        properties.put("group_path", ToolSchema.Property.string("Group 路径"));
        properties.put("project_name", ToolSchema.Property.string("Project 名称"));
        properties.put("project_path", ToolSchema.Property.string("Project 路径"));
        properties.put("namespace_id", ToolSchema.Property.integer("Namespace ID（Group ID）"));
        properties.put("username", ToolSchema.Property.string("用户名"));
        properties.put("email", ToolSchema.Property.string("邮箱"));
        properties.put("name", ToolSchema.Property.string("显示名称"));
        properties.put("user_id", ToolSchema.Property.integer("用户 ID"));
        properties.put("access_level", ToolSchema.Property.integer("访问级别（10=Guest, 20=Reporter, 30=Developer, 40=Maintainer, 50=Owner）"));
        properties.put("token_name", ToolSchema.Property.string("Token 名称"));
        properties.put("scopes", ToolSchema.Property.string("Token 权限范围（逗号分隔）"));

        this.schema = new ToolSchema(NAME, DESCRIPTION, properties, List.of("action"));
    }

    /**
     * 配置 GitLab 连接信息
     */
    public void configure(String gitlabUrl, String rootToken) {
        this.gitlabUrl = gitlabUrl.endsWith("/") ? gitlabUrl.substring(0, gitlabUrl.length() - 1) : gitlabUrl;
        this.rootToken = rootToken;
        log.info("GitLabAdminTool configured: url={}, token={}", gitlabUrl, rootToken != null ? "***" : "null");
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
        return List.of("admin", "gitlab", "user-management", "group-management", "project-management");
    }

    @Override
    public ToolResult execute(ToolParams params, ToolContext context) {
        long startTime = System.currentTimeMillis();
        totalCalls.incrementAndGet();

        if (gitlabUrl == null || rootToken == null) {
            return ToolResult.failure(
                UUID.randomUUID().toString(),
                NAME,
                "GitLab 管理工具未配置，请设置 gitlabUrl 和 rootToken",
                Duration.ZERO
            );
        }

        try {
            String action = params.getString("action");
            Object result = switch (action) {
                case "create_group" -> createGroup(params);
                case "create_project" -> createProject(params);
                case "create_user" -> createUser(params);
                case "add_group_member" -> addGroupMember(params);
                case "create_token" -> createToken(params);
                case "block_user" -> blockUser(params);
                case "unblock_user" -> unblockUser(params);
                default -> throw new IllegalArgumentException("未知管理操作: " + action);
            };

            long duration = System.currentTimeMillis() - startTime;
            totalDurationMs.addAndGet(duration);
            successfulCalls.incrementAndGet();

            return ToolResult.success(
                UUID.randomUUID().toString(),
                NAME,
                result,
                Duration.ofMillis(duration)
            );

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            failedCalls.incrementAndGet();
            log.error("GitLab 管理操作失败", e);
            return ToolResult.failure(
                UUID.randomUUID().toString(),
                NAME,
                "GitLab 管理操作失败: " + e.getMessage(),
                Duration.ofMillis(duration)
            );
        }
    }

    @Override
    public void validate(ToolParams params) {
        String action = params.getString("action");
        if (action == null || action.isEmpty()) {
            throw new IllegalArgumentException("action 参数必填");
        }

        // 根据不同 action 验证必需参数
        switch (action) {
            case "create_group":
                if (params.getString("group_name") == null) throw new IllegalArgumentException("group_name 必填");
                if (params.getString("group_path") == null) throw new IllegalArgumentException("group_path 必填");
                break;
            case "create_project":
                if (params.getString("project_name") == null) throw new IllegalArgumentException("project_name 必填");
                break;
            case "create_user":
                if (params.getString("email") == null) throw new IllegalArgumentException("email 必填");
                if (params.getString("username") == null) throw new IllegalArgumentException("username 必填");
                break;
            case "add_group_member":
                if (params.getInteger("user_id") == null) throw new IllegalArgumentException("user_id 必填");
                if (params.getInteger("access_level") == null) throw new IllegalArgumentException("access_level 必填");
                break;
            case "create_token":
                if (params.getInteger("user_id") == null) throw new IllegalArgumentException("user_id 必填");
                if (params.getString("token_name") == null) throw new IllegalArgumentException("token_name 必填");
                break;
            case "block_user", "unblock_user":
                if (params.getInteger("user_id") == null) throw new IllegalArgumentException("user_id 必填");
                break;
        }
    }

    @Override
    public boolean isAllowed(SecurityPolicy policy) {
        // 管理类工具仅允许 MainBrain 或 FULL 权限用户访问
        // 通过 ToolRegistry 的 BRAIN_TOOL_DEPARTMENT_MAPPING 区分，MainBrain 可访问，其他大脑不可访问
        return policy != null && policy.isToolAllowed(NAME);
    }

    @Override
    public boolean requiresApproval() {
        // 管理类操作需要审批
        return true;
    }

    @Override
    public ToolStats getStats() {
        long avgDuration = totalCalls.get() > 0 ? totalDurationMs.get() / totalCalls.get() : 0;
        return new ToolStats(
            NAME,
            totalCalls.get(),
            successfulCalls.get(),
            failedCalls.get(),
            avgDuration,
            System.currentTimeMillis()
        );
    }

    // ==================== 管理操作实现 ====================

    /**
     * 创建 Group
     */
    private Map<String, Object> createGroup(ToolParams params) throws Exception {
        String name = params.getString("group_name");
        String path = params.getString("group_path");
        String description = params.getString("description");
        Integer parentId = params.getInteger("parent_id");

        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("path", path);
        if (description != null) body.put("description", description);
        if (parentId != null) body.put("parent_id", parentId);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(gitlabUrl + "/api/v4/groups"))
            .header("PRIVATE-TOKEN", rootToken)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(toJson(body)))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            Map<String, Object> group = parseJson(response.body());
            log.info("创建 GitLab Group 成功: id={}, name={}, path={}",
                group.get("id"), group.get("name"), group.get("path"));
            return group;
        } else {
            throw new RuntimeException("创建 Group 失败: " + response.statusCode() + " - " + response.body());
        }
    }

    /**
     * 创建 Project
     */
    private Map<String, Object> createProject(ToolParams params) throws Exception {
        String name = params.getString("project_name");
        String path = params.getString("project_path");
        Integer namespaceId = params.getInteger("namespace_id");
        String description = params.getString("description");
        Boolean visibility = params.getBoolean("visibility_public");

        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        if (path != null) body.put("path", path);
        if (namespaceId != null) body.put("namespace_id", namespaceId);
        if (description != null) body.put("description", description);
        if (visibility != null && visibility) body.put("visibility", "public");
        else body.put("visibility", "private");

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(gitlabUrl + "/api/v4/projects"))
            .header("PRIVATE-TOKEN", rootToken)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(toJson(body)))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            Map<String, Object> project = parseJson(response.body());
            log.info("创建 GitLab Project 成功: id={}, name={}, path={}",
                project.get("id"), project.get("name"), project.get("path_with_namespace"));
            return project;
        } else {
            throw new RuntimeException("创建 Project 失败: " + response.statusCode() + " - " + response.body());
        }
    }

    /**
     * 创建 User
     */
    private Map<String, Object> createUser(ToolParams params) throws Exception {
        String email = params.getString("email");
        String username = params.getString("username");
        String name = params.getString("name");
        String password = params.getString("password");

        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("username", username);
        body.put("name", name != null ? name : username);
        body.put("password", password != null ? password : UUID.randomUUID().toString().substring(0, 16));
        body.put("skip_confirmation", true);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(gitlabUrl + "/api/v4/users"))
            .header("PRIVATE-TOKEN", rootToken)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(toJson(body)))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            Map<String, Object> user = parseJson(response.body());
            log.info("创建 GitLab User 成功: id={}, username={}, email={}",
                user.get("id"), user.get("username"), user.get("email"));

            // 存储员工外部账号映射
            String employeeCode = params.getString("employee_code");
            if (employeeCode != null && accountRepository != null) {
                saveEmployeeAccount(employeeCode, "gitlab",
                    String.valueOf(user.get("id")), username, null);
            }

            return user;
        } else {
            throw new RuntimeException("创建 User 失败: " + response.statusCode() + " - " + response.body());
        }
    }

    /**
     * 添加 Group Member
     */
    private Map<String, Object> addGroupMember(ToolParams params) throws Exception {
        Integer groupId = params.getInteger("group_id");
        Integer userId = params.getInteger("user_id");
        Integer accessLevel = params.getInteger("access_level");

        Map<String, Object> body = new HashMap<>();
        body.put("user_id", userId);
        body.put("access_level", accessLevel);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(gitlabUrl + "/api/v4/groups/" + groupId + "/members"))
            .header("PRIVATE-TOKEN", rootToken)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(toJson(body)))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            Map<String, Object> member = parseJson(response.body());
            log.info("添加 GitLab Group Member 成功: group_id={}, user_id={}, access_level={}",
                groupId, userId, accessLevel);
            return member;
        } else {
            throw new RuntimeException("添加 Group Member 失败: " + response.statusCode() + " - " + response.body());
        }
    }

    /**
     * 创建 Personal Access Token
     */
    private Map<String, Object> createToken(ToolParams params) throws Exception {
        Integer userId = params.getInteger("user_id");
        String tokenName = params.getString("token_name");
        String scopesStr = params.getString("scopes");

        List<String> scopes = scopesStr != null ?
            Arrays.asList(scopesStr.split(",")) :
            List.of("api", "read_api", "read_repository", "write_repository");

        Map<String, Object> body = new HashMap<>();
        body.put("name", tokenName);
        body.put("scopes", scopes);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(gitlabUrl + "/api/v4/users/" + userId + "/personal_access_tokens"))
            .header("PRIVATE-TOKEN", rootToken)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(toJson(body)))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            Map<String, Object> token = parseJson(response.body());
            log.info("创建 GitLab Personal Access Token 成功: user_id={}, name={}", userId, tokenName);

            // 存储员工外部账号映射（包含 Token）
            String employeeCode = params.getString("employee_code");
            if (employeeCode != null && accountRepository != null) {
                saveEmployeeAccount(employeeCode, "gitlab",
                    String.valueOf(userId), null, (String) token.get("token"));
            }

            return token;
        } else {
            throw new RuntimeException("创建 Token 失败: " + response.statusCode() + " - " + response.body());
        }
    }

    /**
     * 锁定 User
     */
    private Map<String, Object> blockUser(ToolParams params) throws Exception {
        Integer userId = params.getInteger("user_id");

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(gitlabUrl + "/api/v4/users/" + userId + "/block"))
            .header("PRIVATE-TOKEN", rootToken)
            .POST(HttpRequest.BodyPublishers.ofString(""))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            log.info("锁定 GitLab User 成功: user_id={}", userId);
            return Map.of("success", true, "user_id", userId, "action", "block");
        } else {
            throw new RuntimeException("锁定 User 失败: " + response.statusCode() + " - " + response.body());
        }
    }

    /**
     * 解锁 User
     */
    private Map<String, Object> unblockUser(ToolParams params) throws Exception {
        Integer userId = params.getInteger("user_id");

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(gitlabUrl + "/api/v4/users/" + userId + "/unblock"))
            .header("PRIVATE-TOKEN", rootToken)
            .POST(HttpRequest.BodyPublishers.ofString(""))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            log.info("解锁 GitLab User 成功: user_id={}", userId);
            return Map.of("success", true, "user_id", userId, "action", "unblock");
        } else {
            throw new RuntimeException("解锁 User 失败: " + response.statusCode() + " - " + response.body());
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 存储员工外部账号映射
     */
    private void saveEmployeeAccount(String employeeCode, String serviceType,
                                     String externalUserId, String externalUsername, String externalToken) {
        try {
            EmployeeExternalAccountEntity entity = new EmployeeExternalAccountEntity();
            entity.setEmployeeCode(employeeCode);
            entity.setServiceType(serviceType);
            entity.setExternalUserId(externalUserId);
            entity.setExternalUsername(externalUsername);
            entity.setExternalToken(externalToken);
            entity.setActive(true);
            entity.setCreatedAt(java.time.Instant.now());
            entity.setUpdatedAt(java.time.Instant.now());

            accountRepository.save(entity);
            log.info("存储员工外部账号映射: employee_code={}, service_type={}, external_user_id={}",
                employeeCode, serviceType, externalUserId);
        } catch (Exception e) {
            log.error("存储员工外部账号映射失败", e);
        }
    }

    /**
     * 简单 JSON 序列化（不依赖 Jackson）
     */
    private String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof String) {
                sb.append("\"").append(escapeJson((String) value)).append("\"");
            } else if (value instanceof List) {
                sb.append(toJsonArray((List<?>) value));
            } else if (value instanceof Boolean || value instanceof Number) {
                sb.append(value);
            } else {
                sb.append("\"").append(escapeJson(value.toString())).append("\"");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private String toJsonArray(List<?> list) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Object item : list) {
            if (!first) sb.append(",");
            first = false;
            if (item instanceof String) {
                sb.append("\"").append(escapeJson((String) item)).append("\"");
            } else {
                sb.append("\"").append(escapeJson(item.toString())).append("\"");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * 简单 JSON 解析（使用已有的 AdminJsonUtils）
     */
    private Map<String, Object> parseJson(String json) {
        try {
            return com.livingagent.core.admin.impl.AdminJsonUtils.parseObject(json);
        } catch (Exception e) {
            log.error("JSON 解析失败: {}", json, e);
            return Map.of();
        }
    }
}