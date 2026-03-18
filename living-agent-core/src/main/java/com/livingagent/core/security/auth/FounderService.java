package com.livingagent.core.security.auth;

import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.security.Employee;
import com.livingagent.core.security.UserIdentity;
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

    public void assignFounderRole(Employee employee) {
        if (founderExists.get()) {
            log.warn("Founder already exists, cannot assign founder role to {}", employee.getName());
            return;
        }

        employee.setIdentity(UserIdentity.INTERNAL_CHAIRMAN);
        employee.setAccessLevel(AccessLevel.FULL);
        employee.setFounder(true);
        employee.setPosition("董事长");

        founderExists.set(true);

        log.info("Assigned founder (Chairman) role to employee: {} ({})", 
                employee.getName(), employee.getEmployeeId());
    }

    public boolean hasFounder() {
        initialize();
        return founderExists.get();
    }

    public interface FounderCheckStrategy {
        boolean hasAnyEmployee();
        boolean hasFounder();
    }
}
