package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.KnowledgeEntryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface KnowledgeEntryRepository extends JpaRepository<KnowledgeEntryEntity, Long> {

    Optional<KnowledgeEntryEntity> findByEntryId(String entryId);

    Optional<KnowledgeEntryEntity> findByKey(String key);

    List<KnowledgeEntryEntity> findByScope(String scope);

    List<KnowledgeEntryEntity> findByScopeAndScopeIdentifier(String scope, String scopeIdentifier);

    List<KnowledgeEntryEntity> findByBrainDomain(String brainDomain);

    List<KnowledgeEntryEntity> findByKnowledgeType(String knowledgeType);

    List<KnowledgeEntryEntity> findByOwnerId(String ownerId);

    List<KnowledgeEntryEntity> findByDepartmentId(String departmentId);

    @Query("SELECT k FROM KnowledgeEntryEntity k WHERE k.key LIKE %:keyword% OR k.content LIKE %:keyword%")
    List<KnowledgeEntryEntity> searchByKeyword(@Param("keyword") String keyword);

    @Query("SELECT k FROM KnowledgeEntryEntity k WHERE k.scope = :scope AND k.scopeIdentifier = :identifier AND (k.key LIKE %:keyword% OR k.content LIKE %:keyword%)")
    List<KnowledgeEntryEntity> searchByKeywordInScope(
        @Param("keyword") String keyword,
        @Param("scope") String scope,
        @Param("identifier") String identifier
    );

    @Query("SELECT k FROM KnowledgeEntryEntity k WHERE k.scope = :scope AND k.scopeIdentifier = :identifier ORDER BY k.relevance DESC")
    List<KnowledgeEntryEntity> findByScopeOrderByRelevance(
        @Param("scope") String scope,
        @Param("identifier") String identifier
    );

    @Query("SELECT k FROM KnowledgeEntryEntity k WHERE k.scope IN ('L2_DEPARTMENT', 'L3_SHARED') AND k.departmentId = :departmentId ORDER BY k.relevance DESC")
    List<KnowledgeEntryEntity> findAccessibleByDepartment(@Param("departmentId") String departmentId);

    @Query("SELECT k FROM KnowledgeEntryEntity k WHERE k.scope = 'L3_SHARED' ORDER BY k.relevance DESC")
    List<KnowledgeEntryEntity> findSharedKnowledge();

    @Query("SELECT k FROM KnowledgeEntryEntity k WHERE k.expiresAt IS NOT NULL AND k.expiresAt < :threshold")
    List<KnowledgeEntryEntity> findExpiredBefore(@Param("threshold") Instant threshold);

    @Query("SELECT k FROM KnowledgeEntryEntity k WHERE k.updatedAt < :threshold AND k.accessCount = 0")
    List<KnowledgeEntryEntity> findUnusedBefore(@Param("threshold") Instant threshold);

    @Query("SELECT k FROM KnowledgeEntryEntity k ORDER BY k.accessCount DESC")
    List<KnowledgeEntryEntity> findMostAccessed(Pageable pageable);

    @Query("SELECT k FROM KnowledgeEntryEntity k ORDER BY k.updatedAt DESC")
    List<KnowledgeEntryEntity> findRecentlyUpdated(Pageable pageable);

    @Query("SELECT k FROM KnowledgeEntryEntity k WHERE k.vectorId IS NOT NULL")
    List<KnowledgeEntryEntity> findWithVectors();

    @Query("SELECT COUNT(k) FROM KnowledgeEntryEntity k WHERE k.scope = :scope")
    int countByScope(@Param("scope") String scope);

    @Query("SELECT COUNT(k) FROM KnowledgeEntryEntity k WHERE k.scope = :scope AND k.scopeIdentifier = :identifier")
    int countByScopeAndIdentifier(@Param("scope") String scope, @Param("identifier") String identifier);

    @Query("SELECT COUNT(k) FROM KnowledgeEntryEntity k WHERE k.brainDomain = :brainDomain")
    int countByBrainDomain(@Param("brainDomain") String brainDomain);

    boolean existsByKey(String key);

    void deleteByKey(String key);

    @Query("SELECT new map(k.scope as scope, COUNT(k) as count) FROM KnowledgeEntryEntity k GROUP BY k.scope")
    List<Object[]> getScopeStatistics();

    @Query("SELECT new map(k.knowledgeType as type, COUNT(k) as count) FROM KnowledgeEntryEntity k GROUP BY k.knowledgeType")
    List<Object[]> getTypeStatistics();

    @Query("SELECT AVG(k.relevance) FROM KnowledgeEntryEntity k WHERE k.scope = :scope")
    Double getAverageRelevanceByScope(@Param("scope") String scope);

    @Query("SELECT SUM(k.accessCount) FROM KnowledgeEntryEntity k")
    Long getTotalAccessCount();

    @Query("SELECT k FROM KnowledgeEntryEntity k WHERE k.scope = 'L1_PRIVATE' AND k.scopeIdentifier = :profileId")
    List<KnowledgeEntryEntity> findPrivateKnowledge(@Param("profileId") String profileId);

    @Query("SELECT k FROM KnowledgeEntryEntity k WHERE k.scope = 'L2_DEPARTMENT' AND k.scopeIdentifier = :departmentId")
    List<KnowledgeEntryEntity> findDepartmentKnowledge(@Param("departmentId") String departmentId);

    @Query("SELECT k FROM KnowledgeEntryEntity k WHERE k.confidence >= :minConfidence ORDER BY k.confidence DESC")
    List<KnowledgeEntryEntity> findByMinConfidence(@Param("minConfidence") Double minConfidence, Pageable pageable);

    @Query("SELECT k FROM KnowledgeEntryEntity k WHERE k.verified = true ORDER BY k.relevance DESC")
    List<KnowledgeEntryEntity> findVerifiedKnowledge(Pageable pageable);

    @Query("SELECT k FROM KnowledgeEntryEntity k WHERE k.neuronId = :neuronId ORDER BY k.updatedAt DESC")
    List<KnowledgeEntryEntity> findByNeuronId(@Param("neuronId") String neuronId);

    @Query("SELECT k FROM KnowledgeEntryEntity k WHERE k.promotedFrom IS NOT NULL")
    List<KnowledgeEntryEntity> findPromotedKnowledge();
}
