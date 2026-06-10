package com.livingagent.core.security.impl;

import com.livingagent.core.security.*;
import com.livingagent.core.security.service.EnterpriseEmployeeService;
import com.livingagent.core.database.repository.AccessAuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PermissionServiceImpl implements PermissionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionServiceImpl.class);

    private final EmployeeAuthService employeeAuthService;
    private final EnterpriseEmployeeService enterpriseEmployeeService;
    private final AccessAuditLogRepository auditLogRepository;
    private final Map<String, List<AccessAuditLog>> auditLogs = new ConcurrentHashMap<>();
    private final Map<String, String> sessionEmployeeMap = new ConcurrentHashMap<>();

    public PermissionServiceImpl(EmployeeAuthService employeeAuthService,
                                  EnterpriseEmployeeService enterpriseEmployeeService,
                                  AccessAuditLogRepository auditLogRepository) {
        this.employeeAuthService = employeeAuthService;
        this.enterpriseEmployeeService = enterpriseEmployeeService;
        this.auditLogRepository = auditLogRepository;
    }

    private Optional<SecurityIdentity> findEmployee(String employeeId) {
        Optional<SecurityIdentity> employee = employeeAuthService.findById(employeeId);
        if (employee.isPresent()) {
            return employee;
        }
        
        Optional<AuthContext> authContext = enterpriseEmployeeService.findById(employeeId);
        if (authContext.isPresent()) {
            SecurityIdentity converted = toEmployee(authContext.get());
            return Optional.of(converted);
        }
        
        return Optional.empty();
    }
    
    private SecurityIdentity toEmployee(AuthContext ctx) {
        SecurityIdentity employee = new SecurityIdentity();
        employee.setEmployeeId(ctx.getEmployeeId());
        employee.setName(ctx.getName());
        employee.setPhone(ctx.getPhone());
        employee.setEmail(ctx.getEmail());
        employee.setDepartment(ctx.getDepartment() != null && !ctx.getDepartment().isBlank() ? ctx.getDepartment() : (ctx.isFounder() ? "管理部" : ""));
        employee.setPosition(ctx.getPosition());
        employee.setIdentity(ctx.getIdentity());
        employee.setAccessLevel(ctx.getAccessLevel());
        employee.setFounder(ctx.isFounder());
        employee.setActive(ctx.isActive());
        employee.setTenantId("tenant_default");
        employee.setStatus("ACTIVE");
        return employee;
    }

    @Override
    public Optional<SecurityIdentity> verifyByPhone(String phone, String verificationCode) {
        log.info("Verifying employee by phone: {}", phone);
        
        Optional<SecurityIdentity> employeeOpt = employeeAuthService.findByPhone(phone);
        if (employeeOpt.isEmpty()) {
            log.warn("SecurityIdentity not found for phone: {}", phone);
            return Optional.empty();
        }

        SecurityIdentity employee = employeeOpt.get();
        
        if (!validateVerificationCode(phone, verificationCode)) {
            recordAccess(employee.getEmployeeId(), "phone_verification", "verify", false);
            return Optional.empty();
        }

        recordAccess(employee.getEmployeeId(), "phone_verification", "verify", true);
        log.info("SecurityIdentity verified by phone: {} -> {}", phone, employee.getName());
        return Optional.of(employee);
    }

    @Override
    public Optional<SecurityIdentity> verifyByVoicePrint(String voicePrintId, float[] voiceVector) {
        log.info("Verifying employee by voice print: {}", voicePrintId);
        
        Optional<SecurityIdentity> employeeOpt = employeeAuthService.findByVoicePrintId(voicePrintId);
        if (employeeOpt.isEmpty()) {
            log.warn("SecurityIdentity not found for voice print: {}", voicePrintId);
            return Optional.empty();
        }

        SecurityIdentity employee = employeeOpt.get();
        
        if (!validateVoiceVector(voicePrintId, voiceVector)) {
            recordAccess(employee.getEmployeeId(), "voice_verification", "verify", false);
            return Optional.empty();
        }

        recordAccess(employee.getEmployeeId(), "voice_verification", "verify", true);
        log.info("SecurityIdentity verified by voice: {} -> {}", voicePrintId, employee.getName());
        return Optional.of(employee);
    }

    @Override
    public Optional<SecurityIdentity> verifyByOAuth(String provider, String oauthUserId, String accessToken) {
        log.info("Verifying employee by OAuth: {} - {}", provider, oauthUserId);
        
        Optional<SecurityIdentity> employeeOpt = employeeAuthService.findByOAuth(provider, oauthUserId);
        if (employeeOpt.isEmpty()) {
            log.warn("SecurityIdentity not found for OAuth: {} - {}", provider, oauthUserId);
            return Optional.empty();
        }

        SecurityIdentity employee = employeeOpt.get();
        
        if (!validateOAuthToken(provider, accessToken)) {
            recordAccess(employee.getEmployeeId(), "oauth_verification", "verify", false);
            return Optional.empty();
        }

        recordAccess(employee.getEmployeeId(), "oauth_verification", "verify", true);
        log.info("SecurityIdentity verified by OAuth: {} - {} -> {}", provider, oauthUserId, employee.getName());
        return Optional.of(employee);
    }

    @Override
    public Optional<SecurityIdentity> getEmployeeById(String employeeId) {
        return employeeAuthService.findById(employeeId);
    }

    @Override
    public Optional<SecurityIdentity> getEmployeeByPhone(String phone) {
        return employeeAuthService.findByPhone(phone);
    }

    @Override
    public Optional<SecurityIdentity> getEmployeeByVoicePrintId(String voicePrintId) {
        return employeeAuthService.findByVoicePrintId(voicePrintId);
    }

    @Override
    public boolean canAccessBrain(String employeeId, String brainName) {
        Optional<SecurityIdentity> employeeOpt = findEmployee(employeeId);
        if (employeeOpt.isEmpty()) {
            log.warn("SecurityIdentity not found: {}", employeeId);
            return false;
        }

        SecurityIdentity employee = employeeOpt.get();
        boolean canAccess = employee.canAccessBrain(brainName);
        
        recordAccess(employeeId, "brain:" + brainName, "access", canAccess);
        
        if (!canAccess) {
            log.warn("Access denied: employee {} cannot access brain {}", employeeId, brainName);
        }
        
        return canAccess;
    }

    @Override
    public boolean canUseModel(String employeeId, String modelName) {
        Optional<SecurityIdentity> employeeOpt = findEmployee(employeeId);
        if (employeeOpt.isEmpty()) {
            return false;
        }

        SecurityIdentity employee = employeeOpt.get();
        boolean canUse = employee.canUseModel(modelName);
        
        recordAccess(employeeId, "model:" + modelName, "use", canUse);
        
        return canUse;
    }

    @Override
    public boolean canExecuteTool(String employeeId, String toolName) {
        Optional<SecurityIdentity> employeeOpt = findEmployee(employeeId);
        if (employeeOpt.isEmpty()) {
            return false;
        }

        SecurityIdentity employee = employeeOpt.get();
        
        if (employee.isChatOnly()) {
            log.info("Chat-only user {} cannot execute tool {}", employeeId, toolName);
            return false;
        }

        boolean canExecute = isToolAllowedForAccessLevel(toolName, employee.getAccessLevel());
        
        recordAccess(employeeId, "tool:" + toolName, "execute", canExecute);
        
        return canExecute;
    }

    @Override
    public Set<String> getAccessibleBrains(String employeeId) {
        Optional<SecurityIdentity> employeeOpt = employeeAuthService.findById(employeeId);
        if (employeeOpt.isEmpty()) {
            return Collections.emptySet();
        }

        SecurityIdentity employee = employeeOpt.get();
        Set<String> brains = new HashSet<>(employee.getAccessLevel().getAllowedBrains());
        
        if (employee.getAllowedBrains() != null) {
            brains.addAll(employee.getAllowedBrains());
        }
        
        return brains;
    }

    @Override
    public Set<String> getAllowedModels(String employeeId) {
        Optional<SecurityIdentity> employeeOpt = employeeAuthService.findById(employeeId);
        if (employeeOpt.isEmpty()) {
            log.warn("getAllowedModels: employee not found, returning empty set instead of hardcoded model");
            return Collections.emptySet();
        }

        return employeeOpt.get().getAccessLevel().getAllowedModels();
    }

    @Override
    public AccessLevel getAccessLevel(String employeeId) {
        Optional<SecurityIdentity> employeeOpt = employeeAuthService.findById(employeeId);
        if (employeeOpt.isEmpty()) {
            return AccessLevel.CHAT_ONLY;
        }

        return employeeOpt.get().getAccessLevel();
    }

    @Override
    public void updateAccessLevel(String employeeId, AccessLevel newLevel) {
        Optional<SecurityIdentity> employeeOpt = employeeAuthService.findById(employeeId);
        if (employeeOpt.isEmpty()) {
            log.warn("Cannot update access level: employee not found: {}", employeeId);
            return;
        }

        SecurityIdentity employee = employeeOpt.get();
        AccessLevel oldLevel = employee.getAccessLevel();
        employee.setAccessLevel(newLevel);
        employeeAuthService.updateEmployee(employee);
        
        recordAccess(employeeId, "access_level", "update", true);
        log.info("Updated access level for {}: {} -> {}", employeeId, oldLevel, newLevel);
    }

    @Override
    public void recordAccess(String employeeId, String resource, String action, boolean granted) {
        AccessAuditLog logEntry = new AccessAuditLog();
        logEntry.setEmployeeId(employeeId);
        logEntry.setResource(resource);
        logEntry.setAction(action);
        logEntry.setGranted(granted);
        logEntry.setReason(granted ? "Access granted" : "Access denied");

        String employeeName = null;
        Optional<SecurityIdentity> empOpt = employeeAuthService.findById(employeeId);
        if (empOpt.isPresent()) {
            employeeName = empOpt.get().getName();
            logEntry.setEmployeeName(employeeName);
        }

        // 持久化到数据库
        try {
            auditLogRepository.save(logEntry);
        } catch (Exception e) {
            log.error("Failed to persist audit log to database, falling back to memory: {}", e.getMessage());
        }

        // 内存缓存兜底（异步持久化失败时可用）
        auditLogs.computeIfAbsent(employeeId, k -> new ArrayList<>()).add(logEntry);

        log.info("AUDIT|employeeId={}|resource={}|action={}|granted={}|employeeName={}|reason={}",
            employeeId, resource, action, granted, employeeName, logEntry.getReason());
    }

    @Override
    public List<AccessAuditLog> getAccessLogs(String employeeId, int limit) {
        // 优先从数据库查询
        try {
            return auditLogRepository.findByEmployeeIdOrderByTimestampDesc(
                employeeId, PageRequest.of(0, limit));
        } catch (Exception e) {
            log.warn("Failed to query audit logs from database, falling back to memory: {}", e.getMessage());
            // 数据库不可用时降级到内存
            List<AccessAuditLog> logs = auditLogs.getOrDefault(employeeId, Collections.emptyList());
            if (logs.size() <= limit) {
                return new ArrayList<>(logs);
            }
            return new ArrayList<>(logs.subList(logs.size() - limit, logs.size()));
        }
    }

    @Override
    public boolean isChatOnlyUser(String employeeId) {
        Optional<SecurityIdentity> employeeOpt = employeeAuthService.findById(employeeId);
        if (employeeOpt.isEmpty()) {
            return true;
        }
        return employeeOpt.get().isChatOnly();
    }

    @Override
    public String getRouteTarget(String employeeId) {
        Optional<SecurityIdentity> employeeOpt = employeeAuthService.findById(employeeId);
        if (employeeOpt.isEmpty()) {
            return "Qwen3Neuron";
        }

        SecurityIdentity employee = employeeOpt.get();
        
        if (employee.isChatOnly()) {
            return "Qwen3Neuron";
        }

        String department = employee.getDepartment();
        if (department == null) {
            return "MainBrain";
        }

        switch (department.toLowerCase()) {
            case "技术部":
            case "tech":
                return "TechBrain";
            case "人力资源":
            case "hr":
                return "HrBrain";
            case "财务部":
            case "finance":
                return "FinanceBrain";
            case "销售部":
            case "sales":
                return "SalesBrain";
            case "客服部":
            case "cs":
                return "CsBrain";
            case "行政部":
            case "admin":
                return "AdminBrain";
            case "法务部":
            case "legal":
                return "LegalBrain";
            case "运营部":
            case "ops":
                return "OpsBrain";
            default:
                return "MainBrain";
        }
    }

    private final Map<String, String> verificationCodes = new ConcurrentHashMap<>();
    private final Map<String, Long> codeExpiryTimes = new ConcurrentHashMap<>();
    private static final long CODE_EXPIRY_MS = 5 * 60 * 1000;

    private static final SecureRandom secureRandom = new SecureRandom();

    public void sendVerificationCode(String phone) {
        String code = String.format("%06d", secureRandom.nextInt(1000000));
        verificationCodes.put(phone, code);
        codeExpiryTimes.put(phone, System.currentTimeMillis() + CODE_EXPIRY_MS);
        log.info("Verification code sent to phone: {}", phone);
    }

    private boolean validateVerificationCode(String phone, String code) {
        if (code == null || code.length() < 4) {
            return false;
        }
        String storedCode = verificationCodes.get(phone);
        Long expiryTime = codeExpiryTimes.get(phone);
        
        if (storedCode == null || expiryTime == null) {
            log.warn("No verification code found for phone: {}", phone);
            return false;
        }
        
        if (System.currentTimeMillis() > expiryTime) {
            verificationCodes.remove(phone);
            codeExpiryTimes.remove(phone);
            log.warn("Verification code expired for phone: {}", phone);
            return false;
        }
        
        boolean valid = storedCode.equals(code);
        if (valid) {
            verificationCodes.remove(phone);
            codeExpiryTimes.remove(phone);
        }
        return valid;
    }

    private boolean validateVoiceVector(String voicePrintId, float[] voiceVector) {
        // TODO: 实现真正的声纹向量相似度比对（如余弦相似度），当前仅检查非空是不安全的
        log.warn("validateVoiceVector: current implementation only checks non-null/non-empty, which is insecure. " +
                "Real vector similarity comparison (e.g., cosine similarity) must be implemented for voicePrintId={}", voicePrintId);
        return voiceVector != null && voiceVector.length > 0;
    }

    private boolean validateOAuthToken(String provider, String accessToken) {
        if (accessToken == null || accessToken.isEmpty()) {
            return false;
        }
        
        return switch (provider.toLowerCase()) {
            case "dingtalk" -> validateDingTalkToken(accessToken);
            case "feishu" -> validateFeishuToken(accessToken);
            case "wechat" -> validateWeChatToken(accessToken);
            default -> {
                log.warn("Unknown OAuth provider: {}", provider);
                yield false;
            }
        };
    }
    
    private boolean validateDingTalkToken(String accessToken) {
        // TODO: 实现真正的钉钉OAuth Token校验，调用钉钉服务端API验证token有效性
        log.warn("validateDingTalkToken: current implementation only checks prefix/length/format, which is insecure. " +
                "Real OAuth provider validation must be implemented.");
        return isValidTokenFormat(accessToken, "dt_");
    }
    
    private boolean validateFeishuToken(String accessToken) {
        // TODO: 实现真正的飞书OAuth Token校验，调用飞书服务端API验证token有效性
        log.warn("validateFeishuToken: current implementation only checks prefix/length/format, which is insecure. " +
                "Real OAuth provider validation must be implemented.");
        return isValidTokenFormat(accessToken, "fs_");
    }
    
    private boolean validateWeChatToken(String accessToken) {
        // TODO: 实现真正的企业微信OAuth Token校验，调用企业微信服务端API验证token有效性
        log.warn("validateWeChatToken: current implementation only checks prefix/length/format, which is insecure. " +
                "Real OAuth provider validation must be implemented.");
        return isValidTokenFormat(accessToken, "wx_");
    }

    private boolean isValidTokenFormat(String accessToken, String requiredPrefix) {
        if (!accessToken.startsWith(requiredPrefix)) {
            return false;
        }
        if (accessToken.length() < 20) {
            log.warn("OAuth token with prefix '{}' is too short (min 20 chars required), got {} chars",
                    requiredPrefix, accessToken.length());
            return false;
        }
        // 检查Token中不能包含空格、换行等非法字符
        for (int i = 0; i < accessToken.length(); i++) {
            char c = accessToken.charAt(i);
            if (Character.isWhitespace(c) || c == '\n' || c == '\r' || c == '\t') {
                log.warn("OAuth token contains illegal whitespace character at position {}", i);
                return false;
            }
        }
        return true;
    }

    private boolean isToolAllowedForAccessLevel(String toolName, AccessLevel level) {
        Set<String> restrictedTools = Set.of("GitLabTool", "JenkinsTool", "JiraTool", "ErpTool", "HrSystemTool");
        
        if (restrictedTools.contains(toolName)) {
            return level == AccessLevel.DEPARTMENT || level == AccessLevel.FULL;
        }
        
        return true;
    }
}
