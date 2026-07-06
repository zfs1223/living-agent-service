package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.ClientUserBindingEntity;
import com.livingagent.core.database.entity.ClientUserBindingId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientUserBindingRepository extends JpaRepository<ClientUserBindingEntity, ClientUserBindingId> {

    /**
     * 根据 clientId 查找所有绑定
     */
    List<ClientUserBindingEntity> findByClientId(String clientId);

    /**
     * 根据 userId 查找所有绑定
     */
    List<ClientUserBindingEntity> findByUserId(String userId);

    /**
     * 根据 clientId 和 userId 查找绑定
     */
    Optional<ClientUserBindingEntity> findByClientIdAndUserId(String clientId, String userId);

    /**
     * 根据 clientId 查找最新的绑定（按 lastActiveAt 降序）
     */
    @Query("SELECT b FROM ClientUserBindingEntity b WHERE b.clientId = :clientId ORDER BY b.lastActiveAt DESC")
    List<ClientUserBindingEntity> findLatestByClientId(@Param("clientId") String clientId);

    /**
     * 根据租户 ID 查找所有绑定
     */
    List<ClientUserBindingEntity> findByTenantId(String tenantId);

    /**
     * 删除指定 clientId 的所有绑定
     */
    void deleteByClientId(String clientId);

    /**
     * 删除指定 userId 的所有绑定
     */
    void deleteByUserId(String userId);
}
