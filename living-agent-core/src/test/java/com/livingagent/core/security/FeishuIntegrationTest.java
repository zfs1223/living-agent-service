package com.livingagent.core.security;

import com.livingagent.core.security.auth.OAuthService;
import com.livingagent.core.security.auth.impl.FeishuOAuthService;
import com.livingagent.core.security.sync.FeishuSyncAdapter;
import com.livingagent.core.security.sync.HrSyncAdapter;
import com.livingagent.core.tool.Tool;
import com.livingagent.core.tool.Tool.ToolParams;
import com.livingagent.core.tool.ToolContext;
import com.livingagent.core.tool.ToolResult;
import com.livingagent.core.tool.impl.enterprise.FeishuTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FeishuIntegrationTest {

    private static final String APP_ID = "cli_a920321f3b7a5cc1";
    private static final String APP_SECRET = "gmU0opRuS3Aps30BWR84ghkv7ELrkncG";

    private FeishuSyncAdapter syncAdapter;
    private FeishuTool feishuTool;

    @BeforeEach
    void setUp() {
        syncAdapter = new FeishuSyncAdapter(APP_ID, APP_SECRET);
        feishuTool = new FeishuTool(APP_ID, APP_SECRET);
    }

    @Test
    @DisplayName("测试飞书连接 - 获取 tenant_access_token")
    void testConnection() {
        System.out.println("========================================");
        System.out.println("测试飞书连接...");
        System.out.println("App ID: " + APP_ID);
        System.out.println("========================================");

        boolean connected = syncAdapter.testConnection();
        
        System.out.println("连接结果: " + (connected ? "成功 ✅" : "失败 ❌"));
        
        assertTrue(connected, "飞书连接应该成功");
    }

    @Test
    @DisplayName("测试获取部门列表")
    void testFetchDepartments() {
        System.out.println("========================================");
        System.out.println("测试获取飞书部门列表...");
        System.out.println("========================================");

        var departments = syncAdapter.fetchDepartments();
        
        System.out.println("获取到 " + departments.size() + " 个部门");
        for (var dept : departments) {
            System.out.println("  - " + dept.getName() + " (ID: " + dept.getDepartmentId() + ")");
        }
        
        assertNotNull(departments, "部门列表不应为 null");
    }

    @Test
    @DisplayName("测试获取员工列表")
    void testFetchEmployees() {
        System.out.println("========================================");
        System.out.println("测试获取飞书员工列表...");
        System.out.println("========================================");

        var employees = syncAdapter.fetchEmployees();
        
        System.out.println("获取到 " + employees.size() + " 个员工");
        for (var emp : employees.stream().limit(10).toList()) {
            System.out.println("  - " + emp.getName() + " (" + emp.getEmail() + ") - " + emp.getDepartment());
        }
        if (employees.size() > 10) {
            System.out.println("  ... 还有 " + (employees.size() - 10) + " 个员工");
        }
        
        assertNotNull(employees, "员工列表不应为 null");
    }

    @Test
    @DisplayName("测试 FeishuTool 获取部门")
    void testFeishuToolGetDepartment() {
        System.out.println("========================================");
        System.out.println("测试 FeishuTool 获取部门...");
        System.out.println("========================================");

        ToolParams params = ToolParams.of(Map.of(
            "action", "get_department",
            "department_id", "0"
        ));
        ToolContext context = ToolContext.of("test-user", "test-session");
        
        ToolResult result = feishuTool.execute(params, context);
        
        System.out.println("执行结果: " + (result.success() ? "成功 ✅" : "失败 ❌"));
        System.out.println("返回数据: " + result.data());
        
        assertTrue(result.success(), "获取部门应该成功");
    }

    @Test
    @DisplayName("测试 OAuth 授权 URL 生成")
    void testOAuthAuthorizationUrl() {
        System.out.println("========================================");
        System.out.println("测试飞书 OAuth 授权 URL 生成...");
        System.out.println("========================================");

        OAuthService oauthService = new FeishuOAuthService(APP_ID, APP_SECRET);
        String redirectUri = "http://localhost:8382/api/auth/oauth/feishu/callback";
        String state = "test-state-123";
        
        String authUrl = oauthService.getAuthorizationUrl(redirectUri, state);
        
        System.out.println("授权 URL: " + authUrl);
        
        assertNotNull(authUrl, "授权 URL 不应为 null");
        assertTrue(authUrl.contains("passport.feishu.cn"), "URL 应该包含飞书域名");
        assertTrue(authUrl.contains(APP_ID), "URL 应该包含 App ID");
    }

    @Test
    @DisplayName("测试创建部门")
    void testCreateDepartment() {
        System.out.println("========================================");
        System.out.println("测试飞书创建部门...");
        System.out.println("========================================");

        ToolParams params = ToolParams.of(Map.of(
            "action", "create_department",
            "name", "测试部门-AI智能体",
            "parent_department_id", "0"
        ));
        ToolContext context = ToolContext.of("test-user", "test-session");
        
        ToolResult result = feishuTool.execute(params, context);
        
        System.out.println("执行结果: " + (result.success() ? "成功 ✅" : "失败 ❌"));
        System.out.println("返回数据: " + result.data());
        System.out.println("错误信息: " + result.error());
        
        if (!result.success()) {
            System.out.println("注意: 创建部门需要飞书应用有 'contact:department' 权限");
        }
    }

    public static void main(String[] args) {
        FeishuIntegrationTest test = new FeishuIntegrationTest();
        test.setUp();
        
        System.out.println("\n");
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║           飞书对接测试 - Living Agent Service              ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println("\n");
        
        try {
            test.testConnection();
            System.out.println();
            test.testFetchDepartments();
            System.out.println();
            test.testFetchEmployees();
            System.out.println();
            test.testFeishuToolGetDepartment();
            System.out.println();
            test.testOAuthAuthorizationUrl();
        } catch (Exception e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
