package com.livingagent.core.evolution.escalation;

import com.livingagent.core.runtime.EvolutionNamespaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一升级通知服务
 * 作为所有升级的唯一出口，整合分散在 EvolutionDecisionEngine、
 * InterventionDecisionEngine、StandardComplianceTraceService 三处的升级逻辑
 */
public class EscalationNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EscalationNotificationService.class);

    private final EvolutionNamespaceService namespaceService;
    private final Map<String, EscalationRecord> pendingEscalations = new ConcurrentHashMap<>();

    public EscalationNotificationService(EvolutionNamespaceService namespaceService) {
        this.namespaceService = namespaceService;
    }

    /**
     * 统一升级入口
     */
    public EscalationRecord escalate(
            String source,
            EscalationLevel level,
            String brainDomain,
            String problem,
            String codeContext,
            List<String> attemptedFixes,
            String suggestion) {

        EscalationRecord record = EscalationRecord.of(level, source, brainDomain, problem);
        record.setCodeContext(codeContext);
        if (attemptedFixes != null) {
            for (String fix : attemptedFixes) {
                record.withAttemptedFix(fix);
            }
        }
        record.withSuggestion(suggestion);

        // 1. 保存到 pending 目录
        saveEscalation(record);

        // 2. 加入内存缓存
        pendingEscalations.put(record.getEscalationId(), record);

        // 3. 记录日志
        log.warn("升级通知: level={}, source={}, domain={}, problem={}",
            level, source, brainDomain, problem);

        // 4. 如果是 EMERGENCY 级别，额外处理
        if (level == EscalationLevel.EMERGENCY) {
            log.error("!!! 紧急升级 !!! id={}, problem={}", record.getEscalationId(), problem);
        }

        return record;
    }

    /** 获取待处理升级列表 */
    public List<EscalationRecord> getPendingEscalations() {
        return new ArrayList<>(pendingEscalations.values());
    }

    /** 获取指定升级记录 */
    public EscalationRecord getEscalation(String escalationId) {
        return pendingEscalations.get(escalationId);
    }

    /** 解决升级 */
    public void resolveEscalation(String escalationId, String resolution, String resolvedBy) {
        EscalationRecord record = pendingEscalations.remove(escalationId);
        if (record != null) {
            record.resolve(resolution, resolvedBy);
            // 移动到 resolved 目录
            moveEscalationToResolved(record);
            log.info("升级已解决: id={}, resolvedBy={}", escalationId, resolvedBy);
        }
    }

    /** 驳回升级 */
    public void dismissEscalation(String escalationId, String reason) {
        EscalationRecord record = pendingEscalations.remove(escalationId);
        if (record != null) {
            record.dismiss(reason);
            moveEscalationToResolved(record);
            log.info("升级已驳回: id={}, reason={}", escalationId, reason);
        }
    }

    // 内部方法
    private void saveEscalation(EscalationRecord record) {
        try {
            Path pendingPath = Paths.get(namespaceService.getEscalationPendingPath(record.getEscalationId()));
            Files.createDirectories(pendingPath.getParent());
            String json = recordToJson(record);
            Files.writeString(pendingPath.resolve("record.json"), json);
        } catch (Exception e) {
            log.warn("保存升级记录失败: {}", e.getMessage());
        }
    }

    private void moveEscalationToResolved(EscalationRecord record) {
        try {
            // 从 pending 删除
            Path pendingFile = Paths.get(namespaceService.getEscalationPendingPath(record.getEscalationId()))
                .resolve("record.json");
            Files.deleteIfExists(pendingFile);

            // 写入 resolved
            Path resolvedPath = Paths.get(namespaceService.getEscalationResolvedPath(record.getEscalationId()));
            Files.createDirectories(resolvedPath);
            String json = recordToJson(record);
            Files.writeString(resolvedPath.resolve("record.json"), json);
        } catch (Exception e) {
            log.warn("移动升级记录失败: {}", e.getMessage());
        }
    }

    /** 简单的 JSON 序列化（不依赖 Jackson） */
    private String recordToJson(EscalationRecord r) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"escalationId\": \"").append(escape(r.getEscalationId())).append("\",\n");
        sb.append("  \"level\": \"").append(r.getLevel().getValue()).append("\",\n");
        sb.append("  \"source\": \"").append(escape(r.getSource())).append("\",\n");
        sb.append("  \"brainDomain\": \"").append(escape(r.getBrainDomain())).append("\",\n");
        sb.append("  \"problem\": \"").append(escape(r.getProblem())).append("\",\n");
        if (r.getCodeContext() != null) sb.append("  \"codeContext\": \"").append(escape(r.getCodeContext())).append("\",\n");
        sb.append("  \"attemptedFixes\": [");
        for (int i = 0; i < r.getAttemptedFixes().size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("\"").append(escape(r.getAttemptedFixes().get(i))).append("\"");
        }
        sb.append("],\n");
        if (r.getSuggestion() != null) sb.append("  \"suggestion\": \"").append(escape(r.getSuggestion())).append("\",\n");
        if (r.getImpactScope() != null) sb.append("  \"impactScope\": \"").append(escape(r.getImpactScope())).append("\",\n");
        sb.append("  \"status\": \"").append(r.getStatus()).append("\",\n");
        sb.append("  \"createdAt\": \"").append(r.getCreatedAt()).append("\"\n");
        if (r.getResolvedAt() != null) {
            sb.append(",  \"resolvedAt\": \"").append(r.getResolvedAt()).append("\"\n");
            sb.append(",  \"resolution\": \"").append(escape(r.getResolution())).append("\"\n");
            if (r.getResolvedBy() != null) sb.append(",  \"resolvedBy\": \"").append(escape(r.getResolvedBy())).append("\"\n");
        }
        sb.append("}");
        return sb.toString();
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
