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

public class FinanceBrain extends AbstractBrain {
    
    private static final Logger log = LoggerFactory.getLogger(FinanceBrain.class);

    public static final String ID = "neuron://finance/finance-brain/001";
    public static final String INPUT_CHANNEL = "channel://finance/tasks";
    public static final String OUTPUT_CHANNEL = "channel://output/text";
    private static final String DEPARTMENT = "finance";
    
    private static final String SYSTEM_PROMPT = """
        你是财务部门的智能助手，负责财务相关事务。
        
        你的职责包括：
        - 财务数据查询和分析
        - 预算管理
        - 费用报销处理
        - 发票管理
        - 财务报表支持
        
        你可以使用以下工具：
        - budget_* : 预算管理相关操作（查询、调整、审批）
        - finance_* : 财务系统专用工具（报表、分析）
        - invoice_* : 发票管理操作（开具、查询、验证）
        - expense_* : 费用报销操作（提交、审批、查询）
        
        请根据用户的需求，使用合适的工具完成任务。
        回答要准确、合规，注重数据安全。
        如果需要多个步骤，请逐步执行。
        """;
    
    public FinanceBrain(List<Tool> tools) {
        super(
            ID,
            "FinanceBrain",
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
        log.info("FinanceBrain started, listening to {}", INPUT_CHANNEL);
    }
    
    @Override
    protected void doStop() {
        log.info("FinanceBrain stopped");
    }
    
    @Override
    protected void doProcess(ChannelMessage message) {
        log.debug("FinanceBrain processing message: {}", message.getId());

        String userMessage = extractText(message);
        if (userMessage == null || userMessage.isEmpty()) {
            log.warn("FinanceBrain received empty message");
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
                    log.warn("FinanceBrain ReAct loop succeeded but returned empty content");
                    publishResponse(message, "任务处理完成，但未生成有效内容。", 0);
                }
            } else {
                publishError(message, result.content());
            }
        } catch (Exception e) {
            log.error("FinanceBrain failed to process message", e);
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
