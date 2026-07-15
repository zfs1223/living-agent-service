package com.livingagent.core.tool.impl;

import com.livingagent.core.security.SecurityPolicy;
import com.livingagent.core.tool.*;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * SensorDataTool - 按需获取 WiFi 物理感知传感器数据
 *
 * 通过 RuView Sensing Service REST API 获取：
 * - 最新感知数据（人员检测、占用统计、生命体征）
 * - 历史生命体征数据
 * - 模型信息
 *
 * 设计原则：
 * - 按需调用，不持续轮询（避免资源浪费）
 * - 失败时返回 null 而非抛异常（由调用方决定处理）
 * - 5分钟间隔抑制重复警告（避免日志污染）
 *
 * 使用示例：
 * <pre>
 * // 获取最新感知数据
 * Map<String, Object> result = sensorDataTool.execute(
 *     ToolParams.of(Map.of("query_type", "current")),
 *     context
 * );
 * </pre>
 */
public class SensorDataTool implements Tool {
    private static final Logger log = LoggerFactory.getLogger(SensorDataTool.class);
    private static final String NAME = "sensor_data";
    private static final String DESCRIPTION = "Get WiFi CSI sensor data from RuView Sensing Service (person detection, occupancy, vital signs). On-demand query, no polling.";
    private static final String VERSION = "1.0.0";
    private static final String DEPARTMENT = "information";

    private final String ruviewApiBaseUrl;
    private final OkHttpClient httpClient;
    private ToolStats stats = ToolStats.empty(NAME);

    // 警告抑制间隔（5分钟）
    private long lastWarnTime = 0;
    private static final long WARN_INTERVAL_MS = 300_000;

    public SensorDataTool(String ruviewApiBaseUrl) {
        this.ruviewApiBaseUrl = ruviewApiBaseUrl;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();
    }

    public SensorDataTool() {
        this("http://ruview-sensing:3000");
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public String getVersion() {
        return VERSION;
    }

    @Override
    public String getDepartment() {
        return DEPARTMENT;
    }

    @Override
    public ToolSchema getSchema() {
        return ToolSchema.builder()
                .name(NAME)
                .description(DESCRIPTION)
                .parameter("query_type", "string", "查询类型: current(最新数据), vital-signs(生命体征), model-info(模型信息)", false)
                .build();
    }

    @Override
    public List<String> getCapabilities() {
        return List.of("person_detection", "occupancy", "vital_signs", "on_demand_query");
    }

    @Override
    public ToolResult execute(ToolParams params, ToolContext context) {
        long startTime = System.currentTimeMillis();

        String queryType = params.getString("query_type");
        if (queryType == null) {
            queryType = "current";
        }

        String apiPath;
        switch (queryType.toLowerCase()) {
            case "vital-signs":
                apiPath = "/api/v1/vital-signs";
                break;
            case "model-info":
                apiPath = "/api/v1/model/info";
                break;
            default:
                apiPath = "/api/v1/sensing/latest";
        }

        try {
            String responseBody = callRuViewApi(apiPath);

            if (responseBody != null) {
                // 返回原始 JSON 字符串，由调用方解析
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("raw_response", responseBody);
                result.put("query_type", queryType);
                result.put("source", "ruview-api");
                result.put("timestamp", System.currentTimeMillis());

                stats = stats.recordCall(true, System.currentTimeMillis() - startTime);
                return ToolResult.success(result);
            } else {
                stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
                return ToolResult.failure("RuView API returned no data");
            }

        } catch (Exception e) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return ToolResult.failure("RuView API call failed: " + e.getMessage());
        }
    }

    @Override
    public void validate(ToolParams params) {
        // 所有参数都是可选的，无需验证
    }

    @Override
    public boolean isAllowed(SecurityPolicy policy) {
        // 传感器数据通常不敏感，默认允许
        return policy.isToolAllowed(NAME);
    }

    @Override
    public boolean requiresApproval() {
        // 传感器数据查询不需要审批
        return false;
    }

    @Override
    public ToolStats getStats() {
        return stats;
    }

    /**
     * 调用 RuView REST API
     */
    private String callRuViewApi(String path) {
        if (ruviewApiBaseUrl == null || ruviewApiBaseUrl.isBlank()) {
            log.debug("RuView API URL not configured, skipping call");
            return null;
        }

        try {
            String url = ruviewApiBaseUrl + path;
            Request request = new Request.Builder()
                    .url(url)
                    .header("Host", "localhost:3000")  // RuView Docker 内部需要
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    return response.body().string();
                } else {
                    warnOnce("RuView API returned status {}: {}", response.code(), url);
                    return null;
                }
            }
        } catch (Exception e) {
            warnOnce("RuView API call failed for {}: {}", path, e.getMessage());
            return null;
        }
    }

    /**
     * 抑制重复警告（5分钟间隔）
     */
    private void warnOnce(String format, Object... args) {
        long now = System.currentTimeMillis();
        if (now - lastWarnTime > WARN_INTERVAL_MS) {
            log.warn(format, args);
            lastWarnTime = now;
        } else {
            log.debug(format, args);
        }
    }
}