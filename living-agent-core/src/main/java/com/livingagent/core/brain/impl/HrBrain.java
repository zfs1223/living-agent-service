package com.livingagent.core.brain.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.livingagent.core.brain.BrainContext;
import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.memory.MemoryCategory;
import com.livingagent.core.provider.Provider;
import com.livingagent.core.tool.Tool;
import com.livingagent.core.tool.ToolContext;
import com.livingagent.core.tool.ToolResult;
import com.livingagent.core.tool.ToolSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HrBrain extends AbstractBrain {
    
    private static final Logger log = LoggerFactory.getLogger(HrBrain.class);

    public static final String ID = "neuron://hr/hr-brain/001";
    public static final String INPUT_CHANNEL = "channel://hr/tasks";
    public static final String OUTPUT_CHANNEL = "channel://output/text";
    private static final String DEPARTMENT = "hr";
    
    private static final int MAX_ITERATIONS = 10;
    
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
    
    private int iterationCount = 0;
    
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
    protected void doStart(BrainContext context) {
        log.info("HrBrain started, listening to {}", INPUT_CHANNEL);
        iterationCount = 0;
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
            String response = executeToolCallLoop(userMessage, message.getSessionId());
            
            ChannelMessage responseMessage = ChannelMessage.text(
                OUTPUT_CHANNEL,
                getId(),
                message.getSourceChannelId(),
                message.getSessionId(),
                response
            );
            responseMessage.addMetadata("original_message_id", message.getId());
            responseMessage.addMetadata("brain_id", getId());
            responseMessage.addMetadata("department", DEPARTMENT);
            responseMessage.addMetadata("iterations", iterationCount);
            responseMessage.addMetadata("type", "brain_response");
            
            publish(OUTPUT_CHANNEL, responseMessage);
            log.debug("Published response to {}", OUTPUT_CHANNEL);
            
        } catch (Exception e) {
            log.error("HrBrain failed to process message", e);
            publishError(message, "处理失败: " + e.getMessage());
        }
    }
    
    private String executeToolCallLoop(String userMessage, String sessionId) {
        Provider provider = getProvider();
        if (provider == null) {
            return "错误：Provider 未配置";
        }

        List<Provider.ChatMessage> history = new ArrayList<>();
        history.add(Provider.ChatMessage.system(SYSTEM_PROMPT));
        history.add(Provider.ChatMessage.user(userMessage));

        iterationCount = 0;

        while (iterationCount < MAX_ITERATIONS) {
            iterationCount++;

            Provider.ChatRequest request = new Provider.ChatRequest(
                history,
                getToolSchemas(),
                "qwen3.5-27b",
                0.7,
                4096
            );

            try {
                Provider.ChatResponse response = provider.chat(request).join();

                if (response.hasToolCalls()) {
                    List<Provider.ToolCallData> toolCalls = response.toolCalls();
                    log.debug("Received {} tool calls", toolCalls.size());

                    history.add(Provider.ChatMessage.assistantWithTools(
                        response.content(),
                        toolCalls
                    ));

                    List<Provider.ToolResultData> results = executeToolCalls(toolCalls, sessionId);
                    history.add(Provider.ChatMessage.toolResult(results));

                } else {
                    return response.content();
                }

            } catch (Exception e) {
                log.error("Error in tool-call loop at iteration {}", iterationCount, e);
                return "处理过程中发生错误: " + e.getMessage();
            }
        }

        return "已达到最大迭代次数，任务可能未完成。";
    }
    
    private List<Provider.ToolResultData> executeToolCalls(
            List<Provider.ToolCallData> toolCalls, String sessionId) {
        
        List<Provider.ToolResultData> results = new ArrayList<>();
        var toolRegistry = getToolRegistry();
        var memory = getMemory();

        for (Provider.ToolCallData call : toolCalls) {
            try {
                log.info("Executing tool: {} (id: {})", call.name(), call.id());

                var toolOpt = toolRegistry.get(call.name());
                if (toolOpt.isEmpty()) {
                    results.add(new Provider.ToolResultData(
                        call.id(),
                        "错误：工具 " + call.name() + " 不存在"
                    ));
                    continue;
                }

                Tool tool = toolOpt.get();
                Map<String, Object> args = parseArguments(call.arguments());
                Tool.ToolParams params = Tool.ToolParams.of(args);
                ToolContext context = ToolContext.of(getId(), sessionId);

                tool.validate(params);
                ToolResult result = tool.execute(params, context);

                String resultContent = result.success()
                    ? formatSuccessResult(result.data())
                    : "错误: " + result.error();

                results.add(new Provider.ToolResultData(call.id(), resultContent));

                if (memory != null) {
                    memory.store(
                        "tool_call:" + call.id(),
                        String.format("Tool: %s, Args: %s, Result: %s",
                            call.name(), args, resultContent),
                        MemoryCategory.DAILY,
                        sessionId
                    );
                }

                log.debug("Tool {} executed successfully: {}", call.name(),
                    resultContent.length() > 100 ? resultContent.substring(0, 100) + "..." : resultContent);

            } catch (Exception e) {
                log.error("Failed to execute tool: {}", call.name(), e);
                results.add(new Provider.ToolResultData(
                    call.id(),
                    "执行失败: " + e.getMessage()
                ));
            }
        }

        return results;
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArguments(String arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return Map.of();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(arguments, Map.class);
        } catch (Exception e) {
            log.warn("Failed to parse arguments: {}", arguments, e);
            return Map.of();
        }
    }

    private String formatSuccessResult(Object data) {
        if (data == null) {
            return "执行成功";
        }
        if (data instanceof String s) {
            return s;
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(data);
        } catch (Exception e) {
            return data.toString();
        }
    }
    
    private String extractText(ChannelMessage message) {
        Object payload = message.getPayload();
        return payload != null ? payload.toString() : null;
    }

    private void publishError(ChannelMessage original, String error) {
        ChannelMessage errorMessage = ChannelMessage.error(
            OUTPUT_CHANNEL,
            getId(),
            original.getSourceChannelId(),
            original.getSessionId(),
            error
        );
        publish(OUTPUT_CHANNEL, errorMessage);
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
