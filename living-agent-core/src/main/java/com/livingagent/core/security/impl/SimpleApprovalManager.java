package com.livingagent.core.security.impl;

import com.livingagent.core.security.ApprovalManager;
import com.livingagent.core.tool.ToolCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SimpleApprovalManager implements ApprovalManager {

    private static final Logger log = LoggerFactory.getLogger(SimpleApprovalManager.class);

    private final Set<String> sessionAllowlist = ConcurrentHashMap.newKeySet();
    private final List<ApprovalLogEntry> auditLog = new ArrayList<>();

    private final Set<String> autoApproveTools = Set.of(
        "read_file", "list_directory", "search_code", "get_task_status",
        "get_project_status", "list_artifacts", "get_artifact_content"
    );

    @Override
    public boolean needsApproval(String toolName) {
        return !autoApproveTools.contains(toolName) && !sessionAllowlist.contains(toolName);
    }

    @Override
    public ApprovalResponse requestApproval(String toolName, ToolCall call) {
        log.info("Approval requested for tool: {}", toolName);
        return ApprovalResponse.YES;
    }

    @Override
    public void recordDecision(String toolName, ApprovalResponse decision, String channel) {
        auditLog.add(new ApprovalLogEntry(
            System.currentTimeMillis(),
            toolName,
            "",
            decision,
            channel
        ));
        log.info("Recorded approval decision: {} -> {} via {}", toolName, decision, channel);
    }

    @Override
    public void addToAllowlist(String toolName) {
        sessionAllowlist.add(toolName);
        log.info("Added {} to session allowlist", toolName);
    }

    @Override
    public void removeFromAllowlist(String toolName) {
        sessionAllowlist.remove(toolName);
        log.info("Removed {} from session allowlist", toolName);
    }

    @Override
    public List<String> getSessionAllowlist() {
        return new ArrayList<>(sessionAllowlist);
    }

    @Override
    public List<ApprovalLogEntry> getAuditLog() {
        return new ArrayList<>(auditLog);
    }

    @Override
    public void clearSessionAllowlist() {
        sessionAllowlist.clear();
        log.info("Cleared session allowlist");
    }
}
