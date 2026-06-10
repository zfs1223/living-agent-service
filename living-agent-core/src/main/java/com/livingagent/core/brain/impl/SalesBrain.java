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

public class SalesBrain extends AbstractBrain {
    
    private static final Logger log = LoggerFactory.getLogger(SalesBrain.class);

    public static final String ID = "neuron://sales/sales-brain/001";
    public static final String INPUT_CHANNEL = "channel://sales/tasks";
    public static final String OUTPUT_CHANNEL = "channel://output/text";
    private static final String DEPARTMENT = "sales";
    
    private static final String SYSTEM_PROMPT = """
        你是销售部门的智能助手，负责销售和客户关系相关事务。
        
        你的职责包括：
        - 客户信息管理和查询
        - 商机跟进和管理
        - 合同管理
        - 销售数据分析和报告
        - 客户沟通支持
        
        你可以使用以下工具：
        - sales_* : 销售管理专用工具（订单、业绩、目标）
        - crm_* : CRM系统操作（客户、联系人、商机）
        - customer_* : 客户管理操作（信息、画像、标签）
        - lead_* : 线索管理操作（获取、分配、转化）
        
        请根据用户的需求，使用合适的工具完成任务。
        回答要专业、积极，注重客户关系维护。
        如果需要多个步骤，请逐步执行。
        """;
    
    public SalesBrain(List<Tool> tools) {
        super(
            ID,
            "SalesBrain",
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
        log.info("SalesBrain started, listening to {}", INPUT_CHANNEL);
    }
    
    @Override
    protected void doStop() {
        log.info("SalesBrain stopped");
    }
    
    @Override
    protected void doProcess(ChannelMessage message) {
        log.debug("SalesBrain processing message: {}", message.getId());

        String userMessage = extractText(message);
        if (userMessage == null || userMessage.isEmpty()) {
            log.warn("SalesBrain received empty message");
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
                    log.warn("SalesBrain ReAct loop succeeded but returned empty content");
                    publishResponse(message, "任务处理完成，但未生成有效内容。", 0);
                }
            } else {
                publishError(message, result.content());
            }
        } catch (Exception e) {
            log.error("SalesBrain failed to process message", e);
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
