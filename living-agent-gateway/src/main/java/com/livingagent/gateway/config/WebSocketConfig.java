package com.livingagent.gateway.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.livingagent.gateway.websocket.AgentWebSocketHandler;
import com.livingagent.gateway.websocket.DepartmentWebSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    
    private final AgentWebSocketHandler agentWebSocketHandler;
    private final DepartmentWebSocketHandler departmentWebSocketHandler;
    
    public WebSocketConfig(AgentWebSocketHandler agentWebSocketHandler,
                          DepartmentWebSocketHandler departmentWebSocketHandler) {
        this.agentWebSocketHandler = agentWebSocketHandler;
        this.departmentWebSocketHandler = departmentWebSocketHandler;
    }
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(agentWebSocketHandler, "/ws/agent")
            .setAllowedOrigins("*");
        
        registry.addHandler(departmentWebSocketHandler, "/ws/dept/*")
            .setAllowedOrigins("*");
        
        registry.addHandler(departmentWebSocketHandler, "/ws/chairman")
            .setAllowedOrigins("*");
        
        registry.addHandler(departmentWebSocketHandler, "/ws/public")
            .setAllowedOrigins("*");
    }
}
