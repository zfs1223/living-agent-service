package com.livingagent.core.brain.impl;

import java.util.List;

import com.livingagent.core.brain.BrainContext;
import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.tool.Tool;
import com.livingagent.core.tool.ToolSchema;

public class SalesBrain extends AbstractBrain {
    
    private static final String ID = "neuron://sales/sales-brain/001";
    private static final String INPUT_CHANNEL = "channel://sales/tasks";
    private static final String OUTPUT_CHANNEL = "channel://output/text";
    private static final String DEPARTMENT = "sales";
    
    private static final String SYSTEM_PROMPT = """
        你是销售部门的智能助手，负责销售和客户关系相关事务。
        
        你的职责包括：
        - 客户信息管理和查询
        - 商机跟进和管理
        - 合同管理
        - 销售数据分析和报告
        - 客户沟通支持
        
        请根据用户的需求，使用合适的工具完成任务。
        回答要专业、积极，注重客户关系维护。
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
