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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Jenkins 管理工具
 * <p>主脑以管理员身份完成 Jenkins 的初始配置：创建 Job/Credential/Plugin。
 * <p>实现 Tool 接口，注册到 ToolRegistry，部门为 "admin_management"，MainBrain 可访问，其他大脑不可访问。
 * <p>关联文档：docs/core/MAINBRAIN_ADMIN_BRIDGE_PLAN.md, docs/core/MAINBRAIN_SERVICE_MANAGEMENT.md
 */
public class JenkinsAdminTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(JenkinsAdminTool.class);

    private static final String NAME = "jenkins_admin";
    private static final String DESCRIPTION = "Jenkins 管理工具 - 创建 Job/Credential（仅 MainBrain 可访问）";
    private static final String VERSION = "1.0.0";
    private static final String DEPARTMENT = "admin_management";

    private final HttpClient httpClient;
    private final EmployeeExternalAccountRepository accountRepository;
    private String jenkinsUrl;
    private String username;
    private String apiToken;

    private final AtomicLong totalCalls = new AtomicLong(0);
    private final AtomicLong successfulCalls = new AtomicLong(0);
    private final AtomicLong failedCalls = new AtomicLong(0);
    private final AtomicLong totalDurationMs = new AtomicLong(0);

    private final ToolSchema schema;

    public JenkinsAdminTool(EmployeeExternalAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

        Map<String, ToolSchema.Property> properties = new HashMap<>();
        properties.put("action", ToolSchema.Property.string("管理操作类型", List.of(
            "create_job", "delete_job", "create_credential", "list_credentials", "create_user"
        )));
        properties.put("job_name", ToolSchema.Property.string("Job 名称"));
        properties.put("pipeline_script", ToolSchema.Property.string("Pipeline 脚本（Jenkinsfile）"));
        properties.put("credential_id", ToolSchema.Property.string("Credential ID"));
        properties.put("credential_description", ToolSchema.Property.string("Credential 描述"));
        properties.put("credential_username", ToolSchema.Property.string("Credential 用户名"));
        properties.put("credential_secret", ToolSchema.Property.string("Credential 密钥"));
        properties.put("user_username", ToolSchema.Property.string("用户名"));
        properties.put("user_password", ToolSchema.Property.string("用户密码"));
        properties.put("user_email", ToolSchema.Property.string("用户邮箱"));

        this.schema = new ToolSchema(NAME, DESCRIPTION, properties, List.of("action"));
    }

    /**
     * 配置 Jenkins 连接信息
     */
    public void configure(String jenkinsUrl, String username, String apiToken) {
        this.jenkinsUrl = jenkinsUrl.endsWith("/") ?
            jenkinsUrl.substring(0, jenkinsUrl.length() - 1) : jenkinsUrl;
        this.username = username;
        this.apiToken = apiToken;
        log.info("JenkinsAdminTool configured: url={}, username={}", jenkinsUrl, username);
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
        return List.of("admin", "jenkins", "job-management", "credential-management", "ci-cd");
    }

    @Override
    public ToolResult execute(ToolParams params, ToolContext context) {
        long startTime = System.currentTimeMillis();
        totalCalls.incrementAndGet();

        if (jenkinsUrl == null || username == null || apiToken == null) {
            return ToolResult.failure(
                UUID.randomUUID().toString(),
                NAME,
                "Jenkins 管理工具未配置，请设置 jenkinsUrl、username 和 apiToken",
                Duration.ZERO
            );
        }

        try {
            String action = params.getString("action");
            Object result = switch (action) {
                case "create_job" -> createJob(params);
                case "delete_job" -> deleteJob(params);
                case "create_credential" -> createCredential(params);
                case "list_credentials" -> listCredentials(params);
                case "create_user" -> createUser(params);
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
            log.error("Jenkins 管理操作失败", e);
            return ToolResult.failure(
                UUID.randomUUID().toString(),
                NAME,
                "Jenkins 管理操作失败: " + e.getMessage(),
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
            case "create_job":
                if (params.getString("job_name") == null) throw new IllegalArgumentException("job_name 必填");
                break;
            case "delete_job":
                if (params.getString("job_name") == null) throw new IllegalArgumentException("job_name 必填");
                break;
            case "create_credential":
                if (params.getString("credential_id") == null) throw new IllegalArgumentException("credential_id 必填");
                break;
            case "create_user":
                if (params.getString("user_username") == null) throw new IllegalArgumentException("user_username 必填");
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
     * 创建 Pipeline Job
     */
    private Map<String, Object> createJob(ToolParams params) throws Exception {
        String jobName = params.getString("job_name");
        String pipelineScript = params.getString("pipeline_script");

        String configXml = buildPipelineJobConfig(jobName, pipelineScript);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(jenkinsUrl + "/createItem?name=" + jobName))
            .header("Authorization", basicAuth())
            .header("Content-Type", "application/xml")
            .POST(HttpRequest.BodyPublishers.ofString(configXml, StandardCharsets.UTF_8))
            .timeout(Duration.ofSeconds(30))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200 || response.statusCode() == 201) {
            log.info("创建 Jenkins Job 成功: {}", jobName);
            return Map.of("success", true, "job_name", jobName, "action", "create_job");
        } else if (response.statusCode() == 400) {
            log.info("Jenkins Job 已存在: {}", jobName);
            return Map.of("success", true, "job_name", jobName, "action", "create_job", "skipped", true);
        } else {
            throw new RuntimeException("创建 Job 失败: " + response.statusCode() + " - " + response.body());
        }
    }

    /**
     * 删除 Job
     */
    private Map<String, Object> deleteJob(ToolParams params) throws Exception {
        String jobName = params.getString("job_name");

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(jenkinsUrl + "/job/" + jobName + "/doDelete"))
            .header("Authorization", basicAuth())
            .POST(HttpRequest.BodyPublishers.ofString(""))
            .timeout(Duration.ofSeconds(30))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200 || response.statusCode() == 302) {
            log.info("删除 Jenkins Job 成功: {}", jobName);
            return Map.of("success", true, "job_name", jobName, "action", "delete_job");
        } else {
            throw new RuntimeException("删除 Job 失败: " + response.statusCode() + " - " + response.body());
        }
    }

    /**
     * 创建 Credential
     */
    private Map<String, Object> createCredential(ToolParams params) throws Exception {
        String credentialId = params.getString("credential_id");
        String description = params.getString("credential_description");
        String credUsername = params.getString("credential_username");
        String credSecret = params.getString("credential_secret");

        String configXml = buildCredentialConfig(credentialId, description, credUsername, credSecret);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(jenkinsUrl + "/credentials/store/system/domain/_/createCredentials"))
            .header("Authorization", basicAuth())
            .header("Content-Type", "application/xml")
            .POST(HttpRequest.BodyPublishers.ofString(configXml, StandardCharsets.UTF_8))
            .timeout(Duration.ofSeconds(30))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200 || response.statusCode() == 201 || response.statusCode() == 302) {
            log.info("创建 Jenkins Credential 成功: {}", credentialId);
            return Map.of("success", true, "credential_id", credentialId, "action", "create_credential");
        } else {
            throw new RuntimeException("创建 Credential 失败: " + response.statusCode() + " - " + response.body());
        }
    }

    /**
     * 列出 Credentials
     */
    private Map<String, Object> listCredentials(ToolParams params) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(jenkinsUrl + "/credentials/store/system/domain/_/api/json"))
            .header("Authorization", basicAuth())
            .GET()
            .timeout(Duration.ofSeconds(30))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            Map<String, Object> credentials = parseJson(response.body());
            log.info("列出 Jenkins Credentials 成功");
            return credentials;
        } else {
            throw new RuntimeException("列出 Credentials 失败: " + response.statusCode() + " - " + response.body());
        }
    }

    /**
     * 创建 User
     */
    private Map<String, Object> createUser(ToolParams params) throws Exception {
        String userUsername = params.getString("user_username");
        String userPassword = params.getString("user_password");
        String userEmail = params.getString("user_email");

        String formData = "username=" + userUsername +
            "&password1=" + (userPassword != null ? userPassword : UUID.randomUUID().toString().substring(0, 16)) +
            "&password2=" + (userPassword != null ? userPassword : UUID.randomUUID().toString().substring(0, 16)) +
            "&email=" + (userEmail != null ? userEmail : userUsername + "@living-agent.local") +
            "&fullname=" + userUsername;

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(jenkinsUrl + "/securityRealm/createAccountByAdmin"))
            .header("Authorization", basicAuth())
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(formData, StandardCharsets.UTF_8))
            .timeout(Duration.ofSeconds(30))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200 || response.statusCode() == 302) {
            log.info("创建 Jenkins User 成功: {}", userUsername);

            // 存储员工外部账号映射
            String employeeCode = params.getString("employee_code");
            if (employeeCode != null && accountRepository != null) {
                saveEmployeeAccount(employeeCode, "jenkins", userUsername, userUsername, null);
            }

            return Map.of("success", true, "username", userUsername, "action", "create_user");
        } else {
            throw new RuntimeException("创建 User 失败: " + response.statusCode() + " - " + response.body());
        }
    }

    // ==================== 辅助方法 ====================

    private String basicAuth() {
        return "Basic " + Base64.getEncoder().encodeToString(
            (username + ":" + apiToken).getBytes(StandardCharsets.UTF_8));
    }

    private String buildPipelineJobConfig(String jobName, String pipelineScript) {
        String script = pipelineScript != null ? pipelineScript :
            "pipeline {\n" +
            "  agent any\n" +
            "  stages {\n" +
            "    stage('Build') {\n" +
            "      steps {\n" +
            "        echo 'Building...'\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "}\n";

        return "<?xml version='1.1' encoding='UTF-8'?>\n" +
            "<flow-definition plugin=\"workflow-job\">\n" +
            "  <description>Auto-created by MainBrain</description>\n" +
            "  <definition class=\"org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition\" plugin=\"workflow-cps\">\n" +
            "    <script>" + escapeXml(script) + "</script>\n" +
            "    <sandbox>true</sandbox>\n" +
            "  </definition>\n" +
            "  <disabled>false</disabled>\n" +
            "</flow-definition>";
    }

    private String buildCredentialConfig(String credentialId, String description,
                                         String username, String secret) {
        return "<?xml version='1.1' encoding='UTF-8'?>\n" +
            "<com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl>\n" +
            "  <scope>GLOBAL</scope>\n" +
            "  <id>" + credentialId + "</id>\n" +
            "  <description>" + (description != null ? escapeXml(description) : "Auto-created") + "</description>\n" +
            "  <username>" + (username != null ? escapeXml(username) : "") + "</username>\n" +
            "  <password>" + (secret != null ? escapeXml(secret) : "") + "</password>\n" +
            "</com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl>";
    }

    private String escapeXml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
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

    private Map<String, Object> parseJson(String json) {
        try {
            return com.livingagent.core.admin.impl.AdminJsonUtils.parseObject(json);
        } catch (Exception e) {
            log.error("JSON 解析失败: {}", json, e);
            return Map.of();
        }
    }
}