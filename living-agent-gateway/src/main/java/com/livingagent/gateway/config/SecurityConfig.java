package com.livingagent.gateway.config;

import com.livingagent.core.security.auth.UnifiedAuthService;
import com.livingagent.gateway.security.SessionAuthenticationFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final UnifiedAuthService authService;

    public SecurityConfig(UnifiedAuthService authService) {
        this.authService = authService;
    }

    @Bean
    public SessionAuthenticationFilter sessionAuthenticationFilter() {
        return new SessionAuthenticationFilter(authService);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring security filter chain");

        http
            // CSRF 保护已禁用：项目使用 Bearer Token 认证为主，Cookie 认证为辅。
            // Cookie 设置了 SameSite=Lax，可防御 CSRF 攻击（Lax 模式下跨站 POST 请求不会携带 Cookie）。
            // 所有修改数据的 API 均需 Bearer Token 或 Cookie 认证，攻击者无法仅凭 CSRF 触发已认证操作。
            // 若未来需要支持跨站 GET 以外的 Cookie 提交场景，必须启用 CSRF 保护。
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> {})
            // 在 Spring Security 认证链之前注入会话认证过滤器，
            // 从 Authorization: Bearer {sessionId} 或 HttpOnly Cookie (access_token) 解析并验证会话，
            // 设置 SecurityContext Authentication 和 X-Employee-Id header
            .addFilterBefore(sessionAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                // 公开端点：无需认证
                .requestMatchers(
                    "/api/system/status",
                    "/api/system/register",
                    "/api/reception/**",
                    "/api/auth/oauth/**",
                    "/api/auth/providers",
                    "/api/public/**",
                    "/api/tasks/public",       // 公开任务列表（桌面端无需登录即可查看）
                    "/api/tenants/registration-config",
                    "/api/auth/sms/send",
                    "/api/auth/phone/login",
                    "/api/auth/login",
                    "/api/auth/register",
                    "/api/invitation-codes/validate",
                    "/api/invitation-codes/use",
                    "/api/version",
                    "/api/notifications/unread-count",
                    "/api/messages/unread-count",
                    "/api/enterprise/settings/notification_bar/public",  // 公开公告栏（登录前显示）
                    "/api/monitoring/health",
                    "/api/health",               // 健康检查（桌面端连接检测用）
                    "/api/health/**",            // P12-B: liveness/readiness 探针
                    "/ws/**",
                    "/login",
                    "/error",
                    "/favicon.ico",
                    "/*.css",
                    "/*.js",
                    "/*.png",
                    "/*.jpg",
                    "/*.svg",
                    "/*.ico",
                    "/assets/**",
                    "/static/**"
                ).permitAll()
                // 管理类端点：需要FULL权限（由拦截器细控）
                .requestMatchers(
                    "/api/model-pool/**",
                    "/api/brain-models/**",
                    "/api/windows-automation/**",
                    "/api/v1/proxy/**",
                    "/api/evolution/**"
                ).authenticated()
                // 部门API：需要认证+部门权限（由拦截器细控）
                .requestMatchers(
                    "/api/tech/**",
                    "/api/hr/**",
                    "/api/finance/**",
                    "/api/sales/**",
                    "/api/admin/**",
                    "/api/cs/**",
                    "/api/legal/**",
                    "/api/ops/**",
                    "/api/dept/**",
                    "/api/enterprise/**"
                ).authenticated()
                // 其余所有请求：需要认证
                .anyRequest().authenticated()
            );

        log.info("Security filter chain configured with public endpoints and session auth filter");
        return http.build();
    }
}
