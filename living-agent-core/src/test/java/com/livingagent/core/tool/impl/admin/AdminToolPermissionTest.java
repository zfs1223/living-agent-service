package com.livingagent.core.tool.impl.admin;

import com.livingagent.core.config.AdminConfig;
import com.livingagent.core.config.LivingAgentCoreConfig;
import com.livingagent.core.security.SecurityPolicy;
import com.livingagent.core.tool.Tool;
import com.livingagent.core.tool.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 管理类工具权限隔离测试
 * <p>验证 MainBrain 可以访问管理类工具，其他大脑不可访问。
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
@DisplayName("管理类工具权限隔离测试")
class AdminToolPermissionTest {

    @Autowired
    private ToolRegistry toolRegistry;

    @Mock
    private SecurityPolicy policy;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("验证管理类工具的 isAllowed 方法")
    void testAdminToolsIsAllowed() {
        // 获取管理类工具
        Optional<Tool> gitLabAdminTool = toolRegistry.get("gitlab_admin");
        Optional<Tool> openProjectAdminTool = toolRegistry.get("openproject_admin");
        Optional<Tool> jenkinsAdminTool = toolRegistry.get("jenkins_admin");

        assertTrue(gitLabAdminTool.isPresent());
        assertTrue(openProjectAdminTool.isPresent());
        assertTrue(jenkinsAdminTool.isPresent());

        // 测试 policy.isToolAllowed(NAME) 返回 true 时，isAllowed 应返回 true
        when(policy.isToolAllowed("gitlab_admin")).thenReturn(true);
        when(policy.isToolAllowed("openproject_admin")).thenReturn(true);
        when(policy.isToolAllowed("jenkins_admin")).thenReturn(true);

        assertTrue(gitLabAdminTool.get().isAllowed(policy));
        assertTrue(openProjectAdminTool.get().isAllowed(policy));
        assertTrue(jenkinsAdminTool.get().isAllowed(policy));

        // 测试 policy.isToolAllowed(NAME) 返回 false 时，isAllowed 应返回 false
        when(policy.isToolAllowed("gitlab_admin")).thenReturn(false);
        when(policy.isToolAllowed("openproject_admin")).thenReturn(false);
        when(policy.isToolAllowed("jenkins_admin")).thenReturn(false);

        assertFalse(gitLabAdminTool.get().isAllowed(policy));
        assertFalse(openProjectAdminTool.get().isAllowed(policy));
        assertFalse(jenkinsAdminTool.get().isAllowed(policy));
    }

    @Test
    @DisplayName("验证管理类工具的 isAllowed 方法对 null policy 的处理")
    void testAdminToolsIsAllowedWithNullPolicy() {
        Optional<Tool> gitLabAdminTool = toolRegistry.get("gitlab_admin");
        Optional<Tool> openProjectAdminTool = toolRegistry.get("openproject_admin");
        Optional<Tool> jenkinsAdminTool = toolRegistry.get("jenkins_admin");

        assertTrue(gitLabAdminTool.isPresent());
        assertTrue(openProjectAdminTool.isPresent());
        assertTrue(jenkinsAdminTool.isPresent());

        // 测试 null policy 时，isAllowed 应返回 false
        assertFalse(gitLabAdminTool.get().isAllowed(null));
        assertFalse(openProjectAdminTool.get().isAllowed(null));
        assertFalse(jenkinsAdminTool.get().isAllowed(null));
    }

    @Test
    @DisplayName("验证管理类工具部门为 admin_management")
    void testAdminToolsDepartmentIsolation() {
        Optional<Tool> gitLabAdminTool = toolRegistry.get("gitlab_admin");
        Optional<Tool> openProjectAdminTool = toolRegistry.get("openproject_admin");
        Optional<Tool> jenkinsAdminTool = toolRegistry.get("jenkins_admin");

        assertTrue(gitLabAdminTool.isPresent());
        assertTrue(openProjectAdminTool.isPresent());
        assertTrue(jenkinsAdminTool.isPresent());

        // 验证部门为 "admin_management"
        assertEquals("admin_management", gitLabAdminTool.get().getDepartment());
        assertEquals("admin_management", openProjectAdminTool.get().getDepartment());
        assertEquals("admin_management", jenkinsAdminTool.get().getDepartment());
    }

    @Test
    @DisplayName("验证员工使用的 GitLabTool/OpenProjectTool/JenkinsTool 不包含管理类 action")
    void testEmployeeToolsDoNotContainAdminActions() {
        // 获取员工使用的工具
        Optional<Tool> gitLabTool = toolRegistry.get("gitlab");
        Optional<Tool> openProjectTool = toolRegistry.get("jira"); // OpenProjectTool 对外暴露为 jira
        Optional<Tool> jenkinsTool = toolRegistry.get("jenkins");

        // 如果这些工具存在，验证它们不包含 admin capability
        gitLabTool.ifPresent(tool -> {
            assertFalse(tool.getCapabilities().contains("admin"),
                "员工使用的 GitLabTool 不应包含 admin capability");
            assertNotEquals("admin_management", tool.getDepartment(),
                "员工使用的 GitLabTool 部门不应为 admin_management");
        });

        openProjectTool.ifPresent(tool -> {
            assertFalse(tool.getCapabilities().contains("admin"),
                "员工使用的 OpenProjectTool 不应包含 admin capability");
            assertNotEquals("admin_management", tool.getDepartment(),
                "员工使用的 OpenProjectTool 部门不应为 admin_management");
        });

        jenkinsTool.ifPresent(tool -> {
            assertFalse(tool.getCapabilities().contains("admin"),
                "员工使用的 JenkinsTool 不应包含 admin capability");
            assertNotEquals("admin_management", tool.getDepartment(),
                "员工使用的 JenkinsTool 部门不应为 admin_management");
        });
    }
}