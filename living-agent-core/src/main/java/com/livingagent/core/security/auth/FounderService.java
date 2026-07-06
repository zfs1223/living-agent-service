package com.livingagent.core.security.auth;

import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.security.AuthContext;
import com.livingagent.core.security.UserIdentity;
import com.livingagent.core.security.service.EnterpriseEmployeeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.time.Instant;

public class FounderService {

    private static final Logger log = LoggerFactory.getLogger(FounderService.class);

    private static final long CACHE_TTL_MS = 10000;

    private final FounderCheckStrategy checkStrategy;
    private final AtomicBoolean founderExists = new AtomicBoolean(false);
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private volatile long lastRefreshTime = 0;
    private volatile Boolean cachedFounderStatus = null;

    public FounderService(FounderCheckStrategy checkStrategy) {
        this.checkStrategy = checkStrategy;
    }

    public synchronized void initialize() {
        if (initialized.get()) {
            return;
        }
        refreshFromDatabase();
        initialized.set(true);
        log.info("FounderService initialized, founder exists: {}", founderExists.get());
    }

    public boolean isFirstUser() {
        refreshFromDatabase();
        if (founderExists.get()) {
            return false;
        }
        boolean hasAny = checkStrategy.hasAnyEmployee();
        return !hasAny;
    }

    public void markFounderRegistered() {
        founderExists.set(true);
        log.info("Founder marked as registered in memory cache");
    }

    public boolean hasFounder() {
        refreshFromDatabase();
        return founderExists.get();
    }

    public void refreshFromDatabase() {
        long now = Instant.now().toEpochMilli();
        if (now - lastRefreshTime < CACHE_TTL_MS && cachedFounderStatus != null) {
            // 缓存命中时不打印日志，避免频繁日志输出
            return;
        }

        boolean newStatus = checkStrategy.hasFounder();
        boolean statusChanged = (cachedFounderStatus == null || cachedFounderStatus != newStatus);

        founderExists.set(newStatus);
        cachedFounderStatus = newStatus;
        lastRefreshTime = now;

        // 只在状态变更时打印INFO日志，减少噪音
        if (statusChanged) {
            log.info("Founder status changed: {}", newStatus);
        }
    }

    public void assignFounderRole(AuthContext authContext) {
        authContext.setIdentity(UserIdentity.INTERNAL_ENTERPRISE);
        authContext.setAccessLevel(AccessLevel.FULL);
        authContext.setFounder(true);
        founderExists.set(true);
        log.info("Founder role assigned to: {}", authContext.getName());
    }

    public interface FounderCheckStrategy {
        boolean hasAnyEmployee();
        boolean hasFounder();
    }
}
