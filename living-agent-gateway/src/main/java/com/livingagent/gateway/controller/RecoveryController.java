package com.livingagent.gateway.controller;

import com.livingagent.core.security.AccessGateService;
import com.livingagent.gateway.controller.common.ApiResponse;
import com.livingagent.gateway.service.BackupRecoveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recovery")
public class RecoveryController {

    private final BackupRecoveryService backupRecoveryService;
    private final AccessGateService accessGateService;

    public RecoveryController(BackupRecoveryService backupRecoveryService, AccessGateService accessGateService) {
        this.backupRecoveryService = backupRecoveryService;
        this.accessGateService = accessGateService;
    }

    @PostMapping("/snapshot")
    public ResponseEntity<ApiResponse<BackupRecoveryService.BackupSnapshot>> createSnapshot(
            @RequestBody Map<String, Object> payload,
            @RequestParam String scope,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        return ResponseEntity.ok(ApiResponse.ok(backupRecoveryService.createSnapshot(scope, payload, employeeId == null ? "system" : employeeId)));
    }

    @GetMapping("/snapshots")
    public ResponseEntity<ApiResponse<List<BackupRecoveryService.BackupSnapshot>>> listSnapshots(
            @RequestParam(required = false) String scope,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        return ResponseEntity.ok(ApiResponse.ok(backupRecoveryService.listSnapshots(scope)));
    }

    @PostMapping("/snapshots/{snapshotId}/restore")
    public ResponseEntity<ApiResponse<Map<String, Object>>> restoreSnapshot(
            @PathVariable String snapshotId,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        boolean restored = backupRecoveryService.restoreSnapshot(snapshotId);
        if (!restored) {
            return ResponseEntity.status(404).body(ApiResponse.err("not_found", "Snapshot not found"));
        }
        return ResponseEntity.ok(ApiResponse.ok(Map.of("snapshotId", snapshotId, "restored", true)));
    }

    @PostMapping("/snapshots/{snapshotId}/verify")
    public ResponseEntity<ApiResponse<BackupRecoveryService.ConsistencyReport>> verifyConsistency(
            @PathVariable String snapshotId,
            @RequestBody Map<String, Object> currentState,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        return ResponseEntity.ok(ApiResponse.ok(backupRecoveryService.verifyConsistency(snapshotId, currentState)));
    }

}
