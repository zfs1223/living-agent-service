package com.livingagent.core.evolution.patch;

import java.time.Instant;
import java.util.*;

/**
 * 补丁提案实体
 * 大脑自主决定是否应用补丁（自毁即自伤的自然约束）
 */
public class PatchProposal {

    private String proposalId;
    private String signalId;
    private String brainDomain;
    private String riskLevel;
    private double confidence;           // 修复确定性
    private List<String> affectedModules;
    private List<String> affectedFiles;
    private String proposalContent;      // 修复方案描述
    private String patchContent;         // 补丁内容（diff格式）
    private boolean autoApplied;         // 是否已自动应用
    private boolean rollbackAvailable;   // 是否有回滚基线
    private String status;               // PENDING/APPLIED/ROLLED_BACK/REJECTED
    private Instant createdAt;
    private Instant appliedAt;
    private Instant rolledBackAt;
    private String rejectReason;

    public PatchProposal() {
        this.proposalId = "patch_" + System.currentTimeMillis();
        this.affectedModules = new ArrayList<>();
        this.affectedFiles = new ArrayList<>();
        this.confidence = 0.5;
        this.autoApplied = false;
        this.rollbackAvailable = false;
        this.status = "PENDING";
        this.createdAt = Instant.now();
    }

    // 静态工厂
    public static PatchProposal of(String brainDomain, String signalId) {
        PatchProposal p = new PatchProposal();
        p.brainDomain = brainDomain;
        p.signalId = signalId;
        return p;
    }

    // 链式方法
    public PatchProposal withConfidence(double confidence) { this.confidence = confidence; return this; }
    public PatchProposal withRiskLevel(String level) { this.riskLevel = level; return this; }
    public PatchProposal withAffectedModule(String module) { this.affectedModules.add(module); return this; }
    public PatchProposal withAffectedFile(String file) { this.affectedFiles.add(file); return this; }
    public PatchProposal withProposalContent(String content) { this.proposalContent = content; return this; }
    public PatchProposal withPatchContent(String content) { this.patchContent = content; return this; }
    public PatchProposal withRollbackAvailable(boolean available) { this.rollbackAvailable = available; return this; }

    public void markApplied() {
        this.status = "APPLIED";
        this.autoApplied = true;
        this.appliedAt = Instant.now();
    }

    public void markRolledBack() {
        this.status = "ROLLED_BACK";
        this.rolledBackAt = Instant.now();
    }

    public void markRejected(String reason) {
        this.status = "REJECTED";
        this.rejectReason = reason;
    }

    // 所有 getter/setter
    public String getProposalId() { return proposalId; }
    public void setProposalId(String proposalId) { this.proposalId = proposalId; }
    public String getSignalId() { return signalId; }
    public void setSignalId(String signalId) { this.signalId = signalId; }
    public String getBrainDomain() { return brainDomain; }
    public void setBrainDomain(String brainDomain) { this.brainDomain = brainDomain; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public List<String> getAffectedModules() { return affectedModules; }
    public void setAffectedModules(List<String> affectedModules) { this.affectedModules = affectedModules; }
    public List<String> getAffectedFiles() { return affectedFiles; }
    public void setAffectedFiles(List<String> affectedFiles) { this.affectedFiles = affectedFiles; }
    public String getProposalContent() { return proposalContent; }
    public void setProposalContent(String proposalContent) { this.proposalContent = proposalContent; }
    public String getPatchContent() { return patchContent; }
    public void setPatchContent(String patchContent) { this.patchContent = patchContent; }
    public boolean isAutoApplied() { return autoApplied; }
    public void setAutoApplied(boolean autoApplied) { this.autoApplied = autoApplied; }
    public boolean isRollbackAvailable() { return rollbackAvailable; }
    public void setRollbackAvailable(boolean rollbackAvailable) { this.rollbackAvailable = rollbackAvailable; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getAppliedAt() { return appliedAt; }
    public void setAppliedAt(Instant appliedAt) { this.appliedAt = appliedAt; }
    public Instant getRolledBackAt() { return rolledBackAt; }
    public void setRolledBackAt(Instant rolledBackAt) { this.rolledBackAt = rolledBackAt; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }

    @Override
    public String toString() {
        return String.format("PatchProposal{id='%s', domain='%s', confidence=%.2f, status='%s'}",
            proposalId, brainDomain, confidence, status);
    }
}
