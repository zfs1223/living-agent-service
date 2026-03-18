package com.livingagent.gateway.controller;

import com.livingagent.core.knowledge.*;
import com.livingagent.core.knowledge.KnowledgeManager.KnowledgeLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeController.class);

    private final KnowledgeManager knowledgeManager;

    public KnowledgeController(KnowledgeManager knowledgeManager) {
        this.knowledgeManager = knowledgeManager;
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<KnowledgeEntryResponse>>> searchKnowledge(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String layer,
            @RequestParam(required = false) String type
    ) {
        log.debug("Searching knowledge: query={}, limit={}", query, limit);

        List<KnowledgeEntry> entries = knowledgeManager.search(query, limit);
        List<KnowledgeEntryResponse> response = entries.stream()
                .map(this::convertToResponse)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<KnowledgeEntryResponse>> getKnowledge(@PathVariable String id) {
        log.debug("Getting knowledge entry: {}", id);

        Optional<KnowledgeEntry> entryOpt = knowledgeManager.retrieve(id);
        if (entryOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("not_found", "Knowledge entry not found: " + id));
        }

        KnowledgeEntryResponse response = convertToResponse(entryOpt.get());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<CategoryInfo>>> getCategories() {
        log.debug("Getting knowledge categories");

        List<CategoryInfo> categories = Arrays.stream(KnowledgeType.values())
                .map(type -> new CategoryInfo(
                        type.name(),
                        type.getDescription(),
                        0
                ))
                .toList();

        return ResponseEntity.ok(ApiResponse.success(categories));
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<ApiResponse<List<KnowledgeEntryResponse>>> getByCategory(
            @PathVariable String category,
            @RequestParam(defaultValue = "20") int limit
    ) {
        log.debug("Getting knowledge by category: {}", category);

        KnowledgeType type;
        try {
            type = KnowledgeType.valueOf(category.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("invalid_category", "Invalid category: " + category));
        }

        List<KnowledgeEntry> allEntries = knowledgeManager.search("", 1000);
        List<KnowledgeEntry> entries = allEntries.stream()
                .filter(e -> e.getKnowledgeType() == type)
                .limit(limit)
                .toList();

        List<KnowledgeEntryResponse> response = entries.stream()
                .map(this::convertToResponse)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<KnowledgeEntryResponse>> createKnowledge(
            @RequestBody CreateKnowledgeRequest request
    ) {
        log.info("Creating knowledge entry: {}", request.key());

        KnowledgeEntry entry = new KnowledgeEntry(request.key(), request.content());
        
        if (request.category() != null) {
            entry.setCategory(request.category());
        }
        if (request.type() != null) {
            try {
                entry.setKnowledgeType(KnowledgeType.valueOf(request.type().toUpperCase()));
            } catch (IllegalArgumentException ignored) {}
        }
        if (request.importance() != null) {
            try {
                entry.setImportance(Importance.valueOf(request.importance().toUpperCase()));
            } catch (IllegalArgumentException ignored) {}
        }
        if (request.brainDomain() != null) {
            entry.setBrainDomain(request.brainDomain());
        }
        if (request.tags() != null) {
            entry.setTags(request.tags());
        }

        KnowledgeLayer layer = request.layer() != null 
                ? KnowledgeLayer.valueOf(request.layer().toUpperCase()) 
                : KnowledgeLayer.DOMAIN;

        switch (layer) {
            case PRIVATE -> knowledgeManager.storePrivate(
                    request.key(), 
                    request.content(), 
                    request.tags() != null ? new HashMap<>(request.tags()) : new HashMap<>()
            );
            case SHARED -> knowledgeManager.storeShared(
                    request.key(), 
                    request.content(), 
                    entry.getKnowledgeType(), 
                    entry.getImportance()
            );
            default -> knowledgeManager.storeDomain(
                    request.key(), 
                    request.content(), 
                    entry.getKnowledgeType(), 
                    entry.getImportance()
            );
        }

        KnowledgeEntryResponse response = convertToResponse(entry);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<KnowledgeEntryResponse>> updateKnowledge(
            @PathVariable String id,
            @RequestBody UpdateKnowledgeRequest request
    ) {
        log.info("Updating knowledge entry: {}", id);

        Optional<KnowledgeEntry> entryOpt = knowledgeManager.retrieve(id);
        if (entryOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("not_found", "Knowledge entry not found: " + id));
        }

        knowledgeManager.update(id, request.content());

        entryOpt = knowledgeManager.retrieve(id);
        KnowledgeEntryResponse response = convertToResponse(entryOpt.get());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteKnowledge(@PathVariable String id) {
        log.info("Deleting knowledge entry: {}", id);

        Optional<KnowledgeEntry> entryOpt = knowledgeManager.retrieve(id);
        if (entryOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("not_found", "Knowledge entry not found: " + id));
        }

        knowledgeManager.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{id}/favorite")
    public ResponseEntity<ApiResponse<Void>> favoriteKnowledge(@PathVariable String id) {
        log.info("Favoriting knowledge entry: {}", id);

        Optional<KnowledgeEntry> entryOpt = knowledgeManager.retrieve(id);
        if (entryOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("not_found", "Knowledge entry not found: " + id));
        }

        KnowledgeEntry entry = entryOpt.get();
        entry.addTag("favorite", "true");

        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/{id}/favorite")
    public ResponseEntity<ApiResponse<Void>> unfavoriteKnowledge(@PathVariable String id) {
        log.info("Unfavoriting knowledge entry: {}", id);

        Optional<KnowledgeEntry> entryOpt = knowledgeManager.retrieve(id);
        if (entryOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("not_found", "Knowledge entry not found: " + id));
        }

        KnowledgeEntry entry = entryOpt.get();
        entry.getTags().remove("favorite");

        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/favorites")
    public ResponseEntity<ApiResponse<List<KnowledgeEntryResponse>>> getFavorites(
            @RequestParam(defaultValue = "20") int limit
    ) {
        log.debug("Getting favorite knowledge entries");

        List<KnowledgeEntry> allEntries = knowledgeManager.search("", 1000);
        List<KnowledgeEntryResponse> favorites = allEntries.stream()
                .filter(e -> "true".equals(e.getTags().get("favorite")))
                .limit(limit)
                .map(this::convertToResponse)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(favorites));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<KnowledgeStats>> getStats() {
        log.debug("Getting knowledge statistics");

        Map<String, Object> stats = knowledgeManager.getStatistics();

        KnowledgeStats response = new KnowledgeStats(
                knowledgeManager.getPrivateKnowledgeCount(),
                knowledgeManager.getDomainKnowledgeCount(),
                knowledgeManager.getSharedKnowledgeCount(),
                stats
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private KnowledgeEntryResponse convertToResponse(KnowledgeEntry entry) {
        return new KnowledgeEntryResponse(
                entry.getEntryId(),
                entry.getKey(),
                entry.getContent(),
                entry.getCategory(),
                entry.getKnowledgeType() != null ? entry.getKnowledgeType().name() : null,
                entry.getImportance() != null ? entry.getImportance().name() : null,
                entry.getBrainDomain(),
                entry.getConfidence(),
                entry.isVerified(),
                entry.getTags(),
                entry.getCreatedAt(),
                entry.getUpdatedAt(),
                entry.getAccessCount(),
                entry.getRelevanceScore()
        );
    }

    public record ApiResponse<T>(
            boolean success,
            T data,
            String error,
            String errorDescription
    ) {
        public static <T> ApiResponse<T> success(T data) {
            return new ApiResponse<>(true, data, null, null);
        }

        public static <T> ApiResponse<T> error(String error, String description) {
            return new ApiResponse<>(false, null, error, description);
        }
    }

    public record KnowledgeEntryResponse(
            String id,
            String key,
            Object content,
            String category,
            String type,
            String importance,
            String brainDomain,
            double confidence,
            boolean verified,
            Map<String, String> tags,
            Instant createdAt,
            Instant updatedAt,
            int accessCount,
            double relevanceScore
    ) {}

    public record CategoryInfo(
            String id,
            String name,
            int count
    ) {}

    public record CreateKnowledgeRequest(
            String key,
            Object content,
            String category,
            String type,
            String importance,
            String layer,
            String brainDomain,
            Map<String, String> tags
    ) {}

    public record UpdateKnowledgeRequest(
            Object content,
            String category,
            String importance
    ) {}

    public record KnowledgeStats(
            int privateCount,
            int domainCount,
            int sharedCount,
            Map<String, Object> additionalStats
    ) {}
}
