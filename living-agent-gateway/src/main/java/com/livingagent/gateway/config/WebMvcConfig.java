package com.livingagent.gateway.config;

import com.livingagent.gateway.interceptor.SystemInitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final SystemInitInterceptor systemInitInterceptor;

    public WebMvcConfig(SystemInitInterceptor systemInitInterceptor) {
        this.systemInitInterceptor = systemInitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(systemInitInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                    "/api/system/status",
                    "/api/system/register",
                    "/auth/register",
                    "/auth/callback/**",
                    "/login",
                    "/error",
                    "/favicon.ico",
                    "/**/*.css",
                    "/**/*.js",
                    "/**/*.png",
                    "/**/*.jpg",
                    "/**/*.svg",
                    "/**/*.ico"
                );
    }
}
