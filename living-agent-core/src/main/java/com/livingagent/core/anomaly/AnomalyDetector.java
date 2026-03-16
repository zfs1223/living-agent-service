package com.livingagent.core.anomaly;

import java.time.Instant;
import java.util.Map;

public interface AnomalyDetector {
    
    AnomalyResult detect(AnomalyContext context);
    
    String getDetectorType();
    
    boolean isEnabled();
    
    void enable();
    
    void disable();
}
