package com.livingagent.core.tool.impl;

import com.livingagent.core.security.SecurityPolicy;
import com.livingagent.core.tool.*;
import com.livingagent.core.tool.weather.WeatherInfo;
import com.livingagent.core.tool.weather.WeatherProvider;
import com.livingagent.core.tool.weather.impl.OpenWeatherMapProvider;
import com.livingagent.core.tool.weather.impl.QWeatherProvider;
import com.livingagent.core.tool.weather.impl.WttrProvider;

import java.util.*;
import java.util.stream.Collectors;

public class WeatherTool implements Tool {
    private static final String NAME = "weather";
    private static final String DESCRIPTION = "Get current weather and forecasts via multiple providers. No API key needed for wttr.in fallback.";
    private static final String VERSION = "1.0.0";
    private static final String DEPARTMENT = "information";

    private final List<WeatherProvider> providers;
    private final WeatherProvider defaultProvider;
    private ToolStats stats = ToolStats.empty(NAME);

    public WeatherTool() {
        this.providers = new ArrayList<>();
        this.providers.add(new WttrProvider(99, true, 10000));
        this.defaultProvider = findDefaultProvider();
    }

    public WeatherTool(List<WeatherProvider> providers) {
        this.providers = providers != null ? new ArrayList<>(providers) : new ArrayList<>();
        if (this.providers.isEmpty()) {
            this.providers.add(new WttrProvider(99, true, 10000));
        }
        this.defaultProvider = findDefaultProvider();
    }

    public WeatherTool withQWeather(String apiKey) {
        if (apiKey != null && !apiKey.isBlank()) {
            this.providers.add(new QWeatherProvider(apiKey, 1, true, 5000));
        }
        return this;
    }

    public WeatherTool withOpenWeatherMap(String apiKey) {
        if (apiKey != null && !apiKey.isBlank()) {
            this.providers.add(new OpenWeatherMapProvider(apiKey, 2, true, 5000));
        }
        return this;
    }

    private WeatherProvider findDefaultProvider() {
        return providers.stream()
                .filter(WeatherProvider::isEnabled)
                .min(Comparator.comparingInt(WeatherProvider::getPriority))
                .orElse(null);
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
                .parameter("location", "string", "城市名称、地区或机场代码", true)
                .parameter("format", "string", "输出格式: simple(一行), detailed(详细), forecast(预报)", false)
                .parameter("days", "integer", "预报天数 (1-3)", false)
                .parameter("provider", "string", "指定数据源: qweather, openweathermap, wttr", false)
                .build();
    }

    @Override
    public List<String> getCapabilities() {
        return List.of("current_weather", "forecast", "multi_provider", "fallback");
    }

    @Override
    public ToolResult execute(ToolParams params, ToolContext context) {
        long startTime = System.currentTimeMillis();
        String location = params.getString("location");
        if (location == null || location.isBlank()) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return ToolResult.failure("location parameter is required");
        }

        String format = params.getString("format");
        if (format == null) format = "detailed";
        
        Integer daysInt = params.getInteger("days");
        int days = daysInt != null ? daysInt : 0;
        
        String preferredProvider = params.getString("provider");

        try {
            WeatherProvider provider = selectProvider(preferredProvider);
            if (provider == null) {
                stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
                return ToolResult.failure("No available weather provider");
            }

            WeatherInfo info;
            if (days > 0 && days <= 3) {
                info = provider.fetchForecast(location, days);
            } else {
                info = provider.fetchWeather(location);
            }

            stats = stats.recordCall(true, System.currentTimeMillis() - startTime);
            return ToolResult.success(formatWeather(info, format));
        } catch (Exception e) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return tryFallbackProviders(location, format, days, preferredProvider, e, startTime);
        }
    }

    @Override
    public void validate(ToolParams params) {
        String location = params.getString("location");
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("location parameter is required");
        }
    }

    @Override
    public boolean isAllowed(SecurityPolicy policy) {
        return policy.isToolAllowed(NAME);
    }

    @Override
    public boolean requiresApproval() {
        return false;
    }

    @Override
    public ToolStats getStats() {
        return stats;
    }

    private WeatherProvider selectProvider(String preferredProvider) {
        if (preferredProvider != null && !preferredProvider.isBlank()) {
            return providers.stream()
                    .filter(p -> p.isEnabled() && p.getName().toLowerCase().contains(preferredProvider.toLowerCase()))
                    .findFirst()
                    .orElse(defaultProvider);
        }
        return defaultProvider;
    }

    private ToolResult tryFallbackProviders(String location, String format, int days, 
                                             String preferredProvider, Exception originalError, long startTime) {
        for (WeatherProvider provider : providers) {
            if (provider.isEnabled() && !provider.equals(selectProvider(preferredProvider))) {
                try {
                    WeatherInfo info;
                    if (days > 0 && days <= 3) {
                        info = provider.fetchForecast(location, days);
                    } else {
                        info = provider.fetchWeather(location);
                    }
                    stats = stats.recordCall(true, System.currentTimeMillis() - startTime);
                    return ToolResult.success(formatWeather(info, format));
                } catch (Exception ignored) {
                    continue;
                }
            }
        }
        return ToolResult.failure("All weather providers failed: " + originalError.getMessage());
    }

    private Map<String, Object> formatWeather(WeatherInfo info, String format) {
        Map<String, Object> result = new LinkedHashMap<>();
        
        result.put("location", info.getLocation());
        result.put("condition", info.getCondition());
        result.put("temperature", info.getTemperature());
        result.put("temperature_unit", info.getTemperatureUnit());
        result.put("feels_like", info.getFeelsLike());
        result.put("humidity", info.getHumidity());
        result.put("humidity_unit", info.getHumidityUnit());
        result.put("wind_speed", info.getWindSpeed());
        result.put("wind_direction", info.getWindDirection());
        result.put("wind_unit", info.getWindUnit());
        result.put("pressure", info.getPressure());
        result.put("pressure_unit", info.getPressureUnit());
        result.put("visibility", info.getVisibility());
        result.put("visibility_unit", info.getVisibilityUnit());
        result.put("provider", info.getProvider());
        result.put("timestamp", info.getTimestamp());
        
        if (info.getForecast() != null && !info.getForecast().isEmpty()) {
            result.put("forecast", info.getForecast());
        }
        
        if (info.getAlert() != null && !info.getAlert().isEmpty()) {
            result.put("alert", info.getAlert());
        }

        switch (format.toLowerCase()) {
            case "simple":
                result.put("summary", info.toSimpleString());
                break;
            case "detailed":
            default:
                result.put("summary", info.toDetailedString());
                break;
        }

        return result;
    }

    public List<String> getAvailableProviders() {
        return providers.stream()
                .filter(WeatherProvider::isEnabled)
                .sorted(Comparator.comparingInt(WeatherProvider::getPriority))
                .map(WeatherProvider::getName)
                .collect(Collectors.toList());
    }
}
