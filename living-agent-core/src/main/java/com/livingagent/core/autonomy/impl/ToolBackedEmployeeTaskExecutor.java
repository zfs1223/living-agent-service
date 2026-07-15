package com.livingagent.core.autonomy.impl;

import com.livingagent.core.autonomy.EmployeeTaskExecutor;
import com.livingagent.core.autonomy.EmployeeWorkAssignment;
import com.livingagent.core.autonomy.ExecutionCapability;
import com.livingagent.core.autonomy.ExecutionCapabilityRequest;
import com.livingagent.core.autonomy.ExecutionCapabilityResolution;
import com.livingagent.core.autonomy.ExecutionCapabilityResolver;
import com.livingagent.core.autonomy.TaskMetadataKeys;
import com.livingagent.core.employee.registry.FixedEmployeeRegistry;
import com.livingagent.core.model.pool.BrainModelResolver;
import com.livingagent.core.model.pool.ResolvedBrainModel;
import com.livingagent.core.provider.impl.ResolvedBrainModelProvider;
import com.livingagent.core.sandbox.SandboxService;
import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.security.SandboxExecutor.ExecutionEnvironment;
import com.livingagent.core.tool.Tool;
import com.livingagent.core.tool.ToolContext;
import com.livingagent.core.tool.Tool.ToolParams;
import com.livingagent.core.tool.ToolRegistry;
import com.livingagent.core.tool.ToolResult;
import com.livingagent.core.tool.backend.BackendRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 工具驱动的员工任务执行器
 * 用于阶段7：按任务类型调用真实工具/沙箱/文件系统
 * 
 * 当前版本：支持 web_prototype/web_development 类型的文件生成（调用LLM生成代码），以及 Docker 沙箱执行
 * 后续可扩展到 software_development/document_generation/data_analysis 等类型
 */
public class ToolBackedEmployeeTaskExecutor implements EmployeeTaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolBackedEmployeeTaskExecutor.class);

    private static final String ARTIFACTS_DIR = System.getProperty("livingagent.artifact.dir", "data/artifacts");
    private static final int LLM_TIMEOUT_SECONDS = 120;

    private static final Map<String, String> DEPARTMENT_TO_BRAIN_ID = Map.ofEntries(
        Map.entry("tech", "TechBrain"),
        Map.entry("hr", "HrBrain"),
        Map.entry("finance", "FinanceBrain"),
        Map.entry("sales", "SalesBrain"),
        Map.entry("cs", "CsBrain"),
        Map.entry("admin", "AdminBrain"),
        Map.entry("legal", "LegalBrain"),
        Map.entry("ops", "OpsBrain"),
        Map.entry("core", "MainBrain")
    );

    private final SandboxService sandboxService;
    private final BrainModelResolver brainModelResolver;
    private final ExecutionCapabilityResolver capabilityResolver;
    private final FixedEmployeeRegistry fixedEmployeeRegistry;
    private final ToolRegistry toolRegistry;
    private final BackendRegistry backendRegistry;  // 64-C-1
    private final ActionOutputValidator outputValidator;  // 64-C-1

    public ToolBackedEmployeeTaskExecutor() {
        this.sandboxService = null;
        this.brainModelResolver = null;
        this.capabilityResolver = null;
        this.fixedEmployeeRegistry = null;
        this.toolRegistry = null;
        this.backendRegistry = null;
        this.outputValidator = null;
    }

    public ToolBackedEmployeeTaskExecutor(SandboxService sandboxService) {
        this.sandboxService = sandboxService;
        this.brainModelResolver = null;
        this.capabilityResolver = null;
        this.fixedEmployeeRegistry = null;
        this.toolRegistry = null;
        this.backendRegistry = null;
        this.outputValidator = null;
    }

    public ToolBackedEmployeeTaskExecutor(SandboxService sandboxService, BrainModelResolver brainModelResolver) {
        this.sandboxService = sandboxService;
        this.brainModelResolver = brainModelResolver;
        this.capabilityResolver = null;
        this.fixedEmployeeRegistry = null;
        this.toolRegistry = null;
        this.backendRegistry = null;
        this.outputValidator = null;
    }

    public ToolBackedEmployeeTaskExecutor(SandboxService sandboxService, BrainModelResolver brainModelResolver, ExecutionCapabilityResolver capabilityResolver) {
        this.sandboxService = sandboxService;
        this.brainModelResolver = brainModelResolver;
        this.capabilityResolver = capabilityResolver;
        this.fixedEmployeeRegistry = null;
        this.toolRegistry = null;
        this.backendRegistry = null;
        this.outputValidator = null;
    }

    public ToolBackedEmployeeTaskExecutor(SandboxService sandboxService, BrainModelResolver brainModelResolver,
                                          ExecutionCapabilityResolver capabilityResolver,
                                          FixedEmployeeRegistry fixedEmployeeRegistry, ToolRegistry toolRegistry) {
        this.sandboxService = sandboxService;
        this.brainModelResolver = brainModelResolver;
        this.capabilityResolver = capabilityResolver;
        this.fixedEmployeeRegistry = fixedEmployeeRegistry;
        this.toolRegistry = toolRegistry;
        this.backendRegistry = null;
        this.outputValidator = null;
    }

    /** 64-C-1: 完整构造函数，包含 BackendRegistry 和 ActionOutputValidator */
    public ToolBackedEmployeeTaskExecutor(SandboxService sandboxService, BrainModelResolver brainModelResolver,
                                          ExecutionCapabilityResolver capabilityResolver,
                                          FixedEmployeeRegistry fixedEmployeeRegistry, ToolRegistry toolRegistry,
                                          BackendRegistry backendRegistry, ActionOutputValidator outputValidator) {
        this.sandboxService = sandboxService;
        this.brainModelResolver = brainModelResolver;
        this.capabilityResolver = capabilityResolver;
        this.fixedEmployeeRegistry = fixedEmployeeRegistry;
        this.toolRegistry = toolRegistry;
        this.backendRegistry = backendRegistry;
        this.outputValidator = outputValidator;
    }

    @Override
    public ExecutionResult executeTask(
            String employeeCode,
            String taskType,
            String taskDescription,
            EmployeeWorkAssignment assignmentTask,
            List<String> availableTools,
            String executionEnvironment) {
        
        log.info("Executing task: employee={}, type={}, env={}, tools={}", 
            employeeCode, taskType, executionEnvironment, availableTools);

        if (availableTools != null) {
            for (String toolName : availableTools) {
                if (!isToolAllowed(toolName, employeeCode)) {
                    log.warn("Tool permission denied: employee={}, tool={}", employeeCode, toolName);
                    return new ExecutionResult(
                        false, "PERMISSION_DENIED",
                        "员工 " + employeeCode + " 无权使用工具: " + toolName,
                        List.of(), List.of(), Map.of("deniedTool", toolName),
                        "Tool permission check failed"
                    );
                }
            }
        }

        // 64-C-1: 行动准备度检查（工具健康 + 前置条件 + 输入完整性）
        if (backendRegistry != null && assignmentTask != null) {
            ActionReadinessChecker readinessChecker = new ActionReadinessChecker(backendRegistry);
            List<String> toolsToCheck = availableTools != null ? availableTools : List.of();
            ActionReadinessChecker.ReadinessResult readiness = readinessChecker.check(assignmentTask, toolsToCheck);
            if (readiness.isBlocked()) {
                log.warn("Task blocked by readiness check: employee={}, blockers={}", employeeCode, readiness.blockers());
                return new ExecutionResult(
                    false, "BLOCKED",
                    "行动准备度检查未通过: " + String.join("; ", readiness.blockers()),
                    List.of(), List.of(),
                    Map.of("readinessBlockers", readiness.blockers(), "readinessWarnings", readiness.warnings()),
                    "Action readiness check blocked"
                );
            }
            if (!readiness.warnings().isEmpty()) {
                log.info("Task readiness warnings: employee={}, warnings={}", employeeCode, readiness.warnings());
            }
        }

        String effectiveEnv = executionEnvironment;
        if (effectiveEnv == null || effectiveEnv.isBlank()) {
            effectiveEnv = determineEnvironment(taskType, 1).getCode();
            log.info("Auto-determined execution environment: employee={}, env={}", employeeCode, effectiveEnv);
        }

        try {
            // 优先使用 ExecutionCapabilityResolver 解析执行能力
            ExecutionCapability capability = resolveExecutionCapability(taskType, taskDescription, assignmentTask);

            if (capability != null) {
                log.info("Routing by executionCapability: employee={}, capability={}, rawType={}",
                    employeeCode, capability, taskType);
                return routeByCapability(capability, employeeCode, taskDescription, assignmentTask, availableTools, effectiveEnv);
            }

            // 兼容兜底：使用旧的 normalizeTaskType
            String normalizedTaskType = normalizeTaskType(taskType, taskDescription, assignmentTask);
            if (!normalizedTaskType.equals(taskType)) {
                log.info("Normalized task type: employee={}, rawType={}, normalizedType={}", employeeCode, taskType, normalizedTaskType);
            }
            ExecutionResult rawResult = switch (normalizedTaskType) {
                case "web_prototype", "web_development" ->
                    executeWebTask(employeeCode, taskDescription, assignmentTask, availableTools, effectiveEnv);
                case "document_generation" ->
                    executeDocumentTask(employeeCode, taskDescription, assignmentTask, availableTools, effectiveEnv);
                case "data_analysis" ->
                    executeDataAnalysisTask(employeeCode, taskDescription, assignmentTask, availableTools, effectiveEnv);
                case "legal_review", "finance_workflow" ->
                    executeReviewTask(employeeCode, taskDescription, assignmentTask, effectiveEnv);
                case "file_system_query" ->
                    executeToolTask(employeeCode, taskDescription, assignmentTask, availableTools, effectiveEnv);
                case "project_management", "issue_tracking" ->
                    executeProjectManagementTask(employeeCode, taskDescription, assignmentTask, availableTools, effectiveEnv);
                default ->
                    executeGenericTask(employeeCode, taskDescription, assignmentTask, availableTools, effectiveEnv);
            };
            // 64-C-1: 输出验证
            return validateAndWrapResult(rawResult, assignmentTask);
        } catch (Exception e) {
            log.error("Task execution failed: employee={}, type={}, env={}, error={}", 
                employeeCode, taskType, effectiveEnv, e.getMessage(), e);
            logExecutionAudit(employeeCode, taskType, effectiveEnv, "FAILED", e.getMessage());
            return new ExecutionResult(
                false, "FAILED", "任务执行失败: " + e.getMessage(),
                List.of(), List.of(), Map.of("executionEnv", effectiveEnv != null ? effectiveEnv : "unknown"), e.getMessage()
            );
        }
    }

    private boolean isToolAllowed(String toolName, String employeeCode) {
        // 无注册表时，降级为允许（保持向后兼容）
        if (fixedEmployeeRegistry == null || toolRegistry == null) {
            log.debug("Tool permission check skipped (no registry): employee={}, tool={}", employeeCode, toolName);
            return true;
        }

        // 1. 查找员工编制定义，获取其已授权工具列表
        var defOpt = fixedEmployeeRegistry.getDefinitionByCode(employeeCode);
        if (defOpt.isEmpty()) {
            // 非固定编制员工（如人类员工），检查工具的部门归属
            // 人类员工不经过此执行器的工具权限检查，降级为允许
            log.debug("Tool permission check: employee {} not in FixedEmployeeRegistry, allowing tool {}", employeeCode, toolName);
            return true;
        }

        var def = defOpt.get();

        // 2. 检查员工编制的工具列表是否包含该工具
        if (def.tools() != null && def.tools().contains(toolName)) {
            return true;
        }

        // 3. 检查工具别名映射
        for (String configuredTool : def.tools()) {
            if (configuredTool.equalsIgnoreCase(toolName)) {
                return true;
            }
        }

        // 4. 检查工具注册表中的工具部门是否与员工部门匹配
        Tool registeredTool = toolRegistry.get(toolName).orElse(null);
        if (registeredTool != null) {
            String toolDept = registeredTool.getDepartment();
            String empDept = def.department();

            // 共享工具（部门为 core/comm/cross_dept）允许所有员工使用
            if ("core".equals(toolDept) || "comm".equals(toolDept) || "cross_dept".equals(toolDept)) {
                return true;
            }

            // 同部门工具允许使用
            if (toolDept != null && toolDept.equalsIgnoreCase(empDept)) {
                return true;
            }
        }

        log.warn("Tool permission denied: employee={} (dept={}), tool={} - not in employee tool list and department mismatch",
            employeeCode, def.department(), toolName);
        return false;
    }

    private void logExecutionAudit(String employeeCode, String taskType, String env, String result, String detail) {
        log.info("ExecutionAudit: employee={}, taskType={}, env={}, result={}, detail={}",
            employeeCode, taskType, env, result, detail != null ? detail.substring(0, Math.min(detail.length(), 200)) : "none");
    }

    private String normalizeTaskType(String taskType, String taskDescription, EmployeeWorkAssignment assignmentTask) {
        String raw = (taskType != null ? taskType : "").toLowerCase(java.util.Locale.ROOT);
        String text = (raw + " " + (taskDescription != null ? taskDescription : "") + " "
            + (assignmentTask != null && assignmentTask.expectedDeliverables() != null
                ? String.join(" ", assignmentTask.expectedDeliverables()) : ""))
            .toLowerCase(java.util.Locale.ROOT);
        if (raw.equals("web_prototype") || raw.equals("web_development")) {
            return raw.equals("web_prototype") ? "web_prototype" : "web_development";
        }
        if (raw.equals("document_generation") || raw.equals("data_analysis")
            || raw.equals("legal_review") || raw.equals("finance_workflow")) {
            return raw;
        }
        // P0-5 修复：game_development / web_game_development 等归一到 web_development
        if (raw.contains("game") || raw.contains("游戏")) {
            return "web_development";
        }
        if (text.contains("网页") || text.contains("前端") || text.contains("html")
            || text.contains("css") || text.contains("javascript") || text.contains("游戏网页")
            || text.contains("web page") || text.contains("frontend") || text.contains("游戏")) {
            return "web_development";
        }
        if (text.contains("数据分析") || text.contains("analysis")) {
            return "data_analysis";
        }
        if (text.contains("审核") || text.contains("审批")) {
            return "legal_review";
        }
        if (text.contains("文档") || text.contains("报告")) {
            return "document_generation";
        }
        if (raw.contains("file_listing") || raw.contains("file_system_query")
            || raw.contains("list_dir") || raw.contains("directory_listing")
            || text.contains("文件列表") || text.contains("目录内容") || text.contains("工作目录")
            || text.contains("列出文件") || text.contains("查看文件") || text.contains("浏览目录")) {
            return "file_system_query";
        }
        // P0-3: 项目管理任务类型归一
        if (raw.contains("project_management") || raw.contains("issue_tracking")
            || raw.contains("jira") || raw.contains("openproject")
            || text.contains("项目管理") || text.contains("任务创建") || text.contains("issue")
            || text.contains("jira") || text.contains("openproject")) {
            return "project_management";
        }
        throw new IllegalArgumentException("Unsupported or unclassified task type: " + taskType);
    }

    /**
     * 使用 ExecutionCapabilityResolver 解析执行能力。
     * 优先从 assignmentTask.context 中读取已解析的 executionCapability，
     * 其次使用注入的 capabilityResolver 实时解析。
     */
    private ExecutionCapability resolveExecutionCapability(String taskType, String taskDescription, EmployeeWorkAssignment assignmentTask) {
        // 1. 优先从 assignmentTask.context 读取已解析的 capability
        if (assignmentTask != null && assignmentTask.context() != null) {
            Object capObj = assignmentTask.context().get("executionCapability");
            if (capObj instanceof ExecutionCapability cap) {
                log.debug("Using pre-resolved executionCapability from assignment context: {}", cap);
                return cap;
            }
            if (capObj instanceof String capStr) {
                try {
                    return ExecutionCapability.valueOf(capStr);
                } catch (IllegalArgumentException ignored) {}
            }
        }

        // 2. 使用注入的 capabilityResolver 实时解析
        if (capabilityResolver != null) {
            List<String> deliverables = assignmentTask != null ? assignmentTask.expectedDeliverables() : null;
            List<String> skills = assignmentTask != null && assignmentTask.context() != null
                ? (List<String>) assignmentTask.context().get("requiredSkills")
                : null;
            String department = assignmentTask != null ? assignmentTask.department() : null;
            List<String> employeeCodes = assignmentTask != null
                ? List.of(assignmentTask.employeeCode()) : null;

            ExecutionCapabilityRequest request = ExecutionCapabilityRequest.of(
                taskDescription, taskType, null, deliverables, skills, department, employeeCodes);
            ExecutionCapabilityResolution resolution = capabilityResolver.resolve(request);

            if (resolution.requiresClarification()) {
                log.warn("ExecutionCapabilityResolver requires clarification: {}", resolution.clarificationQuestions());
                return null; // 降级到 normalizeTaskType 兜底
            }
            if (resolution.requiresHumanReview()) {
                log.warn("ExecutionCapabilityResolver requires human review: {}", resolution.reason());
                return ExecutionCapability.HUMAN_HANDOFF;
            }
            if (resolution.executionCapability() != null) {
                log.info("ExecutionCapabilityResolver resolved: {} (confidence={})",
                    resolution.executionCapability(), resolution.confidence());
                return resolution.executionCapability();
            }
        }

        return null;
    }

    /**
     * 按 ExecutionCapability 路由到具体执行方法。
     */
    private ExecutionResult routeByCapability(
            ExecutionCapability capability, String employeeCode, String taskDescription,
            EmployeeWorkAssignment assignmentTask, List<String> availableTools, String executionEnvironment) throws Exception {
        return switch (capability) {
            case WEB_APP_BUILD ->
                executeWebTask(employeeCode, taskDescription, assignmentTask, availableTools, executionEnvironment);
            case DOCUMENT_GENERATION, BUSINESS_PLAN, RESEARCH_ANALYSIS ->
                executeDocumentTask(employeeCode, taskDescription, assignmentTask, availableTools, executionEnvironment);
            case DATA_ANALYSIS, FINANCE_ANALYSIS ->
                executeDataAnalysisTask(employeeCode, taskDescription, assignmentTask, availableTools, executionEnvironment);
            case LEGAL_REVIEW ->
                executeReviewTask(employeeCode, taskDescription, assignmentTask, executionEnvironment);
            case CODE_CHANGE, CODE_REVIEW ->
                executeCodeReviewTask(employeeCode, taskDescription, assignmentTask, availableTools, executionEnvironment);
            case ARCHITECTURE_DESIGN ->
                executeDocumentTask(employeeCode, taskDescription, assignmentTask, availableTools, executionEnvironment);
            case CUSTOMER_SUPPORT, HR_WORKFLOW, OPERATION_PLAN ->
                executeDocumentTask(employeeCode, taskDescription, assignmentTask, availableTools, executionEnvironment);
            case FILE_SYSTEM_QUERY ->
                executeToolTask(employeeCode, taskDescription, assignmentTask, availableTools, executionEnvironment);
            case PROJECT_MANAGEMENT, ISSUE_TRACKING ->
                executeProjectManagementTask(employeeCode, taskDescription, assignmentTask, availableTools, executionEnvironment);
            case APPROVAL_REQUIRED ->
                new ExecutionResult(false, "NEEDS_APPROVAL", "任务需要审批后才能执行",
                    List.of(), List.of(), Map.of("executionCapability", capability.name()), "Approval required");
            case HUMAN_HANDOFF ->
                new ExecutionResult(false, "NEEDS_HUMAN_REVIEW", "任务需要人工处理",
                    List.of(), List.of(), Map.of("executionCapability", capability.name()), "Human handoff required");
        };
    }

    private ExecutionResult executeWebTask(
            String employeeCode, String taskDescription, EmployeeWorkAssignment assignmentTask,
            List<String> availableTools, String executionEnvironment) throws Exception {
        
        log.info("Executing web development task: {}, env={}", taskDescription, executionEnvironment);
        
        List<ArtifactFile> artifacts = new ArrayList<>();
        List<String> usedTools = new ArrayList<>();
        
        // 根据执行环境选择执行策略
        if ("ARTIFACT_ONLY".equals(executionEnvironment) || "LOCAL_RESTRICTED".equals(executionEnvironment)) {
            // 调用 LLM 生成真实的任务代码
            log.info("Calling LLM to generate web content for task: {}", taskDescription);
            String department = assignmentTask != null ? assignmentTask.department() : null;
            String htmlContent = generateHtmlContentWithLLM(taskDescription, employeeCode, department);
            validateWebArtifact(htmlContent, taskDescription);
            String fileName = "index.html";
            String filePath = saveArtifactFile(assignmentTask, fileName, htmlContent);
            
            artifacts.add(new ArtifactFile(
                fileName, filePath, "html", htmlContent,
                htmlContent.getBytes(StandardCharsets.UTF_8).length
            ));
            usedTools.add("llm_code_generation");
            usedTools.add("file_write");
            
            log.info("Web task completed (artifact-only mode with LLM): generated {}", fileName);
        } else if ("DOCKER_SANDBOX".equals(executionEnvironment)) {
            // 尝试使用 Docker 沙箱执行
            if (sandboxService != null && sandboxService.isAvailable()) {
                log.info("Using Docker sandbox for web task execution");
                try {
                    return executeWebTaskInSandbox(employeeCode, taskDescription, assignmentTask);
                } catch (Exception sandboxError) {
                    log.warn("Docker sandbox execution failed: {}", sandboxError.getMessage());
                    return new ExecutionResult(
                        false, "FAILED", "Docker 沙箱执行失败，未进行兜底伪造完成",
                        List.of(), List.of("docker_sandbox"), Map.of(TaskMetadataKeys.TASK_TYPE, "web_development", "executionEnvironment", executionEnvironment),
                        sandboxError.getMessage()
                    );
                }
            } else {
                return new ExecutionResult(
                    false, "FAILED", "Docker 沙箱不可用，未进行兜底伪造完成",
                    List.of(), List.of(), Map.of(TaskMetadataKeys.TASK_TYPE, "web_development", "executionEnvironment", executionEnvironment),
                    "Docker sandbox not available"
                );
            }
        } else if ("HUMAN_REVIEW_REQUIRED".equals(executionEnvironment)) {
            return new ExecutionResult(
                false, "NEEDS_HUMAN_REVIEW", "任务需要人工审核，员工未自主完成执行",
                List.of(), List.of(), Map.of(TaskMetadataKeys.TASK_TYPE, "web_development", "requiresHumanReview", true),
                "Human review required"
            );
        } else {
            return new ExecutionResult(
                false, "FAILED", "不支持的执行环境: " + executionEnvironment,
                List.of(), List.of(), Map.of(), "Unsupported execution environment"
            );
        }
        
        return new ExecutionResult(
            true, "COMPLETED", "Web 任务已完成，生成 " + artifacts.size() + " 个文件",
            artifacts, usedTools, Map.of(TaskMetadataKeys.TASK_TYPE, "web_development", "executionEnvironment", executionEnvironment), null
        );
    }

    /**
     * 在 Docker 沙箱中执行 Web 任务
     */
    private ExecutionResult executeWebTaskInSandbox(
            String employeeCode, String taskDescription, EmployeeWorkAssignment assignmentTask) throws Exception {
        
        log.info("Executing web task in Docker sandbox: {}", taskDescription);
        
        // 生成初始 HTML 内容：必须由模型基于任务动态生成
        String department = assignmentTask != null ? assignmentTask.department() : null;
        String htmlContent = generateHtmlContentWithLLM(taskDescription, employeeCode, department);
        validateWebArtifact(htmlContent, taskDescription);
        String fileName = "index.html";
        
        // 将任务描述写入沙箱
        String taskScript = """
            #!/bin/bash
            mkdir -p /workspace/output
            cat > /workspace/output/index.html << 'HTMLEOF'
            %s
            HTMLEOF
            echo "Web task completed: index.html generated"
            """.formatted(htmlContent);
        
        // 通过沙箱执行脚本
        com.livingagent.core.sandbox.SandboxService.ExecutionOptions options = 
            com.livingagent.core.sandbox.SandboxService.ExecutionOptions.QUICK;
        
        var future = sandboxService.executeCommand(
            "bash", 
            List.of("-c", taskScript), 
            options
        );
        
        var result = future.get(60, java.util.concurrent.TimeUnit.SECONDS);
        
        if (result.exitCode() == 0) {
            // 沙箱执行成功，读取生成的文件
            String filePath = saveArtifactFile(assignmentTask, fileName, htmlContent);
            
            List<ArtifactFile> artifacts = List.of(
                new ArtifactFile(fileName, filePath, "html", htmlContent,
                    htmlContent.getBytes(StandardCharsets.UTF_8).length)
            );
            
            return new ExecutionResult(
                true, "COMPLETED", "Web 任务在 Docker 沙箱中执行完成",
                artifacts, List.of("docker_sandbox", "file_write"), 
                Map.of(TaskMetadataKeys.TASK_TYPE, "web_development", "executionEnvironment", "DOCKER_SANDBOX"), null
            );
        } else {
            throw new RuntimeException("Docker sandbox execution failed with exit code: " + result.exitCode());
        }
    }

    private ExecutionResult executeDocumentTask(
            String employeeCode, String taskDescription, EmployeeWorkAssignment assignmentTask,
            List<String> availableTools, String executionEnvironment) throws Exception {
        
        log.info("Executing document generation task: {}", taskDescription);
        
        String department = assignmentTask != null ? assignmentTask.department() : null;
        String markdownContent = generateContentWithLLM(
            employeeCode, department,
            "你是专业文档撰写员工。请根据任务要求生成完整、可交付的Markdown文档。只输出Markdown正文，不要解释。",
            "任务描述：" + taskDescription + "\n执行员工：" + employeeCode + "\n请生成可交付文档。",
            "markdown document"
        );
        String fileName = "report.md";
        String filePath = saveArtifactFile(assignmentTask, fileName, markdownContent);
        
        List<ArtifactFile> artifacts = List.of(
            new ArtifactFile(fileName, filePath, "markdown", markdownContent,
                markdownContent.getBytes(StandardCharsets.UTF_8).length)
        );
        
        return new ExecutionResult(
            true, "COMPLETED", "文档生成完成",
            artifacts, List.of("file_write"), Map.of(TaskMetadataKeys.TASK_TYPE, "document_generation"), null
        );
    }

    private ExecutionResult executeDataAnalysisTask(
            String employeeCode, String taskDescription, EmployeeWorkAssignment assignmentTask,
            List<String> availableTools, String executionEnvironment) throws Exception {
        
        log.info("Executing data analysis task: {}", taskDescription);
        
        String department = assignmentTask != null ? assignmentTask.department() : null;
        String reportContent = generateContentWithLLM(
            employeeCode, department,
            "你是专业数据分析员工。请根据任务要求生成结构化分析报告，包含数据假设、分析步骤、发现、风险和建议。只输出Markdown正文。",
            "任务描述：" + taskDescription + "\n执行员工：" + employeeCode + "\n请生成可交付分析报告。",
            "data analysis report"
        );
        String fileName = "analysis_report.md";
        String filePath = saveArtifactFile(assignmentTask, fileName, reportContent);
        
        List<ArtifactFile> artifacts = List.of(
            new ArtifactFile(fileName, filePath, "report", reportContent,
                reportContent.getBytes(StandardCharsets.UTF_8).length)
        );
        
        return new ExecutionResult(
            true, "COMPLETED", "数据分析完成",
            artifacts, List.of("file_write"), Map.of(TaskMetadataKeys.TASK_TYPE, "data_analysis"), null
        );
    }

    private ExecutionResult executeReviewTask(
            String employeeCode, String taskDescription, EmployeeWorkAssignment assignmentTask,
            String executionEnvironment) {
        
        log.info("Executing review task: {}", taskDescription);
        
        try {
            String department = assignmentTask != null ? assignmentTask.department() : null;
            String reviewReport = generateContentWithLLM(
                employeeCode, department,
                "你是专业审核员工。请基于任务要求生成审查意见、风险点、结论和需人工确认事项。只输出Markdown正文，不要使用固定模板。",
                "任务描述：" + taskDescription + "\n执行员工：" + employeeCode + "\n请生成任务相关审查报告。",
                "review report"
            );
            String fileName = "review_report.md";
            String filePath = saveArtifactFile(assignmentTask, fileName, reviewReport);
            
            List<ArtifactFile> artifacts = List.of(
                new ArtifactFile(fileName, filePath, "report", reviewReport,
                    reviewReport.getBytes(StandardCharsets.UTF_8).length)
            );
            
            return new ExecutionResult(
                true, "COMPLETED", "审核报告已由模型生成",
                artifacts, List.of("llm_review_generation", "file_write"), Map.of(TaskMetadataKeys.TASK_TYPE, "review"), null
            );
        } catch (Exception e) {
            return new ExecutionResult(
                false, "FAILED", "审核报告生成失败: " + e.getMessage(),
                List.of(), List.of(), Map.of(), e.getMessage()
            );
        }
    }

    /**
     * 代码审查/代码变更任务执行。
     * 使用 LLM 结合员工可用工具（gitlab/github 等）生成结构化审查报告，
     * 而非生成 HTML 页面（这是 executeWebTask 的行为，不适合代码审查）。
     */
    private ExecutionResult executeCodeReviewTask(
            String employeeCode, String taskDescription, EmployeeWorkAssignment assignmentTask,
            List<String> availableTools, String executionEnvironment) throws Exception {

        log.info("Executing code review task: employee={}, tools={}", employeeCode, availableTools);

        List<ArtifactFile> artifacts = new ArrayList<>();
        List<String> usedTools = new ArrayList<>();

        String department = assignmentTask != null ? assignmentTask.department() : null;

        // 构建工具上下文：告诉 LLM 员工有哪些工具可用于代码分析
        String toolContext = availableTools != null && !availableTools.isEmpty()
            ? "\n\n你可用的工具：" + String.join(", ", availableTools)
            : "";

        // 使用 LLM 生成代码审查报告（Markdown 格式）
        String reviewReport = generateContentWithLLM(
            employeeCode, department,
            "你是专业的代码审查工程师。请对以下任务进行技术评审分析。\n"
                + toolContext
                + "\n\n输出要求："
                + "\n1. 使用 Markdown 格式"
                + "\n2. 包含以下章节：功能概述、架构评估、代码质量、安全性分析、合规性评估、集成建议、风险与结论"
                + "\n3. 给出明确的结论：推荐使用/有条件使用/不推荐",
            "任务描述：" + taskDescription + "\n执行员工：" + employeeCode + "\n请生成技术评审报告。",
            "code_review_report"
        );
        usedTools.add("llm_code_review");
        usedTools.add("file_write");

        // 保存为 Markdown 报告文件
        String fileName = "code_review_report.md";
        String filePath = saveArtifactFile(assignmentTask, fileName, reviewReport);
        artifacts.add(new ArtifactFile(
            fileName, filePath, "report", reviewReport,
            reviewReport.getBytes(StandardCharsets.UTF_8).length
        ));

        log.info("Code review task completed (artifact-only mode with LLM): generated {}", fileName);

        return new ExecutionResult(
            true, "COMPLETED", "代码审查报告已生成",
            artifacts, usedTools,
            Map.of(TaskMetadataKeys.TASK_TYPE, "code_review", "executionEnvironment", executionEnvironment),
            null
        );
    }

    /**
     * P0-3: 项目管理任务执行路由
     * 将 PROJECT_MANAGEMENT / ISSUE_TRACKING 类型的任务路由到 jira 工具执行
     */
    private ExecutionResult executeProjectManagementTask(
            String employeeCode, String taskDescription, EmployeeWorkAssignment assignmentTask,
            List<String> availableTools, String executionEnvironment) throws Exception {

        log.info("Executing project management task: employee={}, task={}", employeeCode, taskDescription);

        // 检查员工是否有 jira 工具权限
        if (availableTools == null || !availableTools.contains("jira")) {
            return new ExecutionResult(
                false, "FAILED", "员工 " + employeeCode + " 没有 jira 工具权限，无法执行项目管理任务",
                List.of(), List.of(),
                Map.of(TaskMetadataKeys.TASK_TYPE, "project_management", "missingTool", "jira"),
                "Employee lacks jira tool permission"
            );
        }

        if (toolRegistry == null) {
            return new ExecutionResult(
                false, "FAILED", "ToolRegistry 不可用，无法执行项目管理任务",
                List.of(), List.of(), Map.of(TaskMetadataKeys.TASK_TYPE, "project_management"),
                "ToolRegistry not available"
            );
        }

        Optional<Tool> jiraToolOpt = toolRegistry.get("jira");
        if (jiraToolOpt.isEmpty()) {
            return new ExecutionResult(
                false, "FAILED", "jira 工具未注册（OpenProject/Jira 未配置），无法执行项目管理任务",
                List.of(), List.of(), Map.of(TaskMetadataKeys.TASK_TYPE, "project_management"),
                "Jira tool not registered"
            );
        }

        // 解析项目管理操作意图
        ToolInvocationPlan plan = resolveProjectManagementInvocation(taskDescription, assignmentTask);
        if (plan == null) {
            // 降级到通用工具路由
            log.info("Cannot resolve project management invocation, falling back to executeToolTask");
            return executeToolTask(employeeCode, taskDescription, assignmentTask, availableTools, executionEnvironment);
        }

        Tool jiraTool = jiraToolOpt.get();
        log.info("Invoking jira tool: action={} for employee={}", plan.action, employeeCode);

        ToolParams params = ToolParams.of(plan.params);
        jiraTool.validate(params);

        ToolContext context = ToolContext.of(
            assignmentTask != null ? assignmentTask.employeeNeuronId() : null,
            assignmentTask != null && assignmentTask.context() != null
                ? (String) assignmentTask.context().get("sessionId") : null,
            null,
            employeeCode
        );

        long startTime = System.currentTimeMillis();
        ToolResult result = jiraTool.execute(params, context);
        long duration = System.currentTimeMillis() - startTime;

        log.info("Jira tool execution completed: success={}, duration={}ms", result.success(), duration);

        if (result.success() && result.data() != null) {
            String resultContent = formatToolResult(result.data());
            String fileName = "project_management_result.txt";
            String filePath = saveArtifactFile(assignmentTask, fileName, resultContent);

            List<ArtifactFile> artifacts = List.of(
                new ArtifactFile(fileName, filePath, "project_management", resultContent,
                    resultContent.getBytes(StandardCharsets.UTF_8).length)
            );

            return new ExecutionResult(
                true, "COMPLETED", "项目管理操作成功: jira." + plan.action,
                artifacts, List.of("jira"),
                Map.of(
                    TaskMetadataKeys.TASK_TYPE, "project_management",
                    "toolName", "jira",
                    "action", plan.action,
                    "executionMode", "PROJECT_MANAGEMENT"
                ),
                null
            );
        } else {
            return new ExecutionResult(
                false, "FAILED", "项目管理操作失败: " + (result.error() != null ? result.error() : "未知错误"),
                List.of(), List.of("jira"),
                Map.of(
                    TaskMetadataKeys.TASK_TYPE, "project_management",
                    "toolName", "jira",
                    "action", plan.action
                ),
                result.error()
            );
        }
    }

    /**
     * P0-3: 解析项目管理操作意图
     * 从任务描述和上下文中提取 jira 工具的 action 和参数
     */
    private ToolInvocationPlan resolveProjectManagementInvocation(String taskDescription, EmployeeWorkAssignment assignmentTask) {
        String desc = taskDescription.toLowerCase();
        String action;
        Map<String, Object> params = new LinkedHashMap<>();

        // 从 assignmentTask 上下文中提取项目管理相关参数
        if (assignmentTask != null && assignmentTask.context() != null) {
            if (assignmentTask.context().containsKey("issueKey")) {
                params.put("issue_key", assignmentTask.context().get("issueKey"));
            }
            if (assignmentTask.context().containsKey("projectId")) {
                params.put("project_id", assignmentTask.context().get("projectId"));
            }
            if (assignmentTask.context().containsKey("executionId")) {
                params.put("execution_id", assignmentTask.context().get("executionId"));
            }
        }

        // 基于关键词匹配操作类型
        if (desc.contains("创建") || desc.contains("新建") || desc.contains("create")) {
            action = "create_issue";
            params.put("summary", taskDescription);
            params.put("issue_type", "Task");
        } else if (desc.contains("更新") || desc.contains("修改") || desc.contains("update")) {
            action = "update_issue";
            if (!params.containsKey("issue_key")) {
                log.warn("Update issue requested but no issue_key provided in context");
                return null;
            }
        } else if (desc.contains("查询") || desc.contains("搜索") || desc.contains("search") || desc.contains("列表")) {
            action = "search_issue";
            params.put("query", taskDescription);
        } else if (desc.contains("评论") || desc.contains("备注") || desc.contains("comment")) {
            action = "add_comment";
            if (!params.containsKey("issue_key")) {
                log.warn("Add comment requested but no issue_key provided in context");
                return null;
            }
            params.put("comment_body", taskDescription);
        } else if (desc.contains("查看") || desc.contains("获取") || desc.contains("get") || desc.contains("详情")) {
            action = "get_issue";
            if (!params.containsKey("issue_key")) {
                // 降级到搜索
                action = "search_issue";
                params.put("query", taskDescription);
            }
        } else {
            // 默认搜索
            action = "search_issue";
            params.put("query", taskDescription);
        }

        return new ToolInvocationPlan("jira", action, params);
    }

    private ExecutionResult executeToolTask(
            String employeeCode, String taskDescription, EmployeeWorkAssignment assignmentTask,
            List<String> availableTools, String executionEnvironment) throws Exception {

        log.info("Executing tool-driven task: employee={}, env={}", employeeCode, executionEnvironment);

        if (toolRegistry == null) {
            return new ExecutionResult(
                false, "FAILED", "ToolRegistry 不可用，无法执行工具调用任务",
                List.of(), List.of(), Map.of(TaskMetadataKeys.TASK_TYPE, "tool_execution"),
                "ToolRegistry not available"
            );
        }

        ToolInvocationPlan plan = resolveToolInvocation(taskDescription, assignmentTask);
        if (plan == null) {
            return new ExecutionResult(
                false, "FAILED", "无法解析工具调用意图: " + taskDescription,
                List.of(), List.of(), Map.of(TaskMetadataKeys.TASK_TYPE, "tool_execution"),
                "Cannot resolve tool invocation from task description"
            );
        }

        Optional<Tool> toolOpt = toolRegistry.get(plan.toolName);
        if (toolOpt.isEmpty()) {
            return new ExecutionResult(
                false, "FAILED", "工具 " + plan.toolName + " 未注册",
                List.of(), List.of(), Map.of(TaskMetadataKeys.TASK_TYPE, "tool_execution", "requestedTool", plan.toolName),
                "Tool not found: " + plan.toolName
            );
        }

        Tool tool = toolOpt.get();
        log.info("Invoking tool: {} with action: {} for employee: {}", plan.toolName, plan.action, employeeCode);

        ToolParams params = ToolParams.of(plan.params);
        tool.validate(params);

        ToolContext context = ToolContext.of(
            assignmentTask != null ? assignmentTask.employeeNeuronId() : null,
            assignmentTask != null && assignmentTask.context() != null
                ? (String) assignmentTask.context().get("sessionId") : null,
            null,
            employeeCode
        );

        long startTime = System.currentTimeMillis();
        ToolResult result = tool.execute(params, context);
        long duration = System.currentTimeMillis() - startTime;

        log.info("Tool {} execution completed: success={}, duration={}ms", plan.toolName, result.success(), duration);

        if (result.success() && result.data() != null) {
            String resultContent = formatToolResult(result.data());
            String fileName = "tool_result.txt";
            String filePath = saveArtifactFile(assignmentTask, fileName, resultContent);

            List<ArtifactFile> artifacts = List.of(
                new ArtifactFile(fileName, filePath, "tool_result", resultContent,
                    resultContent.getBytes(StandardCharsets.UTF_8).length)
            );

            return new ExecutionResult(
                true, "COMPLETED", "工具调用成功: " + plan.toolName + "." + plan.action,
                artifacts, List.of(plan.toolName),
                Map.of(
                    TaskMetadataKeys.TASK_TYPE, "tool_execution",
                    "toolName", plan.toolName,
                    "action", plan.action,
                    "executionMode", "TOOL_EXECUTION"
                ),
                null
            );
        } else {
            return new ExecutionResult(
                false, "FAILED", "工具调用失败: " + (result.error() != null ? result.error() : "未知错误"),
                List.of(), List.of(plan.toolName),
                Map.of(
                    TaskMetadataKeys.TASK_TYPE, "tool_execution",
                    "toolName", plan.toolName,
                    "action", plan.action
                ),
                result.error()
            );
        }
    }

    private ToolInvocationPlan resolveToolInvocation(String taskDescription, EmployeeWorkAssignment assignmentTask) {
        String desc = (taskDescription != null ? taskDescription : "").toLowerCase(java.util.Locale.ROOT);
        String taskType = assignmentTask != null && assignmentTask.context() != null
            ? (String) assignmentTask.context().get(TaskMetadataKeys.TASK_TYPE) : "";
        String combinedType = (taskType != null ? taskType : "").toLowerCase(java.util.Locale.ROOT);

        if (combinedType.contains("file_listing") || combinedType.contains("file_system_query")
            || combinedType.contains("list_dir") || combinedType.contains("directory_listing")
            || desc.contains("文件") || desc.contains("目录") || desc.contains("工作目录")
            || desc.contains("列出") || desc.contains("查看文件") || desc.contains("浏览")
            || desc.contains("list files") || desc.contains("directory") || desc.contains("workspace")) {
            return new ToolInvocationPlan("file_edit", "list_dir",
                Map.of("action", "list_dir", "path", "."));
        }

        if (combinedType.contains("file_read") || desc.contains("读取文件") || desc.contains("查看文件内容")
            || desc.contains("read file") || desc.contains("file content")) {
            String path = extractPathFromDescription(desc);
            return new ToolInvocationPlan("file_edit", "read_file",
                Map.of("action", "read_file", "path", path));
        }

        if (combinedType.contains("code_search") || desc.contains("搜索代码") || desc.contains("查找代码")
            || desc.contains("search code") || desc.contains("find in files")) {
            String pattern = extractSearchPatternFromDescription(desc);
            return new ToolInvocationPlan("file_edit", "search_code",
                Map.of("action", "search_code", "path", ".", "pattern", pattern));
        }

        if (toolRegistry != null) {
            for (Tool tool : toolRegistry.getAll()) {
                String toolName = tool.getName().toLowerCase(java.util.Locale.ROOT);
                if (combinedType.contains(toolName) || desc.contains(toolName)) {
                    return new ToolInvocationPlan(tool.getName(), "default",
                        Map.of("action", "default"));
                }
            }
        }

        return null;
    }

    private String extractPathFromDescription(String desc) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
            "(?:path|路径|文件|file)\\s*[:=]?\\s*['\"]?([^'\"\\s,，]+)").matcher(desc);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return ".";
    }

    private String extractSearchPatternFromDescription(String desc) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
            "(?:pattern|模式|关键词|keyword|search)\\s*[:=]?\\s*['\"]?([^'\"\\s,，]+)").matcher(desc);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private String formatToolResult(Object data) {
        if (data == null) return "";
        if (data instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                sb.append(entry.getKey()).append(": ");
                if (entry.getValue() instanceof List<?> list) {
                    sb.append("\n");
                    for (Object item : list) {
                        sb.append("  - ").append(item).append("\n");
                    }
                } else {
                    sb.append(entry.getValue()).append("\n");
                }
            }
            return sb.toString();
        }
        if (data instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object item : list) {
                sb.append("- ").append(item).append("\n");
            }
            return sb.toString();
        }
        return data.toString();
    }

    private record ToolInvocationPlan(String toolName, String action, Map<String, Object> params) {}

    private ExecutionResult executeGenericTask(
            String employeeCode, String taskDescription, EmployeeWorkAssignment assignmentTask,
            List<String> availableTools, String executionEnvironment) throws Exception {
        
        log.info("Executing generic task: {}", taskDescription);
        
        return new ExecutionResult(
            false, "FAILED", "未识别任务类型，拒绝使用硬编码或通用兜底伪造完成",
            List.of(), List.of(), Map.of(TaskMetadataKeys.TASK_TYPE, "generic", "completionGate", "BLOCKED"),
            "Unsupported or unclassified task type; refusing generic completion path"
        );
    }

    /**
     * 调用 LLM 生成真实的任务代码。
     * 注意：这里不再使用硬编码网页模板兜底；模型不可用或输出不合格时必须失败，避免假完成。
     */
    private String generateHtmlContentWithLLM(String taskDescription, String employeeCode, String department) {
        if (brainModelResolver == null) {
            throw new IllegalStateException("BrainModelResolver not available; cannot generate task-specific web artifact");
        }

        try {
            String departmentBrainId = DEPARTMENT_TO_BRAIN_ID.getOrDefault(
                department != null ? department.toLowerCase() : "tech", "TechBrain");
            ResolvedBrainModel model = brainModelResolver.resolveForEmployee(employeeCode, department, departmentBrainId);
            if (model == null) {
                log.warn("resolveForEmployee returned null for employee={}, department={}, brainId={}, falling back to resolve",
                    employeeCode, department, departmentBrainId);
                model = brainModelResolver.resolve(employeeCode);
            }
            if (model == null) {
                throw new IllegalStateException("No model resolved for employee " + employeeCode + " in department " + department);
            }

            log.info("Model resolved for employee {}: provider={}, model={}, department={}, brainId={}",
                employeeCode, model.getProviderId(), model.getModelName(), department, departmentBrainId);

            String systemPrompt = """
                你是一个专业的前端开发工程师。请根据用户的任务描述，生成完整的、可运行的HTML代码。
                
                要求：
                1. 生成完整的HTML文件，包含DOCTYPE、html、head、body标签
                2. 使用内联CSS和JavaScript，不依赖外部文件
                3. 代码必须完整、可运行
                4. 只输出HTML代码，不要输出任何其他解释或说明
                5. 不要使用markdown代码块包裹，直接输出HTML代码
                
                请确保代码质量高，视觉效果美观。
                """;

            String userPrompt = "任务描述：" + taskDescription + "\n\n请生成完整的HTML代码。";

            ResolvedBrainModelProvider provider = new ResolvedBrainModelProvider(model);
            String htmlContent = provider.chatWithSystem(systemPrompt, userPrompt, null, 0.7)
                .get(LLM_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (htmlContent != null && !htmlContent.isBlank()) {
                // 清理可能存在的markdown代码块标记
                htmlContent = htmlContent.trim();
                if (htmlContent.startsWith("```html")) {
                    htmlContent = htmlContent.substring(7);
                }
                if (htmlContent.startsWith("```")) {
                    htmlContent = htmlContent.substring(3);
                }
                if (htmlContent.endsWith("```")) {
                    htmlContent = htmlContent.substring(0, htmlContent.length() - 3);
                }
                htmlContent = htmlContent.trim();

                log.info("LLM generated web content successfully for employee {}", employeeCode);
                return htmlContent;
            }

            throw new IllegalStateException("LLM returned empty HTML content");
        } catch (Exception e) {
            log.error("LLM call failed for employee {}: {}", employeeCode, e.getMessage());
            throw new IllegalStateException("LLM web artifact generation failed: " + e.getMessage(), e);
        }
    }

    private String generateContentWithLLM(String employeeCode, String department, String systemPrompt, String userPrompt, String contentType) {
        if (brainModelResolver == null) {
            throw new IllegalStateException("BrainModelResolver not available; cannot generate " + contentType);
        }
        try {
            String departmentBrainId = DEPARTMENT_TO_BRAIN_ID.getOrDefault(
                department != null ? department.toLowerCase() : "tech", "TechBrain");
            ResolvedBrainModel model = brainModelResolver.resolveForEmployee(employeeCode, department, departmentBrainId);
            if (model == null) {
                log.warn("resolveForEmployee returned null for employee={}, department={}, brainId={}, falling back to resolve",
                    employeeCode, department, departmentBrainId);
                model = brainModelResolver.resolve(employeeCode);
            }
            if (model == null) {
                throw new IllegalStateException("No model resolved for employee " + employeeCode + " in department " + department);
            }
            log.info("Model resolved for employee {}: provider={}, model={}, department={}, brainId={}",
                employeeCode, model.getProviderId(), model.getModelName(), department, departmentBrainId);
            ResolvedBrainModelProvider provider = new ResolvedBrainModelProvider(model);
            String content = provider.chatWithSystem(systemPrompt, userPrompt, null, 0.7)
                .get(LLM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (content == null || content.isBlank()) {
                throw new IllegalStateException("LLM returned empty " + contentType);
            }
            return content.trim();
        } catch (Exception e) {
            log.error("LLM {} generation failed for employee {}: {}", contentType, employeeCode, e.getMessage());
            throw new IllegalStateException("LLM " + contentType + " generation failed: " + e.getMessage(), e);
        }
    }

    private void validateWebArtifact(String htmlContent, String taskDescription) {
        String html = htmlContent != null ? htmlContent.toLowerCase(java.util.Locale.ROOT) : "";
        if (!html.contains("<!doctype html") && !html.contains("<html")) {
            throw new IllegalStateException("Generated web artifact is not a complete HTML document");
        }
    }

    private String saveArtifactFile(EmployeeWorkAssignment assignmentTask, String fileName, String content) throws Exception {
        String department = assignmentTask != null && assignmentTask.department() != null && !assignmentTask.department().isBlank()
            ? assignmentTask.department()
            : "unknown";
        String employeeCode = assignmentTask != null && assignmentTask.employeeCode() != null && !assignmentTask.employeeCode().isBlank()
            ? assignmentTask.employeeCode()
            : "unknown";
        String executionId = "unknown";
        if (assignmentTask != null && assignmentTask.context() != null) {
            Object value = assignmentTask.context().get("executionId");
            if (value != null && !String.valueOf(value).isBlank()) {
                executionId = String.valueOf(value);
            }
        }

        String sanitizedDept = sanitizePath(department);
        String sanitizedEmployee = sanitizePath(employeeCode);
        String sanitizedExecId = sanitizePath(executionId);
        String sanitizedFile = sanitizePath(fileName);

        // 优先：新结构 by-execution/{executionId}/{employeeCode}/{filename}
        Path newArtifactDir = Paths.get(ARTIFACTS_DIR, "by-execution", sanitizedExecId, sanitizedEmployee);
        Files.createDirectories(newArtifactDir);
        Path newFilePath = newArtifactDir.resolve(sanitizedFile);
        Files.writeString(newFilePath, content, StandardCharsets.UTF_8);

        // 同时写入 by-employee 索引（Windows 兼容：使用文件副本而非软链接）
        try {
            Path employeeIndexDir = Paths.get(ARTIFACTS_DIR, "by-employee", sanitizedEmployee, sanitizedExecId);
            Files.createDirectories(employeeIndexDir);
            Path employeeFilePath = employeeIndexDir.resolve(sanitizedFile);
            Files.writeString(employeeFilePath, content, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Failed to write employee artifact index: {}", e.getMessage());
        }

        // 兼容：保留旧路径 {dept}/{executionId}/{filename}（用于历史数据回溯）
        try {
            Path legacyArtifactDir = Paths.get(ARTIFACTS_DIR, sanitizedDept, sanitizedExecId);
            Files.createDirectories(legacyArtifactDir);
            Path legacyFilePath = legacyArtifactDir.resolve(sanitizedFile);
            // 仅当旧路径不存在时写入，避免覆盖
            if (!Files.exists(legacyFilePath)) {
                Files.writeString(legacyFilePath, content, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.warn("Failed to write legacy artifact path: {}", e.getMessage());
        }

        log.info("Artifact file saved: employee={}, executionId={}, file={}", employeeCode, executionId, sanitizedFile);
        return newFilePath.toString();
    }

    private String sanitizePath(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private ExecutionEnvironment determineEnvironment(String taskType, int riskLevel) {
        if (riskLevel >= 4 || isDestructiveTask(taskType)) {
            return ExecutionEnvironment.HUMAN_REVIEW_REQUIRED;
        }
        if (isCodeExecutionTask(taskType)) {
            return ExecutionEnvironment.DOCKER_SANDBOX;
        }
        if (isArtifactOnlyTask(taskType)) {
            return ExecutionEnvironment.ARTIFACT_ONLY;
        }
        return ExecutionEnvironment.LOCAL_RESTRICTED;
    }

    private boolean isDestructiveTask(String taskType) {
        if (taskType == null) return false;
        String lower = taskType.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("delete") || lower.contains("drop") || lower.contains("destructive")
            || lower.contains("reset") || lower.contains("truncate");
    }

    private boolean isCodeExecutionTask(String taskType) {
        if (taskType == null) return false;
        String lower = taskType.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("web_development") || lower.contains("web_prototype")
            || lower.contains("code") || lower.contains("script");
    }

    private boolean isArtifactOnlyTask(String taskType) {
        if (taskType == null) return false;
        String lower = taskType.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("document") || lower.contains("report") || lower.contains("review");
    }

    /**
     * 64-C-1: 输出验证包装方法
     * 对执行结果进行4层验证，验证失败返回 NEEDS_REWORK 状态
     */
    private ExecutionResult validateAndWrapResult(ExecutionResult rawResult, EmployeeWorkAssignment assignment) {
        if (outputValidator == null || !rawResult.success()) {
            return rawResult;
        }
        String contentToValidate = extractContentForValidation(rawResult);
        String artifactType = inferArtifactType(rawResult);
        if (contentToValidate != null && !contentToValidate.isBlank()) {
            var validationResult = outputValidator.validate(contentToValidate, artifactType, assignment);
            if (!validationResult.valid()) {
                log.warn("Output validation failed: issues={}", validationResult.issues());
                return new ExecutionResult(
                    false, "NEEDS_REWORK",
                    "输出验证失败: " + String.join("; ", validationResult.issues()),
                    rawResult.artifacts(), rawResult.usedTools(),
                    Map.of("validationIssues", validationResult.issues(), "validationWarnings", validationResult.warnings()),
                    "Output validation failed"
                );
            }
            if (!validationResult.warnings().isEmpty()) {
                log.info("Output validation warnings: {}", validationResult.warnings());
            }
        }
        return rawResult;
    }

    private String extractContentForValidation(ExecutionResult result) {
        if (result.artifacts() != null && !result.artifacts().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (var artifact : result.artifacts()) {
                if (artifact.content() != null) {
                    sb.append(artifact.content());
                    sb.append("\n");
                }
            }
            return sb.toString().trim();
        }
        if (result.summary() != null) {
            return result.summary();
        }
        return null;
    }

    private String inferArtifactType(ExecutionResult result) {
        if (result.artifacts() != null && !result.artifacts().isEmpty()) {
            String fileType = result.artifacts().get(0).fileType();
            if (fileType != null) {
                return switch (fileType.toLowerCase()) {
                    case "html", "htm" -> "html";
                    case "md", "markdown" -> "markdown";
                    case "json" -> "json";
                    default -> "text";
                };
            }
        }
        return "text";
    }
}
