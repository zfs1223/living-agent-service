package com.livingagent.core.tool.impl;

import com.livingagent.core.security.SecurityPolicy;
import com.livingagent.core.tool.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 构建触发工具，允许 LLM 触发项目编译和部署。
 *
 * <p>支持的操作：
 * <ul>
 *   <li>compile — 编译指定模块（mvn compile）</li>
 *   <li>build — 完整构建（mvn package -DskipTests）</li>
 *   <li>restart — 重启 Docker 服务</li>
 *   <li>status — 检查构建/服务状态</li>
 * </ul>
 *
 * <p>安全约束：
 * <ul>
 *   <li>所有操作需要审批（requiresApproval = true）</li>
 *   <li>编译超时限制（默认 300 秒）</li>
 *   <li>仅允许在工作区目录内执行</li>
 * </ul>
 */
public class BuildTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(BuildTool.class);

    private static final String NAME = "build";
    private static final String DESCRIPTION = "构建触发工具，支持编译、打包、重启服务。修改源码后需要通过此工具触发重新构建。";
    private static final String VERSION = "1.0.0";
    private static final String DEPARTMENT = "tech";

    private static final String WORKSPACE_ROOT_PROPERTY = "livingagent.workspace.root";
    private static final String DEFAULT_WORKSPACE_ROOT = "/app/workspace";

    private final int timeoutSeconds;
    private volatile Path workspaceRoot;
    private ToolStats stats = ToolStats.empty(NAME);

    public BuildTool() {
        this.workspaceRoot = Path.of(
            System.getProperty(WORKSPACE_ROOT_PROPERTY, DEFAULT_WORKSPACE_ROOT)
        ).toAbsolutePath().normalize();
        this.timeoutSeconds = 300;
    }

    public BuildTool(String workspaceRoot, int timeoutSeconds) {
        this.workspaceRoot = Path.of(workspaceRoot).toAbsolutePath().normalize();
        this.timeoutSeconds = timeoutSeconds;
    }

    public void setWorkspaceRoot(String path) {
        this.workspaceRoot = Path.of(path).toAbsolutePath().normalize();
        log.info("BuildTool workspace root updated to: {}", workspaceRoot);
    }

    public String getWorkspaceRoot() {
        return workspaceRoot.toString();
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
    public ToolSchema getSchema() {
        return ToolSchema.builder()
                .name(NAME)
                .description(DESCRIPTION)
                .parameter("action", "string", "操作类型: compile, build, restart, status", true)
                .parameter("module", "string", "模块名称（如 living-agent-core），为空则编译整个项目", false)
                .parameter("service", "string", "Docker 服务名称（仅 restart 操作需要，默认 living-agent-service）", false)
                .build();
    }

    @Override
    public List<String> getCapabilities() {
        return List.of("compile", "build", "restart", "status_check");
    }

    @Override
    public ToolResult execute(ToolParams params, ToolContext context) {
        long startTime = System.currentTimeMillis();
        String action = params.getString("action");

        if (action == null || action.isBlank()) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return ToolResult.failure("action 参数不能为空");
        }

        try {
            Object result = switch (action.toLowerCase()) {
                case "compile" -> compile(params.getString("module"));
                case "build" -> build(params.getString("module"));
                case "restart" -> restartService(params.getString("service"));
                case "status" -> checkStatus(params.getString("service"));
                default -> throw new IllegalArgumentException("不支持的操作: " + action);
            };

            stats = stats.recordCall(true, System.currentTimeMillis() - startTime);
            return ToolResult.success(result);
        } catch (Exception e) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return ToolResult.failure("执行失败: " + e.getMessage());
        }
    }

    @Override
    public void validate(ToolParams params) {
        String action = params.getString("action");
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action 参数不能为空");
        }

        Set<String> validActions = Set.of("compile", "build", "restart", "status");
        if (!validActions.contains(action.toLowerCase())) {
            throw new IllegalArgumentException("不支持的操作: " + action + "，支持: " + validActions);
        }
    }

    @Override
    public boolean isAllowed(SecurityPolicy policy) {
        return policy != null && policy.getAutonomyLevel() != null;
    }

    @Override
    public boolean requiresApproval() {
        return true;
    }

    @Override
    public ToolStats getStats() { return stats; }

    // ==================== 操作实现 ====================

    private Map<String, Object> compile(String module) throws Exception {
        Path projectDir = resolveProjectDir();
        String command = module != null && !module.isBlank()
            ? String.format("mvn compile -pl %s -am -q", module)
            : "mvn compile -q";

        log.info("Compiling project at {} : {}", projectDir, command);
        ProcessResult pr = executeCommand(command, projectDir);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("action", "compile");
        result.put("module", module != null ? module : "all");
        result.put("success", pr.exitCode == 0);
        result.put("exitCode", pr.exitCode);
        result.put("output", truncate(pr.output, 5000));
        return result;
    }

    private Map<String, Object> build(String module) throws Exception {
        Path projectDir = resolveProjectDir();
        String command = module != null && !module.isBlank()
            ? String.format("mvn package -DskipTests -pl %s -am -q", module)
            : "mvn package -DskipTests -q";

        log.info("Building project at {} : {}", projectDir, command);
        ProcessResult pr = executeCommand(command, projectDir);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("action", "build");
        result.put("module", module != null ? module : "all");
        result.put("success", pr.exitCode == 0);
        result.put("exitCode", pr.exitCode);
        result.put("output", truncate(pr.output, 5000));
        return result;
    }

    private Map<String, Object> restartService(String service) throws Exception {
        String serviceName = service != null && !service.isBlank() ? service : "living-agent-service";
        String command = String.format("docker restart %s", serviceName);

        log.info("Restarting service: {}", serviceName);
        ProcessResult pr = executeCommand(command, workspaceRoot);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("action", "restart");
        result.put("service", serviceName);
        result.put("success", pr.exitCode == 0);
        result.put("output", truncate(pr.output, 2000));
        return result;
    }

    private Map<String, Object> checkStatus(String service) throws Exception {
        String serviceName = service != null && !service.isBlank() ? service : "living-agent-service";

        // 检查 Docker 容器状态
        String command = String.format("docker ps --filter name=%s --format {{.Status}}", serviceName);
        ProcessResult pr = executeCommand(command, workspaceRoot);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("action", "status");
        result.put("service", serviceName);
        result.put("running", pr.exitCode == 0 && !pr.output.isBlank());
        result.put("status", pr.output.trim());
        return result;
    }

    // ==================== 辅助方法 ====================

    private Path resolveProjectDir() {
        // 尝试在 workspace 下查找 living-agent-service 项目
        Path candidate = workspaceRoot.resolve("docker/living-agent-service");
        if (java.nio.file.Files.exists(candidate.resolve("pom.xml"))) {
            return candidate;
        }
        // 直接在 workspace 根目录查找
        if (java.nio.file.Files.exists(workspaceRoot.resolve("pom.xml"))) {
            return workspaceRoot;
        }
        return workspaceRoot;
    }

    private ProcessResult executeCommand(String command, Path workDir) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("sh", "-c", command)
            .directory(workDir.toFile())
            .redirectErrorStream(true);

        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return new ProcessResult(-1, "构建超时（" + timeoutSeconds + "秒）\n" + output);
        }

        return new ProcessResult(process.exitValue(), output.toString());
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "\n... (输出已截断，共 " + text.length() + " 字符)";
    }

    private record ProcessResult(int exitCode, String output) {}
}
