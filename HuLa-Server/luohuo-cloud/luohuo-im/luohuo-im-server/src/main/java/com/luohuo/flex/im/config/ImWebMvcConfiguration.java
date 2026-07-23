package com.luohuo.flex.im.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.luohuo.flex.im.common.interceptor.BlackInterceptor;

@Configuration
@RequiredArgsConstructor
public class ImWebMvcConfiguration implements WebMvcConfigurer {
    private final BlackInterceptor blackInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        String[] commonExclude = new String[] {
                "/doc.html",
                "/swagger-ui.html",
                "/swagger-resources/**",
                "/v3/api-docs/**",
                "/webjars/**",
                "/error",
                "/actuator/**",
                "/favicon.ico"
        };
        registry.addInterceptor(blackInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(commonExclude)
                .order(15);
    }
}
