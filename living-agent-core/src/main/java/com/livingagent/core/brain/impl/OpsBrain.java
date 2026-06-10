package com.livingagent.core.brain.impl;

import java.util.List;

import com.livingagent.core.provider.Provider;
import java.util.Objects;

import com.livingagent.core.brain.BrainContext;
import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.tool.Tool;
import com.livingagent.core.tool.ToolSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpsBrain extends AbstractBrain {
    
    private static final Logger log = LoggerFactory.getLogger(OpsBrain.class);

    public static final String ID = "neuron://ops/ops-brain/001";
    public static final String INPUT_CHANNEL = "channel://ops/tasks";
    public static final String OUTPUT_CHANNEL = "channel://output/text";
    private static final String DEPARTMENT = "ops";
    
    private static final String SYSTEM_PROMPT = """
        你是运营部门的智能助手，负责运营和数据相关事务。
        
        你的职责包括：
        - 数据分析和报告
        - 运营策略支持
        - 用户行为分析
        - 营销活动支持
        - KPI监控和分析
        
        你可以使用以下工具：
        - ops_* : 运营管理专用工具（活动、策略、配置）
        - monitor_* : 监控告警操作（指标、仪表盘、通知）
        - deploy_* : 部署发布操作（版本、回滚、灰度）
        - analytics_* : 数据分析操作（报表、洞察、导出）
        
        请根据用户的需求，使用合适的工具完成任务。
        回答要数据驱动、有洞察力，注重业务价值。
        如果需要多个步骤，请逐步执行。
        """;
    
    public OpsBrain(List<Tool> tools) {
        super(
            ID,
            "OpsBrain",
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
        log.info("OpsBrain started, listening to {}", INPUT_CHANNEL);
    }
    
    @Override
    protected void doStop() {
        log.info("OpsBrain stopped");
    }
    
    @Override
    protected void doProcess(ChannelMessage message) {
        log.debug("OpsBrain processing message: {}", message.getId());

        String userMessage = extractText(message);
        if (userMessage == null || userMessage.isEmpty()) {
            log.warn("OpsBrain received empty message");
            return;
        }

        String sessionId = message.getSessionId();
        
        try {
            // 获取之前的对话历史，保留上下文记忆
            List<Provider.ChatMessage> previousHistory = getSessionHistory(sessionId);
            
            ReActResult result = executeReActLoop(userMessage, sessionId, previousHistory);
            
            if (result.success()) {
                if (result.content() != null && !result.content().isBlank()) {
                    // 更新对话历史
                    updateSessionHistory(sessionId, userMessage, result.content());
                    publishResponse(message, result.content(), result.iterations());
                } else {
                    log.warn("OpsBrain ReAct loop succeeded but returned empty content");
                    publishResponse(message, "任务处理完成，但未生成有效内容。", 0);
                }
            } else {
                publishError(message, result.content());
            }
        } catch (Exception e) {
            log.error("OpsBrain failed to process message", e);
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
