package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.ClientDeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientDeviceRepository extends JpaRepository<ClientDeviceEntity, String> {

    /**
     * 根据 hostname 和 mac_address 查找设备（设备指纹）
     */
    @Query("SELECT c FROM ClientDeviceEntity c WHERE c.hostname = :hostname AND c.macAddress = :macAddress")
    Optional<ClientDeviceEntity> findByHostnameAndMacAddress(
            @Param("hostname") String hostname,
            @Param("macAddress") String macAddress);

    /**
     * 根据 nodeId 查找设备
     */
    Optional<ClientDeviceEntity> findByNodeId(String nodeId);

    /**
     * 根据状态查找设备列表
     */
    List<ClientDeviceEntity> findByStatus(String status);

    /**
     * 根据租户 ID 查找设备列表
     */
    List<ClientDeviceEntity> findByTenantId(String tenantId);

    /**
     * 根据状态和租户 ID 查找设备列表
     */
    List<ClientDeviceEntity> findByStatusAndTenantId(String status, String tenantId);

    /**
     * 检查是否存在相同的 hostname 和 mac_address
     */
    boolean existsByHostnameAndMacAddress(String hostname, String macAddress);
}
