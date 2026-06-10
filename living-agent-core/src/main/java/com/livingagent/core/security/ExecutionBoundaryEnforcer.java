package com.livingagent.core.security;

import com.livingagent.core.autonomy.EmployeeWorkAssignment;
import com.livingagent.core.brain.BrainOutputContract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ExecutionBoundaryEnforcer {

    private static final Logger log = LoggerFactory.getLogger(ExecutionBoundaryEnforcer.class);

    private static final Map<String, Set<String>> DEPARTMENT_JURISDICTIONS = Map.of(
        "tech", Set.of("code_development", "system_architecture", "technical_design", "deployment", "bug_fix", "code_review", "testing"),
        "hr", Set.of("recruitment", "performance_review", "onboarding", "offboarding", "employee_relations", "training"),
        "finance", Set.of("budgeting", "expense_approval", "invoice_processing", "financial_reporting", "payroll"),
        "sales", Set.of("lead_generation", "customer_acquisition", "quotation", "contract_negotiation", "pipeline_management"),
        "cs", Set.of("customer_support", "complaint_handling", "faq_management", "ticket_resolution", "refund_processing"),
        "admin", Set.of("office_management", "asset_management", "procurement", "facility_management", "access_control"),
        "legal", Set.of("contract_review", "compliance_check", "risk_assessment", "dispute_resolution", "regulatory_filing"),
        "ops", Set.of("data_operations", "process_operations", "campaign_operations", "quality_assurance", "workflow_optimization")
    );

    private static final Map<String, Set<String>> CROSS_DEPARTMENT_ACTIONS = Map.of(
        "finance_payment", Set.of("finance"),
        "hr_disciplinary", Set.of("hr"),
        "legal_commitment", Set.of("legal"),
        "security_access_change", Set.of("admin", "tech"),
        "customer_compensation", Set.of("cs", "finance"),
        "contract_signing", Set.of("legal", "sales")
    );

    private static final Set<String> HIGH_RISK_TASK_TYPES = Set.of(
        "deployment", "finance_payment", "hr_disciplinary", "legal_commitment",
        "security_access_change", "contract_signing", "data_deletion"
    );

    public BoundaryCheckResult checkEmployeeAssignment(EmployeeWorkAssignment assignment, String targetDepartment) {
        String employeeDept = assignment.department();
        String taskType = assignment.role();

        if (employeeDept == null || targetDepartment == null) {
            return BoundaryCheckResult.allowed();
        }

        if (!employeeDept.equalsIgnoreCase(targetDepartment)) {
            String msg = String.format("Employee %s from department %s assigned to task in department %s - cross-department boundary violation",
                assignment.employeeCode(), employeeDept, targetDepartment);
            log.warn("BOUNDARY VIOLATION: {}", msg);
            return BoundaryCheckResult.violation("CROSS_DEPARTMENT", msg);
        }

        Set<String> jurisdiction = DEPARTMENT_JURISDICTIONS.getOrDefault(employeeDept.toLowerCase(), Set.of());
        if (!jurisdiction.isEmpty() && taskType != null && !jurisdiction.contains(taskType.toLowerCase())) {
            String msg = String.format("Employee %s from department %s assigned task type %s outside jurisdiction %s",
                assignment.employeeCode(), employeeDept, taskType, jurisdiction);
            log.warn("BOUNDARY VIOLATION: {}", msg);
            return BoundaryCheckResult.violation("OUT_OF_JURISDICTION", msg);
        }

        if (taskType != null && HIGH_RISK_TASK_TYPES.contains(taskType.toLowerCase())) {
            log.info("HIGH_RISK task detected: employee={}, taskType={}, requiresHumanReview=true", assignment.employeeCode(), taskType);
            return BoundaryCheckResult.highRisk(taskType);
        }

        return BoundaryCheckResult.allowed();
    }

    public BoundaryCheckResult checkBrainAction(String brainId, String action, String targetDepartment) {
        String brainDept = extractDepartment(brainId);
        if (brainDept == null) {
            return BoundaryCheckResult.allowed();
        }

        if (CROSS_DEPARTMENT_ACTIONS.containsKey(action)) {
            Set<String> allowedDepts = CROSS_DEPARTMENT_ACTIONS.get(action);
            if (!allowedDepts.contains(brainDept.toLowerCase())) {
                String msg = String.format("Brain %s from department %s attempted cross-department action %s (allowed: %s)",
                    brainId, brainDept, action, allowedDepts);
                log.warn("BOUNDARY VIOLATION: {}", msg);
                return BoundaryCheckResult.violation("CROSS_DEPARTMENT_ACTION", msg);
            }
        }

        return BoundaryCheckResult.allowed();
    }

    public BoundaryCheckResult checkBrainOutput(BrainOutputContract output, String brainDepartment) {
        if (output == null) return BoundaryCheckResult.allowed();

        if (output.riskLevel() == BrainOutputContract.RiskLevel.CRITICAL) {
            log.info("CRITICAL risk output from brain department={}, requiresHumanReview=true", brainDepartment);
            return BoundaryCheckResult.highRisk("CRITICAL_RISK_OUTPUT");
        }

        return BoundaryCheckResult.allowed();
    }

    private String extractDepartment(String brainId) {
        if (brainId == null) return null;
        if (brainId.contains("tech")) return "tech";
        if (brainId.contains("hr")) return "hr";
        if (brainId.contains("finance")) return "finance";
        if (brainId.contains("sales")) return "sales";
        if (brainId.contains("cs")) return "cs";
        if (brainId.contains("admin")) return "admin";
        if (brainId.contains("legal")) return "legal";
        if (brainId.contains("ops")) return "ops";
        if (brainId.contains("main")) return "main";
        return null;
    }

    public static class BoundaryCheckResult {
        private final boolean allowed;
        private final boolean requiresHumanReview;
        private final String violationType;
        private final String violationMessage;
        private final String highRiskTaskType;

        private BoundaryCheckResult(boolean allowed, boolean requiresHumanReview, String violationType,
                                    String violationMessage, String highRiskTaskType) {
            this.allowed = allowed;
            this.requiresHumanReview = requiresHumanReview;
            this.violationType = violationType;
            this.violationMessage = violationMessage;
            this.highRiskTaskType = highRiskTaskType;
        }

        public static BoundaryCheckResult allowed() {
            return new BoundaryCheckResult(true, false, null, null, null);
        }

        public static BoundaryCheckResult violation(String type, String message) {
            return new BoundaryCheckResult(false, false, type, message, null);
        }

        public static BoundaryCheckResult highRisk(String taskType) {
            return new BoundaryCheckResult(true, true, "HIGH_RISK", null, taskType);
        }

        public boolean isAllowed() { return allowed; }
        public boolean requiresHumanReview() { return requiresHumanReview; }
        public String getViolationType() { return violationType; }
        public String getViolationMessage() { return violationMessage; }
        public String getHighRiskTaskType() { return highRiskTaskType; }
        public boolean isViolation() { return !allowed; }
    }
}
