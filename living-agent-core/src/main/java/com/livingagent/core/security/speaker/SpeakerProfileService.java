package com.livingagent.core.security.speaker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class SpeakerProfileService {

    private static final Logger log = LoggerFactory.getLogger(SpeakerProfileService.class);

    private final SpeakerProfileRepository repository;
    private final Map<String, SpeakerProfile> profileCache = new ConcurrentHashMap<>();

    public SpeakerProfileService(SpeakerProfileRepository repository) {
        this.repository = repository;
        loadCache();
    }

    private void loadCache() {
        log.info("Loading speaker profiles from database...");
        repository.findAll().forEach(profile -> {
            profileCache.put(profile.getSpeakerId(), profile);
        });
        log.info("Loaded {} speaker profiles", profileCache.size());
    }

    public SpeakerProfile createProfile(String speakerId, String name, float[] embedding) {
        if (profileCache.containsKey(speakerId)) {
            throw new IllegalStateException("Speaker profile already exists: " + speakerId);
        }

        SpeakerProfile profile = new SpeakerProfile();
        profile.setSpeakerId(speakerId);
        profile.setName(name);
        profile.setEmbedding(embedding);
        profile.setEmbeddingDimension(embedding != null ? embedding.length : 192);
        profile.setCreatedAt(Instant.now());
        profile.setUpdatedAt(Instant.now());
        profile.setActive(true);

        SpeakerProfile saved = repository.save(profile);
        profileCache.put(speakerId, saved);

        log.info("Created speaker profile: {} ({})", name, speakerId);
        return saved;
    }

    public Optional<SpeakerProfile> findBySpeakerId(String speakerId) {
        return Optional.ofNullable(profileCache.get(speakerId));
    }

    public Optional<SpeakerProfile> findByName(String name) {
        return profileCache.values().stream()
            .filter(p -> name.equals(p.getName()))
            .findFirst();
    }

    public SpeakerProfile updateProfile(String speakerId, float[] newEmbedding) {
        SpeakerProfile profile = profileCache.get(speakerId);
        if (profile == null) {
            throw new IllegalArgumentException("Speaker profile not found: " + speakerId);
        }

        profile.setEmbedding(newEmbedding);
        profile.setUpdatedAt(Instant.now());

        SpeakerProfile saved = repository.save(profile);
        profileCache.put(speakerId, saved);

        log.info("Updated speaker profile: {}", speakerId);
        return saved;
    }

    public void deleteProfile(String speakerId) {
        SpeakerProfile removed = profileCache.remove(speakerId);
        if (removed != null) {
            repository.deleteById(speakerId);
            log.info("Deleted speaker profile: {}", speakerId);
        }
    }

    public void updateLastMatched(String speakerId) {
        SpeakerProfile profile = profileCache.get(speakerId);
        if (profile != null) {
            profile.setLastMatchedAt(Instant.now());
            profile.setMatchCount(profile.getMatchCount() + 1);
            repository.save(profile);
        }
    }

    public int getProfileCount() {
        return profileCache.size();
    }

    public interface SpeakerProfileRepository {
        Iterable<SpeakerProfile> findAll();
        SpeakerProfile save(SpeakerProfile profile);
        void deleteById(String speakerId);
        Optional<SpeakerProfile> findById(String speakerId);
    }
}
