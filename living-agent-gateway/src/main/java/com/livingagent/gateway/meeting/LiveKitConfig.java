package com.livingagent.gateway.meeting;

import com.livingagent.gateway.service.SystemConfigService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * LiveKit 配置类 - 闭环 67/68 会议管理基础配置
 *
 * <p>从 application.yml 读取 livekit.* 前缀的配置项，提供 LiveKit Server 连接参数。
 * 同时提供用于调用 LiveKit REST API 的 RestTemplate Bean。</p>
 *
 * <h3>配置优先级</h3>
 * <ol>
 *   <li>SystemConfigService（企业设置动态配置）- 最高优先级</li>
 *   <li>环境变量 / application.yml（Spring Boot 标准配置）- 中优先级</li>
 *   <li>硬编码默认值 - 最低优先级</li>
 * </ol>
 *
 * <p>配置示例（application.yml）：</p>
 * <pre>
 * livekit:
 *   api-url: http://livekit:7880
 *   api-key: las-livekit-api-key
 *   api-secret: las-livekit-api-secret-change-me
 *   webhook-secret: ${livekit.api-secret}
 * </pre>
 *
 * <p>企业设置中可覆盖的 key：</p>
 * <ul>
 *   <li>livekit.api-key - 覆盖 LiveKit API Key</li>
 *   <li>livekit.api-secret - 覆盖 LiveKit API Secret</li>
 *   <li>livekit.api-url - 覆盖 LiveKit Server 地址</li>
 * </ul>
 *
 * @author P81 LiveKit 部署与会议基础
 * @since 1.0.0
 */
@Configuration
@ConfigurationProperties(prefix = "livekit")
public class LiveKitConfig {

    private static final Logger log = LoggerFactory.getLogger(LiveKitConfig.class);

    /** 企业设置中 LiveKit 配置的 key 前缀 */
    private static final String SETTINGS_KEY_API_URL = "livekit.api-url";
    private static final String SETTINGS_KEY_API_KEY = "livekit.api-key";
    private static final String SETTINGS_KEY_API_SECRET = "livekit.api-secret";

    /** LiveKit Server API 地址（如 http://livekit:7880） */
    private String apiUrl = "http://livekit:7880";

    /** LiveKit API Key（用于 JWT 签名和 REST API 认证） */
    private String apiKey = "las-livekit-api-key";

    /** LiveKit API Secret（用于 JWT 签名和 REST API 认证） */
    private String apiSecret = "las-livekit-api-secret-change-me";

    /** Webhook 签名密钥（默认与 api-secret 相同） */
    private String webhookSecret = "las-livekit-api-secret-change-me";

    /** REST API 连接超时（毫秒） */
    private int connectTimeout = 5000;

    /** REST API 读取超时（毫秒） */
    private int readTimeout = 10000;

    private final SystemConfigService systemConfigService;

    public LiveKitConfig(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
        log.info("[P81] LiveKitConfig 初始化 - apiUrl={}, apiKey={}", apiUrl, apiKey);
    }

    /**
     * 启动时从 SystemConfigService 读取企业设置，覆盖默认值。
     * 优先级：SystemConfigService > 环境变量(Spring已注入) > 硬编码默认值
     */
    @PostConstruct
    public void refreshFromSystemConfig() {
        var settings = systemConfigService.getSettings();
        Object urlVal = settings.get(SETTINGS_KEY_API_URL);
        Object keyVal = settings.get(SETTINGS_KEY_API_KEY);
        Object secretVal = settings.get(SETTINGS_KEY_API_SECRET);

        boolean overridden = false;

        if (secretVal instanceof String s && !s.isBlank()) {
            this.apiSecret = s;
            this.webhookSecret = s;
            overridden = true;
            log.info("[P81] LiveKit api-secret 已从企业设置覆盖");
        }
        if (keyVal instanceof String s && !s.isBlank()) {
            this.apiKey = s;
            overridden = true;
            log.info("[P81] LiveKit api-key 已从企业设置覆盖");
        }
        if (urlVal instanceof String s && !s.isBlank()) {
            this.apiUrl = s;
            overridden = true;
            log.info("[P81] LiveKit api-url 已从企业设置覆盖");
        }

        if (overridden) {
            log.info("[P81] LiveKit 配置已从企业设置刷新 - apiUrl={}, apiKey={}", apiUrl, apiKey);
        } else {
            log.info("[P81] LiveKit 使用默认/环境变量配置 - apiUrl={}, apiKey={}", apiUrl, apiKey);
        }
    }

    /**
     * 供 API 调用后刷新配置。
     * 从 SystemConfigService 重新读取 livekit 相关设置并覆盖当前值。
     */
    public void refreshConfig() {
        refreshFromSystemConfig();
    }

    // ========== Getters & Setters ==========

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiSecret() {
        return apiSecret;
    }

    public void setApiSecret(String apiSecret) {
        this.apiSecret = apiSecret;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    // ========== Bean 定义 ==========

    /**
     * 提供 LiveKit REST API 专用 RestTemplate
     * 设置合理的超时时间，避免会议操作阻塞过久
     */
    @Bean("liveKitRestTemplate")
    public RestTemplate liveKitRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        log.info("[P81] LiveKit RestTemplate 初始化 - connectTimeout={}ms, readTimeout={}ms",
                connectTimeout, readTimeout);
        return new RestTemplate(factory);
    }
}
