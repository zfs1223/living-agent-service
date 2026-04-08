package com.livingagent.core.brain.impl;

import com.livingagent.core.brain.Brain;
import com.livingagent.core.brain.BrainContext;
import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.evolution.engine.EvolutionDecisionEngine;
import com.livingagent.core.evolution.personality.BrainPersonality;
import com.livingagent.core.evolution.signal.EvolutionSignal;
import com.livingagent.core.knowledge.KnowledgeBase;
import com.livingagent.core.knowledge.KnowledgeEntry;
import com.livingagent.core.model.selector.MainBrainModelSelector;
import com.livingagent.core.provider.Provider;
import com.livingagent.core.security.*;
import com.livingagent.core.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MainBrain extends AbstractBrain {

    private static final Logger log = LoggerFactory.getLogger(MainBrain.class);

    public static final String ID = "neuron://core/main-brain/001";
    public static final String INPUT_CHANNEL = "channel://dispatch/cross_department";
    public static final String OUTPUT_CHANNEL = "channel://output/main";

    private static final String SYSTEM_PROMPT = """
        你是企业的主大脑，负责跨部门协调和权限管理。
        
        核心职责：
        1. 跨部门协调 - 处理涉及多个部门的复杂任务
        2. 权限管理 - 确保信息访问符合权限边界
        3. 企业知识整合 - 维护组织架构和业务流程知识
        4. 冲突解决 - 处理部门间的资源或优先级冲突
        
        人格参数：
        - 严谨度(rigor): %.2f
        - 创造力(creativity): %.2f
        - 风险容忍(riskTolerance): %.2f
        - 服从性(obedience): %.2f
        
        处理原则：
        - 安全优先：遇到敏感信息时，严格遵守权限边界
        - 效率优先：优先选择最短路径解决问题
        - 透明沟通：向用户说明协调过程和决策依据
        """;

    private final BrainRegistryImpl brainRegistry;
    private final PermissionService permissionService;
    private final Map<String, CoordinationSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<String, Integer> departmentRequestCounts = new ConcurrentHashMap<>();
    private MainBrainModelSelector modelSelector;

    public MainBrain(List<Tool> tools, BrainRegistryImpl brainRegistry, PermissionService permissionService) {
        super(
            ID,
            "MainBrain",
            "core",
            List.of(INPUT_CHANNEL, "channel://dispatch/*"),
            List.of(OUTPUT_CHANNEL),
            tools
        );
        this.brainRegistry = brainRegistry;
        this.permissionService = permissionService;
    }

    @Autowired(required = false)
    public void setModelSelector(MainBrainModelSelector modelSelector) {
        this.modelSelector = modelSelector;
        if (modelSelector != null) {
            log.info("MainBrain: Model selector enabled, current model: {}", 
                modelSelector.getCurrentModel().getDisplayName());
        }
    }

    @Override
    protected void doStart(BrainContext context) {
        log.info("MainBrain started - ready for cross-department coordination");
        log.info("Personality: {}", personality.toKey());
    }

    @Override
    protected void doStop() {
        log.info("MainBrain stopping - active sessions: {}", activeSessions.size());
        activeSessions.clear();
    }

    @Override
    protected void doProcess(ChannelMessage message) {
        log.info("MainBrain processing message: {}", message.getId());

        String userId = message.getMetadata("user_id");
        String userDepartment = message.getMetadata("department");

        if (!checkPermission(userId, message)) {
            publishForbidden(message, userId);
            return;
        }

        String requestType = determineRequestType(message);
        
        switch (requestType) {
            case "cross_department" -> handleCrossDepartmentRequest(message, userId, userDepartment);
            case "permission_check" -> handlePermissionCheck(message, userId);
            case "knowledge_query" -> handleKnowledgeQuery(message, userId);
            case "conflict_resolution" -> handleConflictResolution(message);
            default -> handleGeneralRequest(message, userId);
        }
    }

    private boolean checkPermission(String userId, ChannelMessage message) {
        if (userId == null) {
            log.warn("Message without user_id, denying access");
            return false;
        }

        if (permissionService == null) {
            log.warn("PermissionService not available, allowing access");
            return true;
        }

        AccessLevel level = permissionService.getAccessLevel(userId);
        if (level == AccessLevel.CHAT_ONLY) {
            log.info("User {} has CHAT_ONLY access, routing to chat neuron", userId);
            return false;
        }

        return true;
    }

    private void publishForbidden(ChannelMessage original, String userId) {
        ChannelMessage forbidden = ChannelMessage.error(
            OUTPUT_CHANNEL,
            getId(),
            original.getSourceChannelId(),
            original.getSessionId(),
            "访问被拒绝：您没有权限执行此操作。请使用闲聊功能。"
        );
        forbidden.addMetadata("error_code", "FORBIDDEN");
        forbidden.addMetadata("user_id", userId);
        publish(OUTPUT_CHANNEL, forbidden);
    }

    private String determineRequestType(ChannelMessage message) {
        String content = extractText(message);
        if (content == null) {
            return "general";
        }

        String lowerContent = content.toLowerCase();
        
        if (lowerContent.contains("跨部门") || lowerContent.contains("协调") || 
            lowerContent.contains("多个部门") || lowerContent.contains("协作")) {
            return "cross_department";
        }
        
        if (lowerContent.contains("权限") || lowerContent.contains("访问") ||
            lowerContent.contains("授权") || lowerContent.contains("审批")) {
            return "permission_check";
        }
        
        if (lowerContent.contains("知识") || lowerContent.contains("流程") ||
            lowerContent.contains("组织") || lowerContent.contains("架构")) {
            return "knowledge_query";
        }
        
        if (lowerContent.contains("冲突") || lowerContent.contains("争议") ||
            lowerContent.contains("优先级")) {
            return "conflict_resolution";
        }

        return "general";
    }

    private void handleCrossDepartmentRequest(ChannelMessage message, String userId, String userDepartment) {
        log.info("Handling cross-department request from user {} (dept: {})", userId, userDepartment);

        CoordinationSession session = createCoordinationSession(message, userId);
        activeSessions.put(session.sessionId, session);

        try {
            List<String> involvedDepartments = identifyDepartments(message);
            session.involvedDepartments.addAll(involvedDepartments);

            String coordinationPlan = createCoordinationPlan(message, involvedDepartments);
            session.plan = coordinationPlan;

            for (String dept : involvedDepartments) {
                if (brainRegistry != null) {
                    Optional<Brain> deptBrain = brainRegistry.getByDepartment(dept);
                    if (deptBrain.isPresent()) {
                        forwardToDepartment(session, dept, message);
                        session.forwardedDepartments.add(dept);
                    }
                }
            }

            String response = formatCoordinationResponse(session);
            publishResponse(message, response, session);

        } catch (Exception e) {
            log.error("Failed to handle cross-department request", e);
            publishResponse(message, "跨部门协调失败: " + e.getMessage(), null);
        } finally {
            activeSessions.remove(session.sessionId);
        }
    }

    private void handlePermissionCheck(ChannelMessage message, String userId) {
        log.info("Handling permission check for user {}", userId);

        Map<String, Object> permissionInfo = new HashMap<>();
        
        if (permissionService != null) {
            permissionInfo.put("userId", userId);
            permissionInfo.put("accessLevel", permissionService.getAccessLevel(userId));
            permissionInfo.put("accessibleBrains", permissionService.getAccessibleBrains(userId));
            permissionInfo.put("allowedModels", permissionService.getAllowedModels(userId));
        }

        String response = formatPermissionResponse(permissionInfo);
        publishResponse(message, response, null);
    }

    private void handleKnowledgeQuery(ChannelMessage message, String userId) {
        log.info("Handling knowledge query from user {}", userId);

        KnowledgeBase knowledgeBase = getKnowledgeBase();
        if (knowledgeBase == null) {
            publishResponse(message, "知识库暂时不可用", null);
            return;
        }

        String query = extractText(message);
        List<KnowledgeEntry> results = knowledgeBase.search(query);

        String response = formatKnowledgeResponse(results);
        publishResponse(message, response, null);
    }

    private void handleConflictResolution(ChannelMessage message) {
        log.info("Handling conflict resolution request");

        String response = executeWithLLM(message, "conflict_resolution");
        publishResponse(message, response, null);
    }

    private void handleGeneralRequest(ChannelMessage message, String userId) {
        log.info("Handling general request from user {}", userId);

        String response = executeWithLLM(message, "general");
        publishResponse(message, response, null);
    }

    private CoordinationSession createCoordinationSession(ChannelMessage message, String userId) {
        CoordinationSession session = new CoordinationSession();
        session.sessionId = "coord_" + System.currentTimeMillis();
        session.userId = userId;
        session.originalMessage = message;
        session.createdAt = System.currentTimeMillis();
        return session;
    }

    private List<String> identifyDepartments(ChannelMessage message) {
        String content = extractText(message);
        List<String> departments = new ArrayList<>();

        if (content == null) {
            return departments;
        }

        String lowerContent = content.toLowerCase();
        
        if (lowerContent.contains("技术") || lowerContent.contains("开发") || lowerContent.contains("代码")) {
            departments.add("tech");
        }
        if (lowerContent.contains("人事") || lowerContent.contains("招聘") || lowerContent.contains("hr")) {
            departments.add("hr");
        }
        if (lowerContent.contains("财务") || lowerContent.contains("报销") || lowerContent.contains("预算")) {
            departments.add("finance");
        }
        if (lowerContent.contains("销售") || lowerContent.contains("客户") || lowerContent.contains("商机")) {
            departments.add("sales");
        }
        if (lowerContent.contains("客服") || lowerContent.contains("工单") || lowerContent.contains("投诉")) {
            departments.add("cs");
        }
        if (lowerContent.contains("行政") || lowerContent.contains("会议") || lowerContent.contains("资产")) {
            departments.add("admin");
        }
        if (lowerContent.contains("法务") || lowerContent.contains("合同") || lowerContent.contains("合规")) {
            departments.add("legal");
        }
        if (lowerContent.contains("运营") || lowerContent.contains("数据") || lowerContent.contains("分析")) {
            departments.add("ops");
        }

        return departments;
    }

    private String createCoordinationPlan(ChannelMessage message, List<String> departments) {
        StringBuilder plan = new StringBuilder();
        plan.append("协调计划:\n");
        plan.append("1. 分析任务需求\n");
        plan.append("2. 确定涉及部门: ").append(String.join(", ", departments)).append("\n");
        plan.append("3. 分配子任务到各部门\n");
        plan.append("4. 汇总结果并整合\n");
        return plan.toString();
    }

    private void forwardToDepartment(CoordinationSession session, String department, ChannelMessage original) {
        log.info("Forwarding request to department: {}", department);
        departmentRequestCounts.merge(department, 1, Integer::sum);
    }

    private String executeWithLLM(ChannelMessage message, String context) {
        Provider provider = getProvider();
        if (provider == null) {
            return "LLM服务暂时不可用";
        }

        String systemPrompt = String.format(SYSTEM_PROMPT,
            personality.getRigor(),
            personality.getCreativity(),
            personality.getRiskTolerance(),
            personality.getObedience()
        );

        List<Provider.ChatMessage> history = new ArrayList<>();
        history.add(Provider.ChatMessage.system(systemPrompt));
        history.add(Provider.ChatMessage.user(extractText(message)));

        String modelId = "qwen3.5-27b";
        int contextLength = 4096;
        
        if (modelSelector != null) {
            modelId = modelSelector.getEffectiveModelId();
            contextLength = modelSelector.getCurrentModel().getContextLength();
            log.debug("Using model from selector: {} (context: {})", modelId, contextLength);
        }

        Provider.ChatRequest request = new Provider.ChatRequest(
            history,
            List.of(),
            modelId,
            0.7,
            contextLength
        );

        try {
            Provider.ChatResponse response = provider.chat(request).join();
            return response.content();
        } catch (Exception e) {
            log.error("LLM execution failed", e);
            return "处理请求时发生错误: " + e.getMessage();
        }
    }

    private String formatCoordinationResponse(CoordinationSession session) {
        StringBuilder sb = new StringBuilder();
        sb.append("跨部门协调结果:\n\n");
        sb.append("会话ID: ").append(session.sessionId).append("\n");
        sb.append("涉及部门: ").append(String.join(", ", session.involvedDepartments)).append("\n");
        sb.append("协调计划:\n").append(session.plan).append("\n");
        sb.append("状态: 协调中\n");
        return sb.toString();
    }

    private String formatPermissionResponse(Map<String, Object> info) {
        StringBuilder sb = new StringBuilder();
        sb.append("权限信息:\n\n");
        info.forEach((k, v) -> sb.append(k).append(": ").append(v).append("\n"));
        return sb.toString();
    }

    private String formatKnowledgeResponse(List<KnowledgeEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return "未找到相关知识";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("知识检索结果:\n\n");
        for (int i = 0; i < Math.min(5, entries.size()); i++) {
            KnowledgeEntry entry = entries.get(i);
            sb.append(i + 1).append(". ").append(entry.getKey()).append("\n");
        }
        return sb.toString();
    }

    private void publishResponse(ChannelMessage original, String content, CoordinationSession session) {
        ChannelMessage response = ChannelMessage.text(
            OUTPUT_CHANNEL,
            getId(),
            original.getSourceChannelId(),
            original.getSessionId(),
            content
        );
        response.addMetadata("brain_id", getId());
        response.addMetadata("brain_name", "MainBrain");
        if (session != null) {
            response.addMetadata("coordination_session", session.sessionId);
        }
        publish(OUTPUT_CHANNEL, response);
    }

    private String extractText(ChannelMessage message) {
        Object payload = message.getPayload();
        return payload != null ? payload.toString() : null;
    }

    public Map<String, Object> getCoordinationStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("activeSessions", activeSessions.size());
        stats.put("departmentRequests", new HashMap<>(departmentRequestCounts));
        stats.put("personality", personality.toKey());
        stats.put("evolutionStats", getEvolutionStats());
        return stats;
    }
    
    @Override
    public List<com.livingagent.core.tool.ToolSchema> getToolSchemas() {
        return tools.stream().map(Tool::getSchema).toList();
    }
    
    @Override
    protected String buildPrompt(BrainContext context, String userInput) {
        return String.format(SYSTEM_PROMPT,
            personality.getRigor(),
            personality.getCreativity(),
            personality.getRiskTolerance(),
            personality.getObedience()
        ) + "\n\n用户: " + userInput;
    }

    private static class CoordinationSession {
        String sessionId;
        String userId;
        ChannelMessage originalMessage;
        long createdAt;
        List<String> involvedDepartments = new ArrayList<>();
        List<String> forwardedDepartments = new ArrayList<>();
        String plan;
        Map<String, Object> results = new HashMap<>();
    }
}
