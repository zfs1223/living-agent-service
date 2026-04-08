package com.livingagent.gateway.config;

import com.livingagent.gateway.interceptor.DepartmentPermissionInterceptor;
import com.livingagent.gateway.interceptor.SystemInitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final SystemInitInterceptor systemInitInterceptor;
    private final DepartmentPermissionInterceptor departmentPermissionInterceptor;

    public WebMvcConfig(SystemInitInterceptor systemInitInterceptor, 
                        DepartmentPermissionInterceptor departmentPermissionInterceptor) {
        this.systemInitInterceptor = systemInitInterceptor;
        this.departmentPermissionInterceptor = departmentPermissionInterceptor;
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
        
        registry.addInterceptor(departmentPermissionInterceptor)
                .addPathPatterns("/api/dept/**", "/api/chairman/**")
                .excludePathPatterns(
                    "/api/dept/my",
                    "/error"
                );
    }
}
