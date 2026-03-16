package com.livingagent.core.tool.weather.impl;

import com.livingagent.core.tool.weather.WeatherInfo;
import com.livingagent.core.tool.weather.WeatherProvider;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class WttrProvider implements WeatherProvider {
    
    private static final String BASE_URL = "https://wttr.in/";
    
    private final int priority;
    private final boolean enabled;
    private final int timeout;
    
    public WttrProvider(int priority, boolean enabled, int timeout) {
        this.priority = priority;
        this.enabled = enabled;
        this.timeout = timeout > 0 ? timeout : 10000;
    }
    
    public WttrProvider() {
        this(99, true, 10000);
    }

    @Override
    public String getName() {
        return "wttr.in";
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public WeatherInfo fetchWeather(String location) throws Exception {
        if (!enabled) {
            throw new Exception("wttr.in未启用");
        }
        
        String encodedLocation = URLEncoder.encode(location, StandardCharsets.UTF_8);
        String url = BASE_URL + encodedLocation + "?format=j1&lang=zh";
        String response = sendGetRequest(url);
        
        return parseWeatherResponse(response, location);
    }

    @Override
    public WeatherInfo fetchForecast(String location, int days) throws Exception {
        WeatherInfo info = fetchWeather(location);
        
        String encodedLocation = URLEncoder.encode(location, StandardCharsets.UTF_8);
        String url = BASE_URL + encodedLocation + "?format=j1&lang=zh";
        String response = sendGetRequest(url);
        
        StringBuilder forecast = new StringBuilder();
        int weatherStart = response.indexOf("\"weather\":[");
        if (weatherStart != -1) {
            int count = 0;
            int pos = weatherStart;
            while (count < days && count < 3) {
                int hourlyStart = response.indexOf("\"hourly\":[", pos);
                if (hourlyStart == -1) break;
                
                int timeStart = response.indexOf("\"time\":\"", hourlyStart);
                if (timeStart != -1) {
                    String time = parseString(response, "time", timeStart);
                    String temp = parseString(response, "tempC", timeStart);
                    String desc = parseString(response, "weatherDesc", timeStart);
                    forecast.append(String.format("%s: %s %s°C; ", time, desc, temp));
                }
                
                pos = response.indexOf("},", hourlyStart + 1);
                count++;
            }
        }
        
        info.setForecast(forecast.toString());
        return info;
    }
    
    private WeatherInfo parseWeatherResponse(String response, String location) throws Exception {
        int currentStart = response.indexOf("\"current_condition\":[");
        if (currentStart == -1) {
            currentStart = response.indexOf("\"current\":{");
        }
        if (currentStart == -1) {
            throw new Exception("解析天气数据失败");
        }
        
        String areaName = parseString(response, "areaName", currentStart);
        String country = parseString(response, "country", currentStart);
        String fullLocation = areaName.isEmpty() ? location : areaName + (country.isEmpty() ? "" : ", " + country);
        
        return WeatherInfo.builder()
                .location(fullLocation)
                .temperature(parseDouble(response, "temp_C", currentStart))
                .temperatureUnit("°C")
                .humidity(parseInt(response, "humidity", currentStart))
                .humidityUnit("%")
                .windSpeed(parseDouble(response, "windspeedKmph", currentStart))
                .windDirection(parseString(response, "winddir16Point", currentStart))
                .windUnit("km/h")
                .condition(parseString(response, "weatherDesc", currentStart))
                .pressure(parseDouble(response, "pressure", currentStart))
                .pressureUnit("hPa")
                .visibility(parseDouble(response, "visibility", currentStart))
                .visibilityUnit("km")
                .provider(getName())
                .feelsLike(parseDouble(response, "FeelsLikeC", currentStart))
                .build();
    }
    
    private String parseString(String json, String key, int startPos) {
        String searchKey = "\"" + key + "\":\"";
        int start = json.indexOf(searchKey, startPos);
        if (start == -1) return "";
        start += searchKey.length();
        int end = json.indexOf("\"", start);
        return end == -1 ? "" : json.substring(start, end);
    }
    
    private double parseDouble(String json, String key, int startPos) {
        String searchKey = "\"" + key + "\":";
        int start = json.indexOf(searchKey, startPos);
        if (start == -1) return 0;
        start += searchKey.length();
        
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.' || json.charAt(end) == '-')) {
            end++;
        }
        
        try {
            return Double.parseDouble(json.substring(start, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    private int parseInt(String json, String key, int startPos) {
        return (int) parseDouble(json, key, startPos);
    }
    
    private String sendGetRequest(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(timeout);
        connection.setReadTimeout(timeout);
        connection.setRequestProperty("User-Agent", "curl");
        
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new RuntimeException("HTTP请求失败: " + responseCode);
        }
        
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }
        
        connection.disconnect();
        return response.toString();
    }
}
