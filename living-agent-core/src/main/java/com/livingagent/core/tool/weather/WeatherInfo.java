package com.livingagent.core.tool.weather;

import java.time.Instant;

public class WeatherInfo {
    private String location;
    private double temperature;
    private String temperatureUnit;
    private int humidity;
    private String humidityUnit;
    private double windSpeed;
    private String windDirection;
    private String windUnit;
    private String condition;
    private double pressure;
    private String pressureUnit;
    private double visibility;
    private String visibilityUnit;
    private String provider;
    private long timestamp;
    private double feelsLike;
    private String forecast;
    private String alert;

    public WeatherInfo() {}

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }

    public String getTemperatureUnit() { return temperatureUnit; }
    public void setTemperatureUnit(String temperatureUnit) { this.temperatureUnit = temperatureUnit; }

    public int getHumidity() { return humidity; }
    public void setHumidity(int humidity) { this.humidity = humidity; }

    public String getHumidityUnit() { return humidityUnit; }
    public void setHumidityUnit(String humidityUnit) { this.humidityUnit = humidityUnit; }

    public double getWindSpeed() { return windSpeed; }
    public void setWindSpeed(double windSpeed) { this.windSpeed = windSpeed; }

    public String getWindDirection() { return windDirection; }
    public void setWindDirection(String windDirection) { this.windDirection = windDirection; }

    public String getWindUnit() { return windUnit; }
    public void setWindUnit(String windUnit) { this.windUnit = windUnit; }

    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }

    public double getPressure() { return pressure; }
    public void setPressure(double pressure) { this.pressure = pressure; }

    public String getPressureUnit() { return pressureUnit; }
    public void setPressureUnit(String pressureUnit) { this.pressureUnit = pressureUnit; }

    public double getVisibility() { return visibility; }
    public void setVisibility(double visibility) { this.visibility = visibility; }

    public String getVisibilityUnit() { return visibilityUnit; }
    public void setVisibilityUnit(String visibilityUnit) { this.visibilityUnit = visibilityUnit; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public double getFeelsLike() { return feelsLike; }
    public void setFeelsLike(double feelsLike) { this.feelsLike = feelsLike; }

    public String getForecast() { return forecast; }
    public void setForecast(String forecast) { this.forecast = forecast; }

    public String getAlert() { return alert; }
    public void setAlert(String alert) { this.alert = alert; }

    public String toSimpleString() {
        return String.format("%s: %s %.1f%s", location, condition, temperature, temperatureUnit);
    }

    public String toDetailedString() {
        return String.format("%s: %s %.1f%s (体感%.1f%s), 湿度%d%s, %s %.1f%s, 能见度%.1f%s [%s]",
                location, condition, temperature, temperatureUnit, feelsLike, temperatureUnit,
                humidity, humidityUnit, windDirection, windSpeed, windUnit, visibility, visibilityUnit, provider);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private WeatherInfo info = new WeatherInfo();

        public Builder location(String location) { info.setLocation(location); return this; }
        public Builder temperature(double temperature) { info.setTemperature(temperature); return this; }
        public Builder temperatureUnit(String unit) { info.setTemperatureUnit(unit); return this; }
        public Builder humidity(int humidity) { info.setHumidity(humidity); return this; }
        public Builder humidityUnit(String unit) { info.setHumidityUnit(unit); return this; }
        public Builder windSpeed(double speed) { info.setWindSpeed(speed); return this; }
        public Builder windDirection(String direction) { info.setWindDirection(direction); return this; }
        public Builder windUnit(String unit) { info.setWindUnit(unit); return this; }
        public Builder condition(String condition) { info.setCondition(condition); return this; }
        public Builder pressure(double pressure) { info.setPressure(pressure); return this; }
        public Builder pressureUnit(String unit) { info.setPressureUnit(unit); return this; }
        public Builder visibility(double visibility) { info.setVisibility(visibility); return this; }
        public Builder visibilityUnit(String unit) { info.setVisibilityUnit(unit); return this; }
        public Builder provider(String provider) { info.setProvider(provider); return this; }
        public Builder timestamp(long timestamp) { info.setTimestamp(timestamp); return this; }
        public Builder feelsLike(double feelsLike) { info.setFeelsLike(feelsLike); return this; }
        public Builder forecast(String forecast) { info.setForecast(forecast); return this; }
        public Builder alert(String alert) { info.setAlert(alert); return this; }

        public WeatherInfo build() {
            if (info.getTimestamp() == 0) {
                info.setTimestamp(Instant.now().toEpochMilli());
            }
            return info;
        }
    }
}
