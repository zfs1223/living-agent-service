package com.livingagent.core.tool.weather.impl;

import com.livingagent.core.tool.weather.WeatherInfo;
import com.livingagent.core.tool.weather.WeatherProvider;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class OpenWeatherMapProvider implements WeatherProvider {
    
    private static final String BASE_URL = "https://api.openweathermap.org/data/2.5/weather";
    private static final String FORECAST_URL = "https://api.openweathermap.org/data/2.5/forecast";
    
    private final String apiKey;
    private final int priority;
    private final boolean enabled;
    private final int timeout;
    
    public OpenWeatherMapProvider(String apiKey, int priority, boolean enabled, int timeout) {
        this.apiKey = apiKey;
        this.priority = priority;
        this.enabled = enabled && apiKey != null && !apiKey.isBlank();
        this.timeout = timeout > 0 ? timeout : 5000;
    }
    
    public OpenWeatherMapProvider(String apiKey) {
        this(apiKey, 2, true, 5000);
    }

    @Override
    public String getName() {
        return "OpenWeatherMap";
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
            throw new Exception("OpenWeatherMap未启用");
        }
        
        String encodedLocation = URLEncoder.encode(location, StandardCharsets.UTF_8);
        String url = String.format("%s?q=%s&appid=%s&units=metric&lang=zh_cn", BASE_URL, encodedLocation, apiKey);
        String response = sendGetRequest(url);
        
        return parseWeatherResponse(response);
    }

    @Override
    public WeatherInfo fetchForecast(String location, int days) throws Exception {
        if (!enabled) {
            throw new Exception("OpenWeatherMap未启用");
        }
        
        String encodedLocation = URLEncoder.encode(location, StandardCharsets.UTF_8);
        String url = String.format("%s?q=%s&appid=%s&units=metric&lang=zh_cn&cnt=%d", 
                FORECAST_URL, encodedLocation, apiKey, days * 8);
        String response = sendGetRequest(url);
        
        return parseForecastResponse(response, days);
    }
    
    private WeatherInfo parseWeatherResponse(String response) throws Exception {
        String cityName = parseString(response, "name", 0);
        String country = parseString(response, "country", 0);
        String location = cityName + (country.isEmpty() ? "" : ", " + country);
        
        int mainStart = response.indexOf("\"main\":{");
        int weatherStart = response.indexOf("\"weather\":[");
        int windStart = response.indexOf("\"wind\":{");
        
        return WeatherInfo.builder()
                .location(location)
                .temperature(parseDouble(response, "temp", mainStart))
                .temperatureUnit("°C")
                .humidity(parseInt(response, "humidity", mainStart))
                .humidityUnit("%")
                .windSpeed(parseDouble(response, "speed", windStart))
                .windDirection(getWindDirection(parseDouble(response, "deg", windStart)))
                .windUnit("m/s")
                .condition(parseString(response, "description", weatherStart))
                .pressure(parseInt(response, "pressure", mainStart))
                .pressureUnit("hPa")
                .visibility(parseDouble(response, "visibility", 0) / 1000.0)
                .visibilityUnit("km")
                .provider(getName())
                .feelsLike(parseDouble(response, "feels_like", mainStart))
                .build();
    }
    
    private WeatherInfo parseForecastResponse(String response, int days) throws Exception {
        WeatherInfo info = parseWeatherResponse(response);
        
        StringBuilder forecast = new StringBuilder();
        int listStart = response.indexOf("\"list\":[");
        if (listStart != -1) {
            int count = 0;
            int pos = listStart;
            while (count < days * 8) {
                int dtStart = response.indexOf("\"dt\":", pos);
                if (dtStart == -1) break;
                
                String date = new java.text.SimpleDateFormat("MM-dd HH:mm")
                        .format(new java.util.Date(parseLong(response, "dt", dtStart) * 1000));
                String temp = String.valueOf(parseDouble(response, "temp", dtStart));
                String desc = parseString(response, "description", dtStart);
                
                forecast.append(String.format("%s: %s %.0f°C; ", date, desc, parseDouble(response, "temp", dtStart)));
                
                pos = response.indexOf("},", dtStart);
                count++;
            }
        }
        
        info.setForecast(forecast.toString());
        return info;
    }
    
    private String getWindDirection(double degrees) {
        String[] directions = {"北", "东北", "东", "东南", "南", "西南", "西", "西北"};
        int index = (int) Math.round(degrees / 45.0) % 8;
        return directions[index] + "风";
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
        
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\n')) start++;
        
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
    
    private long parseLong(String json, String key, int startPos) {
        return (long) parseDouble(json, key, startPos);
    }
    
    private String sendGetRequest(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(timeout);
        connection.setReadTimeout(timeout);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        
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
