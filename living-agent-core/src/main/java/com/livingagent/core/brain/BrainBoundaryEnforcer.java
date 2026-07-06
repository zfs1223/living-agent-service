package com.livingagent.core.brain;

import com.livingagent.core.database.entity.BrainBoundaryAuditEntity;
import com.livingagent.core.database.repository.BrainBoundaryAuditRepository;
import com.livingagent.core.evolution.personality.BrainPersonality;
import com.livingagent.core.intervention.InterventionDecisionEngine;
import com.livingagent.core.security.SandboxViolationTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class BrainBoundaryEnforcer {

    private static final Logger log = LoggerFactory.getLogger(BrainBoundaryEnforcer.class);

    private final Map<String, BrainBoundary> boundaries = new LinkedHashMap<>();
    private final BrainBoundaryProperties properties;
    private final BrainBoundaryAuditRepository auditRepository;
    private final InterventionDecisionEngine interventionEngine;
    private final SandboxViolationTracker violationTracker;
    private final BrainRegistry brainRegistry;

    public BrainBoundaryEnforcer(BrainBoundaryProperties properties,
                                 BrainBoundaryAuditRepository auditRepository,
                                 InterventionDecisionEngine interventionEngine,
                                 SandboxViolationTracker violationTracker,
                                 BrainRegistry brainRegistry) {
        this.properties = properties;
        this.auditRepository = auditRepository;
        this.interventionEngine = interventionEngine;
        this.violationTracker = violationTracker;
        this.brainRegistry = brainRegistry;
        registerBoundaries();
    }

    private void registerBoundaries() {
        List<BrainBoundaryProperties.BoundaryDefinition> configBoundaries = properties.getBoundaries();
        if (configBoundaries != null && !configBoundaries.isEmpty()) {
            for (BrainBoundaryProperties.BoundaryDefinition def : configBoundaries) {
                boundaries.put(def.getBrainId(), new BrainBoundary(
                    def.getBrainId(),
                    def.getDepartment(),
                    new HashSet<>(def.getAllowedActions()),
                    new HashSet<>(def.getForbiddenActions()),
                    new HashSet<>(def.getEscalationTriggers()),
                    new HashSet<>(def.getMustEscalateScenarios())
                ));
            }
            log.info("Loaded {} boundary definitions from configuration", boundaries.size());
            return;
        }

        registerDefaultBoundaries();
    }

    private void registerDefaultBoundaries() {
        boundaries.put("main-brain", new BrainBoundary(
            "main-brain", "main",
            Set.of("cross_department_coordination", "task_identification", "strategic_judgment", "final_approval",
                "codebase_full_access", "evolution_full_access", "apply_code_fix", "propose_code_fix"),
            Set.of("direct_tool_execution", "employee_task_execution", "code_development", "financial_payment"),
            Set.of("cross_department_conflict", "high_risk_decision", "resource_conflict", "approval_required"),
            Set.of("finance_payment", "hr_disciplinary", "legal_commitment", "deployment_approval",
                "repair_loop_detected", "consecutive_failures_5")
        ));

        boundaries.put("tech-brain", new BrainBoundary(
            "tech-brain", "tech",
            Set.of("technical_design", "code_development", "system_architecture", "deployment_coordination", "bug_triage",
                "codebase_full_access", "evolution_full_access", "apply_code_fix", "propose_code_fix"),
            Set.of("financial_decision", "hr_decision", "legal_commitment", "customer_promise"),
            Set.of("unclear_requirements", "deployment_risk", "security_vulnerability", "cross_department_dependency"),
            Set.of("production_incident", "data_breach", "service_outage",
                "repair_loop_detected", "consecutive_failures_5")
        ));

        boundaries.put("hr-brain", new BrainBoundary(
            "hr-brain", "hr",
            Set.of("recruitment", "performance_review", "onboarding", "training", "employee_relations",
                "codebase_full_access", "evolution_full_access", "apply_code_fix", "propose_code_fix"),
            Set.of("financial_payment", "legal_judgment", "technical_decision", "sales_commitment"),
            Set.of("employee_privacy", "disciplinary_action", "organizational_change", "compensation_dispute"),
            Set.of("termination", "harassment_report", "legal_dispute",
                "repair_loop_detected", "consecutive_failures_5")
        ));

        boundaries.put("finance-brain", new BrainBoundary(
            "finance-brain", "finance",
            Set.of("budgeting", "expense_approval", "invoice_processing", "financial_reporting", "payroll",
                "codebase_full_access", "evolution_full_access", "apply_code_fix", "propose_code_fix"),
            Set.of("business_strategy", "legal_conclusion", "hr_decision", "technical_architecture"),
            Set.of("large_expenditure", "budget_shortage", "compliance_risk", "audit_finding"),
            Set.of("fraud_suspicion", "regulatory_violation",
                "repair_loop_detected", "consecutive_failures_5")
        ));

        boundaries.put("sales-brain", new BrainBoundary(
            "sales-brain", "sales",
            Set.of("lead_generation", "customer_acquisition", "quotation", "pipeline_management",
                "codebase_full_access", "evolution_full_access", "apply_code_fix", "propose_code_fix"),
            Set.of("technical_commitment", "legal_commitment", "financial_guarantee"),
            Set.of("major_customer_promise", "price_anomaly", "contract_risk"),
            Set.of("contract_breach", "customer_dispute",
                "repair_loop_detected", "consecutive_failures_5")
        ));

        boundaries.put("cs-brain", new BrainBoundary(
            "cs-brain", "cs",
            Set.of("customer_support", "complaint_handling", "faq_management", "ticket_resolution",
                "codebase_full_access", "evolution_full_access", "apply_code_fix", "propose_code_fix"),
            Set.of("compensation_commitment", "technical_modification", "legal_commitment"),
            Set.of("high_severity_complaint", "compensation_request", "legal_risk"),
            Set.of("media_escalation", "regulatory_complaint",
                "repair_loop_detected", "consecutive_failures_5")
        ));

        boundaries.put("admin-brain", new BrainBoundary(
            "admin-brain", "admin",
            Set.of("office_management", "asset_management", "procurement", "facility_management",
                "codebase_full_access", "evolution_full_access", "apply_code_fix", "propose_code_fix"),
            Set.of("hr_disciplinary", "financial_approval", "legal_judgment"),
            Set.of("asset_disposal", "procurement_anomaly", "access_privilege_request"),
            Set.of("security_incident", "facility_emergency",
                "repair_loop_detected", "consecutive_failures_5")
        ));

        boundaries.put("legal-brain", new BrainBoundary(
            "legal-brain", "legal",
            Set.of("contract_review", "compliance_check", "risk_assessment", "regulatory_filing",
                "codebase_full_access", "evolution_full_access", "apply_code_fix", "propose_code_fix"),
            Set.of("business_commitment", "financial_payment", "technical_decision"),
            Set.of("major_contract_risk", "regulatory_risk", "dispute_handling"),
            Set.of("litigation", "regulatory_investigation",
                "repair_loop_detected", "consecutive_failures_5")
        ));

        boundaries.put("ops-brain", new BrainBoundary(
            "ops-brain", "ops",
            Set.of("data_operations", "process_operations", "campaign_operations", "quality_assurance",
                "codebase_full_access", "evolution_full_access", "apply_code_fix", "propose_code_fix"),
            Set.of("sales_commitment", "financial_decision", "legal_commitment"),
            Set.of("campaign_risk", "data_anomaly", "cross_department_process_change"),
            Set.of("data_breach", "system_failure",
                "repair_loop_detected", "consecutive_failures_5")
        ));

        boundaries.put("fixed-employee", new BrainBoundary(
            "fixed-employee", "fixed",
            Set.of("chat_response", "faq_handling", "routine_task", "data_lookup"),
            Set.of("codebase_access", "apply_code_fix", "evolution_write"),
            Set.of("unfamiliar_topic", "out_of_scope_request"),
            Set.of()
        ));

        log.info("Initialized {} default brain boundaries", boundaries.size());
    }

    public BoundaryCheckResult checkAction(String brainId, String actionType) {
        if (!properties.isEnabled()) {
            return BoundaryCheckResult.allowed();
        }

        // P30-A: 黑名单检查——被拉黑的 brain 只允许 chat_response
        if (violationTracker.isBlacklisted(brainId)) {
            if (!"chat_response".equals(actionType)) {
                log.warn("P30-A: Blocked action from blacklisted brain: brain={}, action={}", brainId, actionType);
                auditLog(brainId, actionType, "BLOCKED", "BLACKLISTED", "Brain is blacklisted, only chat_response allowed");
                return BoundaryCheckResult.violation("BLACKLISTED",
                    "Brain " + brainId + " is blacklisted, only chat_response allowed");
            }
            return BoundaryCheckResult.allowed();
        }

        BrainBoundary boundary = findBoundary(brainId);
        if (boundary == null) {
            return BoundaryCheckResult.allowed();
        }

        if (boundary.forbiddenActions().contains(actionType)) {
            log.warn("BRAIN BOUNDARY VIOLATION: brain={} attempted forbidden action={}", brainId, actionType);
            BoundaryCheckResult result = BoundaryCheckResult.violation("FORBIDDEN_ACTION",
                String.format("Brain %s cannot perform action: %s (forbidden by boundary)", brainId, actionType));
            auditLog(brainId, actionType, "VIOLATION", "FORBIDDEN_ACTION", result.getMessage());
            // P30-A: 记录违规，3次后自动拉黑
            violationTracker.recordViolation(brainId, "FORBIDDEN_ACTION:" + actionType);
            return result;
        }

        if (boundary.escalationTriggers().contains(actionType)) {
            // P29-B: 低风险(riskTolerance<0.3)时，escalationTriggers升级为mustEscalate
            double riskTolerance = getRiskTolerance(brainId);
            if (riskTolerance < 0.3) {
                log.info("P29-B: Low riskTolerance({}) escalates trigger to must-escalate: brain={} action={}",
                    String.format("%.2f", riskTolerance), brainId, actionType);
                auditLog(brainId, actionType, "MUST_ESCALATE", "RISK_ADJUSTED_LOW",
                    "riskTolerance=" + String.format("%.2f", riskTolerance) + " escalated to must-escalate");
                if (properties.isAutoEscalateToMainBrain()) {
                    triggerIntervention(brainId, actionType);
                }
                return BoundaryCheckResult.mustEscalate(actionType);
            }
            log.info("BRAIN BOUNDARY ESCALATION: brain={} action={} requires escalation", brainId, actionType);
            auditLog(brainId, actionType, "ESCALATION", "ESCALATION_NEEDED", null);
            return BoundaryCheckResult.needsEscalation(actionType);
        }

        if (boundary.mustEscalateScenarios().contains(actionType)) {
            // P29-B: 高风险(riskTolerance>0.7)时，mustEscalate降级为needsEscalation
            double riskTolerance = getRiskTolerance(brainId);
            if (riskTolerance > 0.7) {
                log.info("P29-B: High riskTolerance({}) downgrades must-escalate to escalation: brain={} action={}",
                    String.format("%.2f", riskTolerance), brainId, actionType);
                auditLog(brainId, actionType, "ESCALATION", "RISK_ADJUSTED_HIGH",
                    "riskTolerance=" + String.format("%.2f", riskTolerance) + " downgraded to needs-escalation");
                return BoundaryCheckResult.needsEscalation(actionType);
            }
            log.warn("BRAIN BOUNDARY MUST ESCALATE: brain={} action={} must be escalated to main brain", brainId, actionType);
            auditLog(brainId, actionType, "MUST_ESCALATE", "MUST_ESCALATE", null);
            if (properties.isAutoEscalateToMainBrain()) {
                triggerIntervention(brainId, actionType);
            }
            return BoundaryCheckResult.mustEscalate(actionType);
        }

        return BoundaryCheckResult.allowed();
    }

    private void triggerIntervention(String brainId, String actionType) {
        try {
            var request = new InterventionDecisionEngine.InterventionRequest();
            request.setOperationType("boundary.must_escalate." + actionType);
            request.setSourceNeuronId(brainId);
            request.setDepartment(extractDepartment(brainId));
            var decision = interventionEngine.evaluate(request);
            log.info("Auto-triggered intervention for must-escalate: brain={} action={} -> decision={}",
                brainId, actionType, decision.getInterventionType());
        } catch (Exception e) {
            log.warn("Failed to trigger intervention for must-escalate: brain={} action={}: {}",
                brainId, actionType, e.getMessage());
        }
    }

    private String extractDepartment(String brainId) {
        BrainBoundary boundary = findBoundary(brainId);
        return boundary != null ? boundary.department() : null;
    }

    private double getRiskTolerance(String brainId) {
        try {
            // 尝试从brainId映射到brainName（去掉前缀和实例号）
            String brainName = resolveBrainName(brainId);
            return brainRegistry.getPersonality(brainName)
                .map(BrainPersonality::getRiskTolerance)
                .orElse(0.5);
        } catch (Exception e) {
            return 0.5; // 默认中间值
        }
    }

    private String resolveBrainName(String brainId) {
        if (brainId == null) return null;
        // "neuron://tech/tech-brain/001" -> "TechBrain"
        for (String key : boundaries.keySet()) {
            if (brainId.equals(key) || brainId.endsWith("/" + key) || brainId.endsWith(":" + key)) {
                return keyToBrainName(key);
            }
        }
        for (Map.Entry<String, BrainBoundary> entry : boundaries.entrySet()) {
            if (brainId.contains("/" + entry.getValue().department() + "/")) {
                return keyToBrainName(entry.getKey());
            }
        }
        return keyToBrainName(brainId);
    }

    private String keyToBrainName(String key) {
        // "tech-brain" -> "TechBrain", "main-brain" -> "MainBrain"
        if (key == null) return null;
        String[] parts = key.replace("-", " ").split(" ");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }

    private void auditLog(String brainId, String actionType, String result,
                          String violationType, String message) {
        if (!properties.isAuditLogEnabled()) return;
        try {
            auditRepository.save(new BrainBoundaryAuditEntity(
                Instant.now(), brainId, actionType, result, violationType, message
            ));
        } catch (Exception e) {
            log.warn("Failed to persist boundary audit log: {}", e.getMessage());
        }
    }

    public boolean isWithinJurisdiction(String brainId, String taskType) {
        BrainBoundary boundary = findBoundary(brainId);
        if (boundary == null) return true;
        return boundary.allowedActions().contains(taskType);
    }

    public List<String> getClarificationTriggers(String brainId) {
        BrainBoundary boundary = findBoundary(brainId);
        if (boundary == null) return List.of();
        return new ArrayList<>(boundary.escalationTriggers());
    }

    public List<String> getMustEscalateScenarios(String brainId) {
        BrainBoundary boundary = findBoundary(brainId);
        if (boundary == null) return List.of();
        return new ArrayList<>(boundary.mustEscalateScenarios());
    }

    public Map<String, BrainBoundary> getBoundaries() {
        return Collections.unmodifiableMap(boundaries);
    }

    public Optional<BrainBoundary> getBoundary(String brainId) {
        return Optional.ofNullable(findBoundary(brainId));
    }

    public BrainBoundaryProperties getProperties() {
        return properties;
    }

    private BrainBoundary findBoundary(String brainId) {
        if (brainId == null) return null;
        if (boundaries.containsKey(brainId)) return boundaries.get(brainId);

        // Exact suffix match: e.g. "neuron://tech/tech-brain/001" matches "tech-brain"
        for (Map.Entry<String, BrainBoundary> entry : boundaries.entrySet()) {
            String key = entry.getKey();
            if (brainId.equals(key) || brainId.endsWith("/" + key) || brainId.endsWith(":" + key)) {
                return entry.getValue();
            }
        }

        // Department-based match for neuron IDs: extract domain and match department
        // e.g. "neuron://tech/reviewer/001" -> department "tech" matches "tech-brain"
        for (Map.Entry<String, BrainBoundary> entry : boundaries.entrySet()) {
            if (brainId.contains("/" + entry.getValue().department() + "/")) {
                return entry.getValue();
            }
        }

        return null;
    }

    public record BrainBoundary(
        String brainId,
        String department,
        Set<String> allowedActions,
        Set<String> forbiddenActions,
        Set<String> escalationTriggers,
        Set<String> mustEscalateScenarios
    ) {}

    public static class BoundaryCheckResult {
        private final boolean allowed;
        private final boolean needsEscalation;
        private final boolean mustEscalate;
        private final String violationType;
        private final String message;
        private final String triggerAction;

        private BoundaryCheckResult(boolean allowed, boolean needsEscalation, boolean mustEscalate,
                                    String violationType, String message, String triggerAction) {
            this.allowed = allowed;
            this.needsEscalation = needsEscalation;
            this.mustEscalate = mustEscalate;
            this.violationType = violationType;
            this.message = message;
            this.triggerAction = triggerAction;
        }

        public static BoundaryCheckResult allowed() {
            return new BoundaryCheckResult(true, false, false, null, null, null);
        }

        public static BoundaryCheckResult violation(String type, String message) {
            return new BoundaryCheckResult(false, false, false, type, message, null);
        }

        public static BoundaryCheckResult needsEscalation(String action) {
            return new BoundaryCheckResult(true, true, false, "ESCALATION_NEEDED", null, action);
        }

        public static BoundaryCheckResult mustEscalate(String action) {
            return new BoundaryCheckResult(true, true, true, "MUST_ESCALATE", null, action);
        }

        public boolean isAllowed() { return allowed; }
        public boolean needsEscalation() { return needsEscalation; }
        public boolean mustEscalate() { return mustEscalate; }
        public String getViolationType() { return violationType; }
        public String getMessage() { return message; }
        public String getTriggerAction() { return triggerAction; }
        public boolean isViolation() { return !allowed; }
    }
}
