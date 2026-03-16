package com.livingagent.core.tool.weather;

public interface WeatherProvider {
    
    String getName();
    
    int getPriority();
    
    boolean isEnabled();
    
    WeatherInfo fetchWeather(String location) throws Exception;
    
    WeatherInfo fetchForecast(String location, int days) throws Exception;
}
