package com.livingagent.core.brain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class BrainBoundaryEnforcer {

    private static final Logger log = LoggerFactory.getLogger(BrainBoundaryEnforcer.class);

    private final Map<String, BrainBoundary> boundaries = new LinkedHashMap<>();

    public BrainBoundaryEnforcer() {
        registerBoundaries();
    }

    private void registerBoundaries() {
        boundaries.put("main-brain", new BrainBoundary(
            "main-brain", "main",
            Set.of("cross_department_coordination", "task_identification", "strategic_judgment", "final_approval"),
            Set.of("direct_tool_execution", "employee_task_execution", "code_development", "financial_payment"),
            Set.of("cross_department_conflict", "high_risk_decision", "resource_conflict", "approval_required"),
            Set.of("finance_payment", "hr_disciplinary", "legal_commitment", "deployment_approval")
        ));

        boundaries.put("tech-brain", new BrainBoundary(
            "tech-brain", "tech",
            Set.of("technical_design", "code_development", "system_architecture", "deployment_coordination", "bug_triage"),
            Set.of("financial_decision", "hr_decision", "legal_commitment", "customer_promise"),
            Set.of("unclear_requirements", "deployment_risk", "security_vulnerability", "cross_department_dependency"),
            Set.of("production_incident", "data_breach", "service_outage")
        ));

        boundaries.put("hr-brain", new BrainBoundary(
            "hr-brain", "hr",
            Set.of("recruitment", "performance_review", "onboarding", "training", "employee_relations"),
            Set.of("financial_payment", "legal_judgment", "technical_decision", "sales_commitment"),
            Set.of("employee_privacy", "disciplinary_action", "organizational_change", "compensation_dispute"),
            Set.of("termination", "harassment_report", "legal_dispute")
        ));

        boundaries.put("finance-brain", new BrainBoundary(
            "finance-brain", "finance",
            Set.of("budgeting", "expense_approval", "invoice_processing", "financial_reporting", "payroll"),
            Set.of("business_strategy", "legal_conclusion", "hr_decision", "technical_architecture"),
            Set.of("large_expenditure", "budget_shortage", "compliance_risk", "audit_finding"),
            Set.of("fraud_suspicion", "regulatory_violation")
        ));

        boundaries.put("sales-brain", new BrainBoundary(
            "sales-brain", "sales",
            Set.of("lead_generation", "customer_acquisition", "quotation", "pipeline_management"),
            Set.of("technical_commitment", "legal_commitment", "financial_guarantee"),
            Set.of("major_customer_promise", "price_anomaly", "contract_risk"),
            Set.of("contract_breach", "customer_dispute")
        ));

        boundaries.put("cs-brain", new BrainBoundary(
            "cs-brain", "cs",
            Set.of("customer_support", "complaint_handling", "faq_management", "ticket_resolution"),
            Set.of("compensation_commitment", "technical_modification", "legal_commitment"),
            Set.of("high_severity_complaint", "compensation_request", "legal_risk"),
            Set.of("media_escalation", "regulatory_complaint")
        ));

        boundaries.put("admin-brain", new BrainBoundary(
            "admin-brain", "admin",
            Set.of("office_management", "asset_management", "procurement", "facility_management"),
            Set.of("hr_disciplinary", "financial_approval", "legal_judgment"),
            Set.of("asset_disposal", "procurement_anomaly", "access_privilege_request"),
            Set.of("security_incident", "facility_emergency")
        ));

        boundaries.put("legal-brain", new BrainBoundary(
            "legal-brain", "legal",
            Set.of("contract_review", "compliance_check", "risk_assessment", "regulatory_filing"),
            Set.of("business_commitment", "financial_payment", "technical_decision"),
            Set.of("major_contract_risk", "regulatory_risk", "dispute_handling"),
            Set.of("litigation", "regulatory_investigation")
        ));

        boundaries.put("ops-brain", new BrainBoundary(
            "ops-brain", "ops",
            Set.of("data_operations", "process_operations", "campaign_operations", "quality_assurance"),
            Set.of("sales_commitment", "financial_decision", "legal_commitment"),
            Set.of("campaign_risk", "data_anomaly", "cross_department_process_change"),
            Set.of("data_breach", "system_failure")
        ));
    }

    public BoundaryCheckResult checkAction(String brainId, String actionType) {
        BrainBoundary boundary = findBoundary(brainId);
        if (boundary == null) {
            return BoundaryCheckResult.allowed();
        }

        if (boundary.forbiddenActions().contains(actionType)) {
            log.warn("BRAIN BOUNDARY VIOLATION: brain={} attempted forbidden action={}", brainId, actionType);
            return BoundaryCheckResult.violation("FORBIDDEN_ACTION",
                String.format("Brain %s cannot perform action: %s (forbidden by boundary)", brainId, actionType));
        }

        if (boundary.escalationTriggers().contains(actionType)) {
            log.info("BRAIN BOUNDARY ESCALATION: brain={} action={} requires escalation", brainId, actionType);
            return BoundaryCheckResult.needsEscalation(actionType);
        }

        if (boundary.mustEscalateScenarios().contains(actionType)) {
            log.warn("BRAIN BOUNDARY MUST ESCALATE: brain={} action={} must be escalated to main brain", brainId, actionType);
            return BoundaryCheckResult.mustEscalate(actionType);
        }

        return BoundaryCheckResult.allowed();
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

    private BrainBoundary findBoundary(String brainId) {
        if (brainId == null) return null;
        if (boundaries.containsKey(brainId)) return boundaries.get(brainId);
        for (Map.Entry<String, BrainBoundary> entry : boundaries.entrySet()) {
            if (brainId.contains(entry.getKey()) || brainId.contains(entry.getValue().department())) {
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
