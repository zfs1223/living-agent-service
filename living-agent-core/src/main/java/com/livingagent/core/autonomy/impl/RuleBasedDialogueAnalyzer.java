package com.livingagent.core.autonomy.impl;

import com.livingagent.core.autonomy.DialogueAnalyzer;
import com.livingagent.core.autonomy.DialogueDecision;
import com.livingagent.core.autonomy.DialogueDecision.MessageKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class RuleBasedDialogueAnalyzer implements DialogueAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(RuleBasedDialogueAnalyzer.class);

    @Override
    public DialogueDecision analyze(String message, String userId, String department, String sessionId) {
        String requestId = UUID.randomUUID().toString();

        MessageKind kind = detectMessageKind(message);
        String intent = kind == MessageKind.TASK ? "general_task" : "general_chat";
        int complexity = kind == MessageKind.TASK ? 3 : 1;
        int riskLevel = kind == MessageKind.TASK ? 2 : 1;

        DialogueDecision decision = new DialogueDecision(
            requestId,
            sessionId,
            userId,
            message,
            kind,
            intent,
            department,
            mapDepartmentToBrain(department),
            Collections.emptyList(),
            kind == MessageKind.TASK,
            false,
            null,
            complexity,
            riskLevel,
            Map.of("analyzer_type", "rule_based_minimal_fallback")
        );

        log.info("[AutonomyTrace] requestId={} stage=dialogue_analyzed kind={} intent={} primary={} complexity={} risk={} source=rule_based_fallback",
            requestId, kind, intent, department, complexity, riskLevel);

        return decision;
    }

    private MessageKind detectMessageKind(String message) {
        if (message == null || message.isBlank()) {
            return MessageKind.CHAT;
        }
        String lower = message.toLowerCase().trim();
        boolean looksLikeTask = lower.length() > 10
            && (lower.contains("做") || lower.contains("创建") || lower.contains("生成")
                || lower.contains("开发") || lower.contains("写") || lower.contains("帮我")
                || lower.contains("审批") || lower.contains("项目"));
        return looksLikeTask ? MessageKind.TASK : MessageKind.CHAT;
    }

    private String mapDepartmentToBrain(String department) {
        if (department == null) return "MainBrain";
        return switch (department.toLowerCase()) {
            case "tech" -> "TechBrain";
            case "hr" -> "HrBrain";
            case "finance" -> "FinanceBrain";
            case "sales" -> "SalesBrain";
            case "cs" -> "CsBrain";
            case "legal" -> "LegalBrain";
            case "admin" -> "AdminBrain";
            case "ops" -> "OpsBrain";
            case "main", "core" -> "MainBrain";
            default -> department + "Brain";
        };
    }
}
