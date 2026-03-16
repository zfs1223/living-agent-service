package com.livingagent.gateway.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.livingagent.gateway.websocket.AgentWebSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    
    private final AgentWebSocketHandler agentWebSocketHandler;
    
    public WebSocketConfig(AgentWebSocketHandler agentWebSocketHandler) {
        this.agentWebSocketHandler = agentWebSocketHandler;
    }
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(agentWebSocketHandler, "/ws/agent")
            .setAllowedOrigins("*");
    }
}
