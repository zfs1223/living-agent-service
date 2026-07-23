package com.livingagent.gateway.meeting;

import com.livingagent.gateway.controller.common.ApiResponse;
import com.livingagent.gateway.security.RequireAccess;
import com.livingagent.gateway.service.SystemConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LiveKit 配置管理 API - 闭环 67/68 企业设置可配置
 *
 * <p>提供 LiveKit 连接参数的动态配置能力，允许管理员通过 API 覆盖默认配置。
 * 配置存储在 SystemConfigService 的 settings 中，优先级高于环境变量。</p>
 *
 * <h3>配置优先级</h3>
 * <ol>
 *   <li>SystemConfigService（本 API 管理）- 最高</li>
 *   <li>环境变量 / application.yml - 中</li>
 *   <li>硬编码默认值 - 最低</li>
 * </ol>
 *
 * <h3>安全说明</h3>
 * <ul>
 *   <li>GET 接口不返回 apiSecret 明文，仅返回 apiKeyConfigured: boolean</li>
 *   <li>PUT 接口需要 system:admin 权限（@RequireAccess）</li>
 * </ul>
 *
 * @author P81 LiveKit 部署与会议基础
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/system/config/livekit")
public class LiveKitConfigController {

    private static final Logger log = LoggerFactory.getLogger(LiveKitConfigController.class);

    /** SystemConfigService settings 中的 key */
    private static final String SETTINGS_KEY_API_URL = "livekit.api-url";
    private static final String SETTINGS_KEY_API_KEY = "livekit.api-key";
    private static final String SETTINGS_KEY_API_SECRET = "livekit.api-secret";

    private final LiveKitConfig liveKitConfig;
    private final SystemConfigService systemConfigService;

    public LiveKitConfigController(LiveKitConfig liveKitConfig, SystemConfigService systemConfigService) {
        this.liveKitConfig = liveKitConfig;
        this.systemConfigService = systemConfigService;
    }

    /**
     * 获取 LiveKit 配置
     *
     * <p>不返回 apiSecret 明文，仅返回 apiKeyConfigured: boolean 标识是否已配置。</p>
     */
    @GetMapping
    @RequireAccess(resource = "system", action = "admin")
    public ResponseEntity<ApiResponse<LiveKitConfigInfo>> getLiveKitConfig() {
        LiveKitConfigInfo info = new LiveKitConfigInfo(
            liveKitConfig.getApiUrl(),
            liveKitConfig.getApiKey(),
            liveKitConfig.getApiSecret() != null && !liveKitConfig.getApiSecret().isBlank()
                && !"las-livekit-api-secret-change-me".equals(liveKitConfig.getApiSecret()),
            liveKitConfig.getConnectTimeout(),
            liveKitConfig.getReadTimeout()
        );
        return ResponseEntity.ok(ApiResponse.ok(info));
    }

    /**
     * 更新 LiveKit 配置
     *
     * <p>将配置写入 SystemConfigService 的 settings，并刷新 LiveKitConfig 的运行时值。
     * 所有字段均为可选，仅更新非空字段。</p>
     */
    @PutMapping
    @RequireAccess(resource = "system", action = "admin")
    public ResponseEntity<ApiResponse<LiveKitConfigInfo>> updateLiveKitConfig(
            @RequestBody LiveKitConfigUpdateRequest request) {

        log.info("[P81] 更新 LiveKit 配置 - apiUrl={}, apiKey={}",
            request.apiUrl(),
            request.apiKey() != null && !request.apiKey().isBlank() ? "***" : "null");

        // 将配置写入 SystemConfigService 的 settings
        Map<String, Object> settingsUpdate = new LinkedHashMap<>();
        if (request.apiUrl() != null && !request.apiUrl().isBlank()) {
            settingsUpdate.put(SETTINGS_KEY_API_URL, request.apiUrl());
        }
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            settingsUpdate.put(SETTINGS_KEY_API_KEY, request.apiKey());
        }
        if (request.apiSecret() != null && !request.apiSecret().isBlank()) {
            settingsUpdate.put(SETTINGS_KEY_API_SECRET, request.apiSecret());
        }

        if (!settingsUpdate.isEmpty()) {
            // 通过 SystemConfigService 更新 settings
            systemConfigService.updateSystemConfig(
                new SystemConfigService.SystemConfigUpdateRequest(null, null, null, settingsUpdate)
            );

            // 刷新 LiveKitConfig 的运行时值
            liveKitConfig.refreshConfig();

            log.info("[P81] LiveKit 配置已更新并刷新");
        }

        // 返回更新后的配置
        LiveKitConfigInfo info = new LiveKitConfigInfo(
            liveKitConfig.getApiUrl(),
            liveKitConfig.getApiKey(),
            liveKitConfig.getApiSecret() != null && !liveKitConfig.getApiSecret().isBlank()
                && !"las-livekit-api-secret-change-me".equals(liveKitConfig.getApiSecret()),
            liveKitConfig.getConnectTimeout(),
            liveKitConfig.getReadTimeout()
        );
        return ResponseEntity.ok(ApiResponse.ok(info));
    }

    // ========== DTO ==========

    /**
     * LiveKit 配置信息（GET 响应）
     * 不返回 apiSecret 明文，用 apiKeyConfigured 标识
     */
    public record LiveKitConfigInfo(
        String apiUrl,
        String apiKey,
        boolean apiKeyConfigured,
        int connectTimeout,
        int readTimeout
    ) {}

    /**
     * LiveKit 配置更新请求（PUT 请求体）
     * 所有字段均为可选，仅更新非空字段
     */
    public record LiveKitConfigUpdateRequest(
        String apiUrl,
        String apiKey,
        String apiSecret
    ) {}
}
