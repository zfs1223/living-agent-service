package com.livingagent.core.security;

import com.livingagent.core.tool.ToolCall;

import java.util.List;

public interface ApprovalManager {

    boolean needsApproval(String toolName);

    ApprovalResponse requestApproval(String toolName, ToolCall call);

    void recordDecision(String toolName, ApprovalResponse decision, String channel);

    void addToAllowlist(String toolName);

    void removeFromAllowlist(String toolName);

    List<String> getSessionAllowlist();

    List<ApprovalLogEntry> getAuditLog();

    void clearSessionAllowlist();

    enum ApprovalResponse {
        YES,
        NO,
        ALWAYS
    }

    record ApprovalLogEntry(
        long timestamp,
        String toolName,
        String argsSummary,
        ApprovalResponse decision,
        String channel
    ) {}
}
