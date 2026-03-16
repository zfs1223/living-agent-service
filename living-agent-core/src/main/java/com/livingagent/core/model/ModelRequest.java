package com.livingagent.core.model;

import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

public class ModelRequest {
    
    private String requestId;
    private String service;
    private Map<String, Object> params;
    private long timestamp;
    
    public ModelRequest() {
        this.requestId = UUID.randomUUID().toString();
        this.params = new HashMap<>();
        this.timestamp = System.currentTimeMillis();
    }
    
    public ModelRequest(String service) {
        this();
        this.service = service;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public String getRequestId() {
        return requestId;
    }
    
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
    
    public String getService() {
        return service;
    }
    
    public void setService(String service) {
        this.service = service;
    }
    
    public Map<String, Object> getParams() {
        return params;
    }
    
    public void setParams(Map<String, Object> params) {
        this.params = params;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public ModelRequest param(String key, Object value) {
        this.params.put(key, value);
        return this;
    }
    
    public static class Builder {
        private final ModelRequest request;
        
        public Builder() {
            this.request = new ModelRequest();
        }
        
        public Builder service(String service) {
            request.setService(service);
            return this;
        }
        
        public Builder param(String key, Object value) {
            request.param(key, value);
            return this;
        }
        
        public Builder params(Map<String, Object> params) {
            request.setParams(params);
            return this;
        }
        
        public ModelRequest build() {
            return request;
        }
    }
}
