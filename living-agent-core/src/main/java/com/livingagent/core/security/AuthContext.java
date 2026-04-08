package com.livingagent.core.security;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AuthContext {

    private String employeeId;
    private String name;
    private String phone;
    private String email;
    private String department;
    private String position;
    private UserIdentity identity;
    private AccessLevel accessLevel;
    private boolean founder;
    private Set<String> allowedBrains;
    private String voicePrintId;
    private String oauthProvider;
    private String oauthUserId;
    private Instant joinDate;
    private Instant leaveDate;
    private Instant lastSyncTime;
    private String syncSource;
    private Map<String, Object> metadata;
    private boolean active;
    private String sessionId;

    public AuthContext() {
        this.identity = UserIdentity.EXTERNAL_VISITOR;
        this.accessLevel = AccessLevel.CHAT_ONLY;
        this.allowedBrains = new HashSet<>();
        this.metadata = new HashMap<>();
        this.active = true;
    }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }

    public UserIdentity getIdentity() { return identity; }
    public void setIdentity(UserIdentity identity) { this.identity = identity; }

    public AccessLevel getAccessLevel() { return accessLevel; }
    public void setAccessLevel(AccessLevel accessLevel) { this.accessLevel = accessLevel; }

    public boolean isFounder() { return founder; }
    public void setFounder(boolean founder) { this.founder = founder; }

    public Set<String> getAllowedBrains() { return allowedBrains; }
    public void setAllowedBrains(Set<String> allowedBrains) { this.allowedBrains = allowedBrains; }

    public String getVoicePrintId() { return voicePrintId; }
    public void setVoicePrintId(String voicePrintId) { this.voicePrintId = voicePrintId; }

    public String getOauthProvider() { return oauthProvider; }
    public void setOauthProvider(String oauthProvider) { this.oauthProvider = oauthProvider; }

    public String getOauthUserId() { return oauthUserId; }
    public void setOauthUserId(String oauthUserId) { this.oauthUserId = oauthUserId; }

    public Instant getJoinDate() { return joinDate; }
    public void setJoinDate(Instant joinDate) { this.joinDate = joinDate; }

    public Instant getLeaveDate() { return leaveDate; }
    public void setLeaveDate(Instant leaveDate) { this.leaveDate = leaveDate; }

    public Instant getLastSyncTime() { return lastSyncTime; }
    public void setLastSyncTime(Instant lastSyncTime) { this.lastSyncTime = lastSyncTime; }

    public String getSyncSource() { return syncSource; }
    public void setSyncSource(String syncSource) { this.syncSource = syncSource; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public boolean hasPermission(AccessLevel required) {
        return this.accessLevel.ordinal() >= required.ordinal();
    }

    public boolean isInternal() {
        return identity != null && identity.name().startsWith("INTERNAL_");
    }

    public boolean canAccessBrain(String brainName) {
        if (accessLevel == AccessLevel.FULL) return true;
        if (allowedBrains == null || allowedBrains.isEmpty()) return false;
        return allowedBrains.contains(brainName);
    }

    public static AuthContext fromEmployeeId(String employeeId) {
        AuthContext ctx = new AuthContext();
        ctx.setEmployeeId(employeeId);
        return ctx;
    }

    public static AuthContext visitor(String sessionId) {
        AuthContext ctx = new AuthContext();
        ctx.setSessionId(sessionId);
        ctx.setIdentity(UserIdentity.EXTERNAL_VISITOR);
        ctx.setAccessLevel(AccessLevel.CHAT_ONLY);
        return ctx;
    }

    @Override
    public String toString() {
        return String.format("AuthContext{id=%s, name=%s, dept=%s, identity=%s, access=%s}",
            employeeId, name, department, identity, accessLevel);
    }
}
