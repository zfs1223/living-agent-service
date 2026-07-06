package com.livingagent.core.tool.impl.admin;

import com.livingagent.core.config.AdminConfig;
import com.livingagent.core.config.LivingAgentCoreConfig;
import com.livingagent.core.tool.Tool;
import com.livingagent.core.tool.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.ContextConfiguration;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 管理类工具注册测试
 * <p>验证 GitLabAdminTool/OpenProjectAdminTool/JenkinsAdminTool 是否正常注册到 ToolRegistry。
 * <p>测试条件：service-admin.enabled=true
 */
@SpringBootTest(classes = {LivingAgentCoreConfig.class, AdminConfig.class})
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "service-admin.enabled=true",
    "tool.gitlab.base-url=http://gitlab:8929",
    "tool.gitlab.access-token=test-token",
    "tool.openproject.base-url=http://openproject:8080",
    "tool.openproject.api-token=test-token",
    "tool.jenkins.base-url=http://jenkins:8080",
    "tool.jenkins.username=test-user",
    "tool.jenkins.api-token=test-token"
})
@DisplayName("管理类工具注册测试")
class AdminToolRegistrationTest {

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired(required = false)
    private GitLabAdminTool gitLabAdminTool;

    @Autowired(required = false)
    private OpenProjectAdminTool openProjectAdminTool;

    @Autowired(required = false)
    private JenkinsAdminTool jenkinsAdminTool;

    @Test
    @DisplayName("验证 AdminConfig 已加载")
    void testAdminConfigLoaded() {
        assertNotNull(gitLabAdminTool, "GitLabAdminTool 应已注册为 Spring Bean");
        assertNotNull(openProjectAdminTool, "OpenProjectAdminTool 应已注册为 Spring Bean");
        assertNotNull(jenkinsAdminTool, "JenkinsAdminTool 应已注册为 Spring Bean");
    }

    @Test
    @DisplayName("验证 GitLabAdminTool 已注册到 ToolRegistry")
    void testGitLabAdminToolRegistered() {
        Optional<Tool> tool = toolRegistry.get("gitlab_admin");
        assertTrue(tool.isPresent(), "GitLabAdminTool 应存在于 ToolRegistry");
        
        Tool adminTool = tool.get();
        assertEquals("gitlab_admin", adminTool.getName());
        assertEquals("admin_management", adminTool.getDepartment());
        assertTrue(adminTool.getCapabilities().contains("admin"));
    }

    @Test
    @DisplayName("验证 OpenProjectAdminTool 已注册到 ToolRegistry")
    void testOpenProjectAdminToolRegistered() {
        Optional<Tool> tool = toolRegistry.get("openproject_admin");
        assertTrue(tool.isPresent(), "OpenProjectAdminTool 应存在于 ToolRegistry");
        
        Tool adminTool = tool.get();
        assertEquals("openproject_admin", adminTool.getName());
        assertEquals("admin_management", adminTool.getDepartment());
        assertTrue(adminTool.getCapabilities().contains("admin"));
    }

    @Test
    @DisplayName("验证 JenkinsAdminTool 已注册到 ToolRegistry")
    void testJenkinsAdminToolRegistered() {
        Optional<Tool> tool = toolRegistry.get("jenkins_admin");
        assertTrue(tool.isPresent(), "JenkinsAdminTool 应存在于 ToolRegistry");
        
        Tool adminTool = tool.get();
        assertEquals("jenkins_admin", adminTool.getName());
        assertEquals("admin_management", adminTool.getDepartment());
        assertTrue(adminTool.getCapabilities().contains("admin"));
    }

    @Test
    @DisplayName("验证管理类工具部门为 admin_management")
    void testAdminToolsDepartment() {
        assertEquals("admin_management", gitLabAdminTool.getDepartment());
        assertEquals("admin_management", openProjectAdminTool.getDepartment());
        assertEquals("admin_management", jenkinsAdminTool.getDepartment());
    }

    @Test
    @DisplayName("验证管理类工具包含 admin capability")
    void testAdminToolsCapability() {
        assertTrue(gitLabAdminTool.getCapabilities().contains("admin"));
        assertTrue(openProjectAdminTool.getCapabilities().contains("admin"));
        assertTrue(jenkinsAdminTool.getCapabilities().contains("admin"));
    }

    @Test
    @DisplayName("验证管理类工具需要审批")
    void testAdminToolsRequireApproval() {
        assertTrue(gitLabAdminTool.requiresApproval());
        assertTrue(openProjectAdminTool.requiresApproval());
        assertTrue(jenkinsAdminTool.requiresApproval());
    }
}