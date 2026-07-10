package com.livingagent.core.config;

import com.livingagent.core.admin.ServiceAdminBootstrap;
import com.livingagent.core.admin.impl.DefaultServiceAdminBootstrap;
import com.livingagent.core.diagnosis.feedback.ServiceBootstrapHealthTracker;
import com.livingagent.core.tool.ToolRegistry;
import com.livingagent.core.tool.impl.admin.GitLabAdminTool;
import com.livingagent.core.tool.impl.admin.JenkinsAdminTool;
import com.livingagent.core.tool.impl.admin.OpenProjectAdminTool;
import com.livingagent.core.database.repository.EmployeeExternalAccountRepository;
import com.livingagent.core.database.repository.ServiceAdminBootstrapStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 服务管理配置
 * <p>配置 GitLabAdminTool/OpenProjectAdminTool/JenkinsAdminTool 并注册到 ToolRegistry。
 * <p>通过 spring.boot.service-admin.enabled 控制是否启用，默认不启用。
 * <p>管理类工具部门为 "admin_management"，MainBrain 可访问，其他大脑不可访问。
 * <p>关联文档：docs/core/MAINBRAIN_ADMIN_BRIDGE_PLAN.md
 */
@Configuration
@ConditionalOnProperty(name = "service-admin.enabled", havingValue = "true", matchIfMissing = false)
public class AdminConfig {

    private static final Logger log = LoggerFactory.getLogger(AdminConfig.class);

    @Bean
    public GitLabAdminTool gitLabAdminTool(
            ToolRegistry toolRegistry,
            EmployeeExternalAccountRepository accountRepository,
            @Value("${tool.gitlab.base-url:}") String gitlabBaseUrl,
            @Value("${tool.gitlab.access-token:}") String gitlabAccessToken) {
        GitLabAdminTool tool = new GitLabAdminTool(accountRepository);
        tool.configure(gitlabBaseUrl, gitlabAccessToken);

        // 注册到 ToolRegistry
        toolRegistry.register(tool);
        log.info("GitLabAdminTool registered to ToolRegistry: baseUrl={}", gitlabBaseUrl);
        return tool;
    }

    @Bean
    public OpenProjectAdminTool openProjectAdminTool(
            ToolRegistry toolRegistry,
            EmployeeExternalAccountRepository accountRepository,
            @Value("${tool.openproject.base-url:}") String openprojectBaseUrl,
            @Value("${tool.openproject.api-token:}") String openprojectApiToken) {
        OpenProjectAdminTool tool = new OpenProjectAdminTool(accountRepository);
        tool.configure(openprojectBaseUrl, openprojectApiToken);

        // 注册到 ToolRegistry
        toolRegistry.register(tool);
        log.info("OpenProjectAdminTool registered to ToolRegistry: baseUrl={}", openprojectBaseUrl);
        return tool;
    }

    @Bean
    public JenkinsAdminTool jenkinsAdminTool(
            ToolRegistry toolRegistry,
            EmployeeExternalAccountRepository accountRepository,
            @Value("${tool.jenkins.base-url:}") String jenkinsBaseUrl,
            @Value("${tool.jenkins.username:}") String jenkinsUsername,
            @Value("${tool.jenkins.api-token:}") String jenkinsApiToken) {
        JenkinsAdminTool tool = new JenkinsAdminTool(accountRepository);
        tool.configure(jenkinsBaseUrl, jenkinsUsername, jenkinsApiToken);

        // 注册到 ToolRegistry
        toolRegistry.register(tool);
        log.info("JenkinsAdminTool registered to ToolRegistry: baseUrl={}", jenkinsBaseUrl);
        return tool;
    }

    @Bean
    public ServiceAdminBootstrap serviceAdminBootstrap(
            ToolRegistry toolRegistry,
            ServiceAdminBootstrapStateRepository stateRepository,
            ServiceBootstrapHealthTracker healthTracker) {
        log.info("ServiceAdminBootstrap initialized (enabled, using ToolRegistry)");
        return new DefaultServiceAdminBootstrap(toolRegistry, stateRepository, healthTracker);
    }
}
