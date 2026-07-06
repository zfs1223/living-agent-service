package com.livingagent.core.security.client;

/**
 * 设备冲突异常
 * 当同一台机器尝试使用不同的 clientId 注册时抛出
 */
public class DeviceConflictException extends RuntimeException {
    
    private final String existingClientId;
    private final String attemptedClientId;
    private final String hostname;
    private final String macAddress;

    public DeviceConflictException(String message, String existingClientId, String attemptedClientId,
                                   String hostname, String macAddress) {
        super(message);
        this.existingClientId = existingClientId;
        this.attemptedClientId = attemptedClientId;
        this.hostname = hostname;
        this.macAddress = macAddress;
    }

    public String getExistingClientId() {
        return existingClientId;
    }

    public String getAttemptedClientId() {
        return attemptedClientId;
    }

    public String getHostname() {
        return hostname;
    }

    public String getMacAddress() {
        return macAddress;
    }
}
