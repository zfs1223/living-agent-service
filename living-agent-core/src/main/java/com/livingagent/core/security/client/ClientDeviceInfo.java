package com.livingagent.core.security.client;

/**
 * 客户端设备信息
 * 用于设备注册和验证的数据传输对象
 */
public record ClientDeviceInfo(
    String clientId,
    String hostname,
    String platform,
    String osUser,
    String macAddress,
    String ipAddress,
    String appVersion,
    String tenantId,
    String applications
) {
    /**
     * 创建最小化的设备信息（仅包含必要字段）
     */
    public static ClientDeviceInfo minimal(String clientId, String hostname, String macAddress) {
        return new ClientDeviceInfo(clientId, hostname, null, null, macAddress, null, null, null, null);
    }

    /**
     * 验证必要字段是否完整
     */
    public boolean isValid() {
        return clientId != null && !clientId.isBlank()
            && hostname != null && !hostname.isBlank();
    }
}
