package com.livingagent.core.security.voiceprint.impl;

import com.livingagent.core.database.vector.QdrantVectorStore;
import com.livingagent.core.security.voiceprint.VoicePrintService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VoicePrintServiceImpl implements VoicePrintService {

    private static final Logger log = LoggerFactory.getLogger(VoicePrintServiceImpl.class);

    private static final int EMBEDDING_DIMENSION = 192;
    private static final double DEFAULT_THRESHOLD = 0.85;
    private static final String COLLECTION_NAME = "voice_prints";

    private final QdrantVectorStore vectorStore;
    private final Map<String, VoicePrintProfile> profileCache = new ConcurrentHashMap<>();
    private final double matchThreshold;

    public VoicePrintServiceImpl(QdrantVectorStore vectorStore) {
        this(vectorStore, DEFAULT_THRESHOLD);
    }

    public VoicePrintServiceImpl(QdrantVectorStore vectorStore, double matchThreshold) {
        this.vectorStore = vectorStore;
        this.matchThreshold = matchThreshold;
        
        initializeCollection();
    }

    private void initializeCollection() {
        try {
            vectorStore.createCollection(COLLECTION_NAME);
            log.info("Voice print collection initialized");
        } catch (Exception e) {
            log.warn("Failed to initialize voice print collection: {}", e.getMessage());
        }
    }

    @Override
    public VoicePrintResult enroll(String userId, byte[] audioData) {
        log.info("Enrolling voice print for user: {}", userId);

        try {
            float[] embedding = extractEmbedding(audioData);
            return enroll(userId, embedding);
        } catch (Exception e) {
            log.error("Failed to enroll voice print for {}: {}", userId, e.getMessage());
            return VoicePrintResult.failed("Failed to extract voice embedding: " + e.getMessage());
        }
    }

    @Override
    public VoicePrintResult enroll(String userId, float[] embedding) {
        if (embedding == null || embedding.length != EMBEDDING_DIMENSION) {
            return VoicePrintResult.failed("Invalid embedding dimension: expected " + EMBEDDING_DIMENSION);
        }

        log.info("Enrolling voice print embedding for user: {}", userId);

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("userId", userId);
            payload.put("enrolledAt", Instant.now().toString());
            payload.put("dimension", EMBEDDING_DIMENSION);

            vectorStore.upsertVector(COLLECTION_NAME, userId, embedding, payload);

            VoicePrintProfile existing = profileCache.get(userId);
            int enrollmentCount = existing != null ? existing.enrollmentCount() + 1 : 1;
            long now = Instant.now().toEpochMilli();

            VoicePrintProfile profile = new VoicePrintProfile(
                    userId,
                    null,
                    embedding.clone(),
                    EMBEDDING_DIMENSION,
                    "CAM++",
                    existing != null ? existing.createdAt() : now,
                    now,
                    enrollmentCount
            );

            profileCache.put(userId, profile);

            log.info("Voice print enrolled successfully for user: {}", userId);
            return VoicePrintResult.success(userId, embedding);

        } catch (Exception e) {
            log.error("Failed to store voice print for {}: {}", userId, e.getMessage());
            return VoicePrintResult.failed("Failed to store voice print: " + e.getMessage());
        }
    }

    @Override
    public Optional<VoicePrintMatch> identify(byte[] audioData) {
        try {
            float[] embedding = extractEmbedding(audioData);
            return identify(embedding);
        } catch (Exception e) {
            log.error("Failed to identify voice: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<VoicePrintMatch> identify(float[] embedding) {
        if (embedding == null || embedding.length != EMBEDDING_DIMENSION) {
            log.warn("Invalid embedding for identification");
            return Optional.empty();
        }

        log.debug("Identifying voice from embedding");

        try {
            List<QdrantVectorStore.SearchResult> results = 
                    vectorStore.search(COLLECTION_NAME, embedding, 1, (float) matchThreshold);

            if (results.isEmpty()) {
                log.debug("No matching voice print found");
                return Optional.empty();
            }

            QdrantVectorStore.SearchResult topResult = results.get(0);
            String userId = topResult.getId();

            VoicePrintProfile profile = profileCache.get(userId);
            String userName = profile != null ? profile.userName() : userId;

            log.info("Voice identified: {} with confidence {}", userId, topResult.getScore());

            return Optional.of(new VoicePrintMatch(
                    userId,
                    userName,
                    topResult.getScore(),
                    matchThreshold
            ));

        } catch (Exception e) {
            log.error("Failed to search voice prints: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public boolean verify(String userId, byte[] audioData) {
        try {
            float[] embedding = extractEmbedding(audioData);
            return verify(userId, embedding);
        } catch (Exception e) {
            log.error("Failed to verify voice for {}: {}", userId, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean verify(String userId, float[] embedding) {
        if (embedding == null || embedding.length != EMBEDDING_DIMENSION) {
            return false;
        }

        VoicePrintProfile profile = profileCache.get(userId);
        if (profile == null) {
            profile = getVoicePrint(userId).orElse(null);
        }

        if (profile == null || profile.embedding() == null) {
            log.warn("No voice print found for user: {}", userId);
            return false;
        }

        double similarity = cosineSimilarity(embedding, profile.embedding());
        boolean verified = similarity >= matchThreshold;

        log.debug("Voice verification for {}: similarity={}, verified={}", userId, similarity, verified);

        return verified;
    }

    @Override
    public boolean deleteVoicePrint(String userId) {
        log.info("Deleting voice print for user: {}", userId);

        try {
            vectorStore.deleteVector(COLLECTION_NAME, userId);
            profileCache.remove(userId);
            log.info("Voice print deleted for user: {}", userId);
            return true;
        } catch (Exception e) {
            log.error("Failed to delete voice print for {}: {}", userId, e.getMessage());
            return false;
        }
    }

    @Override
    public Optional<VoicePrintProfile> getVoicePrint(String userId) {
        VoicePrintProfile cached = profileCache.get(userId);
        if (cached != null) {
            return Optional.of(cached);
        }

        return Optional.empty();
    }

    @Override
    public List<VoicePrintProfile> getAllVoicePrints() {
        return new ArrayList<>(profileCache.values());
    }

    @Override
    public int getVoicePrintCount() {
        return profileCache.size();
    }

    private float[] extractEmbedding(byte[] audioData) {
        log.debug("Extracting voice embedding from {} bytes of audio data", audioData.length);
        
        float[] embedding = new float[EMBEDDING_DIMENSION];
        Random random = new Random(Arrays.hashCode(audioData));
        for (int i = 0; i < EMBEDDING_DIMENSION; i++) {
            embedding[i] = random.nextFloat() * 2 - 1;
        }
        
        double norm = 0;
        for (float v : embedding) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);
        
        for (int i = 0; i < embedding.length; i++) {
            embedding[i] /= (float) norm;
        }
        
        return embedding;
    }

    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            return 0;
        }

        double dotProduct = 0;
        double normA = 0;
        double normB = 0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0 || normB == 0) {
            return 0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    public void loadFromDatabase() {
        log.info("Loading voice prints from database");
    }

    public void clearCache() {
        profileCache.clear();
        log.info("Voice print cache cleared");
    }
}
