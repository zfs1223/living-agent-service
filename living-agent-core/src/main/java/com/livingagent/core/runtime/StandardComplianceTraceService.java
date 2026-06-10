package com.livingagent.core.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class StandardComplianceTraceService {

    private static final Logger log = LoggerFactory.getLogger(StandardComplianceTraceService.class);

    private final RuntimeEventStore runtimeEventStore;

    public StandardComplianceTraceService(RuntimeEventStore runtimeEventStore) {
        this.runtimeEventStore = runtimeEventStore;
    }

    public void traceBoundaryCheck(String entityId, String entityType, String action,
                                   boolean allowed, String violationType, String message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("entityId", entityId != null ? entityId : "");
        data.put("entityType", entityType != null ? entityType : "");
        data.put("action", action != null ? action : "");
        data.put("allowed", String.valueOf(allowed));
        data.put("violationType", violationType != null ? violationType : "");
        data.put("message", message != null ? message : "");
        data.put("timestamp", Instant.now().toString());

        if (!allowed) {
            log.warn("BOUNDARY CHECK FAILED: entity={} type={} action={} violation={}", entityId, entityType, action, violationType);
        }

        runtimeEventStore.appendConversationIdEvent("_system", "compliance",
            "boundary_check", data);
    }

    public void traceStandardLoading(String entityId, String entityType,
                                     boolean dutyCardLoaded, boolean systemPromptLoaded,
                                     boolean runbookLoaded, List<String> missingStandards) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("entityId", entityId != null ? entityId : "");
        data.put("entityType", entityType != null ? entityType : "");
        data.put("dutyCardLoaded", String.valueOf(dutyCardLoaded));
        data.put("systemPromptLoaded", String.valueOf(systemPromptLoaded));
        data.put("runbookLoaded", String.valueOf(runbookLoaded));
        data.put("fullyCompliant", String.valueOf(dutyCardLoaded && systemPromptLoaded && runbookLoaded));
        data.put("missingStandards", missingStandards != null ? String.join(",", missingStandards) : "");
        data.put("timestamp", Instant.now().toString());

        if (!missingStandards.isEmpty()) {
            log.warn("STANDARD LOADING INCOMPLETE: entity={} type={} missing={}", entityId, entityType, missingStandards);
        }

        runtimeEventStore.appendConversationIdEvent("_system", "compliance",
            "standard_loading", data);
    }

    public void traceClarification(String entityId, String entityType, String conversationId,
                                   List<String> clarificationQuestions, String taskKey, String executionId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("entityId", entityId != null ? entityId : "");
        data.put("entityType", entityType != null ? entityType : "");
        data.put("conversationId", conversationId != null ? conversationId : "");
        data.put("clarificationQuestions", clarificationQuestions != null ? String.join(";", clarificationQuestions) : "");
        data.put("taskKey", taskKey != null ? taskKey : "");
        data.put("executionId", executionId != null ? executionId : "");
        data.put("timestamp", Instant.now().toString());

        log.info("CLARIFICATION REQUESTED: entity={} type={} questions={}", entityId, entityType, clarificationQuestions);

        runtimeEventStore.appendConversationIdEvent("_system", conversationId,
            "clarification_requested", data);
    }

    public void traceEscalation(String fromEntityId, String fromEntityType, String toEntityId,
                                String reason, String conversationId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("fromEntityId", fromEntityId != null ? fromEntityId : "");
        data.put("fromEntityType", fromEntityType != null ? fromEntityType : "");
        data.put("toEntityId", toEntityId != null ? toEntityId : "");
        data.put("reason", reason != null ? reason : "");
        data.put("conversationId", conversationId != null ? conversationId : "");
        data.put("timestamp", Instant.now().toString());

        log.info("ESCALATION: from={} to={} reason={}", fromEntityId, toEntityId, reason);

        runtimeEventStore.appendConversationIdEvent("_system", conversationId,
            "escalation", data);
    }

    public void traceReceiptCompliance(String employeeCode, String assignmentId,
                                       boolean followedRunbook, boolean withinJurisdiction,
                                       boolean outputContractCompliant, List<String> violations) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("employeeCode", employeeCode != null ? employeeCode : "");
        data.put("assignmentId", assignmentId != null ? assignmentId : "");
        data.put("followedRunbook", String.valueOf(followedRunbook));
        data.put("withinJurisdiction", String.valueOf(withinJurisdiction));
        data.put("outputContractCompliant", String.valueOf(outputContractCompliant));
        data.put("fullyCompliant", String.valueOf(followedRunbook && withinJurisdiction && outputContractCompliant));
        data.put("violations", violations != null ? String.join(";", violations) : "");
        data.put("timestamp", Instant.now().toString());

        if (!violations.isEmpty()) {
            log.warn("RECEIPT COMPLIANCE VIOLATION: employee={} violations={}", employeeCode, violations);
        }

        runtimeEventStore.appendConversationIdEvent("_system", "compliance",
            "receipt_compliance", data);
    }

    public void tracePermissionCheck(String userId, String resourceType, String resourceId,
                                     String action, boolean allowed, String reason) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId != null ? userId : "");
        data.put("resourceType", resourceType != null ? resourceType : "");
        data.put("resourceId", resourceId != null ? resourceId : "");
        data.put("action", action != null ? action : "");
        data.put("allowed", String.valueOf(allowed));
        data.put("reason", reason != null ? reason : "");
        data.put("timestamp", Instant.now().toString());

        if (!allowed) {
            log.info("PERMISSION DENIED: user={} resource={}/{} action={} reason={}", userId, resourceType, resourceId, action, reason);
        }

        runtimeEventStore.appendConversationIdEvent("_system", "compliance",
            "permission_check", data);
    }
}
