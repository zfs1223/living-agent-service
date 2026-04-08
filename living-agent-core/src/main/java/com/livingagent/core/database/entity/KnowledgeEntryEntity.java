package com.livingagent.core.database.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "knowledge_entries", indexes = {
    @Index(name = "idx_knowledge_entry_id", columnList = "entry_id"),
    @Index(name = "idx_knowledge_scope", columnList = "scope, scope_identifier"),
    @Index(name = "idx_knowledge_brain", columnList = "brain_domain"),
    @Index(name = "idx_knowledge_type", columnList = "knowledge_type"),
    @Index(name = "idx_knowledge_expires", columnList = "expires_at")
})
public class KnowledgeEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entry_id", unique = true, length = 64)
    private String entryId;

    @Column(name = "key", length = 256, nullable = false)
    private String key;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "knowledge_type", length = 32)
    private String knowledgeType;

    @Column(name = "importance", length = 16)
    private String importance;

    @Column(name = "validity", length = 16)
    private String validity;

    @Column(name = "scope", length = 16)
    private String scope;

    @Column(name = "scope_identifier", length = 128)
    private String scopeIdentifier;

    @Column(name = "brain_domain", length = 50)
    private String brainDomain;

    @Column(name = "neuron_id", length = 128)
    private String neuronId;

    @Column(name = "owner_id", length = 64)
    private String ownerId;

    @Column(name = "department_id", length = 64)
    private String departmentId;

    @Column(name = "vector_id", length = 64)
    private String vectorId;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "relevance")
    private Double relevance;

    @Column(name = "access_count")
    private Integer accessCount;

    @Column(name = "verified")
    private Boolean verified;

    @Column(name = "source", length = 64)
    private String source;

    @Column(name = "promoted_from", length = 256)
    private String promotedFrom;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "last_accessed_at")
    private Instant lastAccessedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @ElementCollection
    @CollectionTable(name = "knowledge_tags", joinColumns = @JoinColumn(name = "knowledge_id"))
    @MapKeyColumn(name = "tag_name")
    @Column(name = "tag_value")
    private Map<String, String> tags = new HashMap<>();

    @ElementCollection
    @CollectionTable(name = "knowledge_metadata", joinColumns = @JoinColumn(name = "knowledge_id"))
    @MapKeyColumn(name = "meta_key")
    @Column(name = "meta_value")
    private Map<String, String> metadata = new HashMap<>();

    public KnowledgeEntryEntity() {
        this.entryId = java.util.UUID.randomUUID().toString();
        this.knowledgeType = "FACT";
        this.importance = "MEDIUM";
        this.validity = "LONG_TERM";
        this.confidence = 0.5;
        this.relevance = 1.0;
        this.accessCount = 0;
        this.verified = false;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEntryId() { return entryId; }
    public void setEntryId(String entryId) { this.entryId = entryId; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getKnowledgeType() { return knowledgeType; }
    public void setKnowledgeType(String knowledgeType) { this.knowledgeType = knowledgeType; }

    public String getImportance() { return importance; }
    public void setImportance(String importance) { this.importance = importance; }

    public String getValidity() { return validity; }
    public void setValidity(String validity) { this.validity = validity; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public String getScopeIdentifier() { return scopeIdentifier; }
    public void setScopeIdentifier(String scopeIdentifier) { this.scopeIdentifier = scopeIdentifier; }

    public String getBrainDomain() { return brainDomain; }
    public void setBrainDomain(String brainDomain) { this.brainDomain = brainDomain; }

    public String getNeuronId() { return neuronId; }
    public void setNeuronId(String neuronId) { this.neuronId = neuronId; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public String getDepartmentId() { return departmentId; }
    public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }

    public String getVectorId() { return vectorId; }
    public void setVectorId(String vectorId) { this.vectorId = vectorId; }

    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }

    public Double getRelevance() { return relevance; }
    public void setRelevance(Double relevance) { this.relevance = relevance; }

    public Integer getAccessCount() { return accessCount; }
    public void setAccessCount(Integer accessCount) { this.accessCount = accessCount; }

    public Boolean getVerified() { return verified; }
    public void setVerified(Boolean verified) { this.verified = verified; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getPromotedFrom() { return promotedFrom; }
    public void setPromotedFrom(String promotedFrom) { this.promotedFrom = promotedFrom; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getLastAccessedAt() { return lastAccessedAt; }
    public void setLastAccessedAt(Instant lastAccessedAt) { this.lastAccessedAt = lastAccessedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public Map<String, String> getTags() { return tags; }
    public void setTags(Map<String, String> tags) { this.tags = tags; }

    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public void incrementAccessCount() {
        this.accessCount++;
        this.lastAccessedAt = Instant.now();
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
