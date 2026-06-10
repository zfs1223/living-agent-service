package com.livingagent.core.knowledge;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 知识库文件镜像服务
 *
 * 将知识库三层（L1/L2/L3）的数据按目录结构同步到文件系统：
 * - data/personal-knowledge/{employeeCode}/experiences.jsonl
 * - data/department-knowledge/{dept}/best-practices.jsonl
 * - data/knowledge/shared/policies.jsonl
 *
 * 用途：
 * - 方便在宿主机直接查看/编辑知识库内容
 * - 支持离线审计
 * - 文档型资料文件夹（与 docker-compose 中已挂载的目录对齐）
 */
@Service
public class KnowledgeFileMirrorService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeFileMirrorService.class);

    private static final String PERSONAL_DIR = "data/personal-knowledge";
    private static final String DEPARTMENT_DIR = "data/department-knowledge";
    private static final String SHARED_DIR = "data/knowledge";

    @Autowired(required = false)
    private KnowledgeManager knowledgeManager;

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(Paths.get(PERSONAL_DIR));
            Files.createDirectories(Paths.get(DEPARTMENT_DIR));
            Files.createDirectories(Paths.get(SHARED_DIR));
            log.info("Knowledge file mirror directories initialized: {} | {} | {}",
                PERSONAL_DIR, DEPARTMENT_DIR, SHARED_DIR);
        } catch (IOException e) {
            log.error("Failed to initialize knowledge mirror directories: {}", e.getMessage());
        }
    }

    /**
     * 写入个人知识条目
     */
    public void writePrivateKnowledge(String employeeCode, String key, String content, Map<String, String> metadata) {
        if (employeeCode == null || employeeCode.isBlank()) return;
        writeToJsonl(
            Paths.get(PERSONAL_DIR, sanitize(employeeCode), "experiences.jsonl"),
            Map.of(
                "timestamp", Instant.now().toString(),
                "employeeCode", employeeCode,
                "key", key != null ? key : "",
                "content", content != null ? content : "",
                "metadata", String.valueOf(metadata)
            )
        );
    }

    /**
     * 写入部门知识条目
     */
    public void writeDepartmentKnowledge(String department, String key, String content, Map<String, String> metadata) {
        if (department == null || department.isBlank()) return;
        writeToJsonl(
            Paths.get(DEPARTMENT_DIR, sanitize(department), "best-practices.jsonl"),
            Map.of(
                "timestamp", Instant.now().toString(),
                "department", department,
                "key", key != null ? key : "",
                "content", content != null ? content : "",
                "metadata", String.valueOf(metadata)
            )
        );
    }

    /**
     * 写入共享知识条目
     */
    public void writeSharedKnowledge(String key, String content, Map<String, String> metadata) {
        writeToJsonl(
            Paths.get(SHARED_DIR, "shared", "policies.jsonl"),
            Map.of(
                "timestamp", Instant.now().toString(),
                "key", key != null ? key : "",
                "content", content != null ? content : "",
                "metadata", String.valueOf(metadata)
            )
        );
    }

    private void writeToJsonl(Path file, Map<String, String> entry) {
        try {
            Files.createDirectories(file.getParent());
            // 简化 JSON 序列化（避免引入 ObjectMapper）
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            boolean first = true;
            for (Map.Entry<String, String> e : entry.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(escapeJson(e.getKey())).append("\":");
                sb.append("\"").append(escapeJson(e.getValue())).append("\"");
                first = false;
            }
            sb.append("}\n");
            Files.writeString(file, sb.toString(),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn("Failed to write knowledge mirror to {}: {}", file, e.getMessage());
        }
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r");
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) return "unknown";
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
