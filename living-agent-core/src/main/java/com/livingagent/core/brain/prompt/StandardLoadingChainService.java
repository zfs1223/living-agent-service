package com.livingagent.core.brain.prompt;

import com.livingagent.core.autonomy.EmployeeWorkAssignment;
import com.livingagent.core.brain.BrainContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class StandardLoadingChainService {

    private static final Logger log = LoggerFactory.getLogger(StandardLoadingChainService.class);

    private final InstructionFileLoader instructionFileLoader;

    public StandardLoadingChainService(InstructionFileLoader instructionFileLoader) {
        this.instructionFileLoader = instructionFileLoader;
    }

    public LoadedStandards loadEmployeeStandards(EmployeeWorkAssignment assignment) {
        if (assignment == null) {
            return LoadedStandards.empty();
        }

        String employeeCode = assignment.employeeCode();
        String department = assignment.department();
        LoadedStandards standards = new LoadedStandards(employeeCode, department);

        loadDutyCard(standards, employeeCode, department);
        loadSystemPrompt(standards, employeeCode);
        loadAgentPrompt(standards, employeeCode);
        loadRunbook(standards, employeeCode, department);
        loadDocumentWorkflow(standards, employeeCode, department);
        loadInstructions(standards, employeeCode);

        log.info("Loaded standards for employee {}: dutyCard={}, systemPrompt={}, agentPrompt={}, runbook={}, docWorkflow={}, instructions={}",
            employeeCode,
            standards.hasDutyCard(),
            standards.hasSystemPrompt(),
            standards.hasAgentPrompt(),
            standards.hasRunbook(),
            standards.hasDocumentWorkflow(),
            standards.hasInstructions());

        return standards;
    }

    public LoadedStandards loadBrainStandards(BrainContext brainContext) {
        if (brainContext == null) {
            return LoadedStandards.empty();
        }

        String brainId = brainContext.getBrainId();
        String department = brainContext.getDepartment();
        LoadedStandards standards = new LoadedStandards(brainId, department);

        loadDutyCard(standards, brainId, department);
        loadInstructions(standards, brainId);

        log.info("Loaded standards for brain {}: dutyCard={}, instructions={}",
            brainId, standards.hasDutyCard(), standards.hasInstructions());

        return standards;
    }

    public String composeEmployeeContext(EmployeeWorkAssignment assignment) {
        LoadedStandards standards = loadEmployeeStandards(assignment);

        DynamicPromptBuilder builder = new DynamicPromptBuilder();

        if (standards.hasSystemPrompt()) {
            builder.basePrompt(standards.getSystemPrompt());
        }
        if (standards.hasDutyCard()) {
            builder.guidelines(standards.getDutyCard());
        }
        if (standards.hasAgentPrompt()) {
            builder.role(assignment.employeeCode(), assignment.role(), assignment.department(), null);
        }
        if (standards.hasRunbook()) {
            builder.guidelines(standards.getRunbook());
        }
        if (standards.hasDocumentWorkflow()) {
            builder.guidelines(standards.getDocumentWorkflow());
        }
        if (standards.hasInstructions()) {
            builder.guidelines(standards.getInstructions());
        }

        return builder.build();
    }

    public String composeBrainContext(BrainContext brainContext) {
        LoadedStandards standards = loadBrainStandards(brainContext);

        DynamicPromptBuilder builder = new DynamicPromptBuilder();

        if (standards.hasDutyCard()) {
            builder.basePrompt(standards.getDutyCard());
        }
        if (standards.hasInstructions()) {
            builder.guidelines(standards.getInstructions());
        }

        return builder.build();
    }

    private void loadDutyCard(LoadedStandards standards, String entityId, String department) {
        String dutyCardPath = resolveDutyCardPath(department);
        if (dutyCardPath != null) {
            Optional<String> content = instructionFileLoader.loadFile(dutyCardPath);
            if (content.isPresent()) {
                standards.setDutyCard(content.get());
                return;
            }
            log.warn("Duty card file not found for department {}: {}", department, dutyCardPath);
        }
        // 兜底：遍历所有职责卡尝试匹配部门
        for (String path : List.of(
            "documents/shared/company/hr-20-hr-fixed-employee-duty-card.md",
            "documents/shared/company/hr-21-finance-fixed-employee-duty-card.md",
            "documents/shared/company/hr-22-tech-fixed-employee-duty-card.md",
            "documents/shared/company/hr-23-sales-fixed-employee-duty-card.md",
            "documents/shared/company/hr-24-ops-fixed-employee-duty-card.md",
            "documents/shared/company/hr-25-cs-fixed-employee-duty-card.md",
            "documents/shared/company/hr-26-legal-fixed-employee-duty-card.md"
        )) {
            if (department != null && path.contains("-" + department.toLowerCase() + "-")) {
                Optional<String> content = instructionFileLoader.loadFile(path);
                content.ifPresent(standards::setDutyCard);
                return;
            }
        }
    }

    private void loadSystemPrompt(LoadedStandards standards, String employeeCode) {
        Optional<String> content = instructionFileLoader.loadFile(
            "documents/shared/company/fixed-employee-system-prompts.md");
        if (content.isEmpty()) {
            content = instructionFileLoader.loadFile(
                "documents/shared/company/fixed-employee-system-prompts");
        }
        content.ifPresent(standards::setSystemPrompt);
    }

    private void loadAgentPrompt(LoadedStandards standards, String employeeCode) {
        Optional<String> content = instructionFileLoader.loadFile(
            "documents/shared/company/fixed-employee-agent-prompt.md");
        if (content.isEmpty()) {
            content = instructionFileLoader.loadFile(
                "documents/shared/company/fixed-employee-agent-prompt");
        }
        content.ifPresent(standards::setAgentPrompt);
    }

    private void loadRunbook(LoadedStandards standards, String employeeCode, String department) {
        Optional<String> content = instructionFileLoader.loadFile(
            "documents/shared/company/fixed-employee-autonomous-runbook.md");
        if (content.isEmpty()) {
            content = instructionFileLoader.loadFile(
                "documents/shared/company/fixed-employee-autonomous-runbook");
        }
        content.ifPresent(standards::setRunbook);
    }

    private void loadDocumentWorkflow(LoadedStandards standards, String employeeCode, String department) {
        Optional<String> content = instructionFileLoader.loadFile(
            "documents/shared/company/fixed-employee-document-workflow.md");
        if (content.isEmpty()) {
            content = instructionFileLoader.loadFile(
                "documents/shared/company/fixed-employee-document-workflow");
        }
        content.ifPresent(standards::setDocumentWorkflow);
    }

    private void loadInstructions(LoadedStandards standards, String entityId) {
        List<String> chain = instructionFileLoader.loadInstructionChain(entityId);
        if (!chain.isEmpty()) {
            standards.setInstructions(instructionFileLoader.mergeInstructions(chain));
        }
    }

    private String resolveDutyCardPath(String department) {
        if (department == null) return null;
        String dept = department.toLowerCase();
        // 优先匹配简化路径 duty-cards/{dept}.md
        String shortPath = "documents/shared/company/duty-cards/" + dept + ".md";
        if (instructionFileLoader.exists(shortPath)) {
            return shortPath;
        }
        // 兜底：完整路径
        return switch (dept) {
            case "hr" -> "documents/shared/company/hr-20-hr-fixed-employee-duty-card.md";
            case "finance" -> "documents/shared/company/hr-21-finance-fixed-employee-duty-card.md";
            case "tech" -> "documents/shared/company/hr-22-tech-fixed-employee-duty-card.md";
            case "sales" -> "documents/shared/company/hr-23-sales-fixed-employee-duty-card.md";
            case "ops", "admin" -> "documents/shared/company/hr-24-ops-fixed-employee-duty-card.md";
            case "cs" -> "documents/shared/company/hr-25-cs-fixed-employee-duty-card.md";
            case "legal" -> "documents/shared/company/hr-26-legal-fixed-employee-duty-card.md";
            default -> null;
        };
    }

    public static class LoadedStandards {
        private final String entityId;
        private final String department;
        private String dutyCard;
        private String systemPrompt;
        private String agentPrompt;
        private String runbook;
        private String documentWorkflow;
        private String instructions;

        public LoadedStandards(String entityId, String department) {
            this.entityId = entityId;
            this.department = department;
        }

        public static LoadedStandards empty() {
            return new LoadedStandards("", "");
        }

        public boolean hasDutyCard() { return dutyCard != null && !dutyCard.isBlank(); }
        public boolean hasSystemPrompt() { return systemPrompt != null && !systemPrompt.isBlank(); }
        public boolean hasAgentPrompt() { return agentPrompt != null && !agentPrompt.isBlank(); }
        public boolean hasRunbook() { return runbook != null && !runbook.isBlank(); }
        public boolean hasDocumentWorkflow() { return documentWorkflow != null && !documentWorkflow.isBlank(); }
        public boolean hasInstructions() { return instructions != null && !instructions.isBlank(); }

        public boolean isFullyLoaded() {
            return hasDutyCard() && hasSystemPrompt() && hasRunbook();
        }

        public List<String> getMissingStandards() {
            List<String> missing = new ArrayList<>();
            if (!hasDutyCard()) missing.add("dutyCard");
            if (!hasSystemPrompt()) missing.add("systemPrompt");
            if (!hasRunbook()) missing.add("runbook");
            return missing;
        }

        public String getEntityId() { return entityId; }
        public String getDepartment() { return department; }
        public String getDutyCard() { return dutyCard; }
        public void setDutyCard(String dutyCard) { this.dutyCard = dutyCard; }
        public String getSystemPrompt() { return systemPrompt; }
        public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
        public String getAgentPrompt() { return agentPrompt; }
        public void setAgentPrompt(String agentPrompt) { this.agentPrompt = agentPrompt; }
        public String getRunbook() { return runbook; }
        public void setRunbook(String runbook) { this.runbook = runbook; }
        public String getDocumentWorkflow() { return documentWorkflow; }
        public void setDocumentWorkflow(String documentWorkflow) { this.documentWorkflow = documentWorkflow; }
        public String getInstructions() { return instructions; }
        public void setInstructions(String instructions) { this.instructions = instructions; }
    }
}
