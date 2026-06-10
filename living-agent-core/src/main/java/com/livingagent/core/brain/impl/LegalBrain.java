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

public class LegalBrain extends AbstractBrain {
    
    private static final Logger log = LoggerFactory.getLogger(LegalBrain.class);

    public static final String ID = "neuron://legal/legal-brain/001";
    public static final String INPUT_CHANNEL = "channel://legal/tasks";
    public static final String OUTPUT_CHANNEL = "channel://output/text";
    private static final String DEPARTMENT = "legal";
    
    private static final String SYSTEM_PROMPT = """
        你是法务部门的智能助手，负责法律和合规相关事务。
        
        你的职责包括：
        - 合同审查和管理
        - 合规检查
        - 法律咨询支持
        - 知识产权保护
        - 风险评估
        
        你可以使用以下工具：
        - legal_* : 法务系统专用工具（咨询、案例、法规）
        - contract_* : 合同管理操作（起草、审查、归档）
        - compliance_* : 合规检查操作（审核、报告、整改）
        - risk_* : 风险管理操作（评估、预警、处置）
        
        请根据用户的需求，使用合适的工具完成任务。
        回答要严谨、专业，注意法律风险提示。
        如果需要多个步骤，请逐步执行。
        """;
    
    public LegalBrain(List<Tool> tools) {
        super(
            ID,
            "LegalBrain",
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
        log.info("LegalBrain started, listening to {}", INPUT_CHANNEL);
    }
    
    @Override
    protected void doStop() {
        log.info("LegalBrain stopped");
    }
    
    @Override
    protected void doProcess(ChannelMessage message) {
        log.debug("LegalBrain processing message: {}", message.getId());

        String userMessage = extractText(message);
        if (userMessage == null || userMessage.isEmpty()) {
            log.warn("LegalBrain received empty message");
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
                    log.warn("LegalBrain ReAct loop succeeded but returned empty content");
                    publishResponse(message, "任务处理完成，但未生成有效内容。", 0);
                }
            } else {
                publishError(message, result.content());
            }
        } catch (Exception e) {
            log.error("LegalBrain failed to process message", e);
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
