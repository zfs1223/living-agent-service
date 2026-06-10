package com.livingagent.core.config;

import com.livingagent.core.security.bash.BashSecurityValidator;
import com.livingagent.core.employee.claim.TaskClaimService;
import com.livingagent.core.planner.dag.TaskDagService;
import com.livingagent.core.sandbox.ClaudeCliProperties;
import com.livingagent.core.sandbox.ClaudeExecutionGateway;
import com.livingagent.core.sandbox.SandboxService;
import com.livingagent.core.sandbox.TraeExecutionGateway;
import com.livingagent.core.sandbox.impl.DockerSandboxService;
import com.livingagent.core.sandbox.impl.HybridSandboxService;
import com.livingagent.core.security.impl.SandboxExecutorImpl;
import com.livingagent.core.skill.SkillRegistry;
import com.livingagent.core.tool.Tool;
import com.livingagent.core.tool.ToolRegistry;
import com.livingagent.core.tool.impl.BrowserAutomationTool;
import com.livingagent.core.tool.impl.BuildTool;
import com.livingagent.core.tool.impl.FileEditTool;
import com.livingagent.core.tool.impl.BudgetManagementTool;
import com.livingagent.core.tool.impl.DockerTool;
import com.livingagent.core.tool.impl.GitHubTool;
import com.livingagent.core.tool.impl.HuggingFaceTool;
import com.livingagent.core.tool.impl.InvoiceProcessingTool;
import com.livingagent.core.tool.impl.KnowledgeGraphTool;
import com.livingagent.core.tool.impl.NotionTool;
import com.livingagent.core.tool.impl.OfficeTool;
import com.livingagent.core.tool.impl.PdfTool;
import com.livingagent.core.tool.impl.PlaywrightCrawlerTool;
import com.livingagent.core.tool.impl.ProactiveAgentTool;
import com.livingagent.core.tool.impl.RssReaderTool;
import com.livingagent.core.tool.impl.SearXNGTool;
import com.livingagent.core.tool.impl.SelfImprovingTool;
import com.livingagent.core.tool.impl.SlackTool;
import com.livingagent.core.tool.impl.SkillFinderTool;
import com.livingagent.core.tool.impl.SummarizeTool;
import com.livingagent.core.tool.impl.TavilySearchTool;
import com.livingagent.core.tool.impl.ToolRegistryImpl;
import com.livingagent.core.tool.impl.TraeTool;
import com.livingagent.core.tool.impl.ClaudeCliTool;
import com.livingagent.core.tool.impl.WeatherTool;
import com.livingagent.core.tool.impl.WindowsAppTool;
import com.livingagent.core.tool.impl.enterprise.JiraTool;
import com.livingagent.core.tool.impl.enterprise.GitLabTool;
import com.livingagent.core.tool.impl.enterprise.JenkinsTool;
import com.livingagent.core.tool.impl.enterprise.OpenProjectTool;
import com.livingagent.core.security.ApprovalManager;
import com.livingagent.core.database.repository.WindowsAutomationNodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class ToolConfig {

    private static final Logger log = LoggerFactory.getLogger(ToolConfig.class);

    @Bean
    public ToolRegistry toolRegistry(SkillRegistry skillRegistry,
                                     TraeExecutionGateway traeExecutionGateway,
                                     ClaudeExecutionGateway claudeExecutionGateway,
                                     WindowsAutomationNodeRepository nodeRepository,
                                     ApprovalManager approvalManager,
                                     @Value("${tool.claude-cli.enabled:false}") boolean claudeCliEnabled,
                                     @Value("${tavily.api.key:}") String tavilyApiKey,
                                     @Value("${notion.api.key:}") String notionApiKey,
                                     @Value("${slack.bot.token:}") String slackBotToken,
                                     @Value("${summarize.api.key:}") String summarizeApiKey,
                                     @Value("${weather.qweather.key:}") String qweatherKey,
                                     @Value("${weather.openweathermap.key:}") String openweathermapKey,
                                     @Value("${tool.jira.base-url:}") String jiraBaseUrl,
                                     @Value("${tool.jira.email:}") String jiraEmail,
                                     @Value("${tool.jira.api-token:}") String jiraApiToken,
                                     @Value("${tool.openproject.base-url:}") String openprojectBaseUrl,
                                     @Value("${tool.openproject.api-token:}") String openprojectApiToken,
                                     @Value("${tool.gitlab.base-url:}") String gitlabBaseUrl,
                                     @Value("${tool.gitlab.access-token:}") String gitlabAccessToken,
                                     @Value("${tool.jenkins.base-url:}") String jenkinsBaseUrl,
                                     @Value("${tool.jenkins.username:}") String jenkinsUsername,
                                     @Value("${tool.jenkins.api-token:}") String jenkinsApiToken,
                                     @Value("${tool.gitlab.employee-accounts.T01:}") String gitlabTokenT01,
                                     @Value("${tool.gitlab.employee-accounts.T02:}") String gitlabTokenT02,
                                     @Value("${tool.gitlab.employee-accounts.T03:}") String gitlabTokenT03,
                                     @Value("${tool.gitlab.employee-accounts.T09:}") String gitlabTokenT09,
                                     @Value("${tool.gitlab.employee-accounts.T10:}") String gitlabTokenT10) {
        log.info("Initializing ToolRegistry");
        ToolRegistryImpl registry = new ToolRegistryImpl();

        // ========== 核心工具 (每个神经元必备，完全免费) ==========
        try {
            registry.register(new PlaywrightCrawlerTool());
            log.info("Registered core tool: playwright_crawler");
        } catch (Exception e) {
            log.warn("PlaywrightCrawlerTool not available: {}. Skipping registration.", e.getMessage());
        }
        registry.register(new RssReaderTool());
        registry.register(new ProactiveAgentTool());
        registry.register(new SkillFinderTool(skillRegistry));
        log.info("Registered core tools: rss_reader, proactive_agent, find_skills");

        // ========== 免费搜索工具 (替代付费Tavily) ==========
        registry.register(new SearXNGTool());
        log.info("Registered free search tool: searxng");

        // 付费搜索工具 (可选)
        if (tavilyApiKey != null && !tavilyApiKey.isEmpty()) {
            registry.register(new TavilySearchTool(tavilyApiKey));
            log.info("Registered premium search tool: tavily_search");
        }

        // ========== 免费本地工具 ==========
        registry.register(new WeatherTool()
                .withQWeather(qweatherKey)
                .withOpenWeatherMap(openweathermapKey));
        log.info("Registered weather tool (free tier available)");

        registry.register(new GitHubTool());
        log.info("Registered github tool (requires gh CLI)");

        registry.register(new DockerTool());
        log.info("Registered docker tool (requires Docker CLI)");

        registry.register(new HuggingFaceTool());
        log.info("Registered huggingface tool (requires hf CLI)");

        registry.register(new PdfTool());
        log.info("Registered pdf tool (requires Python pypdf/pdfplumber)");

        registry.register(new OfficeTool());
        log.info("Registered office tool (requires Python python-docx/openpyxl/python-pptx)");

        // ========== 云服务工具 (需API Key) ==========
        if (notionApiKey != null && !notionApiKey.isEmpty()) {
            registry.register(new NotionTool(notionApiKey));
            log.info("Registered notion tool");
        } else {
            registry.register(new NotionTool());
            log.info("Registered notion tool (no API key configured)");
        }

        if (slackBotToken != null && !slackBotToken.isEmpty()) {
            registry.register(new SlackTool(slackBotToken));
            log.info("Registered slack tool");
        } else {
            registry.register(new SlackTool());
            log.info("Registered slack tool (no token configured)");
        }

        if (summarizeApiKey != null && !summarizeApiKey.isEmpty()) {
            registry.register(new SummarizeTool(summarizeApiKey));
            log.info("Registered summarize tool");
        } else {
            registry.register(new SummarizeTool());
            log.info("Registered summarize tool (no API key configured)");
        }

        // ========== 核心技能工具 ==========
        registry.register(new KnowledgeGraphTool());
        log.info("Registered knowledge_graph tool");

        registry.register(new SelfImprovingTool());
        log.info("Registered self_improving tool");

        // ========== 财务部工具 ==========
        registry.register(new BudgetManagementTool());
        log.info("Registered budget_management tool");

        registry.register(new InvoiceProcessingTool());
        log.info("Registered invoice_processing tool");

        registry.register(new WindowsAppTool());
        log.info("Registered windows_app_automation tool (pywinauto bridge)");

        // 注入 NodeRepository 以支持从数据库动态加载节点
        try {
            var toolOpt = registry.get("windows_app_automation");
            if (toolOpt.isPresent() && toolOpt.get() instanceof WindowsAppTool windowsAppTool) {
                windowsAppTool.setNodeRepository(nodeRepository);
                log.info("WindowsAppTool: NodeRepository injected, loading nodes from database");
            }
        } catch (Exception e) {
            log.warn("WindowsAppTool: NodeRepository injection skipped: {}", e.getMessage());
        }

        // 注入 ApprovalManager 以支持高风险操作审批
        try {
            var toolOpt = registry.get("windows_app_automation");
            if (toolOpt.isPresent() && toolOpt.get() instanceof WindowsAppTool windowsAppTool) {
                windowsAppTool.setApprovalManager(approvalManager);
                log.info("WindowsAppTool: ApprovalManager injected, high-risk operations will be approved");
            }
        } catch (Exception e) {
            log.warn("WindowsAppTool: ApprovalManager injection skipped: {}", e.getMessage());
        }

        // ========== 技术部工具 ==========
        registry.register(new BrowserAutomationTool());
        log.info("Registered browser_automation tool");

        registry.register(new FileEditTool());
        log.info("Registered file_edit tool (workspace source code access)");

        registry.register(new BuildTool());
        log.info("Registered build tool (compile/build/restart)");

        registry.register(new TraeTool(traeExecutionGateway));
        log.info("Registered trae tool (via TraeExecutionGateway)");

        if (claudeCliEnabled) {
            registry.register(new ClaudeCliTool(claudeExecutionGateway));
            log.info("Registered claude_cli tool (via ClaudeExecutionGateway)");
        } else {
            log.info("Claude CLI tool disabled (tool.claude-cli.enabled=false)");
        }

        // ========== 企业工具 (Jira/GitLab/Jenkins，需配置凭据) ==========
        // ========== 项目管理工具 (OpenProject 优先，Jira 备选，二选一) ==========
        if (openprojectBaseUrl != null && !openprojectBaseUrl.isEmpty()) {
            registry.register(new OpenProjectTool(openprojectBaseUrl,
                openprojectApiToken != null ? openprojectApiToken : ""));
            log.info("Registered jira tool via OpenProject (baseUrl={}, auth={})", openprojectBaseUrl,
                openprojectApiToken != null && !openprojectApiToken.isEmpty() ? "enabled" : "anonymous");
        } else if (jiraBaseUrl != null && !jiraBaseUrl.isEmpty()) {
            registry.register(new JiraTool(jiraBaseUrl,
                jiraEmail != null ? jiraEmail : "",
                jiraApiToken != null ? jiraApiToken : ""));
            log.info("Registered jira tool via Jira (baseUrl={}, auth={})", jiraBaseUrl,
                jiraEmail != null && !jiraEmail.isEmpty() ? "enabled" : "anonymous");
        } else {
            log.info("Jira/OpenProject tool not registered (missing base-url)");
        }

        if (gitlabBaseUrl != null && !gitlabBaseUrl.isEmpty()) {
            GitLabTool gitLabTool = new GitLabTool();
            gitLabTool.configure(gitlabBaseUrl,
                gitlabAccessToken != null ? gitlabAccessToken : "");

            Map<String, String> gitlabEmployeeAccounts = new java.util.LinkedHashMap<>();
            if (gitlabTokenT01 != null && !gitlabTokenT01.isEmpty()) gitlabEmployeeAccounts.put("T01", gitlabTokenT01);
            if (gitlabTokenT02 != null && !gitlabTokenT02.isEmpty()) gitlabEmployeeAccounts.put("T02", gitlabTokenT02);
            if (gitlabTokenT03 != null && !gitlabTokenT03.isEmpty()) gitlabEmployeeAccounts.put("T03", gitlabTokenT03);
            if (gitlabTokenT09 != null && !gitlabTokenT09.isEmpty()) gitlabEmployeeAccounts.put("T09", gitlabTokenT09);
            if (gitlabTokenT10 != null && !gitlabTokenT10.isEmpty()) gitlabEmployeeAccounts.put("T10", gitlabTokenT10);
            gitLabTool.setEmployeeAccounts(gitlabEmployeeAccounts);

            registry.register(gitLabTool);
            log.info("Registered gitlab tool (baseUrl={}, auth={}, employeeAccounts={})", gitlabBaseUrl,
                gitlabAccessToken != null && !gitlabAccessToken.isEmpty() ? "enabled" : "anonymous",
                gitlabEmployeeAccounts.size());
        } else {
            log.info("GitLab tool not registered (missing tool.gitlab.base-url)");
        }

        if (jenkinsBaseUrl != null && !jenkinsBaseUrl.isEmpty()) {
            registry.register(new JenkinsTool(jenkinsBaseUrl,
                jenkinsUsername != null ? jenkinsUsername : "",
                jenkinsApiToken != null ? jenkinsApiToken : ""));
            log.info("Registered jenkins tool (baseUrl={}, auth={})", jenkinsBaseUrl,
                jenkinsUsername != null && !jenkinsUsername.isEmpty() ? "enabled" : "anonymous");
        } else {
            log.info("Jenkins tool not registered (missing tool.jenkins.base-url)");
        }

        log.info("ToolRegistry initialized with {} tools", registry.getAll().size());
        return registry;
    }

    @Bean
    public BashSecurityValidator bashSecurityValidator() {
        log.info("Initializing BashSecurityValidator");
        return new BashSecurityValidator();
    }

    @Bean
    public SandboxService sandboxService(SandboxExecutorImpl sandboxExecutorImpl) {
        log.info("Initializing Hybrid SandboxService (local + docker fallback)");

        DockerSandboxService dockerSandboxService;
        try {
            com.github.dockerjava.core.DefaultDockerClientConfig dockerConfig =
                com.github.dockerjava.core.DefaultDockerClientConfig.createDefaultConfigBuilder().build();
            com.github.dockerjava.api.DockerClient dockerClient =
                com.github.dockerjava.core.DockerClientImpl.getInstance(dockerConfig);
            dockerSandboxService = new DockerSandboxService(dockerClient);
        } catch (Exception e) {
            log.warn("Docker sandbox init failed, fallback to local sandbox only: {}", e.getMessage());
            dockerSandboxService = null;
        }

        return new HybridSandboxService(sandboxExecutorImpl, dockerSandboxService);
    }

    @Bean
    public TraeExecutionGateway traeExecutionGateway(SandboxService sandboxService) {
        log.info("Initializing TraeExecutionGateway");
        return new TraeExecutionGateway(sandboxService);
    }

    @Bean
    public ClaudeExecutionGateway claudeExecutionGateway(SandboxService sandboxService, ClaudeCliProperties claudeCliProperties) {
        log.info("Initializing ClaudeExecutionGateway");
        return new ClaudeExecutionGateway(sandboxService, claudeCliProperties);
    }

    @Bean
    public TaskClaimService taskClaimService(TaskDagService taskDagService) {
        log.info("Initializing TaskClaimService");
        return new TaskClaimService(taskDagService);
    }
}
