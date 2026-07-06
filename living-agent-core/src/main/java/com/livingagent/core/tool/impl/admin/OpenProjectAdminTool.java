package com.livingagent.core.tool.impl.admin;
import com.livingagent.core.admin.AdminOperationResult;
import com.livingagent.core.admin.EmployeeExternalAccount;
import com.livingagent.core.database.entity.EmployeeExternalAccountEntity;
import com.livingagent.core.database.repository.EmployeeExternalAccountRepository;import com.livingagent.core.security.SecurityPolicy;
import com.livingagent.core.tool.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * OpenProject 管理工具
 * <p>主脑以管理员身份完成 OpenProject 的初始配置：创建 Project/Role/User/Member。
 * <p>实现 Tool 接口，注册到 ToolRegistry，部门为 "admin_management"，MainBrain 可访问，其他大脑不可访问。
 * <p>关联文档：docs/core/MAINBRAIN_ADMIN_BRIDGE_PLAN.md, docs/core/MAINBRAIN_SERVICE_MANAGEMENT.md
 */
public class OpenProjectAdminTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(OpenProjectAdminTool.class);

    private static final String NAME = "openproject_admin";
    private static final String DESCRIPTION = "OpenProject 管理工具 - 创建 Project/Role/User/Member（仅 MainBrain 可访问）";
    private static final String VERSION = "1.0.0";
    private static final String DEPARTMENT = "admin_management";

    private final HttpClient httpClient;
    private final EmployeeExternalAccountRepository accountRepository;
    private String openprojectUrl;
    private String adminApiKey;

    private final AtomicLong totalCalls = new AtomicLong(0);
    private final AtomicLong successfulCalls = new AtomicLong(0);
    private final AtomicLong failedCalls = new AtomicLong(0);
    private final AtomicLong totalDurationMs = new AtomicLong(0);

    private final ToolSchema schema;

    public OpenProjectAdminTool(EmployeeExternalAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

        Map<String, ToolSchema.Property> properties = new HashMap<>();
        properties.put("action", ToolSchema.Property.string("管理操作类型", List.of(
            "create_project", "create_user", "add_member", "create_role", "lock_user"
        )));
        properties.put("project_identifier", ToolSchema.Property.string("Project 标识符"));
        properties.put("project_name", ToolSchema.Property.string("Project 名称"));
        properties.put("project_description", ToolSchema.Property.string("Project 描述"));
        properties.put("role_name", ToolSchema.Property.string("Role 名称"));
        properties.put("permissions", ToolSchema.Property.string("权限列表（逗号分隔）"));
        properties.put("login", ToolSchema.Property.string("用户登录名"));
        properties.put("email", ToolSchema.Property.string("邮箱"));
        properties.put("firstname", ToolSchema.Property.string("用户名（首名）"));
        properties.put("lastname", ToolSchema.Property.string("用户名（末名）"));
        properties.put("project_id", ToolSchema.Property.integer("Project ID"));
        properties.put("user_id", ToolSchema.Property.integer("User ID"));
        properties.put("role_id", ToolSchema.Property.integer("Role ID"));

        this.schema = new ToolSchema(NAME, DESCRIPTION, properties, List.of("action"));
    }

    /**
     * 配置 OpenProject 连接信息
     */
    public void configure(String openprojectUrl, String adminApiKey) {
        this.openprojectUrl = openprojectUrl.endsWith("/") ?
            openprojectUrl.substring(0, openprojectUrl.length() - 1) : openprojectUrl;
        this.adminApiKey = adminApiKey;
        log.info("OpenProjectAdminTool configured: url={}, apiKey={}", openprojectUrl, adminApiKey != null ? "***" : "null");
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
        return List.of("admin", "openproject", "project-management", "user-management", "role-management");
    }

    @Override
    public ToolResult execute(ToolParams params, ToolContext context) {
        long startTime = System.currentTimeMillis();
        totalCalls.incrementAndGet();

        if (openprojectUrl == null || adminApiKey == null) {
            return ToolResult.failure(
                UUID.randomUUID().toString(),
                NAME,
                "OpenProject 管理工具未配置，请设置 openprojectUrl 和 adminApiKey",
                Duration.ZERO
            );
        }

        try {
            String action = params.getString("action");
            Object result = switch (action) {
                case "create_project" -> createProject(params);
                case "create_user" -> createUser(params);
                case "add_member" -> addMember(params);
                case "create_role" -> createRole(params);
                case "lock_user" -> lockUser(params);
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
            log.error("OpenProject 管理操作失败", e);
            return ToolResult.failure(
                UUID.randomUUID().toString(),
                NAME,
                "OpenProject 管理操作失败: " + e.getMessage(),
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

        switch (action) {
            case "create_project":
                if (params.getString("project_identifier") == null) throw new IllegalArgumentException("project_identifier 必填");
                if (params.getString("project_name") == null) throw new IllegalArgumentException("project_name 必填");
                break;
            case "create_user":
                if (params.getString("login") == null) throw new IllegalArgumentException("login 必填");
                if (params.getString("email") == null) throw new IllegalArgumentException("email 必填");
                break;
            case "add_member":
                if (params.getInteger("project_id") == null) throw new IllegalArgumentException("project_id 必填");
                if (params.getInteger("user_id") == null) throw new IllegalArgumentException("user_id 必填");
                if (params.getInteger("role_id") == null) throw new IllegalArgumentException("role_id 必填");
                break;
            case "create_role":
                if (params.getString("role_name") == null) throw new IllegalArgumentException("role_name 必填");
                break;
            case "lock_user":
                if (params.getInteger("user_id") == null) throw new IllegalArgumentException("user_id 必填");
                break;
        }
    }

    @Override
    public boolean isAllowed(SecurityPolicy policy) {
        return policy != null && policy.isToolAllowed(NAME);
    }

    @Override
    public boolean requiresApproval() {
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
     * 创建 Project
     */
    private Map<String, Object> createProject(ToolParams params) throws Exception {
        String identifier = params.getString("project_identifier");
        String name = params.getString("project_name");
        String description = params.getString("project_description");

        Map<String, Object> body = new HashMap<>();
        body.put("identifier", identifier);
        body.put("name", name);
        if (description != null) {
            body.put("description", description);
        }

        HttpResponse<String> response = sendPost("/api/v3/projects", body);

        if (response.statusCode() == 201) {
            Map<String, Object> project = parseJson(response.body());
            log.info("创建 OpenProject Project 成功: id={}, identifier={}, name={}",
                project.get("id"), project.get("identifier"), project.get("name"));
            return project;
        } else {
            throw new RuntimeException("创建 Project 失败: " + response.statusCode() + " - " + response.body());
        }
    }

    /**
     * 创建 User
     */
    private Map<String, Object> createUser(ToolParams params) throws Exception {
        String login = params.getString("login");
        String email = params.getString("email");
        String firstname = params.getString("firstname");
        String lastname = params.getString("lastname");
        String password = params.getString("password");

        Map<String, Object> body = new HashMap<>();
        body.put("login", login);
        body.put("email", email);
        body.put("firstname", firstname != null ? firstname : login);
        body.put("lastname", lastname != null ? lastname : "User");
        body.put("password", password != null ? password : UUID.randomUUID().toString().substring(0, 16));
        body.put("status", "active");

        HttpResponse<String> response = sendPost("/api/v3/users", body);

        if (response.statusCode() == 201) {
            Map<String, Object> user = parseJson(response.body());
            log.info("创建 OpenProject User 成功: id={}, login={}, email={}",
                user.get("id"), user.get("login"), user.get("email"));

            // 存储员工外部账号映射
            String employeeCode = params.getString("employee_code");
            if (employeeCode != null && accountRepository != null) {
                saveEmployeeAccount(employeeCode, "openproject",
                    String.valueOf(user.get("id")), login, null);
            }

            return user;
        } else {
            throw new RuntimeException("创建 User 失败: " + response.statusCode() + " - " + response.body());
        }
    }

    /**
     * 添加 Project Member
     */
    private Map<String, Object> addMember(ToolParams params) throws Exception {
        Integer projectId = params.getInteger("project_id");
        Integer userId = params.getInteger("user_id");
        Integer roleId = params.getInteger("role_id");

        Map<String, Object> body = new HashMap<>();
        body.put("principal", Map.of("href", "/api/v3/users/" + userId));
        body.put("roles", List.of(Map.of("href", "/api/v3/roles/" + roleId)));

        HttpResponse<String> response = sendPost("/api/v3/projects/" + projectId + "/memberships", body);

        if (response.statusCode() == 201) {
            Map<String, Object> membership = parseJson(response.body());
            log.info("添加 OpenProject Project Member 成功: project_id={}, user_id={}, role_id={}",
                projectId, userId, roleId);
            return membership;
        } else {
            throw new RuntimeException("添加 Member 失败: " + response.statusCode() + " - " + response.body());
        }
    }

    /**
     * 创建 Role
     */
    private Map<String, Object> createRole(ToolParams params) throws Exception {
        String roleName = params.getString("role_name");
        String permissionsStr = params.getString("permissions");

        List<String> permissions = permissionsStr != null ?
            Arrays.asList(permissionsStr.split(",")) :
            List.of("view_work_packages", "edit_work_packages");

        Map<String, Object> body = new HashMap<>();
        body.put("name", roleName);
        body.put("permissions", permissions);
        body.put("global", false);

        HttpResponse<String> response = sendPost("/api/v3/roles", body);

        if (response.statusCode() == 201) {
            Map<String, Object> role = parseJson(response.body());
            log.info("创建 OpenProject Role 成功: id={}, name={}", role.get("id"), role.get("name"));
            return role;
        } else {
            throw new RuntimeException("创建 Role 失败: " + response.statusCode() + " - " + response.body());
        }
    }

    /**
     * 锁定 User
     */
    private Map<String, Object> lockUser(ToolParams params) throws Exception {
        Integer userId = params.getInteger("user_id");

        Map<String, Object> body = new HashMap<>();
        body.put("status", "locked");

        HttpResponse<String> response = sendPatch("/api/v3/users/" + userId, body);

        if (response.statusCode() == 200) {
            log.info("锁定 OpenProject User 成功: user_id={}", userId);
            return Map.of("success", true, "user_id", userId, "action", "lock");
        } else {
            throw new RuntimeException("锁定 User 失败: " + response.statusCode() + " - " + response.body());
        }
    }

    // ==================== 辅助方法 ====================

    private HttpResponse<String> sendPost(String path, Map<String, Object> body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(openprojectUrl + path))
            .header("Authorization", "Basic apikey:" + adminApiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(toJson(body)))
            .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> sendPatch(String path, Map<String, Object> body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(openprojectUrl + path))
            .header("Authorization", "Basic apikey:" + adminApiKey)
            .header("Content-Type", "application/json")
            .method("PATCH", HttpRequest.BodyPublishers.ofString(toJson(body)))
            .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

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
            } else if (value instanceof Map) {
                sb.append(toJson((Map<String, Object>) value));
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
            } else if (item instanceof Map) {
                sb.append(toJson((Map<String, Object>) item));
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

    private Map<String, Object> parseJson(String json) {
        try {
            return com.livingagent.core.admin.impl.AdminJsonUtils.parseObject(json);
        } catch (Exception e) {
            log.error("JSON 解析失败: {}", json, e);
            return Map.of();
        }
    }
}