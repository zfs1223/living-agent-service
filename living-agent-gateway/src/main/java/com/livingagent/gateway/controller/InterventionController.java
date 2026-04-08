package com.livingagent.gateway.controller;

import com.livingagent.core.intervention.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping({"/api/intervention", "/api/interventions"})
public class InterventionController {

    private static final Logger log = LoggerFactory.getLogger(InterventionController.class);

    private final InterventionDecisionEngine decisionEngine;

    public InterventionController(InterventionDecisionEngine decisionEngine) {
        this.decisionEngine = decisionEngine;
    }

    @PostMapping("/evaluate")
    public ResponseEntity<Map<String, Object>> evaluateOperation(@RequestBody Map<String, Object> request) {
        String operationType = (String) request.get("operationType");
        @SuppressWarnings("unchecked")
        Map<String, Object> operationDetails = (Map<String, Object>) request.get("operationDetails");
        String sourceNeuronId = (String) request.get("sourceNeuronId");
        String sourceChannelId = (String) request.get("sourceChannelId");

        InterventionDecision decision = decisionEngine.evaluate(
            operationType,
            operationDetails != null ? operationDetails : new HashMap<>(),
            sourceNeuronId,
            sourceChannelId
        );

        log.info("Evaluated operation: {} -> {}", operationType, decision.getInterventionType());

        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", toMap(decision)
        ));
    }

    @GetMapping("/pending")
    public ResponseEntity<Map<String, Object>> getPendingDecisions(
            @RequestParam(required = false) String department) {
        
        List<InterventionDecision> decisions = decisionEngine.getPendingDecisions(department);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", decisions.stream().map(this::toMap).toList()
        ));
    }

    @PostMapping("/{decisionId}/respond")
    public ResponseEntity<Map<String, Object>> respondToDecision(
            @PathVariable String decisionId,
            @RequestBody Map<String, Object> request) {
        
        String humanDecision = (String) request.get("decision");
        String respondedBy = (String) request.get("respondedBy");

        List<InterventionDecision> pendingDecisions = decisionEngine.getPendingDecisions(null);
        InterventionDecision decision = pendingDecisions.stream()
            .filter(d -> d.getDecisionId().equals(decisionId))
            .findFirst()
            .orElse(null);

        if (decision == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Decision not found or already processed"
            ));
        }

        decision.setRespondedBy(respondedBy);
        InterventionDecision updated = decisionEngine.applyLearning(decision, humanDecision);

        log.info("Decision {} responded by {}: {}", decisionId, respondedBy, humanDecision);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", toMap(updated)
        ));
    }

    @PostMapping("/{decisionId}/escalate")
    public ResponseEntity<Map<String, Object>> escalateDecision(@PathVariable String decisionId) {
        List<InterventionDecision> pendingDecisions = decisionEngine.getPendingDecisions(null);
        InterventionDecision decision = pendingDecisions.stream()
            .filter(d -> d.getDecisionId().equals(decisionId))
            .findFirst()
            .orElse(null);

        if (decision == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Decision not found"
            ));
        }

        var escalatedOpt = decisionEngine.escalate(decision);
        
        if (escalatedOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Decision cannot be escalated"
            ));
        }

        log.warn("Decision {} escalated to level {}", decisionId, decision.getEscalationLevel());

        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", toMap(escalatedOpt.get())
        ));
    }

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics(
            @RequestParam(required = false) String department,
            @RequestParam(defaultValue = "0") long since) {
        
        InterventionDecisionEngine.InterventionStatistics stats = 
            decisionEngine.getStatistics(department, since);

        Map<String, Object> data = new HashMap<>();
        data.put("totalEvaluations", stats.getTotalEvaluations());
        data.put("autoExecuted", stats.getAutoExecuted());
        data.put("humanInterventions", stats.getHumanInterventions());
        data.put("pendingDecisions", stats.getPendingDecisions());
        data.put("escalatedDecisions", stats.getEscalatedDecisions());
        data.put("timeoutDecisions", stats.getTimeoutDecisions());
        data.put("averageRiskScore", stats.getAverageRiskScore());
        data.put("averageImpactScore", stats.getAverageImpactScore());
        data.put("interventionRate", stats.getInterventionRate());
        data.put("autoExecutionRate", stats.getAutoExecutionRate());
        data.put("automationLevel", stats.calculateAutomationLevel());

        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", data
        ));
    }

    @PostMapping("/rules")
    public ResponseEntity<Map<String, Object>> registerRule(@RequestBody InterventionRule rule) {
        decisionEngine.registerRule(rule);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Rule registered: " + rule.getRuleId()
        ));
    }

    @DeleteMapping("/rules/{ruleId}")
    public ResponseEntity<Map<String, Object>> unregisterRule(@PathVariable String ruleId) {
        decisionEngine.unregisterRule(ruleId);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Rule unregistered: " + ruleId
        ));
    }

    @GetMapping("/rules")
    public ResponseEntity<Map<String, Object>> getApplicableRules(
            @RequestParam String operationType) {
        
        List<InterventionRule> rules = decisionEngine.getApplicableRules(operationType);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", rules.stream().map(this::ruleToMap).toList()
        ));
    }

    private Map<String, Object> toMap(InterventionDecision decision) {
        Map<String, Object> map = new HashMap<>();
        map.put("decisionId", decision.getDecisionId());
        map.put("operationType", decision.getOperationType());
        map.put("operationDetails", decision.getOperationDetails());
        map.put("riskLevel", decision.getRiskLevel() != null ? decision.getRiskLevel().name() : null);
        map.put("riskScore", decision.getRiskScore());
        map.put("riskFactors", decision.getRiskFactors());
        map.put("impactLevel", decision.getImpactLevel() != null ? decision.getImpactLevel().name() : null);
        map.put("impactScore", decision.getImpactScore());
        map.put("impactScope", decision.getImpactScope());
        map.put("interventionType", decision.getInterventionType() != null ? decision.getInterventionType().name() : null);
        map.put("aiDecision", decision.getAiDecision());
        map.put("humanDecision", decision.getHumanDecision());
        map.put("finalDecision", decision.getFinalDecision());
        map.put("status", decision.getStatus() != null ? decision.getStatus().name() : null);
        map.put("createdAt", decision.getCreatedAt() != null ? decision.getCreatedAt().toString() : null);
        map.put("respondedAt", decision.getRespondedAt() != null ? decision.getRespondedAt().toString() : null);
        map.put("completedAt", decision.getCompletedAt() != null ? decision.getCompletedAt().toString() : null);
        map.put("assignedTo", decision.getAssignedTo());
        map.put("respondedBy", decision.getRespondedBy());
        map.put("department", decision.getDepartment());
        map.put("needsHumanIntervention", decision.needsHumanIntervention());
        map.put("timeoutSeconds", decision.getTimeoutSeconds());
        map.put("escalationLevel", decision.getEscalationLevel());
        map.put("learningApplied", decision.isLearningApplied());
        return map;
    }

    private Map<String, Object> ruleToMap(InterventionRule rule) {
        Map<String, Object> map = new HashMap<>();
        map.put("ruleId", rule.getRuleId());
        map.put("ruleName", rule.getRuleName());
        map.put("description", rule.getDescription());
        map.put("priority", rule.getPriority());
        map.put("enabled", rule.isEnabled());
        map.put("interventionType", rule.getInterventionType() != null ? rule.getInterventionType().name() : null);
        map.put("timeoutSeconds", rule.getTimeoutSeconds());
        map.put("triggerCount", rule.getTriggerCount());
        map.put("successCount", rule.getSuccessCount());
        map.put("successRate", rule.getSuccessRate());
        return map;
    }
}
