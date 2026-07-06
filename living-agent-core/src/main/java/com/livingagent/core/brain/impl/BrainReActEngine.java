package com.livingagent.core.brain.impl;

import com.livingagent.core.brain.BrainBoundaryEnforcer;
import com.livingagent.core.brain.compact.CompactionResult;
import com.livingagent.core.brain.compact.ContextCompactor;
import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.memory.Memory;
import com.livingagent.core.memory.MemoryCategory;
import com.livingagent.core.model.TokenUsage;
import com.livingagent.core.model.UsageTracker;
import com.livingagent.core.model.pool.BrainModelResolver;
import com.livingagent.core.model.pool.ResolvedBrainModel;
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

/**
 * 大脑 ReAct 引擎 — 从 AbstractBrain 中提取的 ReAct 循环逻辑。
 * <p>
 * 负责：ReAct 循环执行、工具调用、上下文压缩。
 */
public class BrainReActEngine {

    private static final Logger log = LoggerFactory.getLogger(BrainReActEngine.class);

    private static final double COST_PER_1K_PROMPT = 0.001;
    private static final double COST_PER_1K_COMPLETION = 0.002;
    private static final int PERSIST_THRESHOLD = 30000;
    private static final int MICRO_COMPACT_KEEP_RECENT = 3;

    private final String brainId;
    private final String brainName;
    private final List<Tool> tools;
    private final BrainModelFallback modelFallback;

    private volatile ContextCompactor contextCompactor;
    private volatile UsageTracker usageTracker;
    private volatile ToolHookManager hookManager;
    private volatile BrainBoundaryEnforcer brainBoundaryEnforcer;

    public BrainReActEngine(String brainId, String brainName, List<Tool> tools, BrainModelFallback modelFallback) {
        this.brainId = brainId;
        this.brainName = brainName;
        this.tools = tools;
        this.modelFallback = modelFallback;
    }

    public void setContextCompactor(ContextCompactor contextCompactor) {
        this.contextCompactor = contextCompactor;
    }

    public void setUsageTracker(UsageTracker usageTracker) {
        this.usageTracker = usageTracker;
    }

    public void setHookManager(ToolHookManager hookManager) {
        this.hookManager = hookManager;
    }

    public void setBrainBoundaryEnforcer(BrainBoundaryEnforcer brainBoundaryEnforcer) {
        this.brainBoundaryEnforcer = brainBoundaryEnforcer;
    }

    /**
     * 执行 ReAct 循环。
     */
    public AbstractBrain.ReActResult executeReActLoop(
            String userMessage,
            String sessionId,
            List<Provider.ChatMessage> previousHistory,
            Provider provider,
            String systemPrompt,
            int maxIterations,
            AbstractBrain brain) {

        if (provider == null && modelFallback.getBrainModelResolver() != null) {
            try {
                ResolvedBrainModel resolved = modelFallback.getBrainModelResolver().resolve(brainId);
                if (resolved != null && resolved.getBaseUrl() != null && !resolved.getBaseUrl().isBlank()) {
                    provider = new com.livingagent.core.provider.impl.ResolvedBrainModelProvider(resolved);
                    log.info("Brain {} dynamically resolved provider: provider={}, model={}", brainId, resolved.getProviderId(), resolved.getModelName());
                }
            } catch (Exception e) {
                log.warn("Brain {} failed to resolve dynamic provider: {}", brainId, e.getMessage());
            }
        }
        if (provider == null) {
            log.warn("[BrainTrace] brain={} session={} event=react_loop_start result=error reason=provider_not_configured", brainId, sessionId);
            return AbstractBrain.ReActResult.error("Provider 未配置: 请在模型池中添加可用模型，并在大脑配置中分配");
        }

        List<Provider.ChatMessage> history = new ArrayList<>();
        history.add(Provider.ChatMessage.system(systemPrompt));

        // 合并之前的对话历史（保留上下文记忆）
        if (previousHistory != null && !previousHistory.isEmpty()) {
            for (Provider.ChatMessage msg : previousHistory) {
                // 跳过系统消息，避免重复
                if (!"system".equalsIgnoreCase(msg.role())) {
                    history.add(msg);
                }
            }
            log.debug("Brain {} session {} merged {} previous messages into history", brainId, sessionId, previousHistory.size());
        }

        history.add(Provider.ChatMessage.user(userMessage));

        int iterationCount = 0;
        long loopStartTime = System.currentTimeMillis();

        log.info("[BrainTrace] brain={} session={} event=react_loop_start maxIterations={}", brainId, sessionId, maxIterations);

        while (iterationCount < maxIterations) {
            iterationCount++;

            if (contextCompactor != null) {
                CompactionResult cr = contextCompactor.microCompact(history, MICRO_COMPACT_KEEP_RECENT);
                if (cr.compacted()) {
                    history = cr.messages();
                    log.debug("Brain {} micro-compacted: removed {} messages", brainId, cr.removedCount());
                }

                cr = contextCompactor.autoCompactIfNeeded(history);
                if (cr.compacted()) {
                    history = cr.messages();
                    log.info("Brain {} auto-compacted: removed {} messages, summary length={}",
                        brainId, cr.removedCount(), cr.summaryLength());
                }
            }

            String currentModelName = modelFallback.getDefaultModel();
            ResolvedBrainModel currentResolvedModel = modelFallback.getCurrentModel();

            Provider.ChatRequest request = new Provider.ChatRequest(
                history,
                brain.getToolSchemas(),
                currentModelName,
                modelFallback.getTemperature(),
                modelFallback.getMaxTokens()
            );

            try {
                long startTime = System.currentTimeMillis();
                Provider.ChatResponse response = provider.chat(request).join();
                long latencyMs = System.currentTimeMillis() - startTime;

                // 记录模型调用成功
                modelFallback.recordModelSuccess(currentResolvedModel, latencyMs);

                if (usageTracker != null) {
                    TokenUsage tokenUsage = TokenUsage.of(
                        sessionId, brainId, currentModelName,
                        response.promptTokens(), response.completionTokens(),
                        COST_PER_1K_PROMPT, "react_loop"
                    );
                    usageTracker.recordUsage(tokenUsage);
                }

                if (response.hasToolCalls()) {
                    List<Provider.ToolCallData> toolCalls = response.toolCalls();
                    log.debug("Brain {} received {} tool calls at iteration {}", brainId, toolCalls.size(), iterationCount);
                    log.info("[BrainTrace] brain={} session={} event=tool_call iteration={} toolCount={} tools={}",
                        brainId, sessionId, iterationCount, toolCalls.size(),
                        toolCalls.stream().map(Provider.ToolCallData::name).toList());

                    history.add(Provider.ChatMessage.assistantWithTools(
                        response.content(),
                        toolCalls
                    ));

                    List<Provider.ToolResultData> results = executeToolCalls(toolCalls, sessionId, brain);

                    for (Provider.ToolResultData result : results) {
                        if (result.content().length() > PERSIST_THRESHOLD && contextCompactor != null) {
                            String persisted = contextCompactor.persistLargeOutput(
                                result.callId(), result.content(), PERSIST_THRESHOLD);
                            results.set(results.indexOf(result),
                                new Provider.ToolResultData(result.callId(), persisted));
                        }
                    }

                    history.add(Provider.ChatMessage.toolResult(results));

                } else {
                    long elapsedMs = System.currentTimeMillis() - loopStartTime;
                    log.info("[BrainTrace] brain={} session={} event=react_loop_complete iterations={} elapsedMs={} result=success",
                        brainId, sessionId, iterationCount, elapsedMs);
                    return AbstractBrain.ReActResult.success(response.content(), iterationCount);
                }

            } catch (Exception e) {
                log.error("Brain {} error in ReAct loop at iteration {}", brainId, iterationCount, e);
                log.error("[BrainTrace] brain={} session={} event=react_loop_error iteration={} error={}",
                    brainId, sessionId, iterationCount, e.getMessage());

                // 记录模型调用失败
                modelFallback.recordModelFailure(currentResolvedModel, e.getMessage());

                // 尝试降级到其他可用模型
                ResolvedBrainModel fallbackModel = modelFallback.tryFallbackModel(currentResolvedModel);
                if (fallbackModel != null) {
                    log.info("[BrainTrace] brain={} session={} event=model_fallback from={} to={} iteration={}",
                        brainId, sessionId,
                        currentResolvedModel != null ? currentResolvedModel.getModelName() : "null",
                        fallbackModel.getModelName(), iterationCount);
                    currentResolvedModel = fallbackModel;
                    // 更新 provider 并继续循环
                    try {
                        Provider fallbackProvider = new com.livingagent.core.provider.impl.ResolvedBrainModelProvider(fallbackModel);
                        brain.updateProvider(fallbackProvider);
                        provider = fallbackProvider; // 关键修复：同步更新局部变量，否则下次循环仍用旧 provider
                    } catch (Exception ex) {
                        log.warn("Failed to create fallback provider: {}", ex.getMessage());
                    }
                    continue;
                }

                if (contextCompactor != null && isContextTooLongError(e)) {
                    CompactionResult cr = contextCompactor.autoCompactIfNeeded(history);
                    if (cr.compacted()) {
                        history = cr.messages();
                        log.info("Brain {} compacted after context-too-long error", brainId);
                        continue;
                    }
                }

                return AbstractBrain.ReActResult.error("处理过程中发生错误: " + e.getMessage(), iterationCount);
            }
        }

        long elapsedMs = System.currentTimeMillis() - loopStartTime;
        log.warn("[BrainTrace] brain={} session={} event=react_loop_max_iterations iterations={} elapsedMs={}",
            brainId, sessionId, maxIterations, elapsedMs);
        return AbstractBrain.ReActResult.maxIterations("已达到最大迭代次数，任务可能未完成。", maxIterations);
    }

    /**
     * 带编译验证的 ReAct 循环：编辑代码后自动编译验证，失败则将错误注入下一轮继续修复。
     * <p>
     * 当 ClaudeCliTool 不可用时，TechBrain 可通过此方法实现自主的 "编辑→编译→修复→重编译" 闭环。
     * <p>
     * 流程：
     * 1. 执行标准 ReAct 循环，获得结果
     * 2. 如果结果包含文件修改（write_file/edit_file），自动触发编译验证
     * 3. 编译失败 → 将编译错误注入 ReAct 上下文 → 继续修复循环
     * 4. 编译成功或达到最大修复轮次 → 返回最终结果
     *
     * @param compileToolName 编译工具名称（如 "build"），在 ToolRegistry 中查找
     * @param maxFixRounds    最大修复轮次（默认 3）
     */
    public AbstractBrain.ReActResult executeCompileFixLoop(
            String userMessage,
            String sessionId,
            List<Provider.ChatMessage> previousHistory,
            Provider provider,
            String systemPrompt,
            int maxIterations,
            AbstractBrain brain,
            String compileToolName,
            int maxFixRounds) {

        // 第一轮：正常执行 ReAct 循环
        AbstractBrain.ReActResult result = executeReActLoop(
            userMessage, sessionId, previousHistory, provider, systemPrompt, maxIterations, brain);

        if (!result.success() || compileToolName == null || brain.getToolRegistry() == null) {
            return result;
        }

        // 检查是否有编译工具可用
        var compileToolOpt = brain.getToolRegistry().get(compileToolName);
        if (compileToolOpt.isEmpty()) {
            log.debug("Brain {} compile tool '{}' not found, skipping compile-fix loop", brainId, compileToolName);
            return result;
        }

        Tool compileTool = compileToolOpt.get();

        // 执行编译验证
        for (int fixRound = 0; fixRound < maxFixRounds; fixRound++) {
            String compileResult = executeCompileTool(compileTool, sessionId, brain);
            if (compileResult == null || compileResult.contains("BUILD SUCCESS") || compileResult.contains("0 errors")) {
                log.info("[BrainTrace] brain={} session={} event=compile_fix_success round={}", brainId, sessionId, fixRound);
                return result;
            }

            // 编译失败，将错误注入 ReAct 继续修复
            log.info("[BrainTrace] brain={} session={} event=compile_fix_needed round={} errors detected", brainId, sessionId, fixRound);

            String fixPrompt = "上一次编辑后的编译验证失败，请根据以下编译错误修复代码：\n\n" +
                compileResult + "\n\n请使用 file_edit 工具（优先用 edit_file 操作进行精确修改）修复上述编译错误。";

            List<Provider.ChatMessage> fixHistory = new ArrayList<>(previousHistory);
            fixHistory.add(Provider.ChatMessage.user(userMessage));
            if (result.content() != null) {
                fixHistory.add(Provider.ChatMessage.assistant(result.content()));
            }

            result = executeReActLoop(
                fixPrompt, sessionId, fixHistory, provider, systemPrompt, maxIterations, brain);

            if (!result.success()) {
                log.warn("[BrainTrace] brain={} session={} event=compile_fix_failed round={}", brainId, sessionId, fixRound);
                return result;
            }
        }

        log.warn("[BrainTrace] brain={} session={} event=compile_fix_max_rounds rounds={}", brainId, sessionId, maxFixRounds);
        return result;
    }

    /**
     * 执行编译工具并返回编译输出。
     */
    private String executeCompileTool(Tool compileTool, String sessionId, AbstractBrain brain) {
        try {
            Tool.ToolParams params = Tool.ToolParams.of(Map.of("action", "compile"));
            ToolContext context = ToolContext.of(brainId, sessionId);
            compileTool.validate(params);
            ToolResult result = compileTool.execute(params, context);
            if (result.success() && result.data() != null) {
                Object output = result.data() instanceof Map ? ((Map<?, ?>) result.data()).get("output") : result.data();
                return output != null ? output.toString() : null;
            }
            return result.success() ? "BUILD SUCCESS" : result.error();
        } catch (Exception e) {
            log.warn("Brain {} compile tool execution failed: {}", brainId, e.getMessage());
            return "编译执行失败: " + e.getMessage();
        }
    }

    /**
     * 执行工具调用。
     */
    public List<Provider.ToolResultData> executeToolCalls(
            List<Provider.ToolCallData> toolCalls, String sessionId, AbstractBrain brain) {

        List<Provider.ToolResultData> results = new ArrayList<>();
        var toolRegistry = brain.getToolRegistry();
        var memory = brain.getMemory();

        for (Provider.ToolCallData call : toolCalls) {
            try {
                log.info("Brain {} executing tool: {} (id: {})", brainId, call.name(), call.id());

                Tool tool = null;
                // 优先从 ToolRegistry 查找
                if (toolRegistry != null) {
                    var toolOpt = toolRegistry.get(call.name());
                    if (toolOpt.isPresent()) {
                        tool = toolOpt.get();
                    }
                }
                // 回退到 this.tools 列表
                if (tool == null && tools != null) {
                    tool = tools.stream()
                        .filter(t -> t.getName().equals(call.name()))
                        .findFirst()
                        .orElse(null);
                }
                if (tool == null) {
                    log.warn("Brain {} hallucinated non-existent tool: {} (id: {}). Available tools: {}",
                        brainId, call.name(), call.id(), getAvailableToolNames());
                    results.add(new Provider.ToolResultData(
                        call.id(),
                        buildToolNotFoundMessage(call.name())
                    ));
                    continue;
                }
                Map<String, Object> args = brain.parseArguments(call.arguments());
                Tool.ToolParams params = Tool.ToolParams.of(args);
                String empCode = brain.context != null ? brain.context.getEmployeeCode() : null;
                String ctxClientId = brain.context != null ? brain.context.getClientId() : null;
                Integer ctxAccessLevel = brain.context != null ? brain.context.getAccessLevel() : null;
                ToolContext context = (ctxClientId != null || empCode != null)
                    ? ToolContext.withClient(brain.getId(), sessionId, null, empCode, ctxClientId, ctxAccessLevel)
                    : ToolContext.of(brain.getId(), sessionId);

                // Brain boundary enforcement: check if this action is allowed
                if (brainBoundaryEnforcer != null) {
                    BrainBoundaryEnforcer.BoundaryCheckResult boundaryResult =
                        brainBoundaryEnforcer.checkAction(brainId, call.name());
                    if (boundaryResult.isViolation()) {
                        log.warn("Brain {} tool {} blocked by boundary: {}", brainId, call.name(), boundaryResult.getMessage());
                        results.add(new Provider.ToolResultData(
                            call.id(),
                            "工具被边界规则拦截: " + boundaryResult.getMessage()
                        ));
                        continue;
                    }
                    if (boundaryResult.mustEscalate()) {
                        log.warn("Brain {} tool {} requires escalation to main brain: trigger={}", brainId, call.name(), boundaryResult.getTriggerAction());
                    } else if (boundaryResult.needsEscalation()) {
                        log.info("Brain {} tool {} may need escalation: trigger={}", brainId, call.name(), boundaryResult.getTriggerAction());
                    }
                }

                if (hookManager != null) {
                    ToolHookResult preResult = hookManager.executePreHooks(call.name(), context);
                    if (preResult.isDenied()) {
                        results.add(new Provider.ToolResultData(
                            call.id(),
                            "工具执行被Hook拦截: " + preResult.getMessage()
                        ));
                        continue;
                    }
                    if (preResult.isWarn()) {
                        log.warn("Tool {} pre-hook warning: {}", call.name(), preResult.getMessage());
                    }
                }

                tool.validate(params);
                ToolResult result = tool.execute(params, context);

                if (hookManager != null) {
                    ToolHookResult postResult = hookManager.executePostHooks(call.name(), context, result);
                    if (postResult.isDenied()) {
                        log.warn("Tool {} post-hook denied result", call.name());
                    }
                }

                String resultContent = result.success()
                    ? brain.formatSuccessResult(result.data())
                    : "错误: " + result.error();

                results.add(new Provider.ToolResultData(call.id(), resultContent));

                if (memory != null) {
                    memory.store(
                        "tool_call:" + call.id(),
                        String.format("Tool: %s, Args: %s, Result: %s",
                            call.name(), args, resultContent),
                        MemoryCategory.DAILY,
                        sessionId
                    );
                }

                log.debug("Brain {} tool {} executed: {}", brainId, call.name(),
                    resultContent.length() > 100 ? resultContent.substring(0, 100) + "..." : resultContent);

            } catch (Exception e) {
                log.error("Brain {} failed to execute tool: {}", brainId, call.name(), e);

                if (hookManager != null) {
                    String empCode = brain.context != null ? brain.context.getEmployeeCode() : null;
                    String errClientId = brain.context != null ? brain.context.getClientId() : null;
                    Integer errAccessLevel = brain.context != null ? brain.context.getAccessLevel() : null;
                    ToolContext errContext = (errClientId != null || empCode != null)
                        ? ToolContext.withClient(brain.getId(), sessionId, null, empCode, errClientId, errAccessLevel)
                        : ToolContext.of(brain.getId(), sessionId);
                    hookManager.executeErrorHooks(call.name(), errContext, e);
                }

                results.add(new Provider.ToolResultData(
                    call.id(),
                    "执行失败: " + e.getMessage()
                ));
            }
        }

        return results;
    }

    private boolean isContextTooLongError(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return false;
        return msg.contains("overlong_prompt") || msg.contains("prompt too long")
            || msg.contains("context_length_exceeded") || msg.contains("max context");
    }

    /**
     * 获取当前可用工具名称列表（合并 ToolRegistry 和内置 tools 列表）。
     */
    private List<String> getAvailableToolNames() {
        List<String> names = new ArrayList<>();
        if (tools != null) {
            for (Tool t : tools) {
                if (t.getName() != null && !names.contains(t.getName())) {
                    names.add(t.getName());
                }
            }
        }
        // ToolRegistry 中的工具也一并列出（如果有的话）
        // 注意：这里无法直接访问 toolRegistry（它是方法局部变量），但 tools 列表通常已覆盖大脑可用工具
        return names;
    }

    /**
     * 构建工具不存在的引导消息。
     * <p>
     * 当模型幻觉了不存在的工具时，返回明确的错误信息并列出可用工具，
     * 引导模型使用正确的工具或直接基于已有知识回答，而不是放弃任务。
     */
    private String buildToolNotFoundMessage(String hallucinatedToolName) {
        List<String> available = getAvailableToolNames();
        StringBuilder sb = new StringBuilder();
        sb.append("错误：工具 '").append(hallucinatedToolName).append("' 不存在，请勿调用不存在的工具。\n\n");
        if (available.isEmpty()) {
            sb.append("当前没有可用的工具。请直接基于你的知识和对话历史回答用户问题，完成用户任务。");
        } else {
            sb.append("当前可用的工具列表如下，请仅使用以下工具：\n");
            for (String name : available) {
                sb.append("- ").append(name).append("\n");
            }
            sb.append("\n请重新思考：\n");
            sb.append("1. 如果任务可以通过上述工具完成，请调用正确的工具\n");
            sb.append("2. 如果当前可用工具无法完成任务，请直接基于你的知识给出完整、详细的回答，不要敷衍或返回测试内容");
        }
        return sb.toString();
    }
}
