package com.livingagent.core.brain.impl;

import com.livingagent.core.brain.BrainContext;
import com.livingagent.core.brain.BrainOutputContract;
import com.livingagent.core.brain.collaboration.LeadOrchestrator;
import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.tool.Tool;
import com.livingagent.core.tool.ToolSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import com.livingagent.core.provider.Provider;
import java.util.Objects;

public class TechBrain extends AbstractBrain {

    private static final Logger log = LoggerFactory.getLogger(TechBrain.class);

    public static final String ID = "neuron://tech/tech-brain/001";
    public static final String INPUT_CHANNEL = "channel://tech/tasks";
    public static final String OUTPUT_CHANNEL = "channel://output/text";

    private static final String SYSTEM_PROMPT_TEMPLATE = TechClaudeCliPromptTemplates.SHARED_POLICY + "\n\n" +
        TechClaudeCliPromptTemplates.CODE_REVIEW + "\n\n" +
        TechClaudeCliPromptTemplates.BUG_FIX + "\n\n" +
        TechClaudeCliPromptTemplates.TEST_GENERATE + "\n\n" +
        TechClaudeCliPromptTemplates.RELEASE_PREP + "\n\n" +
        TechClaudeCliPromptTemplates.REFACTOR_PLAN + "\n\n" +
        "{TOOL_LIST_PLACEHOLDER}\n\n" +
        """
        任务完成后，请明确说明：
        - 已修改的文件
        - 已执行的验证
        - 未完成的风险与后续建议
        """;

    private volatile LeadOrchestrator leadOrchestrator;

    public TechBrain(List<Tool> tools) {
        super(
            ID,
            "TechBrain",
            "tech",
            List.of(INPUT_CHANNEL),
            List.of(OUTPUT_CHANNEL),
            tools
        );
    }

    @Override
    protected String doGetSystemPrompt() {
        // P2-2: 动态生成工具列表，替代硬编码
        return SYSTEM_PROMPT_TEMPLATE.replace("{TOOL_LIST_PLACEHOLDER}", buildDynamicToolList());
    }

    @Override
    protected String getOutputChannel() {
        return OUTPUT_CHANNEL;
    }

    public void setLeadOrchestrator(LeadOrchestrator leadOrchestrator) {
        this.leadOrchestrator = leadOrchestrator;
    }

    @Override
    protected void doStart(BrainContext context) {
        if (leadOrchestrator != null) {
            log.info("TechBrain started with LeadOrchestrator, listening to {}", INPUT_CHANNEL);
        } else {
            log.info("TechBrain started (single mode), listening to {}", INPUT_CHANNEL);
        }
    }

    @Override
    protected void doStop() {
        log.info("TechBrain stopped");
    }

    @Override
    protected void doProcess(ChannelMessage message) {
        log.debug("TechBrain processing message: {}", message.getId());

        if (isCollaborationControlMessage(message)) {
            handleCollaborationControlMessage(message);
            return;
        }

        String userMessage = extractText(message);
        if (userMessage == null || userMessage.isEmpty()) {
            log.warn("TechBrain received empty message");
            publishFallbackResponse(message, "收到空消息，请重新描述您的需求。");
            return;
        }

        Object assignmentCountObj = message.getMetadata().get("assignment_count");
        String assignmentCount = assignmentCountObj != null ? String.valueOf(assignmentCountObj) : "0";
        boolean hasEmployeeAssignments = !"0".equals(assignmentCount) && !assignmentCount.isBlank();

        if (hasEmployeeAssignments) {
            log.info("TechBrain received message with {} employee assignments, waiting for aggregation", assignmentCount);
            // DP0-1 改进：不返回占位符，而是返回一个等待聚合的响应
            // 聚合逻辑在 DepartmentChatService 中实现，会等待回执收集完成后替换此响应
            String waitingMsg = String.format("正在执行 %s 个员工任务，请稍候...", assignmentCount);
            publishResponse(message, waitingMsg, 0);
            return;
        }

        try {
            String sessionId = message.getSessionId();
            List<Provider.ChatMessage> previousHistory = getSessionHistory(sessionId);

            // 判断是否需要编译-修复闭环（BUG_FIX 任务且 claude_cli 不可用时走自身 ReAct+BuildTool）
            boolean useCompileFix = shouldUseCompileFixLoop(message);
            ReActResult result;
            if (useCompileFix) {
                result = getReActEngine().executeCompileFixLoop(
                    userMessage, sessionId, previousHistory,
                    getProvider(), doGetSystemPrompt(), getMaxIterations(),
                    this, "build", 3);
                log.info("TechBrain executed compile-fix loop for session={}", sessionId);
            } else {
                result = executeReActLoop(userMessage, sessionId, previousHistory);
            }

            if (result.success()) {
                if (result.content() != null && !result.content().isBlank()) {
                    updateSessionHistory(sessionId, userMessage, result.content());
                    publishResponse(message, result.content(), result.iterations());
                } else {
                    log.warn("TechBrain ReAct loop succeeded but returned empty content, publishing fallback");
                    publishFallbackResponse(message, "任务处理完成，但未生成有效内容。请重试或补充更多细节。");
                }
            } else {
                publishError(message, result.content());
            }

        } catch (Exception e) {
            log.error("TechBrain failed to process message", e);
            publishFallbackResponse(message, "处理失败: " + e.getMessage());
        }
    }

    private void publishFallbackResponse(ChannelMessage original, String content) {
        try {
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
            responseMessage.addMetadata("type", "brain_response");
            responseMessage.addMetadata("fallback", "true");
            publish(outputChannel, responseMessage);

            // 构建 BrainOutputContract 并保存到 lastOutputContract（DP0-2 修复）
            BrainOutputContract contract = BrainOutputContract.builder()
                .status(BrainOutputContract.BrainOutputStatus.COMPLETED)
                .summary(content != null && content.length() > 500 ? content.substring(0, 500) : content)
                .conversationId(original.getSessionId())
                .riskLevel(BrainOutputContract.RiskLevel.LOW)
                .metadata(Map.of(
                    "original_message_id", original.getId(),
                    "brain_id", getId(),
                    "brain_name", name,
                    "department", department,
                    "type", "brain_response",
                    "fallback", "true"
                ))
                .build();
            this.lastOutputContract = contract;
        } catch (Exception e) {
            log.error("TechBrain failed to publish fallback response", e);
        }
    }

    private boolean isCollaborationControlMessage(ChannelMessage message) {
        if (leadOrchestrator == null) {
            return false;
        }
        String sourceChannel = message.getSourceChannelId();
        if (sourceChannel == null) {
            return false;
        }
        return sourceChannel.startsWith("channel://tech/") && !INPUT_CHANNEL.equals(sourceChannel);
    }

    /**
     * 判断是否使用编译-修复闭环。
     * 条件：BUG_FIX/TEST_GENERATE 任务类型，且 claude_cli 工具不可用或显式请求自身修复。
     */
    private boolean shouldUseCompileFixLoop(ChannelMessage message) {
        Object taskType = message.getMetadata().get("task_type");
        boolean isCodeModifyTask = "BUG_FIX".equals(taskType) || "TEST_GENERATE".equals(taskType) || "CODE_CHANGE".equals(taskType);
        if (!isCodeModifyTask) {
            return false;
        }

        // 检查 claude_cli 工具是否可用
        if (getToolRegistry() != null) {
            var claudeCliOpt = getToolRegistry().get("claude_cli");
            if (claudeCliOpt.isPresent() && claudeCliOpt.get().isAllowed(null)) {
                // ClaudeCliTool 可用，优先使用它（除非显式请求自身修复）
                Object selfFix = message.getMetadata().get("self_fix");
                return "true".equals(String.valueOf(selfFix));
            }
        }

        // ClaudeCliTool 不可用，使用自身 ReAct + BuildTool 闭环
        return true;
    }

    private void handleCollaborationControlMessage(ChannelMessage message) {
        Object type = message.getMetadata().get("type");
        Object taskIdMeta = message.getMetadata().get("task_id");
        String taskId = taskIdMeta != null ? String.valueOf(taskIdMeta) : null;

        if (taskId == null || taskId.isBlank()) {
            log.debug("Ignored collaboration message without task_id: {}", message.getId());
            return;
        }

        String content = extractText(message);
        if ("task_completed".equals(type)) {
            leadOrchestrator.completeTask(taskId, content != null ? content : "");
            log.info("Marked teammate task completed: {}", taskId);
            return;
        }

        if ("task_failed".equals(type)) {
            leadOrchestrator.failTask(taskId, content != null ? content : "unknown error");
            log.warn("Marked teammate task failed: {}", taskId);
            return;
        }

        log.debug("Collaboration message passed through (type={}): {}", type, message.getId());
    }
    
    @Override
    public List<ToolSchema> getToolSchemas() {
        return tools.stream()
            .map(Tool::getSchema)
            .filter(Objects::nonNull)
            .toList();
    }
    
    @Override
    protected String buildPrompt(BrainContext context, String userInput) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(doGetSystemPrompt()).append("\n\n");
        
        if (context.getHistory() != null && !context.getHistory().isEmpty()) {
            prompt.append("对话历史：\n");
            context.getHistory().forEach(msg -> {
                prompt.append(msg.role()).append(": ").append(msg.content()).append("\n");
            });
            prompt.append("\n");
        }
        
        prompt.append("用户: ").append(userInput);
        
        return prompt.toString();
    }
}
