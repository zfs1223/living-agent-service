package com.livingagent.gateway.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @deprecated 真实还原逻辑未实现（restoreSnapshot 仅检查快照是否存在），暂标记废弃。
 */
@Deprecated
@Service
public class BackupRecoveryService {

    private final Map<String, BackupSnapshot> snapshots = new ConcurrentHashMap<>();

    public BackupSnapshot createSnapshot(String scope, Map<String, Object> payload, String createdBy) {
        String snapshotId = scope + "_" + Instant.now().toEpochMilli();
        String checksum = checksum(payload);
        BackupSnapshot snapshot = new BackupSnapshot(
                snapshotId,
                scope,
                Instant.now(),
                createdBy,
                checksum,
                deepCopy(payload)
        );
        snapshots.put(snapshotId, snapshot);
        return snapshot;
    }

    public List<BackupSnapshot> listSnapshots(String scope) {
        List<BackupSnapshot> result = new ArrayList<>();
        for (BackupSnapshot snapshot : snapshots.values()) {
            if (scope == null || scope.isBlank() || Objects.equals(scope, snapshot.scope())) {
                result.add(snapshot);
            }
        }
        result.sort((a, b) -> a.createdAt().compareTo(b.createdAt()));
        return result;
    }

    public BackupSnapshot getSnapshot(String snapshotId) {
        return snapshots.get(snapshotId);
    }

    public boolean restoreSnapshot(String snapshotId) {
        return snapshots.containsKey(snapshotId);
    }

    public ConsistencyReport verifyConsistency(String snapshotId, Map<String, Object> currentState) {
        BackupSnapshot snapshot = snapshots.get(snapshotId);
        if (snapshot == null) {
            return new ConsistencyReport(snapshotId, false, "snapshot_not_found", List.of("Snapshot not found"));
        }

        String currentChecksum = checksum(currentState);
        boolean consistent = Objects.equals(snapshot.checksum(), currentChecksum);
        List<String> details = consistent
                ? List.of("checksum matched")
                : List.of("checksum mismatch", "snapshotChecksum=" + snapshot.checksum(), "currentChecksum=" + currentChecksum);

        return new ConsistencyReport(snapshotId, consistent, consistent ? "consistent" : "inconsistent", details);
    }

    private Map<String, Object> deepCopy(Map<String, Object> payload) {
        return payload == null ? Map.of() : new ConcurrentHashMap<>(payload);
    }

    private String checksum(Map<String, Object> payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String canonical = canonicalize(payload);
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String canonicalize(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return "{}";
        }
        return payload.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + '=' + String.valueOf(entry.getValue()))
                .reduce("{", (acc, item) -> acc.equals("{") ? acc + item : acc + "," + item) + "}";
    }

    public record BackupSnapshot(
            String snapshotId,
            String scope,
            Instant createdAt,
            String createdBy,
            String checksum,
            Map<String, Object> payload
    ) {}

    public record ConsistencyReport(
            String snapshotId,
            boolean consistent,
            String status,
            List<String> details
    ) {}
}
