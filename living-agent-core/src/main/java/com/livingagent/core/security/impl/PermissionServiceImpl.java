package com.livingagent.core.security.impl;

import com.livingagent.core.security.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PermissionServiceImpl implements PermissionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionServiceImpl.class);

    private final EmployeeAuthService employeeAuthService;
    private final Map<String, List<AccessAuditLog>> auditLogs = new ConcurrentHashMap<>();
    private final Map<String, String> sessionEmployeeMap = new ConcurrentHashMap<>();

    public PermissionServiceImpl(EmployeeAuthService employeeAuthService) {
        this.employeeAuthService = employeeAuthService;
    }

    @Override
    public Optional<Employee> verifyByPhone(String phone, String verificationCode) {
        log.info("Verifying employee by phone: {}", phone);
        
        Optional<Employee> employeeOpt = employeeAuthService.findByPhone(phone);
        if (employeeOpt.isEmpty()) {
            log.warn("Employee not found for phone: {}", phone);
            return Optional.empty();
        }

        Employee employee = employeeOpt.get();
        
        if (!validateVerificationCode(phone, verificationCode)) {
            recordAccess(employee.getEmployeeId(), "phone_verification", "verify", false);
            return Optional.empty();
        }

        recordAccess(employee.getEmployeeId(), "phone_verification", "verify", true);
        log.info("Employee verified by phone: {} -> {}", phone, employee.getName());
        return Optional.of(employee);
    }

    @Override
    public Optional<Employee> verifyByVoicePrint(String voicePrintId, float[] voiceVector) {
        log.info("Verifying employee by voice print: {}", voicePrintId);
        
        Optional<Employee> employeeOpt = employeeAuthService.findByVoicePrintId(voicePrintId);
        if (employeeOpt.isEmpty()) {
            log.warn("Employee not found for voice print: {}", voicePrintId);
            return Optional.empty();
        }

        Employee employee = employeeOpt.get();
        
        if (!validateVoiceVector(voicePrintId, voiceVector)) {
            recordAccess(employee.getEmployeeId(), "voice_verification", "verify", false);
            return Optional.empty();
        }

        recordAccess(employee.getEmployeeId(), "voice_verification", "verify", true);
        log.info("Employee verified by voice: {} -> {}", voicePrintId, employee.getName());
        return Optional.of(employee);
    }

    @Override
    public Optional<Employee> verifyByOAuth(String provider, String oauthUserId, String accessToken) {
        log.info("Verifying employee by OAuth: {} - {}", provider, oauthUserId);
        
        Optional<Employee> employeeOpt = employeeAuthService.findByOAuth(provider, oauthUserId);
        if (employeeOpt.isEmpty()) {
            log.warn("Employee not found for OAuth: {} - {}", provider, oauthUserId);
            return Optional.empty();
        }

        Employee employee = employeeOpt.get();
        
        if (!validateOAuthToken(provider, accessToken)) {
            recordAccess(employee.getEmployeeId(), "oauth_verification", "verify", false);
            return Optional.empty();
        }

        recordAccess(employee.getEmployeeId(), "oauth_verification", "verify", true);
        log.info("Employee verified by OAuth: {} - {} -> {}", provider, oauthUserId, employee.getName());
        return Optional.of(employee);
    }

    @Override
    public Optional<Employee> getEmployeeById(String employeeId) {
        return employeeAuthService.findById(employeeId);
    }

    @Override
    public Optional<Employee> getEmployeeByPhone(String phone) {
        return employeeAuthService.findByPhone(phone);
    }

    @Override
    public Optional<Employee> getEmployeeByVoicePrintId(String voicePrintId) {
        return employeeAuthService.findByVoicePrintId(voicePrintId);
    }

    @Override
    public boolean canAccessBrain(String employeeId, String brainName) {
        Optional<Employee> employeeOpt = employeeAuthService.findById(employeeId);
        if (employeeOpt.isEmpty()) {
            log.warn("Employee not found: {}", employeeId);
            return false;
        }

        Employee employee = employeeOpt.get();
        boolean canAccess = employee.canAccessBrain(brainName);
        
        recordAccess(employeeId, "brain:" + brainName, "access", canAccess);
        
        if (!canAccess) {
            log.warn("Access denied: employee {} cannot access brain {}", employeeId, brainName);
        }
        
        return canAccess;
    }

    @Override
    public boolean canUseModel(String employeeId, String modelName) {
        Optional<Employee> employeeOpt = employeeAuthService.findById(employeeId);
        if (employeeOpt.isEmpty()) {
            return false;
        }

        Employee employee = employeeOpt.get();
        boolean canUse = employee.canUseModel(modelName);
        
        recordAccess(employeeId, "model:" + modelName, "use", canUse);
        
        return canUse;
    }

    @Override
    public boolean canExecuteTool(String employeeId, String toolName) {
        Optional<Employee> employeeOpt = employeeAuthService.findById(employeeId);
        if (employeeOpt.isEmpty()) {
            return false;
        }

        Employee employee = employeeOpt.get();
        
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
        Optional<Employee> employeeOpt = employeeAuthService.findById(employeeId);
        if (employeeOpt.isEmpty()) {
            return Collections.emptySet();
        }

        Employee employee = employeeOpt.get();
        Set<String> brains = new HashSet<>(employee.getAccessLevel().getAllowedBrains());
        
        if (employee.getAllowedBrains() != null) {
            brains.addAll(employee.getAllowedBrains());
        }
        
        return brains;
    }

    @Override
    public Set<String> getAllowedModels(String employeeId) {
        Optional<Employee> employeeOpt = employeeAuthService.findById(employeeId);
        if (employeeOpt.isEmpty()) {
            return Collections.singleton("Qwen3-0.6B");
        }

        return employeeOpt.get().getAccessLevel().getAllowedModels();
    }

    @Override
    public AccessLevel getAccessLevel(String employeeId) {
        Optional<Employee> employeeOpt = employeeAuthService.findById(employeeId);
        if (employeeOpt.isEmpty()) {
            return AccessLevel.CHAT_ONLY;
        }

        return employeeOpt.get().getAccessLevel();
    }

    @Override
    public void updateAccessLevel(String employeeId, AccessLevel newLevel) {
        Optional<Employee> employeeOpt = employeeAuthService.findById(employeeId);
        if (employeeOpt.isEmpty()) {
            log.warn("Cannot update access level: employee not found: {}", employeeId);
            return;
        }

        Employee employee = employeeOpt.get();
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
        
        employeeAuthService.findById(employeeId).ifPresent(e -> logEntry.setEmployeeName(e.getName()));
        
        auditLogs.computeIfAbsent(employeeId, k -> new ArrayList<>()).add(logEntry);
        
        log.debug("Recorded access: employee={}, resource={}, action={}, granted={}",
            employeeId, resource, action, granted);
    }

    @Override
    public List<AccessAuditLog> getAccessLogs(String employeeId, int limit) {
        List<AccessAuditLog> logs = auditLogs.getOrDefault(employeeId, Collections.emptyList());
        if (logs.size() <= limit) {
            return new ArrayList<>(logs);
        }
        return new ArrayList<>(logs.subList(logs.size() - limit, logs.size()));
    }

    @Override
    public boolean isChatOnlyUser(String employeeId) {
        Optional<Employee> employeeOpt = employeeAuthService.findById(employeeId);
        if (employeeOpt.isEmpty()) {
            return true;
        }
        return employeeOpt.get().isChatOnly();
    }

    @Override
    public String getRouteTarget(String employeeId) {
        Optional<Employee> employeeOpt = employeeAuthService.findById(employeeId);
        if (employeeOpt.isEmpty()) {
            return "Qwen3Neuron";
        }

        Employee employee = employeeOpt.get();
        
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

    public void sendVerificationCode(String phone) {
        String code = String.format("%06d", new Random().nextInt(1000000));
        verificationCodes.put(phone, code);
        codeExpiryTimes.put(phone, System.currentTimeMillis() + CODE_EXPIRY_MS);
        log.info("Verification code sent to {}: {}", phone, code);
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
        return accessToken.startsWith("dt_") && accessToken.length() > 10;
    }
    
    private boolean validateFeishuToken(String accessToken) {
        return accessToken.startsWith("fs_") && accessToken.length() > 10;
    }
    
    private boolean validateWeChatToken(String accessToken) {
        return accessToken.startsWith("wx_") && accessToken.length() > 10;
    }

    private boolean isToolAllowedForAccessLevel(String toolName, AccessLevel level) {
        Set<String> restrictedTools = Set.of("GitLabTool", "JenkinsTool", "JiraTool", "ErpTool", "HrSystemTool");
        
        if (restrictedTools.contains(toolName)) {
            return level == AccessLevel.DEPARTMENT || level == AccessLevel.FULL;
        }
        
        return true;
    }
}
