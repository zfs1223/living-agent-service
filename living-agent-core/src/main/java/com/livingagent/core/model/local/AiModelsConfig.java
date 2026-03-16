package com.livingagent.core.model.local;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@ConfigurationProperties(prefix = "ai-models")
public class AiModelsConfig {
    
    private String basePath;
    private LlmConfig llmConfig;
    private AsrConfig asrConfig;
    private TtsConfig ttsConfig;
    private SpeakerVerificationConfig speakerVerificationConfig;
    private PythonScriptsConfig pythonScriptsConfig;
    
    public String getBasePath() { return basePath; }
    public void setBasePath(String basePath) { this.basePath = basePath; }
    public LlmConfig getLlmConfig() { return llmConfig; }
    public AsrConfig getAsrConfig() { return asrConfig; }
    public TtsConfig getTtsConfig() { return ttsConfig; }
    public SpeakerVerificationConfig getSpeakerVerificationConfig() { return speakerVerificationConfig; }
    public PythonScriptsConfig getPythonScriptsConfig() { return pythonScriptsConfig; }
    
    public static class LlmConfig {
        private List<ModelConfig> models = new ArrayList<>();
        private String defaultModel = "qwen3-0.6b";
        
        public List<ModelConfig> getModels() { return models; }
        public String getDefaultModel() { return defaultModel; }
        public void setDefaultModel(String defaultModel) { this.defaultModel = defaultModel; }
    }
    
    public static class ModelConfig {
        private String name;
        private String type;
        private boolean enabled;
        private String modelPath;
        private String binaryPath;
        private int maxTokens = 2048;
        private double temperature = 0.7;
        private String pythonScript;
        
        public String getName() { return name; }
        public String getType() { return type; }
        public boolean isEnabled() { return enabled; }
        public String getModelPath() { return modelPath; }
        public String getBinaryPath() { return binaryPath; }
        public int getMaxTokens() { return maxTokens; }
        public double getTemperature() { return temperature; }
        public String getPythonScript() { return pythonScript; }
    }
    
    public static class AsrConfig {
        private String defaultProvider = "sherpa-ncnn";
        private int sampleRate = 16000;
        private int channels = 1;
        private String language = "zh-CN";
        private List<AsrProviderConfig> providers = new ArrayList<>();
        
        public String getDefaultProvider() { return defaultProvider; }
        public int getSampleRate() { return sampleRate; }
        public int getChannels() { return channels; }
        public String getLanguage() { return language; }
        public List<AsrProviderConfig> getProviders() { return providers; }
    }
    
    public static class AsrProviderConfig {
        private String name;
        private boolean enabled;
        private String modelPath;
        private String pythonScript;
        
        public String getName() { return name; }
        public boolean isEnabled() { return enabled; }
        public String getModelPath() { return modelPath; }
        public String getPythonScript() { return pythonScript; }
    }
    
    public static class TtsConfig {
        private String defaultProvider = "kokoro";
        private String defaultVoice = "zf_xiaobei";
        private double defaultSpeed = 1.0;
        private List<TtsProviderConfig> providers = new ArrayList<>();
        
        public String getDefaultProvider() { return defaultProvider; }
        public String getDefaultVoice() { return defaultVoice; }
        public double getDefaultSpeed() { return defaultSpeed; }
        public List<TtsProviderConfig> getProviders() { return providers; }
    }
    
    public static class TtsProviderConfig {
        private String name;
        private boolean enabled;
        private String modelPath;
        private String voicesPath;
        private String pythonScript;
        
        public String getName() { return name; }
        public boolean isEnabled() { return enabled; }
        public String getModelPath() { return modelPath; }
        public String getVoicesPath() { return voicesPath; }
        public String getPythonScript() { return pythonScript; }
    }
    
    public static class SpeakerVerificationConfig {
        private boolean enabled = true;
        private String modelPath;
        private double threshold = 0.33;
        private String pythonScript;
        
        public boolean isEnabled() { return enabled; }
        public String getModelPath() { return modelPath; }
        public double getThreshold() { return threshold; }
        public String getPythonScript() { return pythonScript; }
    }
    
    public static class PythonScriptsConfig {
        private String asr;
        private String tts;
        private String llm;
        private String speaker;
        
        public String getAsr() { return asr; }
        public String getTts() { return tts; }
        public String getLlm() { return llm; }
        public String getSpeaker() { return speaker; }
    }
}
