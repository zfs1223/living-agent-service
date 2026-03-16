package com.livingagent.core.evolution;

import com.livingagent.core.knowledge.BestPractice;
import java.util.List;
import java.util.Optional;

public interface KnowledgeEvolver {
    
    List<KnowledgeEvolution> extractKnowledgeFromConversation(String conversationId);
    
    KnowledgeMergeResult mergeKnowledge(String sourceId, String targetId);
    
    Optional<KnowledgeEvolution> evolveKnowledge(String knowledgeId);
    
    List<KnowledgeEvolution> findSimilarKnowledge(String query, double threshold);
    
    KnowledgePropagationResult propagateKnowledge(String sourceAgent, String targetAgent, String knowledgeId);
    
    List<BestPractice> extractBestPractices(String domain, int minSuccessRate);
    
    KnowledgeQualityReport assessQuality();
    
    void cleanupRedundantKnowledge();
    
    void updateRelevanceScores();
}
