package com.livingagent.core.neuron.impl;

import com.livingagent.core.channel.Channel;
import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.neuron.*;
import com.livingagent.core.tool.Tool;
import com.livingagent.core.model.ModelManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class EyeNeuronImpl implements EyeNeuron {
    
    private static final Logger log = LoggerFactory.getLogger(EyeNeuronImpl.class);
    
    private static final String NEURON_ID = "eye-neuron";
    private static final String NEURON_NAME = "视觉神经元";
    private static final String NEURON_DESCRIPTION = "处理图像理解、OCR、视觉问答等任务";
    private static final String MODEL_ID = "qwen3.5-27b";
    
    private final ModelManager modelManager;
    private NeuronState state = NeuronState.INITIALIZING;
    private NeuronContext context;
    private final Map<String, Object> stateData = new HashMap<>();
    private final List<String> subscribedChannels = new ArrayList<>();
    private final List<String> publishChannels = new ArrayList<>();
    private final Set<String> skills = new HashSet<>();
    
    @Autowired
    public EyeNeuronImpl(ModelManager modelManager) {
        this.modelManager = modelManager;
        this.subscribedChannels.add("visual-input");
        this.subscribedChannels.add("image-processing");
        this.publishChannels.add("visual-output");
        this.state = NeuronState.ACTIVE;
    }
    
    @Override
    public String getId() {
        return NEURON_ID;
    }
    
    @Override
    public String getName() {
        return NEURON_NAME;
    }
    
    @Override
    public String getDescription() {
        return NEURON_DESCRIPTION;
    }
    
    @Override
    public String getType() {
        return "vision";
    }
    
    @Override
    public NeuronState getState() {
        return state;
    }
    
    @Override
    public void setState(NeuronState state) {
        this.state = state;
    }
    
    @Override
    public void initialize(NeuronContext context) {
        this.context = context;
        this.state = NeuronState.ACTIVE;
    }
    
    @Override
    public void start(NeuronContext context) {
        this.context = context;
        this.state = NeuronState.ACTIVE;
        log.info("EyeNeuron started with context: {}", context);
    }
    
    @Override
    public void stop() {
        this.state = NeuronState.STOPPED;
        log.info("EyeNeuron stopped");
    }
    
    @Override
    public void onMessage(ChannelMessage message) {
        log.debug("EyeNeuron received message: {}", message);
        Object payload = message.getPayload();
        if (payload instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> content = (Map<String, Object>) payload;
            String action = (String) content.get("action");
            String imageUrl = (String) content.get("imageUrl");
            String prompt = (String) content.get("prompt");
            
            if ("analyze".equals(action) && imageUrl != null) {
                ImageAnalysisResult result = analyzeImage(imageUrl, prompt != null ? prompt : "描述这张图片");
                ChannelMessage responseMsg = ChannelMessage.text(
                    "visual-output", getId(), message.getSourceChannelId(), 
                    message.getSessionId(), result.toString());
                publish("visual-output", responseMsg);
            }
        }
    }
    
    @Override
    public void subscribe(Channel channel) {
        subscribedChannels.add(channel.getId());
    }
    
    @Override
    public void publishTo(Channel channel) {
        publishChannels.add(channel.getId());
    }
    
    @Override
    public void publish(String channelId, ChannelMessage message) {
        if (context != null) {
            context.publish(channelId, message);
        }
    }
    
    @Override
    public List<String> getSubscribedChannels() {
        return subscribedChannels;
    }
    
    @Override
    public List<String> getPublishChannels() {
        return publishChannels;
    }
    
    @Override
    public List<Tool> getTools() {
        return Collections.emptyList();
    }
    
    @Override
    public Set<String> getSkills() {
        return skills;
    }
    
    @Override
    public void addSkill(String skillName) {
        skills.add(skillName);
    }
    
    @Override
    public void removeSkill(String skillName) {
        skills.remove(skillName);
    }
    
    @Override
    public boolean hasSkill(String skillName) {
        return skills.contains(skillName);
    }
    
    @Override
    public void autoDiscoverSkills() {
        log.debug("Auto-discovering skills for EyeNeuron");
    }
    
    @Override
    public Map<String, Object> getStateData() {
        return stateData;
    }
    
    @Override
    public void setStateData(String key, Object value) {
        stateData.put(key, value);
    }
    
    @Override
    public Object getStateData(String key) {
        return stateData.get(key);
    }
    
    @Override
    public ImageAnalysisResult analyzeImage(String imageUrl, String prompt) {
        log.info("Analyzing image: {} with prompt: {}", imageUrl, prompt);
        long startTime = System.currentTimeMillis();
        
        try {
            String fullPrompt = "请分析这张图片: " + prompt;
            String response = callVisionModel(imageUrl, fullPrompt);
            
            ImageAnalysisResult result = new ImageAnalysisResult();
            result.setImageUrl(imageUrl);
            result.setPrompt(prompt);
            result.setDescription(response);
            result.setConfidence(0.9);
            result.setModelUsed(MODEL_ID);
            result.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            
            return result;
        } catch (Exception e) {
            log.error("Failed to analyze image: {}", imageUrl, e);
            return ImageAnalysisResult.of("分析失败: " + e.getMessage());
        }
    }
    
    @Override
    public ImageAnalysisResult analyzeImage(byte[] imageData, String prompt) {
        String base64 = Base64.getEncoder().encodeToString(imageData);
        return analyzeImage("data:image/jpeg;base64," + base64, prompt);
    }
    
    @Override
    public String extractText(String imageUrl) {
        log.info("Extracting text from image: {}", imageUrl);
        
        try {
            String prompt = "请提取图片中的所有文字内容，保持原有格式。如果图片中没有文字，请返回'无文字'。";
            return callVisionModel(imageUrl, prompt);
        } catch (Exception e) {
            log.error("Failed to extract text from image: {}", imageUrl, e);
            return "文字提取失败: " + e.getMessage();
        }
    }
    
    @Override
    public String extractText(byte[] imageData) {
        String base64 = Base64.getEncoder().encodeToString(imageData);
        return extractText("data:image/jpeg;base64," + base64);
    }
    
    @Override
    public VisualQAResult visualQA(String imageUrl, String question) {
        log.info("Visual QA for image: {}, question: {}", imageUrl, question);
        long startTime = System.currentTimeMillis();
        
        try {
            String response = callVisionModel(imageUrl, question);
            
            VisualQAResult result = new VisualQAResult();
            result.setImageUrl(imageUrl);
            result.setQuestion(question);
            result.setAnswer(response);
            result.setConfidence(0.9);
            result.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            
            return result;
        } catch (Exception e) {
            log.error("Failed visual QA for image: {}", imageUrl, e);
            return VisualQAResult.of(question, "回答失败: " + e.getMessage());
        }
    }
    
    @Override
    public VisualQAResult visualQA(byte[] imageData, String question) {
        String base64 = Base64.getEncoder().encodeToString(imageData);
        return visualQA("data:image/jpeg;base64," + base64, question);
    }
    
    @Override
    public List<DetectedObject> detectObjects(String imageUrl) {
        log.info("Detecting objects in image: {}", imageUrl);
        
        try {
            String prompt = "请识别图片中的所有物体，列出它们的名称。格式：每行一个物体名称。";
            String response = callVisionModel(imageUrl, prompt);
            
            List<DetectedObject> objects = new ArrayList<>();
            String[] lines = response.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (!line.isEmpty()) {
                    DetectedObject obj = new DetectedObject(line, 0.85);
                    objects.add(obj);
                }
            }
            
            return objects;
        } catch (Exception e) {
            log.error("Failed to detect objects in image: {}", imageUrl, e);
            return Collections.emptyList();
        }
    }
    
    @Override
    public List<DetectedObject> detectObjects(byte[] imageData) {
        String base64 = Base64.getEncoder().encodeToString(imageData);
        return detectObjects("data:image/jpeg;base64," + base64);
    }
    
    @Override
    public DocumentAnalysisResult analyzeDocument(String imageUrl) {
        log.info("Analyzing document: {}", imageUrl);
        long startTime = System.currentTimeMillis();
        
        try {
            String prompt = "请分析这份文档，提取所有文字内容，并识别文档类型（如身份证、发票、合同等）。";
            String response = callVisionModel(imageUrl, prompt);
            
            DocumentAnalysisResult result = new DocumentAnalysisResult();
            result.setImageUrl(imageUrl);
            result.setExtractedText(response);
            result.setConfidence(0.9);
            result.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            
            return result;
        } catch (Exception e) {
            log.error("Failed to analyze document: {}", imageUrl, e);
            return DocumentAnalysisResult.of("文档分析失败: " + e.getMessage());
        }
    }
    
    @Override
    public DocumentAnalysisResult analyzeDocument(byte[] imageData) {
        String base64 = Base64.getEncoder().encodeToString(imageData);
        return analyzeDocument("data:image/jpeg;base64," + base64);
    }
    
    @Override
    public FaceAnalysisResult analyzeFace(String imageUrl) {
        log.info("Analyzing face in image: {}", imageUrl);
        
        try {
            String prompt = "请分析图片中的人脸，描述人物的表情、年龄估计、性别等特征。";
            String response = callVisionModel(imageUrl, prompt);
            
            FaceAnalysisResult result = new FaceAnalysisResult();
            result.setImageUrl(imageUrl);
            result.setConfidence(0.85);
            result.addAttribute("analysis", response);
            
            return result;
        } catch (Exception e) {
            log.error("Failed to analyze face in image: {}", imageUrl, e);
            return FaceAnalysisResult.of(0);
        }
    }
    
    @Override
    public FaceAnalysisResult analyzeFace(byte[] imageData) {
        String base64 = Base64.getEncoder().encodeToString(imageData);
        return analyzeFace("data:image/jpeg;base64," + base64);
    }
    
    @Override
    public ComparisonResult compareImages(String imageUrl1, String imageUrl2) {
        log.info("Comparing images: {} vs {}", imageUrl1, imageUrl2);
        
        try {
            String prompt = "请比较这两张图片，描述它们的相似之处和不同之处。";
            String response = callVisionModel(imageUrl1 + "," + imageUrl2, prompt);
            
            ComparisonResult result = new ComparisonResult();
            result.setImageUrl1(imageUrl1);
            result.setImageUrl2(imageUrl2);
            result.setSimilarity(0.75);
            result.setConfidence(0.8);
            result.addSimilarity(response);
            
            return result;
        } catch (Exception e) {
            log.error("Failed to compare images", e);
            return ComparisonResult.of(0.0);
        }
    }
    
    @Override
    public ComparisonResult compareImages(byte[] imageData1, byte[] imageData2) {
        String base64_1 = Base64.getEncoder().encodeToString(imageData1);
        String base64_2 = Base64.getEncoder().encodeToString(imageData2);
        return compareImages(
            "data:image/jpeg;base64," + base64_1,
            "data:image/jpeg;base64," + base64_2
        );
    }
    
    private String callVisionModel(String imageUrl, String prompt) {
        try {
            return modelManager.chatWithImage(MODEL_ID, prompt, imageUrl);
        } catch (Exception e) {
            log.error("Failed to call vision model", e);
            throw new RuntimeException("Vision model call failed", e);
        }
    }
}
