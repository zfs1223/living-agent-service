package com.livingagent.core.security.client;

import com.livingagent.core.database.entity.ClientUserBindingEntity;
import com.livingagent.core.database.repository.ClientUserBindingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 客户端与用户绑定服务
 * 管理 clientId 与 userId 的临时绑定关系
 */
@Service
public class ClientUserBindingService {

    private static final Logger log = LoggerFactory.getLogger(ClientUserBindingService.class);

    private final ClientUserBindingRepository repository;

    public ClientUserBindingService(ClientUserBindingRepository repository) {
        this.repository = repository;
    }

    /**
     * 用户登录时绑定 clientId 与 userId
     */
    @Transactional
    public void bindOnLogin(String clientId, String userId, Integer accessLevel, String departmentCode, String tenantId) {
        if (clientId == null || clientId.isBlank() || userId == null || userId.isBlank()) {
            log.warn("绑定失败：clientId 或 userId 为空");
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        // 查找是否已存在绑定
        List<ClientUserBindingEntity> existing = repository.findByClientId(clientId);
        
        if (existing.isEmpty()) {
            // 新建绑定
            ClientUserBindingEntity entity = new ClientUserBindingEntity();
            entity.setClientId(clientId);
            entity.setUserId(userId);
            entity.setAccessLevel(accessLevel != null ? accessLevel : 0);
            entity.setDepartmentCode(departmentCode);
            entity.setTenantId(tenantId);
            entity.setBoundAt(now);
            entity.setLastActiveAt(now);
            repository.save(entity);
            log.info("新建客户端-用户绑定：clientId={}, userId={}, accessLevel={}", clientId, userId, accessLevel);
        } else {
            // 更新绑定（可能是同一用户重新登录）
            for (ClientUserBindingEntity entity : existing) {
                entity.setLastActiveAt(now);
                entity.setAccessLevel(accessLevel != null ? accessLevel : entity.getAccessLevel());
                entity.setDepartmentCode(departmentCode != null ? departmentCode : entity.getDepartmentCode());
                repository.save(entity);
            }
            log.info("更新客户端-用户绑定：clientId={}, userId={}, accessLevel={}", clientId, userId, accessLevel);
        }
    }

    /**
     * 用户登出时解绑 clientId 与 userId
     */
    @Transactional
    public void unbindOnLogout(String clientId, String userId) {
        if (clientId == null || clientId.isBlank() || userId == null || userId.isBlank()) {
            log.warn("解绑失败：clientId 或 userId 为空");
            return;
        }

        repository.findByClientIdAndUserId(clientId, userId).ifPresent(entity -> {
            repository.delete(entity);
            log.info("解绑客户端-用户：clientId={}, userId={}", clientId, userId);
        });
    }

    /**
     * 更新最后活跃时间
     */
    @Transactional
    public void updateLastActive(String clientId, String userId) {
        repository.findByClientIdAndUserId(clientId, userId).ifPresent(entity -> {
            entity.setLastActiveAt(LocalDateTime.now());
            repository.save(entity);
        });
    }

    /**
     * 查询 clientId 对应的用户权限
     */
    public Integer getAccessLevel(String clientId) {
        List<ClientUserBindingEntity> bindings = repository.findLatestByClientId(clientId);
        if (bindings.isEmpty()) {
            return null;
        }
        return bindings.get(0).getAccessLevel();
    }

    /**
     * 查询 clientId 对应的用户 ID
     */
    public String getUserId(String clientId) {
        List<ClientUserBindingEntity> bindings = repository.findLatestByClientId(clientId);
        if (bindings.isEmpty()) {
            return null;
        }
        return bindings.get(0).getUserId();
    }

    /**
     * 根据 userId 查询最近活跃的 clientId
     * 用于 WindowsAppTool 路由：用户→在线绑定→clientId
     */
    public String getLatestClientId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        List<ClientUserBindingEntity> bindings = repository.findByUserId(userId);
        if (bindings.isEmpty()) {
            return null;
        }
        // 按 lastActiveAt 降序排序，取最近活跃的
        return bindings.stream()
                .max((a, b) -> a.getLastActiveAt().compareTo(b.getLastActiveAt()))
                .map(ClientUserBindingEntity::getClientId)
                .orElse(null);
    }
}
