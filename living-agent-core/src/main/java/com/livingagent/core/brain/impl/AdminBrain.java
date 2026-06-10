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

public class AdminBrain extends AbstractBrain {
    
    private static final Logger log = LoggerFactory.getLogger(AdminBrain.class);

    public static final String ID = "neuron://admin/admin-brain/001";
    public static final String INPUT_CHANNEL = "channel://admin/tasks";
    public static final String OUTPUT_CHANNEL = "channel://output/text";
    private static final String DEPARTMENT = "admin";
    
    private static final String SYSTEM_PROMPT = """
        你是行政部门的智能助手，负责行政和办公相关事务。
        
        你的职责包括：
        - 会议管理和安排
        - 资产管理
        - 采购管理
        - 办公环境维护
        - 文档和档案管理
        
        你可以使用以下工具：
        - admin_* : 行政管理专用工具（公告、通知、审批）
        - office_* : 办公管理操作（会议室、工位、车辆）
        - document_* : 文档管理操作（归档、检索、共享）
        - asset_* : 资产管理操作（登记、领用、盘点）
        
        请根据用户的需求，使用合适的工具完成任务。
        回答要高效、周到，注重服务品质。
        如果需要多个步骤，请逐步执行。
        """;
    
    public AdminBrain(List<Tool> tools) {
        super(
            ID,
            "AdminBrain",
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
        log.info("AdminBrain started, listening to {}", INPUT_CHANNEL);
    }
    
    @Override
    protected void doStop() {
        log.info("AdminBrain stopped");
    }
    
    @Override
    protected void doProcess(ChannelMessage message) {
        log.debug("AdminBrain processing message: {}", message.getId());

        String userMessage = extractText(message);
        if (userMessage == null || userMessage.isEmpty()) {
            log.warn("AdminBrain received empty message");
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
                    log.warn("AdminBrain ReAct loop succeeded but returned empty content");
                    publishResponse(message, "任务处理完成，但未生成有效内容。", 0);
                }
            } else {
                publishError(message, result.content());
            }
        } catch (Exception e) {
            log.error("AdminBrain failed to process message", e);
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
