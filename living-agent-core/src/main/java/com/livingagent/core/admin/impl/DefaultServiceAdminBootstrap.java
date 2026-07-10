package com.livingagent.core.admin.impl;

import com.livingagent.core.admin.AdminOperationResult;
import com.livingagent.core.admin.ServiceAdminBootstrap;
import com.livingagent.core.database.entity.ServiceAdminBootstrapStateEntity;
import com.livingagent.core.database.repository.ServiceAdminBootstrapStateRepository;
import com.livingagent.core.diagnosis.feedback.ServiceBootstrapHealthTracker;
import com.livingagent.core.tool.Tool;
import com.livingagent.core.tool.ToolRegistry;
import com.livingagent.core.tool.Tool.ToolParams;
import com.livingagent.core.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 默认服务初始化实现
 * <p>通过 ToolRegistry 调用 GitLabAdminTool/OpenProjectAdminTool/JenkinsAdminTool 完成全量初始化。
 * <p>幂等设计：通过 service_admin_bootstrap_state 表记录每一步状态，已成功的步骤跳过，失败的步骤重试。
 * <p>关联文档：docs/core/MAINBRAIN_ADMIN_BRIDGE_PLAN.md
 */
public class DefaultServiceAdminBootstrap implements ServiceAdminBootstrap {

    private static final Logger log = LoggerFactory.getLogger(DefaultServiceAdminBootstrap.class);

    private final ToolRegistry toolRegistry;
    private final ServiceAdminBootstrapStateRepository stateRepository;
    private final ServiceBootstrapHealthTracker healthTracker;

    public DefaultServiceAdminBootstrap(ToolRegistry toolRegistry,
                                        ServiceAdminBootstrapStateRepository stateRepository,
                                        ServiceBootstrapHealthTracker healthTracker) {
        this.toolRegistry = toolRegistry;
        this.stateRepository = stateRepository;
        this.healthTracker = healthTracker;
    }

    @Override
    public BootstrapResult bootstrapAll() {
        log.info("ServiceAdminBootstrap: starting full initialization (using ToolRegistry)");
        healthTracker.recordBootstrapStart("all");
        long startTime = System.currentTimeMillis();
        List<BootstrapResult> results = new ArrayList<>();
        results.add(bootstrapService("gitlab"));
        results.add(bootstrapService("openproject"));
        results.add(bootstrapService("jenkins"));

        int totalSteps = results.stream().mapToInt(BootstrapResult::totalSteps).sum();
        int successSteps = results.stream().mapToInt(BootstrapResult::successSteps).sum();
        int skippedSteps = results.stream().mapToInt(BootstrapResult::skippedSteps).sum();
        int failedSteps = results.stream().mapToInt(BootstrapResult::failedSteps).sum();
        boolean allSuccess = results.stream().allMatch(BootstrapResult::success);

        String summary = String.format("All services: total=%d, success=%d, skipped=%d, failed=%d",
            totalSteps, successSteps, skippedSteps, failedSteps);
        log.info("ServiceAdminBootstrap: {}", summary);

        if (allSuccess) {
            healthTracker.recordBootstrapComplete("all", System.currentTimeMillis() - startTime);
        } else {
            healthTracker.recordBootstrapFailure("all", summary);
        }

        return new BootstrapResult(allSuccess, "all", totalSteps, successSteps, skippedSteps, failedSteps, summary);
    }

    @Override
    public BootstrapResult bootstrapService(String serviceType) {
        log.info("ServiceAdminBootstrap: initializing service {} (via ToolRegistry)", serviceType);
        healthTracker.recordBootstrapStart(serviceType);
        long startTime = System.currentTimeMillis();
        List<AdminOperationResult> stepResults = new ArrayList<>();

        switch (serviceType) {
            case "gitlab" -> stepResults.addAll(bootstrapGitLab());
            case "openproject" -> stepResults.addAll(bootstrapOpenProject());
            case "jenkins" -> stepResults.addAll(bootstrapJenkins());
            default -> {
                log.warn("ServiceAdminBootstrap: unknown service type {}", serviceType);
                return BootstrapResult.partial(serviceType, 0, 0, 0, 1, "Unknown service type: " + serviceType);
            }
        }

        int total = stepResults.size();
        int success = (int) stepResults.stream().filter(AdminOperationResult::success).count();
        int skipped = (int) stepResults.stream()
            .filter(r -> r.success() && r.detail() != null && r.detail().contains("SKIPPED"))
            .count();
        int failed = total - success;

        String summary = String.format("%s: total=%d, success=%d, skipped=%d, failed=%d",
            serviceType, total, success, skipped, failed);
        log.info("ServiceAdminBootstrap: {}", summary);

        if (failed == 0) {
            healthTracker.recordBootstrapComplete(serviceType, System.currentTimeMillis() - startTime);
        } else {
            healthTracker.recordBootstrapFailure(serviceType, summary);
        }

        return failed == 0
            ? BootstrapResult.success(serviceType, total, success, skipped, summary)
            : BootstrapResult.partial(serviceType, total, success, skipped, failed, summary);
    }

    @Override
    public boolean isServiceInitialized(String serviceType) {
        List<ServiceAdminBootstrapStateEntity> states = stateRepository.findByServiceType(serviceType);
        if (states.isEmpty()) {
            return false;
        }
        return states.stream().allMatch(s -> "SUCCESS".equals(s.getStatus()));
    }

    // ==================== GitLab 初始化 ====================

    private List<AdminOperationResult> bootstrapGitLab() {
        List<AdminOperationResult> results = new ArrayList<>();
        Optional<Tool> gitLabAdminTool = toolRegistry.get("gitlab_admin");

        if (gitLabAdminTool.isEmpty()) {
            log.warn("GitLabAdminTool not found in ToolRegistry, skipping GitLab initialization");
            results.add(AdminOperationResult.failure("gitlab_bootstrap", "Tool not found", "gitlab_admin not registered"));
            return results;
        }

        Tool tool = gitLabAdminTool.get();

        // Step 1: 创建部门 Group（示例：技术部）
        results.add(executeToolStep(tool, "gitlab", "create_group_tech",
            ToolParams.of(Map.of(
                "action", "create_group",
                "group_name", "技术部",
                "group_path", "tech",
                "description", "技术部 Group"
            ))));

        // Step 2: 在技术部 Group 下创建项目
        results.add(executeToolStep(tool, "gitlab", "create_project_living_agent_service",
            ToolParams.of(Map.of(
                "action", "create_project",
                "project_name", "living-agent-service",
                "namespace_id", 1, // 假设 tech Group ID 为 1
                "description", "Living Agent Service"
            ))));

        // Step 3: 创建数字员工账号（示例：T01）
        results.add(executeToolStep(tool, "gitlab", "create_user_t01",
            ToolParams.of(Map.of(
                "action", "create_user",
                "email", "t01-bot@living-agent.local",
                "username", "t01-code-reviewer-bot",
                "name", "T01-代码审查员",
                "employee_code", "T01"
            ))));

        return results;
    }

    // ==================== OpenProject 初始化 ====================

    private List<AdminOperationResult> bootstrapOpenProject() {
        List<AdminOperationResult> results = new ArrayList<>();
        Optional<Tool> openProjectAdminTool = toolRegistry.get("openproject_admin");

        if (openProjectAdminTool.isEmpty()) {
            log.warn("OpenProjectAdminTool not found in ToolRegistry, skipping OpenProject initialization");
            results.add(AdminOperationResult.failure("openproject_bootstrap", "Tool not found", "openproject_admin not registered"));
            return results;
        }

        Tool tool = openProjectAdminTool.get();

        // Step 1: 创建自定义角色
        results.add(executeToolStep(tool, "openproject", "create_role_worker",
            ToolParams.of(Map.of(
                "action", "create_role",
                "role_name", "Digital Employee - Worker",
                "permissions", "view_work_packages,edit_work_packages,add_work_package_notes,log_time,view_members,view_wiki_pages"
            ))));

        results.add(executeToolStep(tool, "openproject", "create_role_manager",
            ToolParams.of(Map.of(
                "action", "create_role",
                "role_name", "Digital Employee - Manager",
                "permissions", "view_work_packages,edit_work_packages,add_work_package_notes,manage_work_package_relations,manage_members,log_time,view_time_entries,view_wiki_pages,edit_wiki_pages"
            ))));

        // Step 2: 创建部门项目
        results.add(executeToolStep(tool, "openproject", "create_project_tech",
            ToolParams.of(Map.of(
                "action", "create_project",
                "project_identifier", "tech",
                "project_name", "技术部项目",
                "project_description", "技术部工作包管理"
            ))));

        return results;
    }

    // ==================== Jenkins 初始化 ====================

    private List<AdminOperationResult> bootstrapJenkins() {
        List<AdminOperationResult> results = new ArrayList<>();
        Optional<Tool> jenkinsAdminTool = toolRegistry.get("jenkins_admin");

        if (jenkinsAdminTool.isEmpty()) {
            log.warn("JenkinsAdminTool not found in ToolRegistry, skipping Jenkins initialization");
            results.add(AdminOperationResult.failure("jenkins_bootstrap", "Tool not found", "jenkins_admin not registered"));
            return results;
        }

        Tool tool = jenkinsAdminTool.get();

        // Step 1: 创建 Pipeline Job（示例）
        results.add(executeToolStep(tool, "jenkins", "create_job_living_agent_build",
            ToolParams.of(Map.of(
                "action", "create_job",
                "job_name", "living-agent-build",
                "pipeline_script", "pipeline {\n  agent any\n  stages {\n    stage('Build') {\n      steps {\n        echo 'Building...'\n      }\n    }\n  }\n}"
            ))));

        // Step 2: 创建 Credential（示例）
        results.add(executeToolStep(tool, "jenkins", "create_credential_gitlab_token",
            ToolParams.of(Map.of(
                "action", "create_credential",
                "credential_id", "gitlab-api-token",
                "credential_description", "GitLab API Token",
                "credential_username", "gitlab-bot",
                "credential_secret", "placeholder-token"
            ))));

        return results;
    }

    // ==================== 幂等执行辅助方法 ====================

    /**
     * 幂等执行工具步骤
     */
    private AdminOperationResult executeToolStep(Tool tool, String serviceType, String stepName, ToolParams params) {
        // 检查是否已成功执行
        Optional<ServiceAdminBootstrapStateEntity> existing = stateRepository.findByServiceTypeAndStepName(serviceType, stepName);
        if (existing.isPresent() && "SUCCESS".equals(existing.get().getStatus())) {
            log.info("Step {} already succeeded, skipping", stepName);
            return AdminOperationResult.skipped(stepName, existing.get().getId().toString(), "Step already succeeded");
        }

        // 执行步骤
        try {
            ToolResult result = tool.execute(params, null);

            // 记录状态
            ServiceAdminBootstrapStateEntity state = new ServiceAdminBootstrapStateEntity();
            state.setServiceType(serviceType);
            state.setStepName(stepName);
            state.setStatus(result.success() ? "SUCCESS" : "FAILED");
            state.setDetail(result.success() ? "OK" : result.error());
            state.setCreatedAt(Instant.now());
            state.setUpdatedAt(Instant.now());
            stateRepository.save(state);

            if (result.success()) {
                log.info("Step {} executed successfully", stepName);
                return AdminOperationResult.success(stepName, result.data() != null ? result.data().toString() : "OK", "Step executed");
            } else {
                log.error("Step {} failed: {}", stepName, result.error());
                return AdminOperationResult.failure(stepName, "Step failed", result.error());
            }
        } catch (Exception e) {
            log.error("Step {} execution exception", stepName, e);

            // 记录失败状态
            ServiceAdminBootstrapStateEntity state = new ServiceAdminBootstrapStateEntity();
            state.setServiceType(serviceType);
            state.setStepName(stepName);
            state.setStatus("FAILED");
            state.setDetail(e.getMessage());
            state.setCreatedAt(Instant.now());
            state.setUpdatedAt(Instant.now());
            stateRepository.save(state);

            return AdminOperationResult.failure(stepName, "Exception", e.getMessage());
        }
    }
}