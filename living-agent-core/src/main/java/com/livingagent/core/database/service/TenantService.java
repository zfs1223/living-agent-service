package com.livingagent.core.database.service;

import com.livingagent.core.database.entity.TenantEntity;
import com.livingagent.core.database.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class TenantService {

    private static final Logger log = LoggerFactory.getLogger(TenantService.class);

    private final TenantRepository tenantRepository;

    public TenantService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Transactional
    public TenantEntity createTenant(String tenantId, String name, String ownerId) {
        log.info("Creating tenant in database: {} with name: {}", tenantId, name);
        TenantEntity entity = new TenantEntity();
        entity.setTenantId(tenantId);
        entity.setName(name);
        entity.setNameEn(name);
        entity.setDescription("智能企业管理平台");
        entity.setWebsite("https://living-agent.example.com");
        entity.setOwnerId(ownerId);
        entity.setActive(true);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        
        TenantEntity saved = tenantRepository.saveAndFlush(entity);
        log.info("Successfully saved tenant in database: {}", saved.getTenantId());
        return saved;
    }

    public Optional<TenantEntity> findById(String tenantId) {
        return tenantRepository.findById(tenantId);
    }

    public List<TenantEntity> findAll() {
        return tenantRepository.findAll();
    }

    public List<TenantEntity> findAllActive() {
        return tenantRepository.findByActiveTrue();
    }

    @Transactional
    public Optional<TenantEntity> updateName(String tenantId, String name) {
        log.info("Updating tenant name in database: {} -> {}", tenantId, name);
        Optional<TenantEntity> opt = tenantRepository.findById(tenantId);
        if (opt.isEmpty()) {
            log.warn("Tenant {} not found in database, cannot update name", tenantId);
            return Optional.empty();
        }
        
        TenantEntity entity = opt.get();
        entity.setName(name);
        entity.setNameEn(name);
        entity.touch();
        
        TenantEntity saved = tenantRepository.saveAndFlush(entity);
        log.info("Successfully updated tenant name in database: {}", saved.getTenantId());
        return Optional.of(saved);
    }

    public boolean exists(String tenantId) {
        return tenantRepository.existsById(tenantId);
    }

    public boolean existsByOwnerId(String ownerId) {
        return tenantRepository.existsByOwnerId(ownerId);
    }
}
