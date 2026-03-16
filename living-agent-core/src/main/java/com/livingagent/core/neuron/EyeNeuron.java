package com.livingagent.core.neuron;

import java.util.List;

public interface EyeNeuron extends Neuron {
    
    ImageAnalysisResult analyzeImage(String imageUrl, String prompt);
    
    ImageAnalysisResult analyzeImage(byte[] imageData, String prompt);
    
    String extractText(String imageUrl);
    
    String extractText(byte[] imageData);
    
    VisualQAResult visualQA(String imageUrl, String question);
    
    VisualQAResult visualQA(byte[] imageData, String question);
    
    List<DetectedObject> detectObjects(String imageUrl);
    
    List<DetectedObject> detectObjects(byte[] imageData);
    
    DocumentAnalysisResult analyzeDocument(String imageUrl);
    
    DocumentAnalysisResult analyzeDocument(byte[] imageData);
    
    FaceAnalysisResult analyzeFace(String imageUrl);
    
    FaceAnalysisResult analyzeFace(byte[] imageData);
    
    ComparisonResult compareImages(String imageUrl1, String imageUrl2);
    
    ComparisonResult compareImages(byte[] imageData1, byte[] imageData2);
}
