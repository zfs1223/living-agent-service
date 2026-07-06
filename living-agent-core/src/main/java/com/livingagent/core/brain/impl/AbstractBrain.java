package com.livingagent.core.brain.impl;

import com.livingagent.core.brain.Brain;
import com.livingagent.core.brain.BrainBoundaryEnforcer;
import com.livingagent.core.brain.BrainContext;
import com.livingagent.core.brain.BrainOutputContract;
import com.livingagent.core.brain.compact.ContextCompactor;
import com.livingagent.core.brain.compact.CompactionResult;
import com.livingagent.core.brain.prompt.DynamicPromptBuilder;
import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.evolution.engine.EvolutionDecisionEngine;
import com.livingagent.core.evolution.personality.BrainPersonality;
import com.livingagent.core.evolution.signal.EvolutionSignal;
import com.livingagent.core.knowledge.KnowledgeBase;
import com.livingagent.core.memory.Memory;
import com.livingagent.core.memory.MemoryCategory;
import com.livingagent.core.model.TokenUsage;
import com.livingagent.core.model.UsageTracker;
import com.livingagent.core.model.pool.BrainModelResolver;
import com.livingagent.core.model.pool.ModelHealthRegistry;
import com.livingagent.core.model.pool.ResolvedBrainModel;
import com.livingagent.core.model.selector.BrainModelSelector;
import com.livingagent.core.provider.Provider;
import com.livingagent.core.tool.Tool;
import com.livingagent.core.tool.ToolContext;
import com.livingagent.core.tool.ToolRegistry;
import com.livingagent.core.tool.ToolResult;
import com.livingagent.core.tool.ToolSchema;
import com.livingagent.core.tool.hook.ToolHookManager;
import com.livingagent.core.tool.hook.ToolHookResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public abstract class AbstractBrain implements Brain {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final String id;
    protected final String name;
    protected final String department;
    protected final List<String> subscribedChannels;
    protected final List<String> publishChannels;
    protected final List<Tool> tools;

    protected final AtomicReference<BrainState> state = new AtomicReference<>(BrainState.INITIALIZING);
    protected volatile BrainContext context;
    protected volatile boolean running = false;
    protected final Map<String, Object> stateData = new ConcurrentHashMap<>();

    /** 拆分出的会话管理器 */
    protected final BrainSessionManager sessionManager;

    /** 拆分出的模型降级管理器 */
    protected final BrainModelFallback modelFallback;

    /** 拆分出的 ReAct 引擎 */
    protected BrainReActEngine reactEngine;
    
    protected BrainPersonality personality;
    protected AtomicInteger evolutionSuccessCount = new AtomicInteger(0);
    protected AtomicInteger evolutionFailureCount = new AtomicInteger(0);
    protected AtomicLong lastEvolutionTime = new AtomicLong(0);

    /** 最后一次 processWithContract() 调用产生的结构化输出契约 */
    protected volatile BrainOutputContract lastOutputContract;

    protected static final int DEFAULT_MAX_ITERATIONS = 10;
    protected static final String MAX_ITERATIONS_PROPERTY = "livingagent.brain.maxIterations";
    protected static final int DEFAULT_MAX_TOKENS = 4096;
    protected static final double DEFAULT_TEMPERATURE = 0.7;
    protected static final int PERSIST_THRESHOLD = 30000;
    protected static final int MICRO_COMPACT_KEEP_RECENT = 3;

    private volatile ContextCompactor contextCompactor;
    private volatile UsageTracker usageTracker;
    private volatile ToolHookManager hookManager;
    private volatile BrainBoundaryEnforcer brainBoundaryEnforcer;

    protected AbstractBrain(String id, String name, String department,
                            List<String> subscribedChannels, List<String> publishChannels,
                            List<Tool> tools) {
        this.id = id;
        this.name = name;
        this.department = department;
        this.subscribedChannels = Collections.unmodifiableList(new ArrayList<>(subscribedChannels));
        this.publishChannels = Collections.unmodifiableList(new ArrayList<>(publishChannels));
        this.tools = Collections.unmodifiableList(new ArrayList<>(tools));
        this.personality = BrainPersonality.getDefaultForBrain(name);

        // 初始化拆分出的组件
        this.sessionManager = new BrainSessionManager(id);
        this.modelFallback = new BrainModelFallback(id, name);
        this.reactEngine = new BrainReActEngine(id, name, this.tools, this.modelFallback);
    }

    public boolean hasProvider() {
        return context != null && context.getProvider() != null;
    }

    @Override
    public String getId() { return id; }

    @Override
    public String getName() { return name; }

    @Override
    public String getDepartment() { return department; }

    @Override
    public BrainState getState() { return state.get(); }

    @Override
    public List<Tool> getTools() { return tools; }

    @Override
    public List<String> getSubscribedChannels() { return subscribedChannels; }

    @Override
    public List<String> getPublishChannels() { return publishChannels; }
    
    public BrainPersonality getPersonality() { return personality; }
    
    public int getEvolutionSuccessCount() { return evolutionSuccessCount.get(); }
    
    public int getEvolutionFailureCount() { return evolutionFailureCount.get(); }

    public void setContextCompactor(ContextCompactor compactor) {
        this.contextCompactor = compactor;
        this.reactEngine.setContextCompactor(compactor);
    }

    public void setUsageTracker(UsageTracker tracker) {
        this.usageTracker = tracker;
        this.reactEngine.setUsageTracker(tracker);
    }

    public UsageTracker getUsageTracker() {
        return usageTracker;
    }

    public void setHookManager(ToolHookManager hookManager) {
        this.hookManager = hookManager;
        this.reactEngine.setHookManager(hookManager);
    }

    public ToolHookManager getHookManager() {
        return hookManager;
    }

    @Override
    public void start(BrainContext context) {
        if (running) {
            if (context != null && context.getProvider() != null) {
                updateContextWithProvider(context);
                return;
            }
            log.warn("Brain {} already running, ignoring start without Provider", id);
            return;
        }

        this.context = context;
        
        if (context.getPersonality() != null) {
            this.personality = context.getPersonality();
        }
        
        state.set(BrainState.INITIALIZING);

        try {
            doStart(context);
            running = true;
            state.set(BrainState.RUNNING);
            log.info("Brain {} started for department {} with personality {}", id, department, personality.toKey());
        } catch (Exception e) {
            state.set(BrainState.ERROR);
            log.error("Failed to start brain: {}", id, e);
            throw new RuntimeException("Failed to start brain: " + id, e);
        }
    }

    public void updateContextWithProvider(BrainContext newContext) {
        if (newContext == null || newContext.getProvider() == null) {
            log.warn("Brain {} attempted to update context with null Provider", id);
            return;
        }

        if (context != null) {
            log.info("Brain {} context updated: Provider {} -> {}",
                id,
                context.getProvider() != null ? context.getProvider().name() : "null",
                newContext.getProvider().name());

            this.context = newContext;
        } else {
            this.context = newContext;
            log.info("Brain {} context initialized with Provider {}", id, newContext.getProvider().name());
        }

        if (newContext.getPersonality() != null) {
            this.personality = newContext.getPersonality();
        }
    }

    public void updateProvider(Provider provider) {
        if (provider == null) {
            log.warn("Brain {} attempted to update with null Provider", id);
            return;
        }

        if (context == null) {
            log.warn("Brain {} context is null, cannot update Provider. Provider will be set when context is created.", id);
            return;
        }

        log.info("Brain {} Provider updated: {} -> {}",
            id,
            context.getProvider() != null ? context.getProvider().name() : "null",
            provider.name());

        BrainContext.Builder builder = BrainContext.builder()
            .brainId(context.getBrainId())
            .department(context.getDepartment())
            .sessionId(context.getSessionId())
            .provider(provider)
            .memory(context.getMemory())
            .toolRegistry(context.getToolRegistry())
            .knowledgeBase(context.getKnowledgeBase())
            .evolutionEngine(context.getEvolutionEngine())
            .personality(context.getPersonality())
            .channelManager(context.getChannelManager())
            .skillRegistry(context.getSkillRegistry())
            .instructionFileLoader(context.getInstructionFileLoader())
            .employeeId(context.getEmployeeId());

        this.context = builder.build();
        // 保留 clientId 和 accessLevel（updateProvider 会重建 context，需手动恢复）
        this.context.setClientId(context.getClientId());
        this.context.setAccessLevel(context.getAccessLevel());
    }

    public BrainContext getContext() {
        return context;
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }

        running = false;
        state.set(BrainState.STOPPED);

        try {
            doStop();
            log.info("Brain {} stopped", id);
        } catch (Exception e) {
            log.error("Failed to stop brain: {}", id, e);
        }
    }

    @Override
    public void process(ChannelMessage message) {
        if (!running) {
            log.warn("Brain {} received message but not running", id);
            return;
        }

        try {
            state.set(BrainState.RUNNING);
            lastOutputContract = null;
            doProcess(message);
        } catch (Exception e) {
            log.error("Error processing message in brain: {}", id, e);
            state.set(BrainState.ERROR);
            handleProcessingError(message, e);
        }
    }

    @Override
    public BrainOutputContract processWithContract(ChannelMessage message) {
        if (!running) {
            log.warn("Brain {} received message but not running", id);
            return BrainOutputContract.failed("Brain not running: " + id, null);
        }

        try {
            state.set(BrainState.RUNNING);
            lastOutputContract = null;
            doProcess(message);
            return lastOutputContract;
        } catch (Exception e) {
            log.error("Error processing message in brain: {}", id, e);
            state.set(BrainState.ERROR);
            handleProcessingError(message, e);
            return BrainOutputContract.failed("Processing error: " + e.getMessage(), null);
        }
    }

    protected abstract void doStart(BrainContext context);

    protected abstract void doStop();

    protected abstract void doProcess(ChannelMessage message);
    
    protected abstract String buildPrompt(BrainContext context, String userInput);
    
    public abstract List<ToolSchema> getToolSchemas();

    /**
     * P2-2: 根据实际注入的工具动态生成工具列表描述，替代硬编码的工具列表
     * 子类可在 doGetSystemPrompt() 中调用此方法获取动态工具描述
     */
    protected String buildDynamicToolList() {
        if (tools == null || tools.isEmpty()) {
            return "（当前无可用工具）";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("你可以使用以下工具：\n");
        for (Tool tool : tools) {
            ToolSchema schema = tool.getSchema();
            String name = schema != null ? schema.name() : tool.getName();
            String desc = schema != null ? schema.description() : "";
            sb.append("- ").append(name).append(" : ").append(desc).append("\n");
        }
        return sb.toString().trim();
    }

    private static final String WORKSPACE_ROOT_PROPERTY = "livingagent.workspace.root";
    private static final String DEFAULT_WORKSPACE_ROOT = "/app/workspace";

    protected String getSystemPrompt() {
        String base = doGetSystemPrompt();

        DynamicPromptBuilder builder = new DynamicPromptBuilder()
            .basePrompt(base)
            .personality(personality);

        String workspaceRoot = System.getProperty(WORKSPACE_ROOT_PROPERTY, DEFAULT_WORKSPACE_ROOT);
        boolean writeEnabled = Boolean.parseBoolean(
            System.getProperty("livingagent.workspace.writeEnabled", "true"));
        builder.workspace(workspaceRoot, writeEnabled);

        if (context != null) {
            builder.skills(context.getSkillRegistry(), name);

            if (context.getKnowledgeBase() != null) {
                builder.knowledge(context.getKnowledgeBase(), department, 5);
            }

            if (context.getInstructionFileLoader() != null && context.getEmployeeId() != null) {
                var loader = context.getInstructionFileLoader();
                var instructions = loader.loadInstructionChain(context.getEmployeeId());
                if (!instructions.isEmpty()) {
                    builder.guidelines(loader.mergeInstructions(instructions));
                }
            }
        }

        return builder.build();
    }

    protected String doGetSystemPrompt() {
        return "";
    }

    public void setModelSelector(BrainModelSelector modelSelector) {
        this.modelFallback.setModelSelector(modelSelector);
        log.info("Model selector set for brain {}: {}", id,
            modelSelector != null ? modelSelector.getBrainName() : "null");
    }

    public BrainModelSelector getModelSelector() {
        return modelFallback.getModelSelector();
    }

    public void setBrainModelResolver(BrainModelResolver brainModelResolver) {
        this.modelFallback.setBrainModelResolver(brainModelResolver);
    }

    public BrainModelResolver getBrainModelResolver() {
        return modelFallback.getBrainModelResolver();
    }

    public void setBrainBoundaryEnforcer(BrainBoundaryEnforcer brainBoundaryEnforcer) {
        this.brainBoundaryEnforcer = brainBoundaryEnforcer;
        this.reactEngine.setBrainBoundaryEnforcer(brainBoundaryEnforcer);
        log.info("Brain boundary enforcer set for brain {}: {}", id, brainBoundaryEnforcer != null);
    }

    public BrainBoundaryEnforcer getBrainBoundaryEnforcer() {
        return brainBoundaryEnforcer;
    }

    public void setBrainModelAssigner(com.livingagent.core.model.pool.BrainModelAssigner brainModelAssigner) {
        this.modelFallback.setBrainModelAssigner(brainModelAssigner);
        log.info("Brain model assigner set for brain {}: {}", id, brainModelAssigner != null);
    }

    public void setModelPoolManager(com.livingagent.core.model.pool.ModelPoolManager modelPoolManager) {
        this.modelFallback.setModelPoolManager(modelPoolManager);
        log.info("Model pool manager set for brain {}: {}", id, modelPoolManager != null);
    }

    protected ResolvedBrainModel getCurrentModel() {
        return modelFallback.getCurrentModel();
    }

    protected String getDefaultModel() {
        return modelFallback.getDefaultModel();
    }

    protected String resolveDefaultModelName() {
        return modelFallback.resolveDefaultModelName();
    }

    protected int getMaxTokensFromModel() {
        return modelFallback.getMaxTokens();
    }

    protected double getTemperatureFromModel() {
        return modelFallback.getTemperature();
    }

    protected int getMaxIterations() {
        // 支持通过环境变量或系统属性配置最大迭代次数
        String configValue = System.getProperty(MAX_ITERATIONS_PROPERTY);
        if (configValue == null) {
            configValue = System.getenv("LIVINGAGENT_BRAIN_MAX_ITERATIONS");
        }
        if (configValue != null) {
            try {
                int configuredValue = Integer.parseInt(configValue);
                if (configuredValue > 0 && configuredValue <= 100) {
                    log.debug("Using configured max iterations: {}", configuredValue);
                    return configuredValue;
                } else {
                    log.warn("Invalid max iterations value: {}, using default: {}", configuredValue, DEFAULT_MAX_ITERATIONS);
                }
            } catch (NumberFormatException e) {
                log.warn("Failed to parse max iterations: {}, using default: {}", configValue, DEFAULT_MAX_ITERATIONS);
            }
        }
        return DEFAULT_MAX_ITERATIONS;
    }

    protected int getMaxTokens() {
        return getMaxTokensFromModel();
    }

    protected double getTemperature() {
        return getTemperatureFromModel();
    }

    protected String getOutputChannel() {
        return publishChannels.isEmpty() ? "channel://output/text" : publishChannels.get(0);
    }

    protected ReActResult executeReActLoop(String userMessage, String sessionId) {
        return executeReActLoop(userMessage, sessionId, null);
    }

    protected ReActResult executeReActLoop(String userMessage, String sessionId, List<Provider.ChatMessage> previousHistory) {
        return reactEngine.executeReActLoop(
            userMessage, sessionId, previousHistory,
            getProvider(), getSystemPrompt(), getMaxIterations(), this);
    }

    /**
     * 获取会话的对话历史。委托到 BrainSessionManager。
     */
    protected List<Provider.ChatMessage> getSessionHistory(String sessionId) {
        return sessionManager.getSessionHistory(sessionId);
    }

    /**
     * 更新会话的对话历史。委托到 BrainSessionManager。
     */
    protected void updateSessionHistory(String sessionId, String userMessage, String assistantResponse) {
        sessionManager.updateSessionHistory(sessionId, userMessage, assistantResponse);
    }

    /**
     * 驱逐过期的会话历史缓存。委托到 BrainSessionManager。
     */
    protected void evictExpiredSessions() {
        sessionManager.evictExpiredSessions();
    }

    /**
     * 清除会话的对话历史。委托到 BrainSessionManager。
     */
    protected void clearSessionHistory(String sessionId) {
        sessionManager.clearSessionHistory(sessionId);
    }

    @Override
    public void injectSessionHistory(String sessionId, List<Provider.ChatMessage> history) {
        sessionManager.injectSessionHistory(sessionId, history);
    }

    /**
     * 尝试从模型池中找到另一个可用的模型作为降级替代。委托到 BrainModelFallback。
     */
    protected ResolvedBrainModel tryFallbackModel(ResolvedBrainModel failedModel) {
        return modelFallback.tryFallbackModel(failedModel);
    }

    /**
     * 从模型池中找到评分最高的可用模型作为降级替代。委托到 BrainModelFallback。
     */
    protected ResolvedBrainModel findBestAvailableModel(ResolvedBrainModel failedModel) {
        return modelFallback.findBestAvailableModel(failedModel);
    }

    protected ModelHealthRegistry getModelHealthRegistry() {
        return modelFallback.getModelHealthRegistry();
    }

    protected List<Provider.ToolResultData> executeToolCalls(
            List<Provider.ToolCallData> toolCalls, String sessionId) {
        return reactEngine.executeToolCalls(toolCalls, sessionId, this);
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> parseArguments(String arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return Map.of();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(arguments, Map.class);
        } catch (Exception e) {
            log.warn("Failed to parse arguments: {}", arguments, e);
            return Map.of();
        }
    }

    protected String formatSuccessResult(Object data) {
        if (data == null) {
            return "执行成功";
        }
        if (data instanceof String s) {
            return s;
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(data);
        } catch (Exception e) {
            return data.toString();
        }
    }

    protected String extractText(ChannelMessage message) {
        Object payload = message.getPayload();
        return payload != null ? payload.toString() : null;
    }

    protected void publishResponse(ChannelMessage original, String content, int iterations) {
        String outputChannel = getOutputChannel();
        ChannelMessage responseMessage = ChannelMessage.text(
            outputChannel,
            getId(),
            original.getSourceChannelId(),
            original.getSessionId(),
            content
        );
        responseMessage.addMetadata("original_message_id", original.getId());
        responseMessage.addMetadata("brain_id", getId());
        responseMessage.addMetadata("brain_name", name);
        responseMessage.addMetadata("department", department);
        responseMessage.addMetadata("iterations", iterations);
        responseMessage.addMetadata("type", "brain_response");
        publish(outputChannel, responseMessage);

        // NP1-3: 如果原始消息要求响应回传给主脑，则额外发送一条响应消息到 response_channel
        String requiresResponse = original.getMetadata("requires_response");
        String responseChannel = original.getMetadata("response_channel");
        String coordinationSessionId = original.getMetadata("coordination_session_id");
        if ("true".equals(requiresResponse) && responseChannel != null && coordinationSessionId != null) {
            ChannelMessage deptResponse = ChannelMessage.text(
                responseChannel,
                getId(),
                responseChannel,
                original.getSessionId(),
                content
            );
            deptResponse.addMetadata("coordination_session_id", coordinationSessionId);
            deptResponse.addMetadata("is_department_response", "true");
            deptResponse.addMetadata("source_department", department);
            deptResponse.addMetadata("source_brain_id", getId());
            publish(responseChannel, deptResponse);
            log.info("NP1-3: Sent department response back to MainBrain for session {}, dept={}", coordinationSessionId, department);
        }

        // 构建 BrainOutputContract 并保存到 lastOutputContract
        BrainOutputContract.BrainOutputStatus contractStatus = iterations >= getMaxIterations()
            ? BrainOutputContract.BrainOutputStatus.EXECUTING
            : BrainOutputContract.BrainOutputStatus.COMPLETED;
        // DP2-3: 填充 plan 字段，描述执行步骤
        String planDescription = iterations > 0
            ? "ReAct循环执行" + iterations + "步" + (iterations >= getMaxIterations() ? "（达到最大迭代次数）" : "")
            : "直接响应";
        BrainOutputContract contract = BrainOutputContract.builder()
            .status(contractStatus)
            .summary(content != null && content.length() > 500 ? content.substring(0, 500) : content)
            .plan(planDescription)
            .conversationId(original.getSessionId())
            .riskLevel(BrainOutputContract.RiskLevel.LOW)
            .metadata(Map.of(
                "iterations", iterations,
                "brain_id", getId(),
                "department", department
            ))
            .build();
        this.lastOutputContract = contract;
    }

    protected void publishError(ChannelMessage original, String error) {
        String outputChannel = getOutputChannel();
        ChannelMessage errorMessage = ChannelMessage.error(
            outputChannel,
            getId(),
            original.getSourceChannelId(),
            original.getSessionId(),
            error
        );
        errorMessage.addMetadata("original_message_id", original.getId());
        errorMessage.addMetadata("brain_id", getId());
        errorMessage.addMetadata("brain_name", name);
        errorMessage.addMetadata("department", department);
        errorMessage.addMetadata("iterations", 0);
        errorMessage.addMetadata("type", "brain_error");
        publish(outputChannel, errorMessage);

        // 构建 BrainOutputContract 并保存到 lastOutputContract
        BrainOutputContract contract = BrainOutputContract.builder()
            .status(BrainOutputContract.BrainOutputStatus.FAILED)
            .summary(error)
            .conversationId(original.getSessionId())
            .riskLevel(BrainOutputContract.RiskLevel.HIGH)
            .metadata(Map.of(
                "original_message_id", original.getId(),
                "brain_id", getId(),
                "brain_name", name,
                "department", department,
                "iterations", 0,
                "type", "brain_error",
                "error", true
            ))
            .build();
        this.lastOutputContract = contract;
    }

    protected void handleProcessingError(ChannelMessage message, Exception error) {
        log.warn("Brain {} handling processing error: {}", id, error.getMessage());
        
        EvolutionDecisionEngine engine = getEvolutionEngine();
        if (engine != null) {
            EvolutionSignal signal = EvolutionSignal.error(
                "brain_error_" + id,
                error.getClass().getSimpleName(),
                error.getMessage(),
                name
            );
            
            EvolutionDecisionEngine.EvolutionDecision decision = engine.decide(signal);
            log.info("Evolution decision for error: {} with confidence {}", 
                decision.getStrategy(), decision.getConfidence());
            
            if (decision.shouldExecute()) {
                executeEvolutionDecision(decision);
            }
        }
    }
    
    protected void executeEvolutionDecision(EvolutionDecisionEngine.EvolutionDecision decision) {
        log.info("Brain {} executing evolution decision: {}", id, decision.getStrategy());
        
        try {
            switch (decision.getStrategy()) {
                case REPAIR:
                    executeRepair(decision);
                    break;
                case OPTIMIZE:
                    executeOptimize(decision);
                    break;
                case INNOVATE:
                    executeInnovate(decision);
                    break;
                case ESCALATE:
                    escalateToMainBrain(decision);
                    break;
                default:
                    log.debug("Evolution strategy {} not executed", decision.getStrategy());
            }
            
            evolutionSuccessCount.incrementAndGet();
            lastEvolutionTime.set(System.currentTimeMillis());
            
        } catch (Exception e) {
            evolutionFailureCount.incrementAndGet();
            log.error("Failed to execute evolution decision: {}", e.getMessage());
        }
    }
    
    protected void executeRepair(EvolutionDecisionEngine.EvolutionDecision decision) {
        log.info("Brain {} executing repair for skill: {}", id, decision.getTargetSkillId());
        // DP2-1: REPAIR 策略 — 重置技能参数到安全默认值
        if (context != null && decision.getTargetSkillId() != null) {
            Map<String, Object> repairState = new HashMap<>();
            repairState.put("action", "repair");
            repairState.put("skillId", decision.getTargetSkillId());
            repairState.put("timestamp", System.currentTimeMillis());
            repairState.put("reasons", decision.getReasons());
            context.setState("_repair_" + decision.getTargetSkillId(), repairState);
            log.info("DP2-1: Brain {} repaired skill {}, state saved to context", id, decision.getTargetSkillId());
        }
    }

    protected void executeOptimize(EvolutionDecisionEngine.EvolutionDecision decision) {
        log.info("Brain {} executing optimization for skill: {}", id, decision.getTargetSkillId());
        // DP2-1: OPTIMIZE 策略 — 调整人格参数以提升效率
        if (personality != null && decision.getParameters() != null) {
            Object deltaObj = decision.getParameters().get("personalityDelta");
            if (deltaObj instanceof Map<?, ?> deltaMap) {
                for (Map.Entry<?, ?> entry : deltaMap.entrySet()) {
                    String param = String.valueOf(entry.getKey());
                    double delta = entry.getValue() instanceof Number n ? n.doubleValue() : 0.0;
                    updatePersonality(param, delta);
                    log.info("DP2-1: Brain {} optimized personality param {} by {}", id, param, delta);
                }
            }
        }
    }

    protected void executeInnovate(EvolutionDecisionEngine.EvolutionDecision decision) {
        log.info("Brain {} executing innovation: {}", id, decision.getParameters());
        // DP2-1: INNOVATE 策略 — 记录创新尝试到知识库
        if (context != null) {
            Map<String, Object> innovationRecord = new HashMap<>();
            innovationRecord.put("action", "innovate");
            innovationRecord.put("brainId", id);
            innovationRecord.put("parameters", decision.getParameters());
            innovationRecord.put("reasons", decision.getReasons());
            innovationRecord.put("timestamp", System.currentTimeMillis());
            context.setState("_innovation_" + System.currentTimeMillis(), innovationRecord);
            log.info("DP2-1: Brain {} recorded innovation attempt, state saved to context", id);
        }
    }
    
    protected void escalateToMainBrain(EvolutionDecisionEngine.EvolutionDecision decision) {
        log.warn("Brain {} escalating to MainBrain: {}", id, decision.getReasons());
        
        if (context != null) {
            Map<String, Object> escalationData = new HashMap<>();
            escalationData.put("sourceBrain", id);
            escalationData.put("decision", decision);
            escalationData.put("timestamp", System.currentTimeMillis());
            
            context.setState("_escalation", escalationData);
        }
    }
    
    public void updatePersonality(String param, double delta) {
        if (personality != null) {
            com.livingagent.core.evolution.personality.PersonalityMutation mutation = 
                new com.livingagent.core.evolution.personality.PersonalityMutation(
                    param, delta, "manual_update", System.currentTimeMillis()
                );
            personality.applyMutation(mutation);
            log.info("Brain {} personality updated: {} -> {}", id, param, delta);
        }
    }
    
    public Map<String, Object> getEvolutionStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("brainId", id);
        stats.put("brainName", name);
        stats.put("successCount", evolutionSuccessCount);
        stats.put("failureCount", evolutionFailureCount);
        stats.put("lastEvolutionTime", lastEvolutionTime);
        stats.put("personality", personality != null ? personality.toKey() : "null");
        return stats;
    }

    protected void publish(String channelId, ChannelMessage message) {
        if (context != null) {
            context.publish(channelId, message);
        }
    }

    protected Memory getMemory() {
        return context != null ? context.getMemory() : null;
    }

    protected Provider getProvider() {
        return context != null ? context.getProvider() : null;
    }

    protected ToolRegistry getToolRegistry() {
        return context != null ? context.getToolRegistry() : null;
    }

    protected BrainReActEngine getReActEngine() {
        return reactEngine;
    }

    protected KnowledgeBase getKnowledgeBase() {
        return context != null ? context.getKnowledgeBase() : null;
    }
    
    protected EvolutionDecisionEngine getEvolutionEngine() {
        return context != null ? context.getEvolutionEngine() : null;
    }

    public record ReActResult(
        boolean success,
        String content,
        int iterations,
        ReActStatus status
    ) {
        public static ReActResult success(String content, int iterations) {
            return new ReActResult(true, content, iterations, ReActStatus.COMPLETED);
        }

        public static ReActResult error(String message, int iterations) {
            return new ReActResult(false, message, iterations, ReActStatus.ERROR);
        }

        public static ReActResult error(String message) {
            return new ReActResult(false, message, 0, ReActStatus.ERROR);
        }

        public static ReActResult maxIterations(String message, int iterations) {
            return new ReActResult(false, message, iterations, ReActStatus.MAX_ITERATIONS);
        }
    }

    public enum ReActStatus {
        COMPLETED,
        MAX_ITERATIONS,
        ERROR
    }
}
