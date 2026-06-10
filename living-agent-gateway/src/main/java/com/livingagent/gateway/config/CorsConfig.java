package com.livingagent.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;

@Configuration
public class CorsConfig {
    
    private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);

    @Bean
    public CorsFilter corsFilter() {
        log.info("Initializing CORS configuration");
        
        CorsConfiguration config = new CorsConfiguration();
        
        // 生产环境应限制为具体域名，如 Arrays.asList("https://your-domain.com")
        // 开发环境可通过 CORS_ALLOWED_ORIGINS 环境变量配置
        String allowedOrigins = System.getenv("CORS_ALLOWED_ORIGINS");
        if (allowedOrigins != null && !allowedOrigins.isBlank()) {
            config.setAllowedOriginPatterns(Arrays.asList(allowedOrigins.split(",")));
        } else {
            // 开发环境默认允许所有来源，生产环境必须配置 CORS_ALLOWED_ORIGINS
            config.setAllowedOriginPatterns(Arrays.asList("*"));
        }
        
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        
        config.setAllowedHeaders(Arrays.asList("*"));
        
        config.setAllowCredentials(true);
        
        config.setMaxAge(3600L);
        
        config.setExposedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With"));
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        source.registerCorsConfiguration("/ws/**", config);
        
        log.info("CORS filter configured for /api/** and /ws/**");
        
        return new CorsFilter(source);
    }
}
