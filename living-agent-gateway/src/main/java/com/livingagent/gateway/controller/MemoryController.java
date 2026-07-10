package com.livingagent.gateway.controller;

import com.livingagent.core.memory.Memory;
import com.livingagent.core.memory.MemoryCategory;
import com.livingagent.core.memory.MemoryEntry;
import com.livingagent.core.memory.feedback.MemoryConversionTracker;
import com.livingagent.gateway.controller.common.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * P48: 记忆管理 REST API。
 * 
 * 端点：
 * - GET /api/memories - 列表查询
 * - GET /api/memories/stats - 统计接口
 * - GET /api/memories/{id} - 详情查询
 * - DELETE /api/memories/{id} - 删除操作
 * - GET /api/memories/search - 搜索功能
 */
@RestController
@RequestMapping("/api/memories")
public class MemoryController {

    private static final Logger log = LoggerFactory.getLogger(MemoryController.class);

    private final Memory memory;
    private final MemoryConversionTracker conversionTracker;

    public MemoryController(Memory memory, MemoryConversionTracker conversionTracker) {
        this.memory = memory;
        this.conversionTracker = conversionTracker;
    }

    /**
     * GET /api/memories - 列表查询
     * 
     * @param employeeId 可选，会话ID
     * @param type 可选，类型筛选（CORE/DAILY/CONVERSATION/CUSTOM）
     * @param limit 可选，数量限制
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<MemoryEntry>>> listMemories(
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "100") int limit) {
        
        try {
            MemoryCategory category = null;
            if (type != null && !type.isBlank()) {
                try {
                    category = MemoryCategory.valueOf(type.toUpperCase());
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid memory category: {}", type);
                }
            }
            
            String sessionId = employeeId;
            List<MemoryEntry> entries = memory.list(category, sessionId).join();
            
            // 限制返回数量
            if (entries.size() > limit) {
                entries = entries.subList(0, limit);
            }
            
            return ResponseEntity.ok(ApiResponse.ok(entries));
        } catch (Exception e) {
            log.error("Failed to list memories: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.err("list_failed", "Failed to list memories: " + e.getMessage()));
        }
    }

    /**
     * GET /api/memories/stats - 统计接口
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMemoryStats() {
        try {
            int totalCount = memory.count().join();
            MemoryConversionTracker.MemoryConversionReport report = conversionTracker.getReport();
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("total_count", totalCount);
            stats.put("memories_created", report.memoriesCreated());
            stats.put("knowledge_extracted", report.knowledgeExtracted());
            stats.put("knowledge_referenced", report.knowledgeReferenced());
            stats.put("memories_archived", report.memoriesArchived());
            stats.put("conversion_rate", report.conversionRate());
            stats.put("reference_rate", report.referenceRate());
            stats.put("archive_rate", report.archiveRate());
            stats.put("captured_at", report.capturedAt().toString());
            
            return ResponseEntity.ok(ApiResponse.ok(stats));
        } catch (Exception e) {
            log.error("Failed to get memory stats: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.err("stats_failed", "Failed to get memory stats: " + e.getMessage()));
        }
    }

    /**
     * GET /api/memories/{id} - 详情查询
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MemoryEntry>> getMemory(@PathVariable String id) {
        try {
            Optional<MemoryEntry> entry = memory.get(id).join();
            if (entry.isPresent()) {
                return ResponseEntity.ok(ApiResponse.ok(entry.get()));
            } else {
                return ResponseEntity.status(404)
                        .body(ApiResponse.err("not_found", "Memory not found: " + id));
            }
        } catch (Exception e) {
            log.error("Failed to get memory {}: {}", id, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.err("get_failed", "Failed to get memory: " + e.getMessage()));
        }
    }

    /**
     * DELETE /api/memories/{id} - 删除操作
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteMemory(@PathVariable String id) {
        try {
            boolean deleted = memory.forget(id).join();
            if (deleted) {
                conversionTracker.recordMemoryArchived();
                return ResponseEntity.ok(ApiResponse.ok(null));
            } else {
                return ResponseEntity.status(404)
                        .body(ApiResponse.err("not_found", "Memory not found: " + id));
            }
        } catch (Exception e) {
            log.error("Failed to delete memory {}: {}", id, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.err("delete_failed", "Failed to delete memory: " + e.getMessage()));
        }
    }

    /**
     * GET /api/memories/search - 搜索功能
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<MemoryEntry>>> searchMemories(
            @RequestParam String q,
            @RequestParam(defaultValue = "50") int limit) {
        
        try {
            List<MemoryEntry> results = memory.recall(q, limit, null).join();
            return ResponseEntity.ok(ApiResponse.ok(results));
        } catch (Exception e) {
            log.error("Failed to search memories: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.err("search_failed", "Failed to search memories: " + e.getMessage()));
        }
    }
}