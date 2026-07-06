package com.livingagent.core.evolution.codemapper;

import com.livingagent.core.evolution.signal.EvolutionSignal;
import com.livingagent.core.runtime.EvolutionNamespaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 错误到代码映射器
 * 建立异常类/错误码 → 代码文件 → 架构文档的映射
 * 让大脑在遇到异常时能快速定位到具体代码位置
 */
public class ErrorCodeMapper {

    private static final Logger log = LoggerFactory.getLogger(ErrorCodeMapper.class);

    // 异常模式 → CodeContext 映射
    private final Map<String, CodeContext> exceptionMappings = new ConcurrentHashMap<>();

    // 进化空间命名空间服务（可选依赖，用于定位 error-mappings.yaml）
    private EvolutionNamespaceService namespaceService;

    public ErrorCodeMapper() {
        initDefaultMappings();
    }

    /**
     * 设置进化空间命名空间服务（可选依赖）
     */
    public void setNamespaceService(EvolutionNamespaceService namespaceService) {
        this.namespaceService = namespaceService;
    }

    /**
     * 从异常映射到代码上下文
     */
    public CodeContext map(Throwable error) {
        if (error == null) {
            return new CodeContext();
        }

        // 1. 先检查异常类上的 @CodeLocation 注解
        CodeLocation location = error.getClass().getAnnotation(CodeLocation.class);
        if (location != null) {
            CodeContext ctx = new CodeContext();
            ctx.setModule(location.module());
            ctx.setDescription(location.description());
            ctx.setDocRef(location.docRef());
            ctx.setRiskLevel(location.riskLevel());
            return ctx;
        }

        // 2. 按异常类名模式匹配
        String className = error.getClass().getSimpleName();
        for (Map.Entry<String, CodeContext> entry : exceptionMappings.entrySet()) {
            if (className.contains(entry.getKey()) || className.matches(entry.getKey())) {
                return copyContext(entry.getValue());
            }
        }

        // 3. 按异常消息关键词匹配
        String message = error.getMessage();
        if (message != null) {
            for (Map.Entry<String, CodeContext> entry : exceptionMappings.entrySet()) {
                if (message.toLowerCase().contains(entry.getKey().toLowerCase())) {
                    return copyContext(entry.getValue());
                }
            }
        }

        // 4. 未匹配，返回空上下文
        CodeContext unknown = new CodeContext();
        unknown.setModule("unknown");
        unknown.setDescription("无法定位到具体代码位置");
        return unknown;
    }

    /**
     * 从进化信号映射到代码上下文
     */
    public CodeContext map(EvolutionSignal signal) {
        if (signal == null) {
            return new CodeContext();
        }

        String domain = signal.getBrainDomain();
        CodeContext ctx = new CodeContext();

        // 按大脑域映射
        switch (domain != null ? domain.toLowerCase() : "") {
            case "tech": case "technology":
                ctx.setModule("core/brain/impl/TechBrain");
                ctx.setDescription("技术部大脑");
                ctx.setDocRef("docs/BRAIN_AND_EMPLOYEE_STANDARDS_INDEX.md#TechBrain");
                break;
            case "hr": case "human-resources":
                ctx.setModule("core/brain/impl/HrBrain");
                ctx.setDescription("人力资源部大脑");
                ctx.setDocRef("docs/BRAIN_AND_EMPLOYEE_STANDARDS_INDEX.md#HrBrain");
                break;
            case "finance": case "financial":
                ctx.setModule("core/brain/impl/FinanceBrain");
                ctx.setDescription("财务部大脑");
                ctx.setDocRef("docs/BRAIN_AND_EMPLOYEE_STANDARDS_INDEX.md#FinanceBrain");
                break;
            case "sales": case "marketing":
                ctx.setModule("core/brain/impl/SalesBrain");
                ctx.setDescription("销售部大脑");
                ctx.setDocRef("docs/BRAIN_AND_EMPLOYEE_STANDARDS_INDEX.md#SalesBrain");
                break;
            case "cs": case "customer-service":
                ctx.setModule("core/brain/impl/CsBrain");
                ctx.setDescription("客服部大脑");
                ctx.setDocRef("docs/BRAIN_AND_EMPLOYEE_STANDARDS_INDEX.md#CsBrain");
                break;
            case "admin": case "administration":
                ctx.setModule("core/brain/impl/AdminBrain");
                ctx.setDescription("行政部大脑");
                ctx.setDocRef("docs/BRAIN_AND_EMPLOYEE_STANDARDS_INDEX.md#AdminBrain");
                break;
            case "legal":
                ctx.setModule("core/brain/impl/LegalBrain");
                ctx.setDescription("法务部大脑");
                ctx.setDocRef("docs/BRAIN_AND_EMPLOYEE_STANDARDS_INDEX.md#LegalBrain");
                break;
            case "ops": case "operations":
                ctx.setModule("core/brain/impl/OpsBrain");
                ctx.setDescription("运营部大脑");
                ctx.setDocRef("docs/BRAIN_AND_EMPLOYEE_STANDARDS_INDEX.md#OpsBrain");
                break;
            default:
                ctx.setModule("core/brain/impl/MainBrain");
                ctx.setDescription("主脑/总控");
                ctx.setDocRef("docs/BRAIN_AND_EMPLOYEE_STANDARDS_INDEX.md#MainBrain");
                break;
        }

        ctx.setRiskLevel("MEDIUM");
        return ctx;
    }

    /**
     * 注册自定义异常映射
     */
    public void registerMapping(String exceptionPattern, CodeContext context) {
        exceptionMappings.put(exceptionPattern, context);
        log.debug("注册异常映射: {} -> {}", exceptionPattern, context.getModule());
    }

    /**
     * 刷新映射表
     */
    public void refreshMappings() {
        exceptionMappings.clear();
        initDefaultMappings();

        // 尝试从 YAML 文件加载自定义映射
        if (namespaceService != null) {
            try {
                Path yamlPath = Paths.get(namespaceService.getCodebasePath(), "error-mappings.yaml");
                if (Files.exists(yamlPath)) {
                    int loaded = loadFromYaml(yamlPath);
                    log.info("从 YAML 加载了 {} 条自定义错误映射", loaded);
                }
            } catch (Exception e) {
                log.warn("加载 YAML 错误映射失败: {}", e.getMessage());
            }
        }

        log.info("错误映射表已刷新，共 {} 条映射", exceptionMappings.size());
    }

    private void initDefaultMappings() {
        // 大脑边界违规
        registerMapping("BrainBoundaryViolation", CodeContext.of("core/brain",
            "BrainBoundaryEnforcer.java", "ExecutionBoundaryEnforcer.java")
            .withDescription("大脑职责边界违规")
            .withDocRef("docs/BRAIN_AND_EMPLOYEE_STANDARDS_INDEX.md#2.2")
            .withRiskLevel("HIGH"));

        // 进化熔断
        registerMapping("EvolutionCircuitBreaker", CodeContext.of("core/evolution/circuitbreaker",
            "EvolutionCircuitBreaker.java")
            .withDescription("进化熔断器触发")
            .withDocRef("documents/shared/governance/05-evolution-governance.md")
            .withRiskLevel("CRITICAL"));

        // 知识存储异常
        registerMapping("KnowledgeStore", CodeContext.of("core/knowledge",
            "LayeredKnowledgeBaseImpl.java", "KnowledgeManagerImpl.java")
            .withDescription("知识存储异常")
            .withDocRef("docs/CODE_STRUCTURE_AND_FILE_GUIDE.md#knowledge")
            .withRiskLevel("MEDIUM"));

        // 员工越权
        registerMapping("EmployeeBoundary", CodeContext.of("core/security",
            "ExecutionBoundaryEnforcer.java")
            .withDescription("员工越权操作")
            .withDocRef("docs/BRAIN_AND_EMPLOYEE_STANDARDS_INDEX.md#5.2")
            .withRiskLevel("HIGH"));

        // 工具执行异常
        registerMapping("ToolExecution", CodeContext.of("core/tool",
            "ToolBackedEmployeeTaskExecutor.java")
            .withDescription("工具执行异常")
            .withRiskLevel("MEDIUM"));

        // 模型调用异常
        registerMapping("ModelCall", CodeContext.of("core/model",
            "BrainModelResolver.java")
            .withDescription("模型调用异常")
            .withRiskLevel("MEDIUM"));

        // WebSocket异常
        registerMapping("WebSocket", CodeContext.of("gateway/websocket",
            "DepartmentWebSocketHandler.java", "AgentWebSocketHandler.java")
            .withDescription("WebSocket连接异常")
            .withRiskLevel("MEDIUM"));

        // 审批异常
        registerMapping("Approval", CodeContext.of("gateway/controller",
            "ApprovalController.java")
            .withDescription("审批流程异常")
            .withRiskLevel("HIGH"));

        // 安全异常
        registerMapping("Security", CodeContext.of("core/security",
            "ExecutionBoundaryEnforcer.java", "SandboxExecutor.java")
            .withDescription("安全权限异常")
            .withDocRef("documents/shared/governance/06-security-governance.md")
            .withRiskLevel("CRITICAL"));
    }

    private CodeContext copyContext(CodeContext original) {
        CodeContext copy = new CodeContext();
        copy.setModule(original.getModule());
        copy.setDescription(original.getDescription());
        copy.setFiles(new ArrayList<>(original.getFiles()));
        copy.setDocRef(original.getDocRef());
        copy.setRiskLevel(original.getRiskLevel());
        copy.setSuggestedFix(original.getSuggestedFix());
        return copy;
    }

    /**
     * 从 YAML 文件加载错误映射
     *
     * @param yamlPath YAML 文件路径
     * @return 加载的映射条目数
     */
    public int loadFromYaml(Path yamlPath) {
        try {
            String content = Files.readString(yamlPath);
            return loadFromYamlString(content);
        } catch (IOException e) {
            log.warn("读取 YAML 文件失败 [{}]: {}", yamlPath, e.getMessage());
            return 0;
        }
    }

    /**
     * 从 YAML 字符串加载错误映射
     * 简单手动解析，不依赖 SnakeYAML 等第三方库
     * <p>
     * 支持的 YAML 格式：
     * <pre>
     * mappings:
     *   - exceptionPattern: "BrainBoundaryViolationException"
     *     module: "core/brain"
     *     files: "BrainBoundaryEnforcer.java,ExecutionBoundaryEnforcer.java"
     *     docRef: "docs/BRAIN_AND_EMPLOYEE_STANDARDS_INDEX.md#2.2"
     *     riskLevel: "HIGH"
     * </pre>
     *
     * @param yamlContent YAML 内容字符串
     * @return 加载的映射条目数
     */
    public int loadFromYamlString(String yamlContent) {
        if (yamlContent == null || yamlContent.isBlank()) {
            return 0;
        }

        int count = 0;
        // 当前正在构建的映射条目
        String exceptionPattern = null;
        String module = null;
        String files = null;
        String docRef = null;
        String riskLevel = null;

        for (String line : yamlContent.split("\n")) {
            String trimmed = line.trim();

            // 跳过空行和注释
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }

            // 识别新条目开始：以 "- exceptionPattern:" 开头
            if (trimmed.startsWith("- exceptionPattern:")) {
                // 先保存上一个条目
                if (exceptionPattern != null) {
                    registerYamlEntry(exceptionPattern, module, files, docRef, riskLevel);
                    count++;
                }
                // 开始新条目
                exceptionPattern = extractYamlValue(trimmed, "- exceptionPattern:");
                module = null;
                files = null;
                docRef = null;
                riskLevel = null;
                continue;
            }

            // 提取各字段值
            if (trimmed.startsWith("exceptionPattern:")) {
                exceptionPattern = extractYamlValue(trimmed, "exceptionPattern:");
            } else if (trimmed.startsWith("module:")) {
                module = extractYamlValue(trimmed, "module:");
            } else if (trimmed.startsWith("files:")) {
                files = extractYamlValue(trimmed, "files:");
            } else if (trimmed.startsWith("docRef:")) {
                docRef = extractYamlValue(trimmed, "docRef:");
            } else if (trimmed.startsWith("riskLevel:")) {
                riskLevel = extractYamlValue(trimmed, "riskLevel:");
            }
        }

        // 注册最后一个条目
        if (exceptionPattern != null) {
            registerYamlEntry(exceptionPattern, module, files, docRef, riskLevel);
            count++;
        }

        return count;
    }

    /**
     * 提取 YAML 行中冒号后的值，去除引号和首尾空白
     */
    private String extractYamlValue(String line, String prefix) {
        String value = line.substring(prefix.length()).trim();
        // 去除引号包裹
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            value = value.substring(1, value.length() - 1);
        } else if (value.startsWith("'") && value.endsWith("'") && value.length() >= 2) {
            value = value.substring(1, value.length() - 1);
        }
        return value.isEmpty() ? null : value;
    }

    /**
     * 将 YAML 解析出的字段注册为映射条目
     */
    private void registerYamlEntry(String exceptionPattern, String module, String files, String docRef, String riskLevel) {
        if (exceptionPattern == null || exceptionPattern.isBlank()) {
            return;
        }

        CodeContext ctx = new CodeContext();
        ctx.setModule(module != null ? module : "unknown");

        // 解析逗号分隔的文件列表
        if (files != null && !files.isBlank()) {
            List<String> fileList = new ArrayList<>();
            for (String f : files.split(",")) {
                String trimmed = f.trim();
                if (!trimmed.isEmpty()) {
                    fileList.add(trimmed);
                }
            }
            ctx.setFiles(fileList);
        }

        if (docRef != null) {
            ctx.setDocRef(docRef);
        }
        if (riskLevel != null) {
            ctx.setRiskLevel(riskLevel);
        }

        registerMapping(exceptionPattern, ctx);
    }
}
