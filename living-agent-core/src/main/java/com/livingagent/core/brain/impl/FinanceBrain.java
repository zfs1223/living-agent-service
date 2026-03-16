package com.livingagent.core.brain.impl;

import java.util.List;

import com.livingagent.core.brain.BrainContext;
import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.tool.Tool;
import com.livingagent.core.tool.ToolSchema;

public class FinanceBrain extends AbstractBrain {
    
    private static final String ID = "neuron://finance/finance-brain/001";
    private static final String INPUT_CHANNEL = "channel://finance/tasks";
    private static final String OUTPUT_CHANNEL = "channel://output/text";
    private static final String DEPARTMENT = "finance";
    
    private static final String SYSTEM_PROMPT = """
        你是财务部门的智能助手，负责财务相关事务。
        
        你的职责包括：
        - 财务数据查询和分析
        - 预算管理
        - 费用报销处理
        - 发票管理
        - 财务报表支持
        
        请根据用户的需求，使用合适的工具完成任务。
        回答要准确、合规，注重数据安全。
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
    protected void doStart(BrainContext context) {
    }
    
    @Override
    protected void doStop() {
    }
    
    @Override
    protected void doProcess(ChannelMessage message) {
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
            .filter(java.util.Objects::nonNull)
            .toList();
    }
}
