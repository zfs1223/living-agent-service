package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.TenantEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TenantRepository extends JpaRepository<TenantEntity, String> {

    List<TenantEntity> findByActiveTrue();
    
    List<TenantEntity> findByOwnerId(String ownerId);
    
    boolean existsByOwnerId(String ownerId);
}
