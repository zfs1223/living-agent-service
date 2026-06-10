package com.livingagent.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import com.livingagent.gateway.websocket.AgentWebSocketHandler;
import com.livingagent.gateway.websocket.DepartmentWebSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private static final int MAX_TEXT_MESSAGE_BUFFER_SIZE = 131072;   // 128KB
    private static final int MAX_BINARY_MESSAGE_BUFFER_SIZE = 262144;  // 256KB
    
    private final AgentWebSocketHandler agentWebSocketHandler;
    private final DepartmentWebSocketHandler departmentWebSocketHandler;
    
    public WebSocketConfig(AgentWebSocketHandler agentWebSocketHandler,
                          DepartmentWebSocketHandler departmentWebSocketHandler) {
        this.agentWebSocketHandler = agentWebSocketHandler;
        this.departmentWebSocketHandler = departmentWebSocketHandler;
    }
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 生产环境应限制为具体域名，通过 WS_ALLOWED_ORIGINS 环境变量配置
        String allowedOrigins = System.getenv("WS_ALLOWED_ORIGINS");
        String[] origins = (allowedOrigins != null && !allowedOrigins.isBlank())
            ? allowedOrigins.split(",") : new String[]{"*"};

        registry.addHandler(agentWebSocketHandler, "/ws/agent")
            .setAllowedOrigins(origins);

        registry.addHandler(departmentWebSocketHandler, "/ws/dept/*")
            .setAllowedOrigins(origins);

        registry.addHandler(departmentWebSocketHandler, "/ws/enterprise")
            .setAllowedOrigins(origins);

        registry.addHandler(departmentWebSocketHandler, "/ws/public")
            .setAllowedOrigins(origins);
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(MAX_TEXT_MESSAGE_BUFFER_SIZE);
        container.setMaxBinaryMessageBufferSize(MAX_BINARY_MESSAGE_BUFFER_SIZE);
        return container;
    }
}
