package com.livingagent.core.security.client;

import com.livingagent.core.database.entity.ClientDeviceEntity;
import com.livingagent.core.database.repository.ClientDeviceRepository;
import com.livingagent.core.security.client.feedback.ClientDeviceHealthMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * 客户端设备注册服务
 * 
 * 职责：
 * 1. 注册或更新客户端设备信息
 * 2. 验证设备指纹唯一性（hostname + mac_address）
 * 3. 防止同一台机器注册多个 clientId
 * 4. 支持重装后找回原 clientId
 * 
 * 安全约束：
 * - 同一台机器（hostname + macAddress）只能有一个 clientId
 * - 如果设备已注册，返回原 clientId（即使客户端传了新的）
 * - 如果 clientId 冲突，抛出 DeviceConflictException
 */
@Service
public class ClientDeviceRegistryService {

    private static final Logger log = LoggerFactory.getLogger(ClientDeviceRegistryService.class);

    private final ClientDeviceRepository repository;
    private final ClientDeviceHealthMonitor deviceHealthMonitor;

    public ClientDeviceRegistryService(ClientDeviceRepository repository,
                                       ClientDeviceHealthMonitor deviceHealthMonitor) {
        this.repository = repository;
        this.deviceHealthMonitor = deviceHealthMonitor;
    }

    /**
     * 注册或更新客户端设备信息
     * 
     * @param info 设备信息
     * @return 注册成功的设备实体（可能返回原 clientId）
     * @throws DeviceConflictException 如果同一台机器尝试使用不同的 clientId
     */
    @Transactional
    public ClientDeviceEntity registerOrUpdate(ClientDeviceInfo info) {
        if (!info.isValid()) {
            throw new IllegalArgumentException("设备信息不完整：clientId 和 hostname 必须提供");
        }

        // 1. 先查找设备指纹对应的已注册设备
        Optional<ClientDeviceEntity> existingOpt = repository.findByHostnameAndMacAddress(
            info.hostname(), info.macAddress());

        if (existingOpt.isPresent()) {
            ClientDeviceEntity existing = existingOpt.get();
            
            // 2. 设备已注册，验证 clientId 是否匹配
            if (!existing.getClientId().equals(info.clientId())) {
                // 同一台机器尝试用不同的 clientId 注册 → 拒绝
                deviceHealthMonitor.recordOperation(existing.getClientId(), false);
                log.warn("[DeviceRegistry] 同一台机器尝试注册不同 clientId: " +
                    "existing={}, new={}, hostname={}, mac={}",
                    existing.getClientId(), info.clientId(), info.hostname(), info.macAddress());
                throw new DeviceConflictException(
                    "该设备已注册为 clientId=" + existing.getClientId() +
                    "，请使用原有 clientId 或联系管理员重置",
                    existing.getClientId(),
                    info.clientId(),
                    info.hostname(),
                    info.macAddress()
                );
            }
            
            // 3. clientId 匹配，更新活跃时间和 IP
            existing.setLastSeenAt(LocalDateTime.now());
            if (info.ipAddress() != null && !info.ipAddress().isBlank()) {
                existing.setIpAddress(info.ipAddress());
            }
            if (info.appVersion() != null && !info.appVersion().isBlank()) {
                existing.setAppVersion(info.appVersion());
            }
            if (info.applications() != null && !info.applications().isBlank()) {
                existing.setApplications(info.applications());
            }
            
            log.info("[DeviceRegistry] 设备活跃时间更新: clientId={}, hostname={}",
                existing.getClientId(), existing.getHostname());
            deviceHealthMonitor.recordOperation(existing.getClientId(), true);
            return repository.save(existing);
        }

        // 4. 新设备注册
        String clientId = (info.clientId() != null && !info.clientId().isBlank())
            ? info.clientId()
            : UUID.randomUUID().toString();

        ClientDeviceEntity entity = new ClientDeviceEntity();
        entity.setClientId(clientId);
        entity.setHostname(info.hostname());
        entity.setPlatform(info.platform() != null ? info.platform() : "unknown");
        entity.setOsUser(info.osUser());
        entity.setMacAddress(info.macAddress());
        entity.setIpAddress(info.ipAddress());
        entity.setAppVersion(info.appVersion());
        entity.setFirstSeenAt(LocalDateTime.now());
        entity.setLastSeenAt(LocalDateTime.now());
        entity.setStatus("active");
        entity.setTenantId(info.tenantId());

        log.info("[DeviceRegistry] 新设备注册: clientId={}, hostname={}, mac={}",
            clientId, info.hostname(), info.macAddress());
        deviceHealthMonitor.registerDevice(clientId);
        deviceHealthMonitor.recordOperation(clientId, true);
        return repository.save(entity);
    }

    /**
     * 验证 clientId 是否有效且与设备信息匹配
     * 
     * @param clientId 客户端 ID
     * @param info 设备信息
     * @return true 如果验证通过
     */
    public boolean validate(String clientId, ClientDeviceInfo info) {
        if (clientId == null || clientId.isBlank()) {
            return false;
        }

        Optional<ClientDeviceEntity> entityOpt = repository.findById(clientId);
        if (entityOpt.isEmpty()) {
            return false;
        }

        ClientDeviceEntity entity = entityOpt.get();
        if (!"active".equals(entity.getStatus())) {
            return false;
        }

        // 验证 hostname 和 mac_address 是否匹配
        boolean hostnameMatch = entity.getHostname().equals(info.hostname());
        boolean macMatch = entity.getMacAddress() == null || 
                          entity.getMacAddress().equals(info.macAddress());

        return hostnameMatch && macMatch;
    }

    /**
     * 根据 clientId 查找设备
     */
    public Optional<ClientDeviceEntity> findByClientId(String clientId) {
        return repository.findById(clientId);
    }

    /**
     * 根据设备指纹查找设备（用于重装后找回原 clientId）
     */
    public Optional<ClientDeviceEntity> findByFingerprint(String hostname, String macAddress) {
        return repository.findByHostnameAndMacAddress(hostname, macAddress);
    }

    /**
     * 更新设备的 nodeId 映射
     * 
     * @param clientId 客户端 ID
     * @param nodeId pywinauto 节点 ID
     * @return true 如果更新成功
     */
    @Transactional
    public boolean updateNodeId(String clientId, String nodeId) {
        Optional<ClientDeviceEntity> entityOpt = repository.findById(clientId);
        if (entityOpt.isEmpty()) {
            return false;
        }

        ClientDeviceEntity entity = entityOpt.get();
        entity.setNodeId(nodeId);
        repository.save(entity);

        log.info("[DeviceRegistry] 设备 nodeId 映射更新: clientId={}, nodeId={}", clientId, nodeId);
        return true;
    }

    /**
     * 标记设备为离线状态
     */
    @Transactional
    public void markOffline(String clientId) {
        Optional<ClientDeviceEntity> entityOpt = repository.findById(clientId);
        if (entityOpt.isPresent()) {
            ClientDeviceEntity entity = entityOpt.get();
            entity.setStatus("offline");
            repository.save(entity);
            deviceHealthMonitor.recordOperation(clientId, false);
            log.info("[DeviceRegistry] 设备标记为离线: clientId={}", clientId);
        }
    }

    /**
     * 标记设备为活跃状态
     */
    @Transactional
    public void markActive(String clientId) {
        Optional<ClientDeviceEntity> entityOpt = repository.findById(clientId);
        if (entityOpt.isPresent()) {
            ClientDeviceEntity entity = entityOpt.get();
            entity.setStatus("active");
            entity.setLastSeenAt(LocalDateTime.now());
            repository.save(entity);
            log.info("[DeviceRegistry] 设备标记为活跃: clientId={}", clientId);
        }
    }

    /**
     * 更新设备的应用列表（客户端主动上报）
     * 
     * @param clientId 客户端 ID
     * @param applications 应用列表（JSON 格式或逗号分隔）
     * @return true 如果更新成功
     */
    @Transactional
    public boolean updateApplications(String clientId, String applications) {
        if (clientId == null || clientId.isBlank()) {
            return false;
        }
        Optional<ClientDeviceEntity> entityOpt = repository.findById(clientId);
        if (entityOpt.isEmpty()) {
            log.warn("[DeviceRegistry] 更新应用列表失败：clientId 不存在: {}", clientId);
            return false;
        }

        ClientDeviceEntity entity = entityOpt.get();
        entity.setApplications(applications);
        repository.save(entity);

        log.info("[DeviceRegistry] 设备应用列表更新: clientId={}, apps={}", clientId, applications);
        return true;
    }

    /**
     * 追加应用（操作时发现新应用）
     * 如果应用已存在则不重复添加
     * 
     * @param clientId 客户端 ID
     * @param appName 应用名称
     * @return true 如果追加成功
     */
    @Transactional
    public boolean appendApplication(String clientId, String appName) {
        if (clientId == null || clientId.isBlank() || appName == null || appName.isBlank()) {
            return false;
        }
        Optional<ClientDeviceEntity> entityOpt = repository.findById(clientId);
        if (entityOpt.isEmpty()) {
            return false;
        }

        ClientDeviceEntity entity = entityOpt.get();
        String currentApps = entity.getApplications();
        
        // 检查应用是否已存在
        if (currentApps != null && currentApps.contains(appName)) {
            log.debug("[DeviceRegistry] 应用已存在，跳过: clientId={}, app={}", clientId, appName);
            return true;
        }

        // 追加应用
        String newApps;
        if (currentApps == null || currentApps.isBlank()) {
            newApps = appName;
        } else {
            newApps = currentApps + "," + appName;
        }
        entity.setApplications(newApps);
        repository.save(entity);

        log.info("[DeviceRegistry] 追加应用: clientId={}, app={}, total={}", clientId, appName, newApps);
        return true;
    }

    /**
     * 获取设备的应用列表
     * 
     * @param clientId 客户端 ID
     * @return 应用列表（可能为 null）
     */
    public String getApplications(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return null;
        }
        return repository.findById(clientId)
            .map(ClientDeviceEntity::getApplications)
            .orElse(null);
    }
}
