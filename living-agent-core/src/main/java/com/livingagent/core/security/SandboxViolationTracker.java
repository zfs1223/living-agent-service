package com.livingagent.core.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * P30-A: 沙箱违规追踪器。
 * 追踪每个 brain 的违规次数，达到阈值后自动加入黑名单并收紧边界，TTL 到期后自动恢复。
 */
@Service
public class SandboxViolationTracker {

    private static final Logger log = LoggerFactory.getLogger(SandboxViolationTracker.class);

    private static final int VIOLATION_THRESHOLD = 3;
    private static final long BLACKLIST_TTL_SECONDS = 3600;

    private final Map<String, ViolationRecord> violations = new ConcurrentHashMap<>();
    private final Map<String, BlacklistEntry> blacklist = new ConcurrentHashMap<>();

    public record ViolationRecord(
        String brainId,
        List<Instant> violationTimes,
        List<String> violationTypes
    ) {
        public ViolationRecord(String brainId) {
            this(brainId, new ArrayList<>(), new ArrayList<>());
        }

        public ViolationRecord addViolation(String type) {
            List<Instant> times = new ArrayList<>(violationTimes);
            List<String> types = new ArrayList<>(violationTypes);
            times.add(Instant.now());
            types.add(type);
            return new ViolationRecord(brainId, times, types);
        }

        public int count() { return violationTimes.size(); }

        public int countSince(Instant since) {
            return (int) violationTimes.stream().filter(t -> t.isAfter(since)).count();
        }
    }

    public record BlacklistEntry(
        String brainId,
        Instant blacklistedAt,
        Instant expiresAt,
        String reason,
        int violationCount
    ) {
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    public void recordViolation(String brainId, String violationType) {
        violations.compute(brainId, (k, existing) ->
            existing == null ? new ViolationRecord(brainId).addViolation(violationType)
                            : existing.addViolation(violationType));

        ViolationRecord record = violations.get(brainId);
        int recentCount = record.countSince(Instant.now().minusSeconds(3600));
        log.warn("P30-A: Violation recorded: brain={}, type={}, recentCount={}/{}",
            brainId, violationType, recentCount, VIOLATION_THRESHOLD);

        if (recentCount >= VIOLATION_THRESHOLD && !isBlacklisted(brainId)) {
            addToBlacklist(brainId, "Exceeded violation threshold: " + recentCount + " violations in 1h", recentCount);
        }
    }

    public boolean isBlacklisted(String brainId) {
        BlacklistEntry entry = blacklist.get(brainId);
        if (entry == null) return false;
        if (entry.isExpired()) {
            blacklist.remove(brainId);
            log.info("P30-A: Blacklist entry expired, removed: brain={}", brainId);
            return false;
        }
        return true;
    }

    public void addToBlacklist(String brainId, String reason, int violationCount) {
        Instant now = Instant.now();
        BlacklistEntry entry = new BlacklistEntry(
            brainId, now,
            now.plusSeconds(BLACKLIST_TTL_SECONDS),
            reason, violationCount);
        blacklist.put(brainId, entry);
        log.error("P30-A: Brain added to blacklist: brain={}, reason={}, TTL={}s",
            brainId, reason, BLACKLIST_TTL_SECONDS);
    }

    public void removeFromBlacklist(String brainId) {
        BlacklistEntry removed = blacklist.remove(brainId);
        if (removed != null) {
            violations.remove(brainId);
            log.info("P30-A: Brain removed from blacklist: brain={}", brainId);
        }
    }

    public Optional<BlacklistEntry> getBlacklistEntry(String brainId) {
        BlacklistEntry entry = blacklist.get(brainId);
        if (entry != null && entry.isExpired()) {
            blacklist.remove(brainId);
            return Optional.empty();
        }
        return Optional.ofNullable(entry);
    }

    public List<BlacklistEntry> getAllBlacklisted() {
        List<BlacklistEntry> result = new ArrayList<>();
        Iterator<Map.Entry<String, BlacklistEntry>> it = blacklist.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, BlacklistEntry> entry = it.next();
            if (entry.getValue().isExpired()) {
                it.remove();
            } else {
                result.add(entry.getValue());
            }
        }
        return result;
    }

    public ViolationRecord getViolationRecord(String brainId) {
        return violations.get(brainId);
    }

    public int getViolationCount(String brainId) {
        ViolationRecord record = violations.get(brainId);
        return record != null ? record.count() : 0;
    }

    public int getRecentViolationCount(String brainId) {
        ViolationRecord record = violations.get(brainId);
        return record != null ? record.countSince(Instant.now().minusSeconds(3600)) : 0;
    }
}
