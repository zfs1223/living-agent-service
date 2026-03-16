package com.livingagent.core.model;

import java.util.Map;
import java.util.HashMap;
import java.util.List;

public class ModelResponse {
    
    private boolean success;
    private String error;
    private Map<String, Object> data;
    private String model;
    private long duration;
    
    public ModelResponse() {
        this.data = new HashMap<>();
    }
    
    public static ModelResponse success() {
        ModelResponse response = new ModelResponse();
        response.setSuccess(true);
        return response;
    }
    
    public static ModelResponse success(Map<String, Object> data) {
        ModelResponse response = success();
        response.setData(data);
        return response;
    }
    
    public static ModelResponse failure(String error) {
        ModelResponse response = new ModelResponse();
        response.setSuccess(false);
        response.setError(error);
        return response;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getError() {
        return error;
    }
    
    public void setError(String error) {
        this.error = error;
    }
    
    public Map<String, Object> getData() {
        return data;
    }
    
    public void setData(Map<String, Object> data) {
        this.data = data;
    }
    
    public String getModel() {
        return model;
    }
    
    public void setModel(String model) {
        this.model = model;
    }
    
    public long getDuration() {
        return duration;
    }
    
    public void setDuration(long duration) {
        this.duration = duration;
    }
    
    public Object get(String key) {
        return data.get(key);
    }
    
    public String getText() {
        Object text = data.get("text");
        return text != null ? text.toString() : null;
    }
    
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getResults() {
        Object results = data.get("results");
        if (results instanceof List) {
            return (List<Map<String, Object>>) results;
        }
        return null;
    }
}
