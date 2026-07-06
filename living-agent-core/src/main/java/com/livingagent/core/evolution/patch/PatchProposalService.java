package com.livingagent.core.evolution.patch;

import com.livingagent.core.runtime.EvolutionNamespaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 补丁提案服务
 * 负责创建、保存和查询补丁提案
 * 大脑自主决定是否应用补丁
 */
public class PatchProposalService {

    private static final Logger log = LoggerFactory.getLogger(PatchProposalService.class);

    private final EvolutionNamespaceService namespaceService;
    private final Map<String, PatchProposal> proposals = new ConcurrentHashMap<>();

    public PatchProposalService(EvolutionNamespaceService namespaceService) {
        this.namespaceService = namespaceService;
    }

    /**
     * 创建并保存补丁提案
     */
    public PatchProposal createProposal(PatchProposal proposal) {
        // 保存到文件系统
        saveProposal(proposal);

        // 加入内存缓存
        proposals.put(proposal.getProposalId(), proposal);

        log.info("补丁提案已创建: id={}, domain={}, confidence={}",
            proposal.getProposalId(), proposal.getBrainDomain(), proposal.getConfidence());

        return proposal;
    }

    /**
     * 获取补丁提案
     */
    public PatchProposal getProposal(String proposalId) {
        return proposals.get(proposalId);
    }

    /**
     * 获取指定大脑域的所有提案
     */
    public List<PatchProposal> getProposalsByDomain(String brainDomain) {
        List<PatchProposal> result = new ArrayList<>();
        for (PatchProposal p : proposals.values()) {
            if (brainDomain.equals(p.getBrainDomain())) {
                result.add(p);
            }
        }
        return result;
    }

    /**
     * 获取所有待处理提案
     */
    public List<PatchProposal> getPendingProposals() {
        List<PatchProposal> result = new ArrayList<>();
        for (PatchProposal p : proposals.values()) {
            if ("PENDING".equals(p.getStatus())) {
                result.add(p);
            }
        }
        return result;
    }

    // 内部方法
    private void saveProposal(PatchProposal proposal) {
        try {
            Path patchDir = Paths.get(namespaceService.getEvolutionPatchPath(proposal.getProposalId()));
            Files.createDirectories(patchDir);

            // 保存 proposal.md
            if (proposal.getProposalContent() != null) {
                Files.writeString(patchDir.resolve("proposal.md"), proposal.getProposalContent());
            }

            // 保存 patch.diff
            if (proposal.getPatchContent() != null) {
                Files.writeString(patchDir.resolve("patch.diff"), proposal.getPatchContent());
            }

            // 保存 metadata.json
            String metadata = buildMetadataJson(proposal);
            Files.writeString(patchDir.resolve("metadata.json"), metadata);

        } catch (IOException e) {
            log.warn("保存补丁提案失败: {}", e.getMessage());
        }
    }

    private String buildMetadataJson(PatchProposal p) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"proposalId\": \"").append(escape(p.getProposalId())).append("\",\n");
        sb.append("  \"signalId\": \"").append(escape(p.getSignalId())).append("\",\n");
        sb.append("  \"brainDomain\": \"").append(escape(p.getBrainDomain())).append("\",\n");
        sb.append("  \"riskLevel\": \"").append(escape(p.getRiskLevel())).append("\",\n");
        sb.append("  \"confidence\": ").append(p.getConfidence()).append(",\n");
        sb.append("  \"autoApplied\": ").append(p.isAutoApplied()).append(",\n");
        sb.append("  \"rollbackAvailable\": ").append(p.isRollbackAvailable()).append(",\n");
        sb.append("  \"status\": \"").append(p.getStatus()).append("\",\n");
        sb.append("  \"createdAt\": \"").append(p.getCreatedAt()).append("\"\n");
        sb.append("}");
        return sb.toString();
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
