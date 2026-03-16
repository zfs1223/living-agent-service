package com.livingagent.core.security.voiceprint;

import java.util.List;
import java.util.Optional;

public interface VoicePrintService {

    VoicePrintResult enroll(String userId, byte[] audioData);
    
    VoicePrintResult enroll(String userId, float[] embedding);
    
    Optional<VoicePrintMatch> identify(byte[] audioData);
    
    Optional<VoicePrintMatch> identify(float[] embedding);
    
    boolean verify(String userId, byte[] audioData);
    
    boolean verify(String userId, float[] embedding);
    
    boolean deleteVoicePrint(String userId);
    
    Optional<VoicePrintProfile> getVoicePrint(String userId);
    
    List<VoicePrintProfile> getAllVoicePrints();
    
    int getVoicePrintCount();

    record VoicePrintResult(
            boolean success,
            String userId,
            float[] embedding,
            String error
    ) {
        public static VoicePrintResult success(String userId, float[] embedding) {
            return new VoicePrintResult(true, userId, embedding, null);
        }
        
        public static VoicePrintResult failed(String error) {
            return new VoicePrintResult(false, null, null, error);
        }
    }

    record VoicePrintMatch(
            String userId,
            String userName,
            double confidence,
            double threshold
    ) {
        public boolean isMatch() {
            return confidence >= threshold;
        }
    }

    record VoicePrintProfile(
            String userId,
            String userName,
            float[] embedding,
            int dimension,
            String model,
            long createdAt,
            long updatedAt,
            int enrollmentCount
    ) {}
}
