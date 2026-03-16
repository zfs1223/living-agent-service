package com.livingagent.core.model;

import java.util.Map;
import java.util.List;

public class ModelStatus {
    
    private Map<String, Boolean> modelsLoaded;
    private int totalModels;
    private int loadedCount;
    private int sessionCount;
    private List<String> sessions;
    
    public ModelStatus() {
    }
    
    public Map<String, Boolean> getModelsLoaded() {
        return modelsLoaded;
    }
    
    public void setModelsLoaded(Map<String, Boolean> modelsLoaded) {
        this.modelsLoaded = modelsLoaded;
        if (modelsLoaded != null) {
            this.totalModels = modelsLoaded.size();
            this.loadedCount = (int) modelsLoaded.values().stream().filter(v -> v).count();
        }
    }
    
    public int getTotalModels() {
        return totalModels;
    }
    
    public void setTotalModels(int totalModels) {
        this.totalModels = totalModels;
    }
    
    public int getLoadedCount() {
        return loadedCount;
    }
    
    public void setLoadedCount(int loadedCount) {
        this.loadedCount = loadedCount;
    }
    
    public int getSessionCount() {
        return sessionCount;
    }
    
    public void setSessionCount(int sessionCount) {
        this.sessionCount = sessionCount;
    }
    
    public List<String> getSessions() {
        return sessions;
    }
    
    public void setSessions(List<String> sessions) {
        this.sessions = sessions;
    }
    
    public boolean isModelLoaded(String modelName) {
        return modelsLoaded != null && modelsLoaded.getOrDefault(modelName, false);
    }
    
    public boolean isAsrAvailable() {
        return isModelLoaded("sherpa") || isModelLoaded("funasr");
    }
    
    public boolean isLlmAvailable() {
        return isModelLoaded("qwen3") || isModelLoaded("bitnet");
    }
    
    public boolean isTtsAvailable() {
        return isModelLoaded("supertonic") || isModelLoaded("melotts");
    }
}
