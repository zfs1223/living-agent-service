package com.livingagent.core.knowledge;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface LayeredKnowledgeBase extends KnowledgeBase {

    void store(String key, Object knowledge, KnowledgeScope scope, String scopeIdentifier, Map<String, String> metadata);

    Optional<Object> retrieve(String key, KnowledgeScope scope, String scopeIdentifier);

    Optional<KnowledgeEntry> retrieveEntry(String key, KnowledgeScope scope, String scopeIdentifier);

    List<KnowledgeEntry> search(String query, KnowledgeScope scope, String scopeIdentifier);

    List<KnowledgeEntry> getByScope(KnowledgeScope scope, String scopeIdentifier);

    List<KnowledgeEntry> searchAccessible(String query, String profileId, String departmentId);

    boolean hasAccess(String profileId, KnowledgeScope scope, String scopeIdentifier);

    void promoteKnowledge(String key, KnowledgeScope fromScope, String fromIdentifier,
                          KnowledgeScope toScope, String toIdentifier);

    void shareToDepartment(String key, String profileId, String departmentId);

    void shareToGlobal(String key, String departmentId);

    KnowledgeStatistics getStatisticsByScope(KnowledgeScope scope, String scopeIdentifier);

    List<KnowledgeEntry> getPrivateKnowledge(String profileId);

    List<KnowledgeEntry> getDepartmentKnowledge(String departmentId);

    List<KnowledgeEntry> getSharedKnowledge();

    void grantAccess(String key, String profileId, KnowledgeScope scope, String scopeIdentifier);

    void revokeAccess(String key, String profileId, KnowledgeScope scope, String scopeIdentifier);

    List<String> getAccessibleProfiles(String key, KnowledgeScope scope, String scopeIdentifier);

    record KnowledgeStatistics(
            int totalEntries,
            long totalSize,
            int experienceCount,
            int bestPracticeCount,
            double averageRelevance,
            int accessCount
    ) {}
}
