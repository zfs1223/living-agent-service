package com.livingagent.core.tool.weather.impl;

import com.livingagent.core.tool.weather.WeatherInfo;
import com.livingagent.core.tool.weather.WeatherProvider;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class QWeatherProvider implements WeatherProvider {
    
    private static final String BASE_URL = "https://devapi.qweather.com/v7/weather/now";
    private static final String FORECAST_URL = "https://devapi.qweather.com/v7/weather/3d";
    private static final String GEO_URL = "https://geoapi.qweather.com/v2/city/lookup";
    
    private final String apiKey;
    private final int priority;
    private final boolean enabled;
    private final int timeout;
    
    public QWeatherProvider(String apiKey, int priority, boolean enabled, int timeout) {
        this.apiKey = apiKey;
        this.priority = priority;
        this.enabled = enabled && apiKey != null && !apiKey.isBlank();
        this.timeout = timeout > 0 ? timeout : 5000;
    }
    
    public QWeatherProvider(String apiKey) {
        this(apiKey, 1, true, 5000);
    }

    @Override
    public String getName() {
        return "和风天气";
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
            throw new Exception("和风天气未启用");
        }
        
        String cityId = getCityId(location);
        if (cityId == null) {
            throw new Exception("未找到城市: " + location);
        }
        
        String url = String.format("%s?location=%s&key=%s", BASE_URL, cityId, apiKey);
        String response = sendGetRequest(url);
        
        return parseWeatherResponse(response, location);
    }

    @Override
    public WeatherInfo fetchForecast(String location, int days) throws Exception {
        if (!enabled) {
            throw new Exception("和风天气未启用");
        }
        
        String cityId = getCityId(location);
        if (cityId == null) {
            throw new Exception("未找到城市: " + location);
        }
        
        String url = String.format("%s?location=%s&key=%s", FORECAST_URL, cityId, apiKey);
        String response = sendGetRequest(url);
        
        return parseForecastResponse(response, location, days);
    }
    
    private String getCityId(String location) throws Exception {
        try {
            String encodedLocation = URLEncoder.encode(location, StandardCharsets.UTF_8.toString());
            String url = String.format("%s?location=%s&key=%s", GEO_URL, encodedLocation, apiKey);
            String response = sendGetRequest(url);
            
            int locationStart = response.indexOf("\"location\":[");
            if (locationStart == -1) return null;
            
            int idStart = response.indexOf("\"id\":\"", locationStart);
            if (idStart == -1) return null;
            
            idStart += 6;
            int idEnd = response.indexOf("\"", idStart);
            return response.substring(idStart, idEnd);
        } catch (Exception e) {
            return null;
        }
    }
    
    private WeatherInfo parseWeatherResponse(String response, String location) throws Exception {
        String nowKey = "\"now\":";
        int nowStart = response.indexOf(nowKey);
        if (nowStart == -1) {
            throw new Exception("解析天气数据失败");
        }
        
        return WeatherInfo.builder()
                .location(location)
                .temperature(parseDouble(response, "temp", nowStart))
                .temperatureUnit("°C")
                .humidity(parseInt(response, "humidity", nowStart))
                .humidityUnit("%")
                .windSpeed(parseDouble(response, "windSpeed", nowStart))
                .windDirection(parseString(response, "windDir", nowStart))
                .windUnit("km/h")
                .condition(parseString(response, "text", nowStart))
                .pressure(parseInt(response, "pressure", nowStart))
                .pressureUnit("hPa")
                .visibility(parseDouble(response, "vis", nowStart))
                .visibilityUnit("km")
                .provider(getName())
                .feelsLike(parseDouble(response, "feelsLike", nowStart))
                .build();
    }
    
    private WeatherInfo parseForecastResponse(String response, String location, int days) throws Exception {
        WeatherInfo info = parseWeatherResponse(response, location);
        StringBuilder forecast = new StringBuilder();
        
        int dailyStart = response.indexOf("\"daily\":[");
        if (dailyStart != -1) {
            int count = 0;
            int pos = dailyStart;
            while (count < days && count < 3) {
                int fxDateStart = response.indexOf("\"fxDate\":\"", pos);
                if (fxDateStart == -1) break;
                
                String date = parseString(response, "fxDate", fxDateStart);
                String textDay = parseString(response, "textDay", fxDateStart);
                String tempMax = parseString(response, "tempMax", fxDateStart);
                String tempMin = parseString(response, "tempMin", fxDateStart);
                
                forecast.append(String.format("%s: %s %s~%s°C; ", date, textDay, tempMin, tempMax));
                
                pos = response.indexOf("}", fxDateStart + 1);
                count++;
            }
        }
        
        info.setForecast(forecast.toString());
        return info;
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
        String searchKey = "\"" + key + "\":\"";
        int start = json.indexOf(searchKey, startPos);
        if (start == -1) return 0;
        start += searchKey.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return 0;
        try {
            return Double.parseDouble(json.substring(start, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    private int parseInt(String json, String key, int startPos) {
        String searchKey = "\"" + key + "\":\"";
        int start = json.indexOf(searchKey, startPos);
        if (start == -1) return 0;
        start += searchKey.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return 0;
        try {
            return Integer.parseInt(json.substring(start, end));
        } catch (NumberFormatException e) {
            return 0;
        }
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
