package com.livingagent.gateway.parallel;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.livingagent.core.provider.Provider;
import com.livingagent.core.provider.ProviderRegistry;
import com.livingagent.core.provider.impl.BitNetProvider;
import com.livingagent.gateway.dialogue.DialogueMessage;
import com.livingagent.gateway.event.ToolResultEvent;
import com.livingagent.gateway.executor.ToolExecutorService;

@Service
public class ParallelModelService {
    
    private static final Logger log = LoggerFactory.getLogger(ParallelModelService.class);
    
    private final ProviderRegistry providerRegistry;
    private final ToolExecutorService toolExecutorService;
    private final ApplicationEventPublisher eventPublisher;
    private final ExecutorService parallelExecutor;
    
    public ParallelModelService(ProviderRegistry providerRegistry,
                                ToolExecutorService toolExecutorService,
                                ApplicationEventPublisher eventPublisher) {
        this.providerRegistry = providerRegistry;
        this.toolExecutorService = toolExecutorService;
        this.eventPublisher = eventPublisher;
        this.parallelExecutor = Executors.newFixedThreadPool(2);
    }
    
    public CompletableFuture<ParallelResult> processWithParallelModels(
            String sessionId,
            String userInput,
            List<DialogueMessage> history,
            String systemPrompt) {
        
        log.debug("Starting parallel model processing for session: {}", sessionId);
        
        CompletableFuture<BitNetProvider.ToolIntentResult> toolIntentFuture = CompletableFuture.supplyAsync(() -> {
            try {
                Provider toolProvider = providerRegistry.get("bitnet").orElse(null);
                if (toolProvider instanceof BitNetProvider bitnetProvider) {
                    List<String> availableTools = getAvailableToolNames();
                    return bitnetProvider.processToolIntent(userInput, availableTools).join();
                }
                return new BitNetProvider.ToolIntentResult(false, null, null, null);
            } catch (Exception e) {
                log.debug("BitNet tool intent processing failed: {}", e.getMessage());
                return new BitNetProvider.ToolIntentResult(false, null, null, null);
            }
        }, parallelExecutor);
        
        CompletableFuture<String> responseFuture = CompletableFuture.supplyAsync(() -> {
            try {
                Provider chatProvider = providerRegistry.getDefault();
                if (chatProvider == null) {
                    return "抱歉，服务暂时不可用。";
                }
                
                String prompt = buildPrompt(systemPrompt, userInput, history);
                return chatProvider.simpleChat(prompt, null, 0.7).join();
            } catch (Exception e) {
                log.error("Chat generation failed", e);
                return "抱歉，生成回复时出现错误。";
            }
        }, parallelExecutor);
        
        return toolIntentFuture.thenCombine(responseFuture, (toolIntent, response) -> {
            ParallelResult result = new ParallelResult();
            result.setResponse(response);
            
            if (toolIntent.isToolCall()) {
                log.info("Tool intent detected: tool={}, session={}", toolIntent.toolName(), sessionId);
                
                result.setToolCall(true);
                result.setToolName(toolIntent.toolName());
                result.setToolParameters(toolIntent.parameters());
                
                executeToolAsync(sessionId, toolIntent.toolName(), toolIntent.parameters());
            } else {
                result.setToolCall(false);
            }
            
            return result;
        });
    }
    
    private void executeToolAsync(String sessionId, String toolName, Map<String, Object> parameters) {
        CompletableFuture.runAsync(() -> {
            try {
                var toolResult = toolExecutorService.execute(toolName, parameters, sessionId, "system");
                
                ToolResultEvent event = toolResult.success()
                    ? ToolResultEvent.success(sessionId, toolName, toolResult.getData())
                    : ToolResultEvent.failure(sessionId, toolName, toolResult.error());
                
                eventPublisher.publishEvent(event);
                
                log.info("Tool {} executed, success: {}", toolName, toolResult.success());
            } catch (Exception e) {
                log.error("Tool execution failed: {}", toolName, e);
                eventPublisher.publishEvent(ToolResultEvent.failure(sessionId, toolName, e.getMessage()));
            }
        });
    }
    
    private List<String> getAvailableToolNames() {
        return toolExecutorService.getAllExecutors().stream()
            .map(e -> e.getName())
            .toList();
    }
    
    private String buildPrompt(String systemPrompt, String userInput, List<DialogueMessage> history) {
        StringBuilder sb = new StringBuilder();
        
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            sb.append(systemPrompt).append("\n\n");
        }
        
        if (history != null && !history.isEmpty()) {
            sb.append("对话历史：\n");
            for (DialogueMessage msg : history) {
                if (!msg.isSystem()) {
                    sb.append(msg.role()).append(": ").append(msg.content()).append("\n");
                }
            }
            sb.append("\n");
        }
        
        sb.append("用户: ").append(userInput);
        
        return sb.toString();
    }
    
    public void shutdown() {
        parallelExecutor.shutdown();
    }
    
    public static class ParallelResult {
        private String response;
        private boolean toolCall;
        private String toolName;
        private Map<String, Object> toolParameters;
        
        public String getResponse() {
            return response;
        }
        
        public void setResponse(String response) {
            this.response = response;
        }
        
        public boolean isToolCall() {
            return toolCall;
        }
        
        public void setToolCall(boolean toolCall) {
            this.toolCall = toolCall;
        }
        
        public String getToolName() {
            return toolName;
        }
        
        public void setToolName(String toolName) {
            this.toolName = toolName;
        }
        
        public Map<String, Object> getToolParameters() {
            return toolParameters;
        }
        
        public void setToolParameters(Map<String, Object> toolParameters) {
            this.toolParameters = toolParameters;
        }
    }
}
