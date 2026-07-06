package com.livingagent.core.evolution.patch;

import com.livingagent.core.evolution.escalation.EscalationLevel;
import com.livingagent.core.evolution.escalation.EscalationNotificationService;
import com.livingagent.core.runtime.EvolutionNamespaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/**
 * 补丁应用服务
 * 大脑自主决定是否应用补丁（自毁即自伤的自然约束）
 * 修复前自动保存回滚基线（含受影响文件的原始内容）
 * 支持两种补丁格式：
 * - unified diff 格式（patchContent 为标准 diff）
 * - old_string→new_string 格式（patchContent 为 JSON: [{"file":"path","old":"...","new":"..."}]）
 * 失败时自动回滚
 */
public class PatchApplicationService {

    private static final Logger log = LoggerFactory.getLogger(PatchApplicationService.class);

    private final EvolutionNamespaceService namespaceService;
    private final PatchProposalService proposalService;
    private final EscalationNotificationService escalationService;

    public PatchApplicationService(
            EvolutionNamespaceService namespaceService,
            PatchProposalService proposalService,
            EscalationNotificationService escalationService) {
        this.namespaceService = namespaceService;
        this.proposalService = proposalService;
        this.escalationService = escalationService;
    }

    /**
     * 应用补丁（大脑自主决定）
     * 根据 confidence 决定行为：
     * - >= 0.8: 直接应用
     * - 0.5~0.8: 应用但通知人类
     * - < 0.5: 不应用，通知人类
     */
    public PatchProposal applyPatch(String proposalId) {
        PatchProposal proposal = proposalService.getProposal(proposalId);
        if (proposal == null) {
            log.warn("补丁提案不存在: {}", proposalId);
            return null;
        }

        double confidence = proposal.getConfidence();

        if (confidence < 0.5) {
            log.info("补丁确定性过低 ({}), 不自动应用，通知人类", confidence);
            escalationService.escalate(
                "evolution",
                EscalationLevel.WARNING,
                proposal.getBrainDomain(),
                "代码修复提案需要人类审查: " + proposal.getProposalContent(),
                String.join(", ", proposal.getAffectedFiles()),
                List.of(),
                proposal.getProposalContent()
            );
            return proposal;
        }

        // 1. 保存回滚基线（含受影响文件的原始内容）
        saveRollbackBaseline(proposal);

        // 2. 实际应用补丁到文件
        boolean applied = applyPatchContent(proposal);
        if (!applied) {
            log.warn("补丁内容应用失败，仅标记状态: id={}", proposalId);
        }

        // 3. 标记为已应用
        proposal.markApplied();
        proposal.setRollbackAvailable(true);

        // 4. 如果 0.5~0.8，通知人类
        if (confidence < 0.8) {
            escalationService.escalate(
                "evolution",
                EscalationLevel.WARNING,
                proposal.getBrainDomain(),
                "代码修复已自动应用（确定性=" + String.format("%.2f", confidence) + "）: " + proposal.getProposalContent(),
                String.join(", ", proposal.getAffectedFiles()),
                List.of(),
                null
            );
        }

        log.info("补丁已应用: id={}, confidence={}, domain={}, filesChanged={}",
            proposalId, confidence, proposal.getBrainDomain(), applied);

        return proposal;
    }

    /**
     * 回滚补丁：从回滚基线恢复受影响文件的原始内容
     */
    public boolean rollbackPatch(String proposalId) {
        PatchProposal proposal = proposalService.getProposal(proposalId);
        if (proposal == null || !proposal.isRollbackAvailable()) {
            log.warn("无法回滚补丁: id={}, rollbackAvailable={}",
                proposalId, proposal != null && proposal.isRollbackAvailable());
            return false;
        }

        try {
            Path rollbackDir = Paths.get(namespaceService.getEvolutionRollbackPath(proposal.getBrainDomain()));
            Path baselineMetaPath = rollbackDir.resolve(proposal.getProposalId() + "_baseline.json");

            if (!Files.exists(baselineMetaPath)) {
                log.warn("回滚基线文件不存在: {}", baselineMetaPath);
                proposal.markRolledBack();
                return true;
            }

            // 恢复每个受影响文件的原始内容
            int restoredCount = 0;
            for (String affectedFile : proposal.getAffectedFiles()) {
                Path contentBaseline = rollbackDir.resolve(proposal.getProposalId() + "_" + sanitizeFileName(affectedFile) + ".orig");
                if (Files.exists(contentBaseline)) {
                    Path targetFile = Paths.get(affectedFile);
                    if (Files.exists(targetFile)) {
                        Files.copy(contentBaseline, targetFile, StandardCopyOption.REPLACE_EXISTING);
                        restoredCount++;
                        log.info("Restored file from baseline: {}", affectedFile);
                    }
                }
            }

            proposal.markRolledBack();
            log.info("补丁已回滚: id={}, filesRestored={}", proposalId, restoredCount);
            return true;
        } catch (Exception e) {
            log.error("回滚补丁失败: id={}, error={}", proposalId, e.getMessage());
            return false;
        }
    }

    /**
     * 实际应用补丁内容到文件系统。
     * 支持 old_string→new_string 格式和 unified diff 格式。
     */
    private boolean applyPatchContent(PatchProposal proposal) {
        String patchContent = proposal.getPatchContent();
        if (patchContent == null || patchContent.isBlank()) {
            log.debug("补丁无 patchContent，跳过文件应用");
            return false;
        }

        List<String> affectedFiles = proposal.getAffectedFiles();
        if (affectedFiles == null || affectedFiles.isEmpty()) {
            log.debug("补丁无 affectedFiles，跳过文件应用");
            return false;
        }

        // 判断补丁格式并应用
        String trimmed = patchContent.trim();
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            // JSON 格式: old_string→new_string
            return applyJsonPatch(patchContent, affectedFiles);
        } else if (trimmed.startsWith("---") || trimmed.startsWith("diff ")) {
            // Unified diff 格式
            return applyUnifiedDiffPatch(patchContent, affectedFiles);
        } else {
            log.warn("无法识别补丁格式，首行: {}", trimmed.substring(0, Math.min(50, trimmed.length())));
            return false;
        }
    }

    /**
     * 应用 JSON 格式补丁：[{"file":"path","old":"...","new":"..."}, ...]
     */
    private boolean applyJsonPatch(String patchContent, List<String> affectedFiles) {
        int appliedCount = 0;
        // 简单 JSON 解析（不依赖 Jackson，与 SourceTreeIndexer 风格一致）
        // 支持单个对象和数组两种格式
        String[] entries;
        if (patchContent.trim().startsWith("[")) {
            // 数组格式，按 },{ 分割
            String inner = patchContent.trim();
            inner = inner.substring(1, inner.length() - 1).trim(); // 去掉外层 []
            entries = inner.split("\\},\\s*\\{");
        } else {
            entries = new String[]{patchContent.trim()};
        }

        for (String entry : entries) {
            try {
                String file = extractJsonString(entry, "file");
                String oldStr = extractJsonString(entry, "old");
                String newStr = extractJsonString(entry, "new");
                if (file == null || oldStr == null || newStr == null) {
                    log.warn("JSON 补丁条目缺少 file/old/new 字段: {}", entry);
                    continue;
                }

                Path targetPath = Paths.get(file);
                if (!Files.exists(targetPath)) {
                    log.warn("JSON 补丁目标文件不存在: {}", file);
                    continue;
                }

                String content = Files.readString(targetPath, StandardCharsets.UTF_8);
                int index = content.indexOf(oldStr);
                if (index < 0) {
                    log.warn("JSON 补丁 old_string 未在文件中找到: {}", file);
                    continue;
                }

                String newContent = content.substring(0, index) + newStr + content.substring(index + oldStr.length());
                Files.writeString(targetPath, newContent, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                appliedCount++;
                log.info("JSON patch applied: file={}, replaced {} chars with {} chars", file, oldStr.length(), newStr.length());
            } catch (Exception e) {
                log.warn("JSON 补丁条目应用失败: {}", e.getMessage());
            }
        }

        return appliedCount > 0;
    }

    /**
     * 应用 unified diff 格式补丁。
     * 解析每个 hunk 并逐个应用。
     */
    private boolean applyUnifiedDiffPatch(String patchContent, List<String> affectedFiles) {
        int appliedCount = 0;
        String[] lines = patchContent.split("\n");
        String currentFile = null;
        StringBuilder currentHunk = new StringBuilder();

        for (String line : lines) {
            if (line.startsWith("--- ")) {
                // 源文件标记，忽略（用 affectedFiles 列表）
                continue;
            } else if (line.startsWith("+++ ")) {
                // 目标文件标记
                currentFile = line.substring(4).trim();
                if (currentFile.startsWith("b/")) currentFile = currentFile.substring(2);
                continue;
            } else if (line.startsWith("@@ ")) {
                // 新 hunk 开始，先应用之前的 hunk
                if (currentFile != null && currentHunk.length() > 0) {
                    if (applyHunk(currentFile, currentHunk.toString())) {
                        appliedCount++;
                    }
                    currentHunk = new StringBuilder();
                }
                currentHunk.append(line).append("\n");
            } else if (line.startsWith("+") || line.startsWith("-") || line.startsWith(" ") || line.startsWith("\\ ")) {
                currentHunk.append(line).append("\n");
            }
        }

        // 应用最后一个 hunk
        if (currentFile != null && currentHunk.length() > 0) {
            if (applyHunk(currentFile, currentHunk.toString())) {
                appliedCount++;
            }
        }

        return appliedCount > 0;
    }

    /**
     * 应用单个 hunk 到文件。
     */
    private boolean applyHunk(String filePath, String hunk) {
        try {
            Path targetPath = Paths.get(filePath);
            if (!Files.exists(targetPath)) {
                log.warn("Diff hunk 目标文件不存在: {}", filePath);
                return false;
            }

            List<String> fileLines = new ArrayList<>(Files.readAllLines(targetPath, StandardCharsets.UTF_8));
            String[] hunkLines = hunk.split("\n");

            // 解析 @@ 行获取起始行号
            int targetLine = 0;
            for (String hl : hunkLines) {
                if (hl.startsWith("@@ ")) {
                    // @@ -a,b +c,d @@ → 我们需要 +c (目标起始行)
                    String rangePart = hl.substring(3);
                    int plusIdx = rangePart.indexOf('+');
                    int commaIdx = rangePart.indexOf(',', plusIdx);
                    int atIdx = rangePart.indexOf(" @@", plusIdx);
                    if (plusIdx >= 0) {
                        String startStr;
                        if (commaIdx > plusIdx) {
                            startStr = rangePart.substring(plusIdx + 1, commaIdx);
                        } else if (atIdx > plusIdx) {
                            startStr = rangePart.substring(plusIdx + 1, atIdx).trim();
                        } else {
                            startStr = rangePart.substring(plusIdx + 1).trim();
                        }
                        targetLine = Integer.parseInt(startStr) - 1; // 0-based index
                    }
                    break;
                }
            }

            // 应用修改行
            int currentLine = targetLine;
            List<String> result = new ArrayList<>(fileLines.subList(0, Math.max(0, currentLine)));

            for (String hl : hunkLines) {
                if (hl.startsWith("@@ ") || hl.startsWith("\\ ")) continue;
                if (hl.startsWith(" ")) {
                    // 上下文行，保留
                    if (currentLine < fileLines.size()) {
                        result.add(fileLines.get(currentLine));
                    }
                    currentLine++;
                } else if (hl.startsWith("-")) {
                    // 删除行，跳过
                    currentLine++;
                } else if (hl.startsWith("+")) {
                    // 添加行
                    result.add(hl.substring(1));
                }
            }

            // 添加剩余行
            if (currentLine < fileLines.size()) {
                result.addAll(fileLines.subList(currentLine, fileLines.size()));
            }

            Files.writeString(targetPath, String.join("\n", result) + "\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("Diff hunk applied: file={}, targetLine={}", filePath, targetLine);
            return true;
        } catch (Exception e) {
            log.warn("Diff hunk 应用失败: file={}, error={}", filePath, e.getMessage());
            return false;
        }
    }

    // 内部方法
    private void saveRollbackBaseline(PatchProposal proposal) {
        try {
            Path rollbackDir = Paths.get(namespaceService.getEvolutionRollbackPath(proposal.getBrainDomain()));
            Files.createDirectories(rollbackDir);

            // 保存每个受影响文件的原始内容（用于真正回滚）
            for (String affectedFile : proposal.getAffectedFiles()) {
                Path sourcePath = Paths.get(affectedFile);
                if (Files.exists(sourcePath) && Files.isRegularFile(sourcePath)) {
                    Path contentBaseline = rollbackDir.resolve(
                        proposal.getProposalId() + "_" + sanitizeFileName(affectedFile) + ".orig");
                    Files.copy(sourcePath, contentBaseline, StandardCopyOption.REPLACE_EXISTING);
                    log.debug("Saved content baseline for: {}", affectedFile);
                }
            }

            // 保存基线元数据
            String baseline = buildBaselineJson(proposal);
            Files.writeString(rollbackDir.resolve(proposal.getProposalId() + "_baseline.json"), baseline);

            log.info("回滚基线已保存: domain={}, proposal={}, files={}",
                proposal.getBrainDomain(), proposal.getProposalId(), proposal.getAffectedFiles().size());
        } catch (IOException e) {
            log.warn("保存回滚基线失败: {}", e.getMessage());
        }
    }

    private String sanitizeFileName(String path) {
        return path.replace("/", "_").replace("\\", "_").replace(":", "_");
    }

    private String buildBaselineJson(PatchProposal p) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"proposalId\": \"").append(escape(p.getProposalId())).append("\",\n");
        sb.append("  \"brainDomain\": \"").append(escape(p.getBrainDomain())).append("\",\n");
        sb.append("  \"savedAt\": \"").append(Instant.now()).append("\",\n");
        sb.append("  \"affectedFiles\": [");
        for (int i = 0; i < p.getAffectedFiles().size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("\"").append(escape(p.getAffectedFiles().get(i))).append("\"");
        }
        sb.append("],\n");
        sb.append("  \"patchContentHash\": \"").append(p.getPatchContent() != null ? Integer.toHexString(p.getPatchContent().hashCode()) : "").append("\"\n");
        sb.append("}");
        return sb.toString();
    }

    private String extractJsonString(String json, String key) {
        String searchKey = "\"" + key + "\":\"";
        int idx = json.indexOf(searchKey);
        if (idx < 0) return null;
        int start = idx + searchKey.length();
        // 查找结束引号（处理转义）
        StringBuilder result = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                if (next == '"' || next == '\\') {
                    result.append(next);
                    i++;
                } else if (next == 'n') {
                    result.append('\n');
                    i++;
                } else if (next == 't') {
                    result.append('\t');
                    i++;
                } else {
                    result.append(c);
                }
            } else if (c == '"') {
                return result.toString();
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
