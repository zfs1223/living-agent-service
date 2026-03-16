package com.livingagent.core.neuron;

public class DetectedObject {
    
    private String objectId;
    private String label;
    private double confidence;
    private BoundingBox boundingBox;
    private String category;
    
    public DetectedObject() {
        this.objectId = java.util.UUID.randomUUID().toString();
    }
    
    public DetectedObject(String label, double confidence) {
        this();
        this.label = label;
        this.confidence = confidence;
    }
    
    public DetectedObject(String label, double confidence, BoundingBox boundingBox) {
        this(label, confidence);
        this.boundingBox = boundingBox;
    }
    
    public String getObjectId() { return objectId; }
    public void setObjectId(String objectId) { this.objectId = objectId; }
    
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    
    public BoundingBox getBoundingBox() { return boundingBox; }
    public void setBoundingBox(BoundingBox boundingBox) { this.boundingBox = boundingBox; }
    
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    
    @Override
    public String toString() {
        return "DetectedObject{label='" + label + "', confidence=" + String.format("%.2f", confidence) + "}";
    }
    
    public static class BoundingBox {
        private double x;
        private double y;
        private double width;
        private double height;
        
        public BoundingBox() {}
        
        public BoundingBox(double x, double y, double width, double height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
        
        public double getX() { return x; }
        public void setX(double x) { this.x = x; }
        
        public double getY() { return y; }
        public void setY(double y) { this.y = y; }
        
        public double getWidth() { return width; }
        public void setWidth(double width) { this.width = width; }
        
        public double getHeight() { return height; }
        public void setHeight(double height) { this.height = height; }
    }
}
