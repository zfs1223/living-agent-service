package com.livingagent.core.brain.impl;

import java.util.List;
import java.util.Objects;

import com.livingagent.core.brain.BrainContext;
import com.livingagent.core.provider.Provider;
import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.tool.Tool;
import com.livingagent.core.tool.ToolSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CsBrain extends AbstractBrain {
    
    private static final Logger log = LoggerFactory.getLogger(CsBrain.class);

    public static final String ID = "neuron://cs/cs-brain/001";
    public static final String INPUT_CHANNEL = "channel://cs/tasks";
    public static final String OUTPUT_CHANNEL = "channel://output/text";
    private static final String DEPARTMENT = "cs";
    
    private static final String SYSTEM_PROMPT = """
        你是客服部门的智能助手，负责客户服务和支持相关事务。
        
        你的职责包括：
        - 工单处理和跟踪
        - 客户问题解答
        - 知识库检索
        - 客户满意度调查
        - 投诉处理
        
        你可以使用以下工具：
        - cs_* : 客服系统专用工具（会话、转接、评价）
        - ticket_* : 工单管理操作（创建、更新、查询、关闭）
        - support_* : 支持服务操作（远程协助、升级处理）
        - faq_* : 常见问题操作（检索、推荐、更新）
        
        请根据用户的需求，使用合适的工具完成任务。
        回答要友好、耐心，注重客户体验。
        如果需要多个步骤，请逐步执行。
        """;
    
    public CsBrain(List<Tool> tools) {
        super(
            ID,
            "CsBrain",
            DEPARTMENT,
            List.of(INPUT_CHANNEL),
            List.of(OUTPUT_CHANNEL),
            tools
        );
    }
    
    @Override
    public String getDepartment() {
        return DEPARTMENT;
    }

    @Override
    protected String doGetSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    @Override
    protected String getOutputChannel() {
        return OUTPUT_CHANNEL;
    }

    @Override
    protected void doStart(BrainContext context) {
        log.info("CsBrain started, listening to {}", INPUT_CHANNEL);
    }
    
    @Override
    protected void doStop() {
        log.info("CsBrain stopped");
    }
    
    @Override
    protected void doProcess(ChannelMessage message) {
        log.debug("CsBrain processing message: {}", message.getId());

        String userMessage = extractText(message);
        if (userMessage == null || userMessage.isEmpty()) {
            log.warn("CsBrain received empty message");
            return;
        }

        try {
            String sessionId = message.getSessionId();
            List<Provider.ChatMessage> previousHistory = getSessionHistory(sessionId);
            ReActResult result = executeReActLoop(userMessage, sessionId, previousHistory);
            
            if (result.success()) {
                updateSessionHistory(sessionId, userMessage, result.content());
                if (result.content() != null && !result.content().isBlank()) {
                    publishResponse(message, result.content(), result.iterations());
                } else {
                    log.warn("CsBrain ReAct loop succeeded but returned empty content");
                    publishResponse(message, "任务处理完成，但未生成有效内容。", 0);
                }
            } else {
                publishError(message, result.content());
            }
        } catch (Exception e) {
            log.error("CsBrain failed to process message", e);
            publishError(message, "处理失败: " + e.getMessage());
        }
    }
    
    @Override
    protected String buildPrompt(BrainContext context, String userInput) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(SYSTEM_PROMPT).append("\n\n");
        
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
    
    @Override
    public List<ToolSchema> getToolSchemas() {
        return tools.stream()
            .map(Tool::getSchema)
            .filter(Objects::nonNull)
            .toList();
    }
}
