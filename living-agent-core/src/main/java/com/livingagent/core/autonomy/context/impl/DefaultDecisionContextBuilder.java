package com.livingagent.core.autonomy.context.impl;

import com.livingagent.core.autonomy.context.DecisionContext;
import com.livingagent.core.autonomy.context.DecisionContextBuilder;
import com.livingagent.core.brain.Brain;
import com.livingagent.core.brain.BrainRegistry;
import com.livingagent.core.employee.registry.FixedEmployeeRegistry;
import com.livingagent.core.knowledge.KnowledgeEntry;
import com.livingagent.core.knowledge.KnowledgeManager;
import com.livingagent.core.model.pool.BrainModelResolver;
import com.livingagent.core.model.pool.ResolvedBrainModel;
import com.livingagent.core.security.auth.UnifiedAuthService;
import com.livingagent.core.tool.Tool;
import com.livingagent.core.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class DefaultDecisionContextBuilder implements DecisionContextBuilder {

    private static final Logger log = LoggerFactory.getLogger(DefaultDecisionContextBuilder.class);

    private final UnifiedAuthService authService;
    private final BrainRegistry brainRegistry;
    private final FixedEmployeeRegistry fixedEmployeeRegistry;
    private final ToolRegistry toolRegistry;
    private final KnowledgeManager knowledgeManager;
    private final BrainModelResolver brainModelResolver;

    public DefaultDecisionContextBuilder(
            UnifiedAuthService authService,
            BrainRegistry brainRegistry,
            FixedEmployeeRegistry fixedEmployeeRegistry,
            ToolRegistry toolRegistry,
            KnowledgeManager knowledgeManager,
            BrainModelResolver brainModelResolver) {
        this.authService = authService;
        this.brainRegistry = brainRegistry;
        this.fixedEmployeeRegistry = fixedEmployeeRegistry;
        this.toolRegistry = toolRegistry;
        this.knowledgeManager = knowledgeManager;
        this.brainModelResolver = brainModelResolver;
    }

    @Override
    public DecisionContext build(String message, String userId, String sessionId) {
        return build(message, userId, sessionId, null);
    }

    @Override
    public DecisionContext build(String message, String userId, String sessionId, String department) {
        return buildFull(message, userId, sessionId, new BuildOptions(
            true, true, false, false, false, department, List.of(), 10, 5
        ));
    }

    @Override
    public DecisionContext buildFull(String message, String userId, String sessionId, BuildOptions options) {
        DecisionContext.RequestContext requestContext = buildRequestContext(message, sessionId);
        DecisionContext.UserContext userContext = buildUserContext(userId);
        
        List<DecisionContext.BrainContext> brainContexts = buildBrainContexts(options.targetDepartment());
        List<DecisionContext.EmployeeContext> employeeContexts = options.includeEmployees()
            ? buildEmployeeContexts(options.targetDepartment(), options.requiredCapabilities(), options.maxEmployees())
            : List.of();
        List<DecisionContext.ToolContext> toolContexts = options.includeTools()
            ? buildToolContexts()
            : List.of();
        List<DecisionContext.KnowledgeContext> knowledgeContexts = options.includeKnowledge()
            ? buildKnowledgeContexts(message, options.maxKnowledge())
            : List.of();
        
        DecisionContext.ProjectContext projectContext = options.includeProject()
            ? buildProjectContext(userId)
            : null;
        DecisionContext.ApprovalContext approvalContext = options.includeApproval()
            ? buildApprovalContext(message)
            : null;
        DecisionContext.ConstraintsContext constraintsContext = buildConstraintsContext(userId);

        return new DecisionContext(
            requestContext,
            userContext,
            brainContexts,
            employeeContexts,
            toolContexts,
            knowledgeContexts,
            projectContext,
            approvalContext,
            constraintsContext
        );
    }

    private DecisionContext.RequestContext buildRequestContext(String message, String sessionId) {
        String requestId = UUID.randomUUID().toString();
        return new DecisionContext.RequestContext(
            message,
            sessionId,
            requestId,
            "decision_context",
            Map.of()
        );
    }

    private DecisionContext.UserContext buildUserContext(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        
        return new DecisionContext.UserContext(
            userId, userId, "unknown", List.of(), "CHAT_ONLY", false, Map.of()
        );
    }

    private List<DecisionContext.BrainContext> buildBrainContexts(String targetDepartment) {
        List<DecisionContext.BrainContext> contexts = new ArrayList<>();
        
        for (Brain brain : brainRegistry.getAll()) {
            String dept = brain.getDepartment();
            if (targetDepartment != null && !targetDepartment.equalsIgnoreCase(dept)) {
                continue;
            }
            
            String modelId = null;
            ResolvedBrainModel resolved = brainModelResolver.resolve(brain.getId());
            if (resolved != null) {
                modelId = resolved.getModelName();
            }
            
            contexts.add(new DecisionContext.BrainContext(
                dept,
                brain.getId(),
                brain.getState().name(),
                modelId,
                Map.of()
            ));
        }
        
        return contexts;
    }

    private List<DecisionContext.EmployeeContext> buildEmployeeContexts(
            String department, List<String> requiredCapabilities, int maxEmployees) {
        
        List<FixedEmployeeRegistry.FixedEmployeeDefinition> definitions;
        
        if (department != null && !department.isBlank()) {
            definitions = fixedEmployeeRegistry.getDefinitionsByDepartment(department);
        } else {
            definitions = fixedEmployeeRegistry.getAllDefinitions();
        }
        
        if (requiredCapabilities != null && !requiredCapabilities.isEmpty()) {
            definitions = definitions.stream()
                .filter(d -> d.capabilities().stream()
                    .anyMatch(cap -> requiredCapabilities.stream()
                        .anyMatch(req -> cap.toLowerCase().contains(req.toLowerCase()))))
                .collect(Collectors.toList());
        }
        
        if (definitions.size() > maxEmployees) {
            definitions = definitions.subList(0, maxEmployees);
        }
        
        return definitions.stream()
            .map(def -> new DecisionContext.EmployeeContext(
                def.code(),
                def.name(),
                def.departmentName(),
                def.title(),
                def.roles(),
                def.capabilities(),
                def.tools(),
                0.0,
                0.8,
                true,
                def.neuronId()
            ))
            .collect(Collectors.toList());
    }

    private List<DecisionContext.ToolContext> buildToolContexts() {
        return toolRegistry.getAll().stream()
            .map(tool -> new DecisionContext.ToolContext(
                tool.getName(),
                tool.getDescription(),
                inferRiskLevel(tool),
                tool.requiresApproval(),
                schemaToMap(tool.getSchema())
            ))
            .collect(Collectors.toList());
    }

    private String inferRiskLevel(Tool tool) {
        if (tool.requiresApproval()) {
            return "HIGH";
        }
        String name = tool.getName().toLowerCase();
        if (name.contains("delete") || name.contains("remove") || name.contains("drop")) {
            return "HIGH";
        }
        if (name.contains("write") || name.contains("update") || name.contains("create")) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private Map<String, Object> schemaToMap(Object schema) {
        if (schema == null) return Map.of();
        try {
            return Map.of("type", "object");
        } catch (Exception e) {
            return Map.of();
        }
    }

    private List<DecisionContext.KnowledgeContext> buildKnowledgeContexts(String message, int maxKnowledge) {
        if (knowledgeManager == null || message == null || message.isBlank()) {
            return List.of();
        }
        
        try {
            List<KnowledgeEntry> entries = knowledgeManager.search(message, maxKnowledge);
            return entries.stream()
                .map(e -> new DecisionContext.KnowledgeContext(
                    e.getKey(),
                    e.getCategory() != null ? e.getCategory() : e.getKey(),
                    e.getContent() != null ? e.getContent().toString() : "",
                    e.getSource() != null ? e.getSource() : "unknown",
                    e.getScope() != null ? e.getScope().name() : "L2_DEPARTMENT"
                ))
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.debug("Failed to search knowledge: {}", e.getMessage());
            return List.of();
        }
    }

    private DecisionContext.ProjectContext buildProjectContext(String userId) {
        return null;
    }

    private DecisionContext.ApprovalContext buildApprovalContext(String message) {
        if (message == null) return null;
        
        String lower = message.toLowerCase();
        boolean likelyRequiresApproval = 
            lower.contains("删除") || lower.contains("删除") ||
            lower.contains("审批") || lower.contains("批准") ||
            lower.contains("预算") || lower.contains("合同") ||
            lower.contains("付款") || lower.contains("支付");
        
        if (likelyRequiresApproval) {
            return new DecisionContext.ApprovalContext(
                true,
                "AUTO_DETECTED",
                List.of("需要审批确认"),
                "PENDING"
            );
        }
        
        return new DecisionContext.ApprovalContext(false, null, List.of(), null);
    }

    private DecisionContext.ConstraintsContext buildConstraintsContext(String userId) {
        return new DecisionContext.ConstraintsContext(
            "default",
            "NORMAL",
            List.of(),
            List.of("删除生产数据", "修改系统配置", "外发敏感数据")
        );
    }
}
