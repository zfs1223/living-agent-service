package com.livingagent.core.security;

import java.util.Optional;

public interface AccessGateService {

    Optional<GateDecision> evaluate(String employeeId, String targetType, String targetName);

    boolean canRoute(String employeeId, String targetType, String targetName);

    String resolveRouteTarget(String employeeId, String targetType, String targetName);

    boolean hasFullAccess(String employeeId);

    boolean belongsToDepartment(String employeeId, String departmentCode);

    final class GateDecision {
        private final boolean allowed;
        private final AccessLevel accessLevel;
        private final String routeTarget;
        private final String reason;

        public GateDecision(boolean allowed, AccessLevel accessLevel, String routeTarget, String reason) {
            this.allowed = allowed;
            this.accessLevel = accessLevel;
            this.routeTarget = routeTarget;
            this.reason = reason;
        }

        public boolean isAllowed() {
            return allowed;
        }

        public AccessLevel getAccessLevel() {
            return accessLevel;
        }

        public String getRouteTarget() {
            return routeTarget;
        }

        public String getReason() {
            return reason;
        }
    }
}
