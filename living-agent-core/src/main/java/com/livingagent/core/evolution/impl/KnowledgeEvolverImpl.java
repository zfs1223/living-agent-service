package com.livingagent.core.evolution.impl;

import com.livingagent.core.evolution.*;
import com.livingagent.core.knowledge.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class KnowledgeEvolverImpl implements KnowledgeEvolver {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeEvolverImpl.class);

    private final KnowledgeBase knowledgeBase;
    private final Map<String, KnowledgeEvolution> evolutionHistory = new ConcurrentHashMap<>();
    private final Map<String, Integer> accessFrequency = new ConcurrentHashMap<>();
    private final Map<String, Double> feedbackScores = new ConcurrentHashMap<>();

    private double mergeThreshold = 0.85;
    private double propagationThreshold = 0.7;
    private int maxEvolutionHistory = 1000;

    public KnowledgeEvolverImpl(KnowledgeBase knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    @Override
    public List<KnowledgeEvolution> extractKnowledgeFromConversation(String conversationId) {
        log.info("Extracting knowledge from conversation: {}", conversationId);
        
        List<KnowledgeEvolution> extractions = new ArrayList<>();
        
        List<Experience> experiences = knowledgeBase.getExperiences(conversationId);
        
        for (Experience exp : experiences) {
            KnowledgeEvolution evolution = extractFromExperience(exp);
            if (evolution != null) {
                extractions.add(evolution);
                evolutionHistory.put(evolution.getEvolutionId(), evolution);
            }
        }

        log.info("Extracted {} knowledge items from conversation {}", extractions.size(), conversationId);
        return extractions;
    }

    @Override
    public KnowledgeMergeResult mergeKnowledge(String sourceId, String targetId) {
        log.info("Merging knowledge: {} -> {}", sourceId, targetId);

        Optional<KnowledgeEntry> sourceOpt = knowledgeBase.retrieveEntry(sourceId);
        Optional<KnowledgeEntry> targetOpt = knowledgeBase.retrieveEntry(targetId);

        if (sourceOpt.isEmpty() || targetOpt.isEmpty()) {
            return KnowledgeMergeResult.failed("Source or target knowledge not found");
        }

        KnowledgeEntry source = sourceOpt.get();
        KnowledgeEntry target = targetOpt.get();

        double similarity = calculateSimilarity(source, target);
        if (similarity < mergeThreshold) {
            return KnowledgeMergeResult.failed(
                "Similarity too low for merge: " + similarity + " < " + mergeThreshold);
        }

        KnowledgeEntry merged = mergeEntries(source, target);

        knowledgeBase.update(targetId, merged.getContent());
        knowledgeBase.delete(sourceId);

        KnowledgeEvolution evolution = new KnowledgeEvolution();
        evolution.setEvolutionId("merge_" + System.currentTimeMillis());
        evolution.setKnowledgeId(targetId);
        evolution.setEvolutionType(KnowledgeEvolution.EvolutionType.MERGE);
        evolution.setSourceId(sourceId);
        evolution.setConfidence(similarity);
        evolution.setTimestamp(Instant.now());

        return KnowledgeMergeResult.success(targetId, sourceId, merged, similarity);
    }

    @Override
    public Optional<KnowledgeEvolution> evolveKnowledge(String knowledgeId) {
        log.info("Evolving knowledge: {}", knowledgeId);

        Optional<KnowledgeEntry> entryOpt = knowledgeBase.retrieveEntry(knowledgeId);
        if (entryOpt.isEmpty()) {
            log.warn("Knowledge not found: {}", knowledgeId);
            return Optional.empty();
        }

        KnowledgeEntry entry = entryOpt.get();

        int accessCount = accessFrequency.getOrDefault(knowledgeId, 0);
        double feedbackScore = feedbackScores.getOrDefault(knowledgeId, 0.5);

        KnowledgeEvolution evolution = new KnowledgeEvolution();
        evolution.setEvolutionId("evo_" + System.currentTimeMillis());
        evolution.setKnowledgeId(knowledgeId);
        evolution.setTimestamp(Instant.now());

        if (accessCount > 100 && feedbackScore > 0.8) {
            evolution.setEvolutionType(KnowledgeEvolution.EvolutionType.PROMOTE);
            entry.setImportance(Importance.HIGH);
            entry.setValidity(Validity.LONG_TERM);
            log.info("Promoted knowledge {} to HIGH importance", knowledgeId);
        } else if (accessCount > 50 && feedbackScore > 0.6) {
            evolution.setEvolutionType(KnowledgeEvolution.EvolutionType.ENHANCE);
            entry.setConfidence(Math.min(1.0, entry.getConfidence() + 0.1));
            log.info("Enhanced knowledge {} confidence", knowledgeId);
        } else if (feedbackScore < 0.3) {
            evolution.setEvolutionType(KnowledgeEvolution.EvolutionType.DEPRECATE);
            entry.setImportance(Importance.LOW);
            log.info("Deprecated knowledge {} due to low feedback", knowledgeId);
        } else {
            evolution.setEvolutionType(KnowledgeEvolution.EvolutionType.MAINTAIN);
        }

        evolution.setConfidence(feedbackScore);
        evolutionHistory.put(evolution.getEvolutionId(), evolution);

        knowledgeBase.update(knowledgeId, entry);

        return Optional.of(evolution);
    }

    @Override
    public List<KnowledgeEvolution> findSimilarKnowledge(String query, double threshold) {
        log.debug("Finding similar knowledge for: {}", query);

        List<KnowledgeEntry> results = knowledgeBase.search(query);
        
        return results.stream()
            .filter(entry -> {
                double similarity = calculateQuerySimilarity(query, entry);
                return similarity >= threshold;
            })
            .map(entry -> {
                KnowledgeEvolution evolution = new KnowledgeEvolution();
                evolution.setEvolutionId("sim_" + System.currentTimeMillis());
                evolution.setKnowledgeId(entry.getKey());
                evolution.setEvolutionType(KnowledgeEvolution.EvolutionType.SIMILARITY);
                evolution.setConfidence(calculateQuerySimilarity(query, entry));
                evolution.setTimestamp(Instant.now());
                return evolution;
            })
            .collect(Collectors.toList());
    }

    @Override
    public KnowledgePropagationResult propagateKnowledge(String sourceAgent, String targetAgent, String knowledgeId) {
        log.info("Propagating knowledge {} from {} to {}", knowledgeId, sourceAgent, targetAgent);

        Optional<KnowledgeEntry> entryOpt = knowledgeBase.retrieveEntry(knowledgeId);
        if (entryOpt.isEmpty()) {
            return KnowledgePropagationResult.failed("Knowledge not found: " + knowledgeId);
        }

        KnowledgeEntry entry = entryOpt.get();

        if (!shouldPropagate(entry)) {
            return KnowledgePropagationResult.failed("Knowledge does not meet propagation criteria");
        }

        KnowledgeEntry propagated = cloneForPropagation(entry, targetAgent);
        propagated.setNeuronId(targetAgent);

        String newKey = knowledgeId + "_" + targetAgent;
        knowledgeBase.store(newKey, propagated.getContent(), createMetadata(propagated));

        return KnowledgePropagationResult.success(knowledgeId, newKey, targetAgent, propagationThreshold);
    }

    @Override
    public List<BestPractice> extractBestPractices(String domain, int minSuccessRate) {
        log.info("Extracting best practices for domain: {}", domain);

        List<BestPractice> practices = new ArrayList<>();
        List<BestPractice> existing = knowledgeBase.getBestPractices(domain);

        for (BestPractice bp : existing) {
            if (bp.getSuccessRate() >= minSuccessRate) {
                practices.add(bp);
            }
        }

        return practices.stream()
            .sorted((a, b) -> Double.compare(b.getSuccessRate(), a.getSuccessRate()))
            .collect(Collectors.toList());
    }

    @Override
    public KnowledgeQualityReport assessQuality() {
        log.info("Assessing knowledge quality");

        KnowledgeQualityReport report = new KnowledgeQualityReport();
        
        List<KnowledgeEntry> allEntries = knowledgeBase.search("");
        
        int totalKnowledge = allEntries.size();
        int verifiedCount = 0;
        int highImportanceCount = 0;
        int expiredCount = 0;
        int lowConfidenceCount = 0;
        
        List<KnowledgeIssue> issues = new ArrayList<>();

        for (KnowledgeEntry entry : allEntries) {
            if (entry.isVerified()) {
                verifiedCount++;
            }
            if (entry.getImportance() == Importance.HIGH) {
                highImportanceCount++;
            }
            if (entry.isExpired()) {
                expiredCount++;
                issues.add(KnowledgeIssue.expired(entry.getKey(), entry.getExpiresAt()));
            }
            if (entry.getConfidence() < 0.3) {
                lowConfidenceCount++;
                issues.add(KnowledgeIssue.lowConfidence(entry.getKey(), entry.getConfidence()));
            }
        }

        report.setTotalKnowledge(totalKnowledge);
        report.setVerifiedCount(verifiedCount);
        report.setHighImportanceCount(highImportanceCount);
        report.setExpiredCount(expiredCount);
        report.setLowConfidenceCount(lowConfidenceCount);
        report.setIssues(issues);
        report.setQualityScore(calculateQualityScore(report));
        report.setGeneratedAt(Instant.now());

        return report;
    }

    @Override
    public void cleanupRedundantKnowledge() {
        log.info("Cleaning up redundant knowledge");

        List<KnowledgeEntry> allEntries = knowledgeBase.search("");
        Map<String, List<KnowledgeEntry>> groupedByKey = new HashMap<>();

        for (KnowledgeEntry entry : allEntries) {
            groupedByKey.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(entry);
        }

        for (Map.Entry<String, List<KnowledgeEntry>> group : groupedByKey.entrySet()) {
            if (group.getValue().size() > 1) {
                List<KnowledgeEntry> duplicates = group.getValue();
                duplicates.sort((a, b) -> Double.compare(b.getRelevanceScore(), a.getRelevanceScore()));
                
                KnowledgeEntry best = duplicates.get(0);
                for (int i = 1; i < duplicates.size(); i++) {
                    KnowledgeEntry duplicate = duplicates.get(i);
                    if (calculateSimilarity(best, duplicate) > mergeThreshold) {
                        knowledgeBase.delete(duplicate.getKey());
                        log.debug("Removed duplicate knowledge: {}", duplicate.getKey());
                    }
                }
            }
        }

        allEntries.stream()
            .filter(KnowledgeEntry::isExpired)
            .forEach(entry -> {
                knowledgeBase.delete(entry.getKey());
                log.debug("Removed expired knowledge: {}", entry.getKey());
            });
    }

    @Override
    public void updateRelevanceScores() {
        log.info("Updating relevance scores");

        List<KnowledgeEntry> allEntries = knowledgeBase.search("");

        for (KnowledgeEntry entry : allEntries) {
            double newScore = entry.calculateRelevanceScore();
            int accessCount = accessFrequency.getOrDefault(entry.getKey(), 0);
            double feedbackScore = feedbackScores.getOrDefault(entry.getKey(), 0.5);
            
            double adjustedScore = newScore * 0.5 + (accessCount / 100.0) * 0.3 + feedbackScore * 0.2;
            entry.setRelevanceScore(adjustedScore);
            
            knowledgeBase.updateKnowledgeRelevance(entry.getKey(), adjustedScore);
        }
    }

    public void recordAccess(String knowledgeId) {
        accessFrequency.merge(knowledgeId, 1, Integer::sum);
    }

    public void recordFeedback(String knowledgeId, double score) {
        feedbackScores.put(knowledgeId, score);
    }

    private KnowledgeEvolution extractFromExperience(Experience experience) {
        if (experience == null || experience.getContent() == null) {
            return null;
        }

        KnowledgeEvolution evolution = new KnowledgeEvolution();
        evolution.setEvolutionId("exp_" + System.currentTimeMillis());
        evolution.setKnowledgeId("knowledge_" + System.currentTimeMillis());
        evolution.setEvolutionType(KnowledgeEvolution.EvolutionType.EXTRACT);
        evolution.setConfidence(0.7);
        evolution.setTimestamp(Instant.now());

        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setKey(evolution.getKnowledgeId());
        entry.setContent(experience.getContent());
        entry.setKnowledgeType(KnowledgeType.EXPERIENCE);
        entry.setImportance(Importance.MEDIUM);
        entry.setBrainDomain(experience.getContext());

        knowledgeBase.store(entry.getKey(), entry.getContent(), createMetadata(entry));

        return evolution;
    }

    private double calculateSimilarity(KnowledgeEntry a, KnowledgeEntry b) {
        if (a.getVector() != null && b.getVector() != null) {
            return cosineSimilarity(a.getVector(), b.getVector());
        }
        
        String contentA = a.getContent() != null ? a.getContent().toString() : "";
        String contentB = b.getContent() != null ? b.getContent().toString() : "";
        return jaccardSimilarity(contentA, contentB);
    }

    private double calculateQuerySimilarity(String query, KnowledgeEntry entry) {
        String content = entry.getContent() != null ? entry.getContent().toString() : "";
        return jaccardSimilarity(query.toLowerCase(), content.toLowerCase());
    }

    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) return 0;
        
        double dotProduct = 0;
        double normA = 0;
        double normB = 0;
        
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private double jaccardSimilarity(String a, String b) {
        Set<String> wordsA = new HashSet<>(Arrays.asList(a.toLowerCase().split("\\s+")));
        Set<String> wordsB = new HashSet<>(Arrays.asList(b.toLowerCase().split("\\s+")));
        
        Set<String> intersection = new HashSet<>(wordsA);
        intersection.retainAll(wordsB);
        
        Set<String> union = new HashSet<>(wordsA);
        union.addAll(wordsB);
        
        return union.isEmpty() ? 0 : (double) intersection.size() / union.size();
    }

    private KnowledgeEntry mergeEntries(KnowledgeEntry source, KnowledgeEntry target) {
        KnowledgeEntry merged = new KnowledgeEntry();
        merged.setKey(target.getKey());
        merged.setContent(target.getContent() + "\n\n[补充] " + source.getContent());
        merged.setKnowledgeType(target.getKnowledgeType());
        merged.setImportance(target.getImportance().ordinal() > source.getImportance().ordinal() 
            ? target.getImportance() : source.getImportance());
        merged.setConfidence((target.getConfidence() + source.getConfidence()) / 2);
        merged.setBrainDomain(target.getBrainDomain());
        merged.setVerified(target.isVerified() && source.isVerified());
        
        merged.getTags().putAll(target.getTags());
        merged.getTags().putAll(source.getTags());
        
        return merged;
    }

    private boolean shouldPropagate(KnowledgeEntry entry) {
        return entry.getConfidence() >= propagationThreshold
            && entry.getImportance() != Importance.LOW
            && !entry.isExpired();
    }

    private KnowledgeEntry cloneForPropagation(KnowledgeEntry source, String targetAgent) {
        KnowledgeEntry clone = new KnowledgeEntry();
        clone.setKey(source.getKey());
        clone.setContent(source.getContent());
        clone.setKnowledgeType(source.getKnowledgeType());
        clone.setImportance(source.getImportance());
        clone.setValidity(source.getValidity());
        clone.setConfidence(source.getConfidence() * 0.9);
        clone.setBrainDomain(source.getBrainDomain());
        clone.setNeuronId(targetAgent);
        clone.getTags().putAll(source.getTags());
        return clone;
    }

    private Map<String, String> createMetadata(KnowledgeEntry entry) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("type", entry.getKnowledgeType().name());
        metadata.put("importance", entry.getImportance().name());
        metadata.put("brainDomain", entry.getBrainDomain() != null ? entry.getBrainDomain() : "");
        return metadata;
    }

    private double calculateQualityScore(KnowledgeQualityReport report) {
        if (report.getTotalKnowledge() == 0) return 0;
        
        double verifiedRatio = (double) report.getVerifiedCount() / report.getTotalKnowledge();
        double highImportanceRatio = (double) report.getHighImportanceCount() / report.getTotalKnowledge();
        double expiredRatio = (double) report.getExpiredCount() / report.getTotalKnowledge();
        double lowConfidenceRatio = (double) report.getLowConfidenceCount() / report.getTotalKnowledge();
        
        return verifiedRatio * 0.3 
             + highImportanceRatio * 0.3 
             + (1 - expiredRatio) * 0.2 
             + (1 - lowConfidenceRatio) * 0.2;
    }
}
