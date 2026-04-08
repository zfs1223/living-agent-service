package com.livingagent.core.security.auth;

import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.security.AuthContext;
import com.livingagent.core.security.UserIdentity;
import com.livingagent.core.security.service.EnterpriseEmployeeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

public class FounderService {

    private static final Logger log = LoggerFactory.getLogger(FounderService.class);

    private final FounderCheckStrategy checkStrategy;
    private final AtomicBoolean founderExists = new AtomicBoolean(false);
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    public FounderService(FounderCheckStrategy checkStrategy) {
        this.checkStrategy = checkStrategy;
    }

    public synchronized void initialize() {
        if (initialized.get()) {
            return;
        }
        founderExists.set(checkStrategy.hasFounder());
        initialized.set(true);
        log.info("FounderService initialized, founder exists: {}", founderExists.get());
    }

    public boolean isFirstUser() {
        initialize();
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
        initialize();
        return founderExists.get();
    }

    public void refreshFromDatabase() {
        founderExists.set(checkStrategy.hasFounder());
        log.info("Founder status refreshed from database: {}", founderExists.get());
    }

    public void assignFounderRole(AuthContext authContext) {
        authContext.setIdentity(UserIdentity.INTERNAL_CHAIRMAN);
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
