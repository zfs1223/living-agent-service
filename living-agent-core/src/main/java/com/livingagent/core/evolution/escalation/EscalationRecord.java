package com.livingagent.core.evolution.escalation;

import java.time.Instant;
import java.util.*;

/**
 * 升级记录
 * 记录一次升级通知的完整信息
 */
public class EscalationRecord {

    private String escalationId;       // 升级ID
    private EscalationLevel level;     // 升级级别
    private String source;             // 来源（evolution/intervention/compliance）
    private String brainDomain;        // 涉及的大脑域
    private String problem;            // 问题描述
    private String codeContext;        // 代码上下文（模块/文件/文档引用）
    private List<String> attemptedFixes; // 已尝试的修复
    private String suggestion;         // 建议的修复方案
    private String impactScope;        // 影响范围
    private String status;             // PENDING/RESOLVED/DISMISSED
    private Instant createdAt;         // 创建时间
    private Instant resolvedAt;        // 解决时间
    private String resolution;         // 解决方案
    private String resolvedBy;         // 解决者

    // 构造函数、getter/setter
    public EscalationRecord() {
        this.escalationId = "esc_" + System.currentTimeMillis();
        this.level = EscalationLevel.WARNING;
        this.attemptedFixes = new ArrayList<>();
        this.status = "PENDING";
        this.createdAt = Instant.now();
    }

    // 静态工厂方法
    public static EscalationRecord of(EscalationLevel level, String source, String brainDomain, String problem) {
        EscalationRecord record = new EscalationRecord();
        record.level = level;
        record.source = source;
        record.brainDomain = brainDomain;
        record.problem = problem;
        return record;
    }

    public EscalationRecord withCodeContext(String codeContext) { this.codeContext = codeContext; return this; }
    public EscalationRecord withAttemptedFix(String fix) { this.attemptedFixes.add(fix); return this; }
    public EscalationRecord withSuggestion(String suggestion) { this.suggestion = suggestion; return this; }
    public EscalationRecord withImpactScope(String scope) { this.impactScope = scope; return this; }

    public void resolve(String resolution, String resolvedBy) {
        this.status = "RESOLVED";
        this.resolution = resolution;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = Instant.now();
    }

    public void dismiss(String reason) {
        this.status = "DISMISSED";
        this.resolution = reason;
        this.resolvedAt = Instant.now();
    }

    // 所有 getter/setter 方法
    public String getEscalationId() { return escalationId; }
    public void setEscalationId(String escalationId) { this.escalationId = escalationId; }
    public EscalationLevel getLevel() { return level; }
    public void setLevel(EscalationLevel level) { this.level = level; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getBrainDomain() { return brainDomain; }
    public void setBrainDomain(String brainDomain) { this.brainDomain = brainDomain; }
    public String getProblem() { return problem; }
    public void setProblem(String problem) { this.problem = problem; }
    public String getCodeContext() { return codeContext; }
    public void setCodeContext(String codeContext) { this.codeContext = codeContext; }
    public List<String> getAttemptedFixes() { return attemptedFixes; }
    public void setAttemptedFixes(List<String> attemptedFixes) { this.attemptedFixes = attemptedFixes; }
    public String getSuggestion() { return suggestion; }
    public void setSuggestion(String suggestion) { this.suggestion = suggestion; }
    public String getImpactScope() { return impactScope; }
    public void setImpactScope(String impactScope) { this.impactScope = impactScope; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }

    @Override
    public String toString() {
        return String.format("EscalationRecord{id='%s', level=%s, source='%s', domain='%s', status='%s'}",
            escalationId, level, source, brainDomain, status);
    }
}
