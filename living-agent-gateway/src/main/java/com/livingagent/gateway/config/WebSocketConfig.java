package com.livingagent.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import com.livingagent.gateway.websocket.AgentWebSocketHandler;
import com.livingagent.gateway.websocket.AuthHandshakeInterceptor;
import com.livingagent.gateway.websocket.DepartmentWebSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private static final int MAX_TEXT_MESSAGE_BUFFER_SIZE = 131072;   // 128KB
    private static final int MAX_BINARY_MESSAGE_BUFFER_SIZE = 262144;  // 256KB
    
    private final AgentWebSocketHandler agentWebSocketHandler;
    private final DepartmentWebSocketHandler departmentWebSocketHandler;
    private final AuthHandshakeInterceptor authHandshakeInterceptor;
    
    public WebSocketConfig(AgentWebSocketHandler agentWebSocketHandler,
                          DepartmentWebSocketHandler departmentWebSocketHandler,
                          AuthHandshakeInterceptor authHandshakeInterceptor) {
        this.agentWebSocketHandler = agentWebSocketHandler;
        this.departmentWebSocketHandler = departmentWebSocketHandler;
        this.authHandshakeInterceptor = authHandshakeInterceptor;
    }
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 使用 setAllowedOriginPatterns("*") 允许所有来源连接 WebSocket
        // 安全性由 AuthHandshakeInterceptor 的 Token 认证保证
        String[] origins = {"*"};

        registry.addHandler(agentWebSocketHandler, "/ws/agent")
            .addInterceptors(authHandshakeInterceptor)
            .setAllowedOriginPatterns(origins);

        registry.addHandler(departmentWebSocketHandler, "/ws/dept/*")
            .addInterceptors(authHandshakeInterceptor)
            .setAllowedOriginPatterns(origins);

        registry.addHandler(departmentWebSocketHandler, "/ws/enterprise")
            .addInterceptors(authHandshakeInterceptor)
            .setAllowedOriginPatterns(origins);

        registry.addHandler(departmentWebSocketHandler, "/ws/public")
            .addInterceptors(authHandshakeInterceptor)
            .setAllowedOriginPatterns(origins);
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(MAX_TEXT_MESSAGE_BUFFER_SIZE);
        container.setMaxBinaryMessageBufferSize(MAX_BINARY_MESSAGE_BUFFER_SIZE);
        return container;
    }
}
