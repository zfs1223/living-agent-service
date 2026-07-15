package com.livingagent.gateway.controller;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.livingagent.gateway.controller.common.ApiResponse;

@RestController
@RequestMapping("/api/collaborations")
public class CollaborationController {

    private static final Logger log = LoggerFactory.getLogger(CollaborationController.class);
    private final Map<String, Map<String, Object>> store = new ConcurrentHashMap<>();
    private final AtomicLong idSeq = new AtomicLong(1);

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listCollaborations(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status) {
        var items = store.values().stream()
                .filter(m -> type == null || type.equals(m.get("type")))
                .filter(m -> status == null || status.equals(m.get("status")))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(items));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCollaboration(@PathVariable String id) {
        var item = store.get(id);
        if (item == null) {
            return ResponseEntity.status(404).body(ApiResponse.err("not_found", "Collaboration not found: " + id));
        }
        return ResponseEntity.ok(ApiResponse.ok(item));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> createCollaboration(@RequestBody Map<String, Object> body) {
        String id = String.valueOf(idSeq.getAndIncrement());
        body.put("id", id);
        body.putIfAbsent("status", "DEVELOPER_WRITING");
        body.putIfAbsent("createdAt", java.time.Instant.now().toString());
        body.putIfAbsent("updatedAt", java.time.Instant.now().toString());
        store.put(id, body);
        log.info("Created collaboration: id={}, type={}", id, body.get("type"));
        return ResponseEntity.ok(ApiResponse.ok(body));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<ApiResponse<Map<String, Object>>> submitCode(@PathVariable String id, @RequestBody Map<String, Object> body) {
        var item = store.get(id);
        if (item == null) return ResponseEntity.status(404).body(ApiResponse.err("not_found", "Not found: " + id));
        item.put("status", "CODE_SUBMITTED");
        item.put("updatedAt", java.time.Instant.now().toString());
        return ResponseEntity.ok(ApiResponse.ok(item));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<Map<String, Object>>> approveReview(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        var item = store.get(id);
        if (item == null) return ResponseEntity.status(404).body(ApiResponse.err("not_found", "Not found: " + id));
        item.put("status", "REVIEW_APPROVED");
        item.put("updatedAt", java.time.Instant.now().toString());
        return ResponseEntity.ok(ApiResponse.ok(item));
    }

    @PostMapping("/{id}/request-changes")
    public ResponseEntity<ApiResponse<Map<String, Object>>> requestChanges(@PathVariable String id, @RequestBody Map<String, Object> body) {
        var item = store.get(id);
        if (item == null) return ResponseEntity.status(404).body(ApiResponse.err("not_found", "Not found: " + id));
        item.put("status", "CHANGES_REQUESTED");
        item.put("updatedAt", java.time.Instant.now().toString());
        return ResponseEntity.ok(ApiResponse.ok(item));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCollaboration(@PathVariable String id) {
        store.remove(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
