package com.livingagent.core.brain.impl;

import java.util.List;

import com.livingagent.core.brain.BrainContext;
import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.tool.Tool;
import com.livingagent.core.tool.ToolSchema;

public class CsBrain extends AbstractBrain {
    
    private static final String DEPARTMENT_NAME = "cs";
    private static final String SYSTEM_PROMPT = """
        你是客服部门的智能助手，负责客户服务和支持相关事务。
        
        你的职责包括：
        - 工单处理和跟踪
        - 客户问题解答
        - 知识库检索
        - 客户满意度调查
        - 投诉处理
        
        请根据用户的需求，使用合适的工具完成任务。
        回答要友好、耐心，注重客户体验。
        """;
    
    public CsBrain(String id, String name, String department,
                   List<String> subscribedChannels, List<String> publishChannels,
                   List<Tool> tools) {
        super(id, name, department, subscribedChannels, publishChannels, tools);
    }
    
    public CsBrain(String id, List<Tool> tools) {
        super(id, "CsBrain", DEPARTMENT_NAME, 
              List.of("cs.requests", "cs.tickets", "general"),
              List.of("cs.responses", "notifications"),
              tools);
    }
    
    @Override
    public String getDepartment() {
        return DEPARTMENT_NAME;
    }
    
    @Override
    protected void doStart(BrainContext context) {
        log.info("CsBrain {} started", getId());
    }
    
    @Override
    protected void doStop() {
        log.info("CsBrain {} stopped", getId());
    }
    
    @Override
    protected void doProcess(ChannelMessage message) {
        log.debug("CsBrain {} processing message: {}", getId(), message.getContent());
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
        return tools.stream().map(Tool::getSchema).toList();
    }
}
