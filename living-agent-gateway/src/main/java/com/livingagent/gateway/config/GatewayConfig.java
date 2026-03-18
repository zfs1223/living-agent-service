package com.livingagent.gateway.config;

import com.livingagent.core.brain.BrainRegistry;
import com.livingagent.core.brain.impl.BrainRegistryImpl;
import com.livingagent.core.employee.EmployeeService;
import com.livingagent.core.knowledge.KnowledgeManager;
import com.livingagent.core.neuron.NeuronRegistry;
import com.livingagent.core.proactive.suggestion.ProactiveSuggestionService;
import com.livingagent.core.security.auth.OAuthService;
import com.livingagent.core.security.auth.UnifiedAuthService;
import com.livingagent.core.security.voiceprint.VoicePrintService;
import com.livingagent.core.security.auth.PhoneVerificationService;
import com.livingagent.core.tool.Tool;
import com.livingagent.core.tool.ToolRegistry;
import com.livingagent.gateway.executor.ToolExecutor;
import com.livingagent.gateway.executor.ToolExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;

@Configuration
public class GatewayConfig {
    
    private static final Logger log = LoggerFactory.getLogger(GatewayConfig.class);

    @Bean
    public UnifiedAuthService unifiedAuthService(
            List<OAuthService> oauthServices,
            VoicePrintService voicePrintService,
            PhoneVerificationService phoneVerificationService
    ) {
        log.info("Initializing UnifiedAuthService with {} OAuth providers", 
                oauthServices != null ? oauthServices.size() : 0);
        return new UnifiedAuthService(oauthServices, voicePrintService, phoneVerificationService);
    }

    @Bean
    public ProactiveSuggestionService proactiveSuggestionService() {
        log.info("Initializing ProactiveSuggestionService");
        return new ProactiveSuggestionService(null, null, List.of());
    }
    
    @Bean
    public ToolExecutorService toolExecutorService(
            ApplicationEventPublisher eventPublisher,
            ToolRegistry toolRegistry) {
        log.info("Initializing ToolExecutorService");
        ToolExecutorService service = new ToolExecutorService(eventPublisher);
        
        for (Tool tool : toolRegistry.getAll()) {
            ToolExecutor executor = createExecutorFromTool(tool);
            if (executor != null) {
                service.register(executor);
                log.debug("Registered tool executor: {}", tool.getName());
            }
        }
        
        log.info("ToolExecutorService initialized with {} executors", service.getExecutorCount());
        return service;
    }
    
    private ToolExecutor createExecutorFromTool(Tool tool) {
        return new ToolExecutor() {
            @Override
            public String getName() {
                return tool.getName();
            }
            
            @Override
            public String getDescription() {
                return tool.getDescription();
            }
            
            @Override
            public com.livingagent.core.tool.ToolResult execute(Map<String, Object> parameters, String userId) {
                com.livingagent.core.tool.Tool.ToolParams params = com.livingagent.core.tool.Tool.ToolParams.of(parameters);
                com.livingagent.core.tool.ToolContext context = com.livingagent.core.tool.ToolContext.of(
                    userId, 
                    "gateway-session"
                );
                return tool.execute(params, context);
            }
            
            @Override
            public boolean requiresApproval() {
                return tool.requiresApproval();
            }
            
            @Override
            public String[] getRequiredParameters() {
                List<String> required = tool.getSchema().required();
                return required != null ? required.toArray(new String[0]) : new String[0];
            }
        };
    }
}
