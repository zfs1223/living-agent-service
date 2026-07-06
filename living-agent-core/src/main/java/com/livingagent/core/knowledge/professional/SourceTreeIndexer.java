package com.livingagent.core.knowledge.professional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/**
 * 源码结构索引生成器
 * 扫描项目源码目录，生成 source-tree.json
 * 让大脑了解项目的代码组织和关键文件功能
 */
public class SourceTreeIndexer {

    private static final Logger log = LoggerFactory.getLogger(SourceTreeIndexer.class);

    // 最大扫描深度
    private static final int MAX_SCAN_DEPTH = 3;

    // 模块描述映射
    private static final Map<String, String> MODULE_DESCRIPTIONS = Map.ofEntries(
        Map.entry("living-agent-app", "启动模块 (Spring Boot + Dockerfile)"),
        Map.entry("living-agent-core", "核心领域层 (大脑/进化/知识/安全/技能/工具)"),
        Map.entry("living-agent-gateway", "网关模块 (REST API + WebSocket)"),
        Map.entry("living-agent-perception", "感知模块 (ASR/TTS)"),
        Map.entry("living-agent-skill", "技能模块"),
        Map.entry("living-agent-native", "Rust原生模块 (音频/管道/压缩/安全/存储)"),
        Map.entry("frontend", "前端 (React + Vite + TypeScript)"),
        Map.entry("documents", "企业知识源 (部门架构/员工编制/职责卡)"),
        Map.entry("docs", "设计文档"),
        Map.entry("init-db", "数据库初始化脚本")
    );

    // 核心包描述映射（living-agent-core 下的子包）
    private static final Map<String, String> CORE_PACKAGE_DESCRIPTIONS = Map.ofEntries(
        Map.entry("autonomy", "对话自治 (意图分析/编排/Trace)"),
        Map.entry("brain", "大脑处理 (决策/边界/Prompt)"),
        Map.entry("channel", "通道通信"),
        Map.entry("employee", "员工管理"),
        Map.entry("evolution", "进化闭环 (信号/决策/执行/熔断)"),
        Map.entry("knowledge", "知识系统 (三层知识库/RAG)"),
        Map.entry("memory", "记忆系统"),
        Map.entry("model", "模型管理"),
        Map.entry("security", "安全权限"),
        Map.entry("skill", "技能注册"),
        Map.entry("tool", "工具注册"),
        Map.entry("config", "配置"),
        Map.entry("runtime", "运行时 (事件/Trace/命名空间)"),
        Map.entry("database", "数据库实体"),
        Map.entry("intervention", "干预决策")
    );

    // 关键文件描述映射
    private static final Map<String, String> KEY_FILE_ROLES = Map.ofEntries(
        Map.entry("Brain.java", "大脑统一接口"),
        Map.entry("AbstractBrain.java", "大脑公共基类"),
        Map.entry("BrainBoundaryEnforcer.java", "大脑职责边界硬判断"),
        Map.entry("BrainRegistry.java", "大脑注册表"),
        Map.entry("BrainOutputContract.java", "大脑输出契约"),
        Map.entry("DynamicPromptBuilder.java", "动态提示词构建器"),
        Map.entry("InstructionFileLoader.java", "指令文件加载器"),
        Map.entry("StandardLoadingChainService.java", "规范强制加载链"),
        Map.entry("EvolutionOrchestrator.java", "进化编排器"),
        Map.entry("EvolutionExecutor.java", "进化执行器"),
        Map.entry("EvolutionCircuitBreaker.java", "进化熔断器"),
        Map.entry("EvolutionDecisionEngine.java", "进化决策引擎"),
        Map.entry("DataNamespaceService.java", "数据命名空间服务"),
        Map.entry("RuntimeEventStore.java", "运行时事件存储"),
        Map.entry("StandardComplianceTraceService.java", "标准合规追踪"),
        Map.entry("ProfessionalKnowledgeSeeder.java", "专业知识播种器"),
        Map.entry("FixedEmployeeRegistry.java", "固定员工注册表"),
        Map.entry("ExecutionBoundaryEnforcer.java", "员工越权拦截"),
        Map.entry("EmployeeWorkAssignment.java", "员工任务单"),
        Map.entry("EmployeeTaskExecutor.java", "员工执行器"),
        Map.entry("EmployeeExecutionReceiptService.java", "员工回执服务"),
        Map.entry("KnowledgeManagerImpl.java", "知识管理器实现"),
        Map.entry("LayeredKnowledgeBaseImpl.java", "分层知识库实现")
    );

    /**
     * 生成源码结构索引
     * @param projectRoot 项目根目录
     * @param outputPath 输出路径（source-tree.json）
     * @return 生成的模块数
     */
    public int generateIndex(Path projectRoot, Path outputPath) {
        if (projectRoot == null || !Files.isDirectory(projectRoot)) {
            log.warn("项目根目录不存在: {}", projectRoot);
            return 0;
        }

        Map<String, Object> index = new LinkedHashMap<>();
        index.put("generatedAt", Instant.now().toString());
        index.put("projectRoot", projectRoot.getFileName().toString());

        Map<String, Object> modules = new LinkedHashMap<>();

        // 遍历项目根目录下的模块
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(projectRoot)) {
            for (Path entry : stream) {
                if (!Files.isDirectory(entry)) {
                    continue;
                }
                String moduleName = entry.getFileName().toString();
                // 只处理已知模块
                if (!MODULE_DESCRIPTIONS.containsKey(moduleName)) {
                    continue;
                }

                Map<String, Object> moduleInfo = scanModule(entry);
                modules.put(moduleName, moduleInfo);
            }
        } catch (IOException e) {
            log.warn("扫描项目根目录失败: {}", projectRoot, e);
            return 0;
        }

        index.put("modules", modules);

        // 写入JSON文件
        writeJson(index, outputPath);

        int moduleCount = modules.size();
        log.info("源码结构索引生成完成，共 {} 个模块，输出至: {}", moduleCount, outputPath);
        return moduleCount;
    }

    /**
     * 扫描单个模块目录
     * @param moduleDir 模块目录
     * @return 模块信息映射
     */
    Map<String, Object> scanModule(Path moduleDir) {
        String moduleName = moduleDir.getFileName().toString();
        Map<String, Object> moduleInfo = new LinkedHashMap<>();
        moduleInfo.put("description", MODULE_DESCRIPTIONS.getOrDefault(moduleName, ""));

        // living-agent-core 需要扫描子包结构
        if ("living-agent-core".equals(moduleName)) {
            Path coreSrcDir = moduleDir.resolve("src/main/java/com/livingagent/core");
            if (Files.isDirectory(coreSrcDir)) {
                Map<String, Object> packages = scanCorePackages(coreSrcDir);
                if (!packages.isEmpty()) {
                    moduleInfo.put("packages", packages);
                }
            }
        } else {
            // 其他模块查找关键文件
            List<Map<String, String>> keyFiles = findKeyFiles(moduleDir, MAX_SCAN_DEPTH);
            if (!keyFiles.isEmpty()) {
                moduleInfo.put("keyFiles", keyFiles);
            }
        }

        return moduleInfo;
    }

    /**
     * 扫描 living-agent-core 的子包
     * @param coreDir core 目录路径
     * @return 包信息映射
     */
    Map<String, Object> scanCorePackages(Path coreDir) {
        Map<String, Object> packages = new LinkedHashMap<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(coreDir)) {
            for (Path entry : stream) {
                if (!Files.isDirectory(entry)) {
                    continue;
                }
                String packageName = entry.getFileName().toString();
                String description = CORE_PACKAGE_DESCRIPTIONS.getOrDefault(packageName, "");

                Map<String, Object> packageInfo = new LinkedHashMap<>();
                if (!description.isEmpty()) {
                    packageInfo.put("description", description);
                }

                // 查找该包下的关键文件
                List<Map<String, String>> keyFiles = findKeyFiles(entry, MAX_SCAN_DEPTH);
                if (!keyFiles.isEmpty()) {
                    packageInfo.put("keyFiles", keyFiles);
                }

                // 只添加有描述或有keyFiles的包
                if (!packageInfo.isEmpty()) {
                    packages.put("core/" + packageName, packageInfo);
                }
            }
        } catch (IOException e) {
            log.warn("扫描core子包失败: {}", coreDir, e);
        }

        return packages;
    }

    /**
     * 查找目录下的关键文件（.java 文件）
     * @param dir 目录路径
     * @param maxDepth 最大扫描深度
     * @return 关键文件列表
     */
    List<Map<String, String>> findKeyFiles(Path dir, int maxDepth) {
        List<Map<String, String>> keyFiles = new ArrayList<>();

        try {
            Files.walk(dir, maxDepth)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(p -> {
                    String fileName = p.getFileName().toString();
                    String role = getFileRole(fileName);
                    if (role != null) {
                        // 计算相对路径（相对于扫描起始目录）
                        String relativePath = dir.relativize(p).toString().replace('\\', '/');
                        Map<String, String> fileInfo = new LinkedHashMap<>();
                        fileInfo.put("path", relativePath);
                        fileInfo.put("role", role);
                        keyFiles.add(fileInfo);
                    }
                });
        } catch (IOException e) {
            log.warn("查找关键文件失败: {}", dir, e);
        }

        return keyFiles;
    }

    /**
     * 获取文件角色描述
     * @param fileName 文件名
     * @return 角色描述，未知文件返回 null
     */
    String getFileRole(String fileName) {
        return KEY_FILE_ROLES.get(fileName);
    }

    /**
     * 将索引数据写入JSON文件（手动拼接，不依赖Jackson）
     * @param index 索引数据
     * @param outputPath 输出路径
     */
    void writeJson(Map<String, Object> index, Path outputPath) {
        try {
            // 确保输出目录存在
            Path parentDir = outputPath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            String json = buildJsonString(index);
            Files.writeString(outputPath, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("源码结构索引已写入: {}", outputPath);
        } catch (IOException e) {
            log.warn("写入source-tree.json失败: {}", outputPath, e);
        }
    }

    /**
     * 手动构建JSON字符串
     * @param data 数据映射
     * @return JSON字符串
     */
    @SuppressWarnings("unchecked")
    private String buildJsonString(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        appendMapEntries(sb, data, 1);
        // 移除末尾多余的逗号和换行
        removeTrailingComma(sb);
        sb.append("\n}");
        return sb.toString();
    }

    /**
     * 递归拼接Map条目
     */
    @SuppressWarnings("unchecked")
    private void appendMapEntries(StringBuilder sb, Map<String, Object> map, int indent) {
        String prefix = "  ".repeat(indent);
        int index = 0;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (index > 0) {
                sb.append(",\n");
            } else {
                sb.append("\n");
            }
            sb.append(prefix).append("\"").append(escapeJson(entry.getKey())).append("\": ");
            appendValue(sb, entry.getValue(), indent);
            index++;
        }
    }

    /**
     * 拼接值（根据类型递归处理）
     */
    @SuppressWarnings("unchecked")
    private void appendValue(StringBuilder sb, Object value, int indent) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            sb.append("\"").append(escapeJson((String) value)).append("\"");
        } else if (value instanceof Map) {
            sb.append("{");
            appendMapEntries(sb, (Map<String, Object>) value, indent + 1);
            removeTrailingComma(sb);
            sb.append("\n").append("  ".repeat(indent)).append("}");
        } else if (value instanceof List) {
            appendList(sb, (List<Object>) value, indent);
        } else {
            sb.append("\"").append(escapeJson(value.toString())).append("\"");
        }
    }

    /**
     * 拼接列表
     */
    @SuppressWarnings("unchecked")
    private void appendList(StringBuilder sb, List<Object> list, int indent) {
        if (list.isEmpty()) {
            sb.append("[]");
            return;
        }

        String prefix = "  ".repeat(indent + 1);
        sb.append("[");

        // 判断列表元素是否为简单Map（keyFiles结构）
        boolean allMaps = list.stream().allMatch(e -> e instanceof Map);

        if (allMaps) {
            // Map元素换行展示
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    sb.append(",");
                }
                sb.append("\n").append(prefix);
                Map<String, Object> item = (Map<String, Object>) list.get(i);
                sb.append("{");
                appendMapEntries(sb, item, indent + 2);
                removeTrailingComma(sb);
                sb.append("\n").append(prefix).append("}");
            }
            sb.append("\n").append("  ".repeat(indent)).append("]");
        } else {
            // 简单值同行展示
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                appendValue(sb, list.get(i), indent + 1);
            }
            sb.append("]");
        }
    }

    /**
     * 移除StringBuilder末尾的逗号（用于JSON格式修正）
     */
    private void removeTrailingComma(StringBuilder sb) {
        // 从末尾向前查找，跳过空白，找到逗号则移除
        int len = sb.length();
        int i = len - 1;
        while (i >= 0 && (sb.charAt(i) == '\n' || sb.charAt(i) == '\r' || sb.charAt(i) == ' ' || sb.charAt(i) == '\t')) {
            i--;
        }
        if (i >= 0 && sb.charAt(i) == ',') {
            sb.deleteCharAt(i);
        }
    }

    /**
     * JSON字符串转义
     */
    private String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
