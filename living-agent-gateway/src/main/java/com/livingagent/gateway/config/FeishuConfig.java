package com.livingagent.gateway.config;

import com.livingagent.core.security.auth.OAuthService;
import com.livingagent.core.security.auth.impl.FeishuOAuthService;
import com.livingagent.core.security.sync.FeishuSyncAdapter;
import com.livingagent.core.security.sync.HrSyncAdapter;
import com.livingagent.core.tool.Tool;
import com.livingagent.core.tool.ToolRegistry;
import com.livingagent.core.tool.impl.enterprise.ChairmanFeishuTool;
import com.livingagent.core.tool.impl.enterprise.HrFeishuTool;
import com.livingagent.core.tool.impl.enterprise.EmployeeFeishuTool;
import com.livingagent.core.proactive.alert.AlertNotifier;
import com.livingagent.core.proactive.alert.impl.FeishuNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@ConditionalOnProperty(name = "feishu.enabled", havingValue = "true", matchIfMissing = false)
public class FeishuConfig {

    private static final Logger log = LoggerFactory.getLogger(FeishuConfig.class);

    @Value("${feishu.chairman.app-id:}")
    private String chairmanAppId;

    @Value("${feishu.chairman.app-secret:}")
    private String chairmanAppSecret;

    @Value("${feishu.hr.app-id:}")
    private String hrAppId;

    @Value("${feishu.hr.app-secret:}")
    private String hrAppSecret;

    @Value("${feishu.employee.app-id:}")
    private String employeeAppId;

    @Value("${feishu.employee.app-secret:}")
    private String employeeAppSecret;

    @Value("${feishu.webhook-key:}")
    private String webhookKey;

    @Bean
    @Primary
    public OAuthService feishuOAuthService() {
        String appId = chairmanAppId != null && !chairmanAppId.isEmpty() ? chairmanAppId : hrAppId;
        String appSecret = chairmanAppId != null && !chairmanAppId.isEmpty() ? chairmanAppSecret : hrAppSecret;
        
        log.info("Initializing FeishuOAuthService with App ID: {}", maskAppId(appId));
        
        if (appId == null || appId.isEmpty()) {
            log.warn("Feishu App ID not configured, FeishuOAuthService will not work properly");
        }
        
        return new FeishuOAuthService(appId, appSecret);
    }

    @Bean
    @Primary
    public HrSyncAdapter feishuSyncAdapter() {
        String appId = chairmanAppId != null && !chairmanAppId.isEmpty() ? chairmanAppId : hrAppId;
        String appSecret = chairmanAppId != null && !chairmanAppId.isEmpty() ? chairmanAppSecret : hrAppSecret;
        
        log.info("Initializing FeishuSyncAdapter (Chairman/HR level)");
        return new FeishuSyncAdapter(appId, appSecret);
    }

    @Bean
    public Tool chairmanFeishuTool() {
        if (chairmanAppId == null || chairmanAppId.isEmpty()) {
            log.warn("Chairman Feishu App ID not configured, ChairmanFeishuTool will not be available");
            return null;
        }
        log.info("Initializing ChairmanFeishuTool with App ID: {}", maskAppId(chairmanAppId));
        return new ChairmanFeishuTool(chairmanAppId, chairmanAppSecret);
    }

    @Bean
    public Tool hrFeishuTool() {
        if (hrAppId == null || hrAppId.isEmpty()) {
            log.warn("HR Feishu App ID not configured, HrFeishuTool will not be available");
            return null;
        }
        log.info("Initializing HrFeishuTool with App ID: {}", maskAppId(hrAppId));
        return new HrFeishuTool(hrAppId, hrAppSecret);
    }

    @Bean
    public Tool employeeFeishuTool() {
        if (employeeAppId == null || employeeAppId.isEmpty()) {
            log.warn("Employee Feishu App ID not configured, EmployeeFeishuTool will not be available");
            return null;
        }
        log.info("Initializing EmployeeFeishuTool with App ID: {}", maskAppId(employeeAppId));
        return new EmployeeFeishuTool(employeeAppId, employeeAppSecret);
    }

    @Bean
    @ConditionalOnProperty(name = "feishu.webhook-key")
    public AlertNotifier feishuNotifier() {
        if (webhookKey != null && !webhookKey.isEmpty()) {
            log.info("Initializing FeishuNotifier with webhook key");
            return new FeishuNotifier(webhookKey);
        }
        log.info("Feishu webhook key not configured, FeishuNotifier disabled");
        return null;
    }

    private String maskAppId(String appId) {
        if (appId == null || appId.length() < 8) {
            return appId;
        }
        return appId.substring(0, 4) + "****" + appId.substring(appId.length() - 4);
    }
}
