package com.livingagent.core.security.service;

import com.livingagent.core.database.entity.EnterpriseEmployeeEntity;
import com.livingagent.core.database.repository.EnterpriseEmployeeRepository;
import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.security.AuthContext;
import com.livingagent.core.security.UserIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class EnterpriseEmployeeService {

    private static final Logger log = LoggerFactory.getLogger(EnterpriseEmployeeService.class);

    private final EnterpriseEmployeeRepository employeeRepository;

    public EnterpriseEmployeeService(EnterpriseEmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    public AuthContext createAuthContext(AuthContext authContext) {
        EnterpriseEmployeeEntity entity = toEntity(authContext);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        
        EnterpriseEmployeeEntity saved = employeeRepository.save(entity);
        log.info("Created auth context in database: {} ({})", authContext.getName(), authContext.getEmployeeId());
        
        return toDomain(saved);
    }

    public AuthContext updateAuthContext(AuthContext authContext) {
        Optional<EnterpriseEmployeeEntity> existingOpt = employeeRepository.findById(authContext.getEmployeeId());
        
        if (existingOpt.isEmpty()) {
            throw new IllegalArgumentException("Auth context not found: " + authContext.getEmployeeId());
        }
        
        EnterpriseEmployeeEntity entity = existingOpt.get();
        updateEntityFromDomain(entity, authContext);
        entity.setUpdatedAt(Instant.now());
        
        EnterpriseEmployeeEntity saved = employeeRepository.save(entity);
        log.info("Updated auth context in database: {} ({})", authContext.getName(), authContext.getEmployeeId());
        
        return toDomain(saved);
    }

    public Optional<AuthContext> findById(String employeeId) {
        return employeeRepository.findById(employeeId).map(this::toDomain);
    }

    public Optional<AuthContext> findByPhone(String phone) {
        return employeeRepository.findByPhone(phone).map(this::toDomain);
    }

    public Optional<AuthContext> findByEmail(String email) {
        return employeeRepository.findByEmail(email).map(this::toDomain);
    }

    public Optional<AuthContext> findByOAuth(String provider, String oauthUserId) {
        return employeeRepository.findByOauthProviderAndOauthUserId(provider, oauthUserId)
            .map(this::toDomain);
    }

    public List<AuthContext> findByDepartment(String departmentId) {
        return employeeRepository.findByDepartmentId(departmentId).stream()
            .map(this::toDomain)
            .collect(Collectors.toList());
    }

    public List<AuthContext> findAllActive() {
        return employeeRepository.findByActiveTrue().stream()
            .map(this::toDomain)
            .collect(Collectors.toList());
    }

    public List<AuthContext> findAll() {
        return employeeRepository.findAll().stream()
            .map(this::toDomain)
            .collect(Collectors.toList());
    }

    public void deleteAuthContext(String employeeId) {
        employeeRepository.deleteById(employeeId);
        log.info("Deleted auth context from database: {}", employeeId);
    }

    public boolean hasAnyAuthContext() {
        return employeeRepository.hasAnyEmployee();
    }

    public boolean hasFounder() {
        return employeeRepository.hasFounder();
    }

    public void setVoicePrintId(String employeeId, String voicePrintId) {
        employeeRepository.findById(employeeId).ifPresent(entity -> {
            entity.setVoicePrintId(voicePrintId);
            entity.setUpdatedAt(Instant.now());
            employeeRepository.save(entity);
            log.info("Set voice print ID for auth context {}: {}", employeeId, voicePrintId);
        });
    }

    public void linkOAuthAccount(String employeeId, String provider, String oauthUserId) {
        employeeRepository.findById(employeeId).ifPresent(entity -> {
            entity.setOauthProvider(provider);
            entity.setOauthUserId(oauthUserId);
            entity.setUpdatedAt(Instant.now());
            employeeRepository.save(entity);
            log.info("Linked OAuth account for auth context {}: {} - {}", employeeId, provider, oauthUserId);
        });
    }

    public void updateAuthContextStatus(String employeeId, UserIdentity newIdentity) {
        employeeRepository.findById(employeeId).ifPresent(entity -> {
            entity.setIdentity(newIdentity.name());
            entity.setAccessLevel(newIdentity.getDefaultAccessLevel().name());
            entity.setUpdatedAt(Instant.now());
            
            if (newIdentity == UserIdentity.INTERNAL_DEPARTED) {
                entity.setActive(false);
                entity.setLeaveDate(Instant.now());
            }
            
            employeeRepository.save(entity);
            log.info("Updated auth context {} status to {}", employeeId, newIdentity);
        });
    }

    private EnterpriseEmployeeEntity toEntity(AuthContext ctx) {
        EnterpriseEmployeeEntity entity = new EnterpriseEmployeeEntity();
        entity.setEmployeeId(ctx.getEmployeeId());
        entity.setName(ctx.getName());
        entity.setPhone(ctx.getPhone());
        entity.setEmail(ctx.getEmail());
        entity.setDepartmentId(ctx.getDepartment());
        entity.setDepartmentName(ctx.getDepartment());
        entity.setPosition(ctx.getPosition());
        entity.setIdentity(ctx.getIdentity() != null ? ctx.getIdentity().name() : UserIdentity.EXTERNAL_VISITOR.name());
        entity.setAccessLevel(ctx.getAccessLevel() != null ? ctx.getAccessLevel().name() : AccessLevel.CHAT_ONLY.name());
        entity.setFounder(ctx.isFounder());
        entity.setVoicePrintId(ctx.getVoicePrintId());
        entity.setOauthProvider(ctx.getOauthProvider());
        entity.setOauthUserId(ctx.getOauthUserId());
        entity.setJoinDate(ctx.getJoinDate());
        entity.setActive(ctx.isActive());
        entity.setSyncSource(ctx.getSyncSource());
        entity.setLastSyncTime(ctx.getLastSyncTime());
        return entity;
    }

    private void updateEntityFromDomain(EnterpriseEmployeeEntity entity, AuthContext ctx) {
        if (ctx.getName() != null) entity.setName(ctx.getName());
        if (ctx.getPhone() != null) entity.setPhone(ctx.getPhone());
        if (ctx.getEmail() != null) entity.setEmail(ctx.getEmail());
        if (ctx.getDepartment() != null) {
            entity.setDepartmentId(ctx.getDepartment());
            entity.setDepartmentName(ctx.getDepartment());
        }
        if (ctx.getPosition() != null) entity.setPosition(ctx.getPosition());
        if (ctx.getIdentity() != null) {
            entity.setIdentity(ctx.getIdentity().name());
            entity.setAccessLevel(ctx.getIdentity().getDefaultAccessLevel().name());
        }
        if (ctx.getAccessLevel() != null) {
            entity.setAccessLevel(ctx.getAccessLevel().name());
        }
        entity.setFounder(ctx.isFounder());
        entity.setActive(ctx.isActive());
        if (ctx.getVoicePrintId() != null) entity.setVoicePrintId(ctx.getVoicePrintId());
        if (ctx.getOauthProvider() != null) entity.setOauthProvider(ctx.getOauthProvider());
        if (ctx.getOauthUserId() != null) entity.setOauthUserId(ctx.getOauthUserId());
    }

    private AuthContext toDomain(EnterpriseEmployeeEntity entity) {
        AuthContext ctx = new AuthContext();
        ctx.setEmployeeId(entity.getEmployeeId());
        ctx.setName(entity.getName());
        ctx.setPhone(entity.getPhone());
        ctx.setEmail(entity.getEmail());
        ctx.setDepartment(entity.getDepartmentId());
        ctx.setPosition(entity.getPosition());
        
        try {
            ctx.setIdentity(UserIdentity.valueOf(entity.getIdentity()));
        } catch (IllegalArgumentException e) {
            ctx.setIdentity(UserIdentity.EXTERNAL_VISITOR);
        }
        
        try {
            ctx.setAccessLevel(AccessLevel.valueOf(entity.getAccessLevel()));
        } catch (IllegalArgumentException e) {
            ctx.setAccessLevel(AccessLevel.CHAT_ONLY);
        }
        
        ctx.setFounder(entity.isFounder());
        ctx.setVoicePrintId(entity.getVoicePrintId());
        ctx.setOauthProvider(entity.getOauthProvider());
        ctx.setOauthUserId(entity.getOauthUserId());
        ctx.setJoinDate(entity.getJoinDate());
        ctx.setLeaveDate(entity.getLeaveDate());
        ctx.setActive(entity.isActive());
        ctx.setSyncSource(entity.getSyncSource());
        ctx.setLastSyncTime(entity.getLastSyncTime());
        return ctx;
    }
}
