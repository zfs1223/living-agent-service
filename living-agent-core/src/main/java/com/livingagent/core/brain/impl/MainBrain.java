package com.livingagent.core.brain.impl;

import com.livingagent.core.brain.Brain;
import com.livingagent.core.brain.BrainContext;
import com.livingagent.core.brain.BrainOutputContract;
import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.evolution.engine.EvolutionDecisionEngine;
import com.livingagent.core.evolution.personality.BrainPersonality;
import com.livingagent.core.evolution.signal.EvolutionSignal;
import com.livingagent.core.knowledge.KnowledgeBase;
import com.livingagent.core.knowledge.KnowledgeEntry;
import com.livingagent.core.model.pool.BrainModelResolver;
import com.livingagent.core.model.pool.ModelHealthRegistry;
import com.livingagent.core.model.pool.ResolvedBrainModel;
import com.livingagent.core.provider.Provider;
import com.livingagent.core.provider.impl.ResolvedBrainModelProvider;
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

    @Override
    public BrainOutputContract processWithContract(ChannelMessage message) {
        if (!running) {
            log.warn("MainBrain received message but not running");
            return BrainOutputContract.failed("MainBrain not running", null);
        }

        try {
            state.set(BrainState.RUNNING);
            lastOutputContract = null;
            doProcess(message);

            // 如果 doProcess 已通过 publishResponse/publishError 设置了 lastOutputContract，直接增强
            if (lastOutputContract != null) {
                String userId = message.getMetadata("user_id");
                String requestType = determineRequestType(message);
                List<String> involvedDepts = identifyDepartments(message);

                BrainOutputContract.Builder enhanced = BrainOutputContract.builder()
                    .status(lastOutputContract.status())
                    .summary(lastOutputContract.summary())
                    .plan(lastOutputContract.plan())
                    .clarificationQuestions(lastOutputContract.clarificationQuestions())
                    .blockingIssues(lastOutputContract.blockingIssues())
                    .assignedWorkers(lastOutputContract.assignedWorkers())
                    .riskLevel(lastOutputContract.riskLevel())
                    .nextSteps(lastOutputContract.nextSteps())
                    .conversationId(lastOutputContract.conversationId())
                    .taskKey(lastOutputContract.taskKey())
                    .executionId(lastOutputContract.executionId())
                    .traceId(lastOutputContract.traceId())
                    .artifacts(lastOutputContract.artifacts())
                    .metadata(lastOutputContract.metadata());

                // 增强：添加 MainBrain 特有信息
                Map<String, Object> enhancedMeta = new LinkedHashMap<>(lastOutputContract.metadata());
                enhancedMeta.put("requestType", requestType);
                enhancedMeta.put("involvedDepartments", involvedDepts);
                if (userId != null) {
                    enhancedMeta.put("userId", userId);
                }
                enhanced.metadata(Map.copyOf(enhancedMeta));

                if (!involvedDepts.isEmpty()) {
                    enhanced.assignedWorkers(involvedDepts.stream()
                        .map(dept -> "department://" + dept)
                        .toList());
                }

                lastOutputContract = enhanced.build();
            }

            return lastOutputContract;
        } catch (Exception e) {
            log.error("Error processing message in MainBrain", e);
            state.set(BrainState.ERROR);
            handleProcessingError(message, e);
            return BrainOutputContract.failed("MainBrain processing error: " + e.getMessage(), null);
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

        // 实际转发：将消息发布到目标部门大脑的输入通道
        try {
            if (brainRegistry != null) {
                Optional<Brain> deptBrain = brainRegistry.getByDepartment(department);
                if (deptBrain.isPresent()) {
                    Brain targetBrain = deptBrain.get();
                    // 构建转发消息，携带协调会话元数据
                    ChannelMessage forwardMessage = ChannelMessage.text(
                        OUTPUT_CHANNEL,                           // sourceChannel
                        getId(),                                  // sourceNeuronId
                        targetBrain.getSubscribedChannels().get(0), // targetChannel
                        original.getSessionId(),
                        original.getContent()
                    );
                    // 添加协调元数据
                    forwardMessage.addMetadata("coordination_session_id", session.sessionId);
                    forwardMessage.addMetadata("forwarded_by", getId());
                    forwardMessage.addMetadata("original_user_id", original.getMetadata("user_id"));
                    forwardMessage.addMetadata("original_department", original.getMetadata("department"));

                    // 发布到目标大脑的输入通道
                    publish(targetBrain.getSubscribedChannels().get(0), forwardMessage);
                    log.info("Successfully forwarded message to department {} brain {} via channel {}",
                        department, targetBrain.getId(), targetBrain.getSubscribedChannels().get(0));
                } else {
                    log.warn("No brain found for department: {}, skipping forward", department);
                }
            }
        } catch (Exception e) {
            log.error("Failed to forward message to department {}: {}", department, e.getMessage(), e);
        }
    }

    private Provider ensureProvider() {
        Provider provider = getProvider();
        if (provider != null) {
            return provider;
        }
        if (getBrainModelResolver() == null) {
            log.warn("MainBrain has no BrainModelResolver, cannot create provider");
            return null;
        }
        ResolvedBrainModel resolved = getBrainModelResolver().resolve(getId());
        if (resolved == null) {
            log.warn("MainBrain could not resolve model from BrainModelResolver");
            return null;
        }
        provider = new ResolvedBrainModelProvider(resolved);
        updateProvider(provider);
        log.info("MainBrain provider initialized: providerId={}, model={}",
            resolved.getProviderId(), resolved.getModelName());
        return provider;
    }

    public String callLlm(String systemPrompt, String userMessage) {
        return callLlm(systemPrompt, userMessage, getMaxTokensFromModel(), getTemperatureFromModel());
    }

    public String callLlm(String systemPrompt, String userMessage, int maxTokens, double temperature) {
        return callLlmWithTools(systemPrompt, userMessage, maxTokens, temperature, true);
    }

    /**
     * 带工具调用的 LLM 调用方法。
     * 
     * @param systemPrompt 系统提示
     * @param userMessage 用户消息
     * @param maxTokens 最大 token 数
     * @param temperature 温度参数
     * @param enableTools 是否启用工具调用
     * @return LLM 响应内容
     */
    public String callLlmWithTools(String systemPrompt, String userMessage, int maxTokens, double temperature, boolean enableTools) {
        Provider provider = ensureProvider();
        if (provider == null) {
            log.warn("MainBrain.callLlm: Provider not available");
            return null;
        }

        String modelId = getDefaultModel();
        ResolvedBrainModel resolvedModel = getCurrentModel();
        if (resolvedModel != null) {
            modelId = resolvedModel.getModelName();
            if (maxTokens <= 0) maxTokens = resolvedModel.getMaxTokens();
            if (temperature < 0) temperature = resolvedModel.getTemperature();
            log.debug("MainBrain.callLlm using resolved model: {} (maxTokens: {}, temperature: {})",
                modelId, maxTokens, temperature);
        }

        List<Provider.ChatMessage> history = new ArrayList<>();
        history.add(Provider.ChatMessage.system(systemPrompt));
        history.add(Provider.ChatMessage.user(userMessage != null ? userMessage : ""));

        // 获取工具 schema（如果启用工具调用）
        List<com.livingagent.core.tool.ToolSchema> toolSchemas = enableTools ? getToolSchemas() : List.of();

        // 最大工具迭代次数：对于复杂任务（如代码探索、多文件分析）需要更多迭代
        // 当达到限制时，会强制让 LLM 给出最终响应而不是返回错误
        int maxToolIterations = 20;
        int toolIteration = 0;
        
        try {
            while (toolIteration < maxToolIterations) {
                Provider.ChatRequest request = new Provider.ChatRequest(
                    history,
                    toolSchemas,
                    modelId,
                    temperature,
                    maxTokens
                );

                Provider.ChatResponse response = provider.chat(request).join();
                log.debug("MainBrain.callLlm iteration {}: model={}, tokens(prompt={}, completion={}), hasToolCalls={}",
                    toolIteration, modelId, response.promptTokens(), response.completionTokens(), response.hasToolCalls());

                // 如果没有工具调用，返回最终响应
                if (!response.hasToolCalls()) {
                    return response.content();
                }

                // 处理工具调用
                List<Provider.ToolCallData> toolCalls = response.toolCalls();
                log.info("MainBrain executing {} tool calls (iteration {})", toolCalls.size(), toolIteration);

                // 将助手响应（包含工具调用）添加到历史
                history.add(Provider.ChatMessage.assistantWithTools(response.content(), toolCalls));

                // 执行每个工具调用
                List<Provider.ToolResultData> toolResults = new ArrayList<>();
                for (Provider.ToolCallData toolCall : toolCalls) {
                    String toolName = toolCall.name();
                    String toolArgs = toolCall.arguments();
                    
                    log.info("MainBrain executing tool: {} (args: {})", toolName, toolArgs);
                    
                    String resultContent = executeToolCall(toolName, toolArgs);
                    toolResults.add(new Provider.ToolResultData(toolCall.id(), resultContent));
                }

                // 将工具结果添加到历史
                history.add(Provider.ChatMessage.toolResult(toolResults));

                toolIteration++;
            }

            // 达到最大迭代次数，强制让 LLM 基于当前历史给出最终响应（不再允许工具调用）
            log.info("MainBrain reached max tool iterations ({}), forcing final response", maxToolIterations);
            Provider.ChatRequest finalRequest = new Provider.ChatRequest(
                history,
                List.of(), // 不再允许工具调用
                modelId,
                temperature,
                maxTokens
            );
            Provider.ChatResponse finalResponse = provider.chat(finalRequest).join();
            return finalResponse.content() != null ? finalResponse.content() : "已完成工具调用，但未能生成最终响应。";
            
        } catch (Exception e) {
            log.error("MainBrain.callLlm failed: {}", e.getMessage());
            ResolvedBrainModel failedModel = getCurrentModel();
            if (failedModel != null && failedModel.getModelId() != null) {
                ModelHealthRegistry registry = getModelHealthRegistry();
                if (registry != null) {
                    registry.recordFailure(failedModel.getModelId().toString(), failedModel.getProviderId(),
                        "callLlm failed: " + e.getMessage());
                    log.info("[BrainTrace] brain={} event=model_failure_recorded model={} provider={} error={}",
                        id, failedModel.getModelName(), failedModel.getProviderId(), e.getMessage());
                }
            }
            ResolvedBrainModel fallbackModel = tryFallbackModel(failedModel);
            if (fallbackModel != null) {
                log.info("MainBrain.callLlm retrying with fallback model: {}", fallbackModel.getModelName());
                try {
                    Provider fallbackProvider = new ResolvedBrainModelProvider(fallbackModel);
                    List<Provider.ChatMessage> retryHistory = new ArrayList<>();
                    retryHistory.add(Provider.ChatMessage.system(systemPrompt));
                    retryHistory.add(Provider.ChatMessage.user(userMessage != null ? userMessage : ""));
                    Provider.ChatRequest retryRequest = new Provider.ChatRequest(
                        retryHistory, List.of(), fallbackModel.getModelName(), temperature, maxTokens);
                    Provider.ChatResponse retryResponse = fallbackProvider.chat(retryRequest).join();
                    if (retryResponse.content() != null && !retryResponse.content().isBlank()) {
                        if (fallbackModel.getModelId() != null) {
                            ModelHealthRegistry reg = getModelHealthRegistry();
                            if (reg != null) {
                                reg.recordSuccess(fallbackModel.getModelId().toString(), fallbackModel.getProviderId(), 0);
                            }
                        }
                        return retryResponse.content();
                    }
                } catch (Exception retryEx) {
                    log.error("MainBrain.callLlm fallback also failed: {}", retryEx.getMessage());
                    if (fallbackModel.getModelId() != null) {
                        ModelHealthRegistry reg = getModelHealthRegistry();
                        if (reg != null) {
                            reg.recordFailure(fallbackModel.getModelId().toString(), fallbackModel.getProviderId(),
                                "fallback also failed: " + retryEx.getMessage());
                        }
                    }
                }
            }
            return null;
        }
    }

    /**
     * 工具别名映射：LLM 可能将子操作名误认为独立工具名，此处做别名解析。
     * 例如 FileEditTool 的 search_code 子操作可能被 LLM 当成独立工具调用。
     */
    private static final Map<String, String> TOOL_ALIAS_MAP = Map.of(
        "search_code", "file_edit"
    );

    private String resolveToolName(String toolName) {
        String resolved = TOOL_ALIAS_MAP.getOrDefault(toolName, toolName);
        if (!resolved.equals(toolName)) {
            log.info("MainBrain: Tool alias resolved: {} -> {}", toolName, resolved);
        }
        return resolved;
    }

    /**
     * 执行工具调用。
     */
    private String executeToolCall(String toolName, String argumentsJson) {
        try {
            String resolvedToolName = resolveToolName(toolName);
            Optional<Tool> toolOpt = tools.stream()
                .filter(t -> t.getName().equals(resolvedToolName))
                .findFirst();
            
            if (toolOpt.isEmpty()) {
                log.warn("MainBrain: Tool not found: {}", toolName);
                return "错误：工具 '" + toolName + "' 不存在";
            }
            
            Tool tool = toolOpt.get();
            
            // 解析参数
            Map<String, Object> args = new java.util.HashMap<>();
            if (argumentsJson != null && !argumentsJson.isBlank()) {
                try {
                    args = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(argumentsJson, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                } catch (Exception e) {
                    log.warn("Failed to parse tool arguments as JSON, using raw string: {}", e.getMessage());
                    args.put("input", argumentsJson);
                }
            }

            // 别名解析时自动注入 action 参数（LLM 调用子操作名时不会传 action 字段）
            if (!resolvedToolName.equals(toolName) && !args.containsKey("action")) {
                args.put("action", toolName);
                log.info("MainBrain: Auto-injected action={} for aliased tool call {} -> {}",
                    toolName, toolName, resolvedToolName);
            }
            
            // 执行工具
            com.livingagent.core.tool.Tool.ToolParams params = com.livingagent.core.tool.Tool.ToolParams.of(args);
            com.livingagent.core.tool.ToolContext context = new com.livingagent.core.tool.ToolContext(
                null,  // neuronId
                null,  // sessionId
                null,  // securityPolicy
                java.time.Duration.ofSeconds(60),  // timeout
                false, // sandboxed
                null   // workingDirectory
            );
            
            com.livingagent.core.tool.ToolResult result = tool.execute(params, context);
            
            if (result.success()) {
                Object data = result.data();
                if (data != null) {
                    return data.toString();
                }
                return "工具执行成功";
            } else {
                return "工具执行失败: " + result.error();
            }
            
        } catch (Exception e) {
            log.error("MainBrain tool execution failed: tool={}, error={}", toolName, e.getMessage());
            return "工具执行异常: " + e.getMessage();
        }
    }

    private String executeWithLLM(ChannelMessage message, String context) {
        String systemPrompt = String.format(SYSTEM_PROMPT,
            personality.getRigor(),
            personality.getCreativity(),
            personality.getRiskTolerance(),
            personality.getObedience()
        );

        String result = callLlm(systemPrompt, extractText(message));
        return result != null ? result : "LLM服务暂时不可用";
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

    protected String extractText(ChannelMessage message) {
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
