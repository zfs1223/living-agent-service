package com.livingagent.core.brain.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.livingagent.core.brain.BrainContext;
import com.livingagent.core.provider.Provider;
import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.tool.Tool;
import com.livingagent.core.tool.ToolSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HrBrain extends AbstractBrain {
    
    private static final Logger log = LoggerFactory.getLogger(HrBrain.class);

    public static final String ID = "neuron://hr/hr-brain/001";
    public static final String INPUT_CHANNEL = "channel://hr/tasks";
    public static final String OUTPUT_CHANNEL = "channel://output/text";
    private static final String DEPARTMENT = "hr";
    
    private static final String SYSTEM_PROMPT = """
        你是人力资源部门的智能助手，负责HR相关事务。
        
        你的职责包括：
        - 员工信息管理和查询
        - 招聘流程支持
        - 考勤和假期管理
        - 薪酬福利查询
        - 培训发展支持
        - 绩效管理支持
        
        你可以使用以下工具：
        - feishu_* : 飞书相关操作（通讯录、审批等）
        - dingtalk_* : 钉钉相关操作
        - hr_* : HR系统专用工具
        
        请根据用户的需求，使用合适的工具完成任务。
        回答要专业、友好，注重员工体验。
        如果需要多个步骤，请逐步执行。
        """;
    
    public HrBrain(List<Tool> tools) {
        super(
            ID,
            "HrBrain",
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
        log.info("HrBrain started, listening to {}", INPUT_CHANNEL);
    }
    
    @Override
    protected void doStop() {
        log.info("HrBrain stopped");
    }
    
    @Override
    protected void doProcess(ChannelMessage message) {
        log.debug("HrBrain processing message: {}", message.getId());

        String userMessage = extractText(message);
        if (userMessage == null || userMessage.isEmpty()) {
            log.warn("HrBrain received empty message");
            return;
        }

        try {
            String sessionId = message.getSessionId();
            List<Provider.ChatMessage> previousHistory = getSessionHistory(sessionId);
            ReActResult result = executeReActLoop(userMessage, sessionId, previousHistory);
            
            if (result.success()) {
                updateSessionHistory(sessionId, userMessage, result.content());
                publishResponse(message, result.content(), result.iterations());
            } else {
                publishError(message, result.content());
            }
            
        } catch (Exception e) {
            log.error("HrBrain failed to process message", e);
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
