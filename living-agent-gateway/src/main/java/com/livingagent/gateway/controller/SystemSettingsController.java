package com.livingagent.gateway.controller;

import com.livingagent.core.security.AccessGateService;
import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.security.AuthContext;
import com.livingagent.core.settings.feedback.SettingsChangeImpactTracker;
import com.livingagent.gateway.controller.common.ApiResponse;
import com.livingagent.core.security.auth.UnifiedAuthService;
import com.livingagent.core.security.auth.UnifiedAuthService.AuthSession;
import com.livingagent.core.tool.ToolRegistry;
import com.livingagent.core.tool.impl.BuildTool;
import com.livingagent.core.tool.impl.FileEditTool;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping(path = {"/api/enterprise/settings", "/api/enterprise/system-settings"})
public class SystemSettingsController {

    private static final Logger log = LoggerFactory.getLogger(SystemSettingsController.class);

    private final UnifiedAuthService authService;
    private final AccessGateService accessGateService;
    private final ToolRegistry toolRegistry;

    @Autowired(required = false)
    private SettingsChangeImpactTracker settingsChangeImpactTracker;

    @org.springframework.beans.factory.annotation.Value("${model.default:qwen3.5:27b}")
    private String defaultModelDefault;

    @org.springframework.beans.factory.annotation.Value("${model.fallback:qwen3:0.6b}")
    private String defaultModelFallback;

    private final Map<String, Object> systemSettings = new ConcurrentHashMap<>();
    private final List<SettingsChangeRecord> changeHistory = new CopyOnWriteArrayList<>();
    private final List<SettingsVersion> versionHistory = new CopyOnWriteArrayList<>();
    private static final int MAX_VERSION_HISTORY = 100;

    public SystemSettingsController(UnifiedAuthService authService, AccessGateService accessGateService, ToolRegistry toolRegistry) {
        this.authService = authService;
        this.accessGateService = accessGateService;
        this.toolRegistry = toolRegistry;
    }

    @PostConstruct
    public void init() {
        initDefaultSettings();
    }

    private void initDefaultSettings() {
        systemSettings.put("system.name", "Living Agent Service");
        systemSettings.put("system.version", "1.0.0");
        systemSettings.put("system.timezone", "Asia/Shanghai");
        systemSettings.put("system.language", "zh-CN");
        
        systemSettings.put("model.default", defaultModelDefault);
        systemSettings.put("model.fallback", defaultModelFallback);
        systemSettings.put("model.timeout", 30000);
        systemSettings.put("model.maxRetries", 3);
        
        systemSettings.put("security.sessionTimeout", 3600);
        systemSettings.put("security.maxLoginAttempts", 5);
        systemSettings.put("security.passwordMinLength", 8);
        
        systemSettings.put("notification.email.enabled", true);
        systemSettings.put("notification.sms.enabled", false);
        systemSettings.put("notification.push.enabled", true);
        
        systemSettings.put("budget.monthlyDefault", 10000.0);
        systemSettings.put("budget.alertThreshold", 0.8);
        
        systemSettings.put("knowledge.maxSize", 10000);
        systemSettings.put("knowledge.expirationDays", 365);
        
        systemSettings.put("company_intro.tenant_default", "");

        // 工作区配置（FileEditTool 热配置）
        systemSettings.put("workspace.root", System.getProperty("livingagent.workspace.root", "/app/workspace"));
        systemSettings.put("workspace.writeEnabled", true);
        systemSettings.put("workspace.allowedExtensions", ".java,.xml,.yml,.yaml,.properties,.json,.md,.sql,.ts,.tsx,.js,.jsx,.css,.html,.py,.sh,.ps1,.txt");

        versionHistory.add(new SettingsVersion("v1", "Initial default settings", new HashMap<>(systemSettings), Instant.now(), "system"));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllSettings(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty() || !isEnterprise(ctxOpt.get())) {
            return ResponseEntity.status(403).build();
        }
        String effectiveEmployeeId = employeeId != null && !employeeId.isBlank() ? employeeId : ctxOpt.get().getEmployeeId();
        if (!accessGateService.canRoute(effectiveEmployeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).build();
        }
        
        return ResponseEntity.ok(new HashMap<>(systemSettings));
    }

    @GetMapping("/{category}")
    public ResponseEntity<Map<String, Object>> getSettingsByCategory(
            @PathVariable String category,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty() || !isEnterprise(ctxOpt.get())) {
            return ResponseEntity.status(403).build();
        }
        String effectiveEmployeeId = employeeId != null && !employeeId.isBlank() ? employeeId : ctxOpt.get().getEmployeeId();
        if (!accessGateService.canRoute(effectiveEmployeeId, "brain", "AdminBrain")) {
            return ResponseEntity.status(403).build();
        }
        
        Map<String, Object> categorySettings = new HashMap<>();
        String prefix = category + ".";
        
        systemSettings.forEach((key, value) -> {
            if (key.startsWith(prefix)) {
                categorySettings.put(key.substring(prefix.length()), value);
            }
        });
        
        return ResponseEntity.ok(categorySettings);
    }

    @GetMapping("/{category}/{key}")
    public ResponseEntity<SettingValue> getSetting(
            @PathVariable String category, 
            @PathVariable String key,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty() || !isEnterprise(ctxOpt.get())) {
            return ResponseEntity.status(403).build();
        }
        
        String fullKey = category + "." + key;
        Object value = systemSettings.get(fullKey);
        
        if (value == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(new SettingValue(fullKey, value, value.getClass().getSimpleName()));
    }

    @PutMapping("/{category}/{key}")
    public ResponseEntity<SettingValue> updateSetting(
            @PathVariable String category,
            @PathVariable String key,
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty() || !isEnterprise(ctxOpt.get())) {
            return ResponseEntity.status(403).build();
        }
        
        AuthContext ctx = ctxOpt.get();
        String fullKey = category + "." + key;
        Object oldValue = systemSettings.get(fullKey);
        Object newValue = request.get("value");
        
        if (newValue == null) {
            return ResponseEntity.badRequest().build();
        }
        
        systemSettings.put(fullKey, newValue);
        
        SettingsChangeRecord record = new SettingsChangeRecord(
            UUID.randomUUID().toString(),
            fullKey,
            oldValue,
            newValue,
            ctx.getEmployeeId(),
            Instant.now(),
            (String) request.getOrDefault("reason", "")
        );
        changeHistory.add(record);
        
        log.info("Setting updated: key={}, oldValue={}, newValue={}, by={}", 
            fullKey, oldValue, newValue, ctx.getEmployeeId());

        if (settingsChangeImpactTracker != null) {
            settingsChangeImpactTracker.recordChange(fullKey, String.valueOf(oldValue), String.valueOf(newValue));
        }
        
        return ResponseEntity.ok(new SettingValue(fullKey, newValue, newValue.getClass().getSimpleName()));
    }

    @PostMapping("/batch")
    public ResponseEntity<BatchUpdateResult> batchUpdate(
            @RequestBody Map<String, Object> settings,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return doBatchUpdate(settings, authorization);
    }

    @PutMapping
    public ResponseEntity<BatchUpdateResult> batchUpdatePut(
            @RequestBody Map<String, Object> settings,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return doBatchUpdate(settings, authorization);
    }

    private ResponseEntity<BatchUpdateResult> doBatchUpdate(
            Map<String, Object> settings,
            String authorization) {
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty() || !isEnterprise(ctxOpt.get())) {
            return ResponseEntity.status(403).build();
        }

        AuthContext ctx = ctxOpt.get();
        createSettingsVersion("Batch update", ctx.getEmployeeId());
        int updated = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();

        for (Map.Entry<String, Object> entry : settings.entrySet()) {
            try {
                String key = entry.getKey();
                Object oldValue = systemSettings.get(key);
                Object newValue = entry.getValue();

                systemSettings.put(key, newValue);

                SettingsChangeRecord record = new SettingsChangeRecord(
                    UUID.randomUUID().toString(),
                    key,
                    oldValue,
                    newValue,
                    ctx.getEmployeeId(),
                    Instant.now(),
                    "Batch update"
                );
                changeHistory.add(record);

                if (settingsChangeImpactTracker != null) {
                    settingsChangeImpactTracker.recordChange(key, String.valueOf(oldValue), String.valueOf(newValue));
                }

                updated++;
            } catch (Exception e) {
                failed++;
                errors.add(entry.getKey() + ": " + e.getMessage());
            }
        }

        log.info("Batch settings update: updated={}, failed={}, by={}", updated, failed, ctx.getEmployeeId());

        return ResponseEntity.ok(new BatchUpdateResult(updated, failed, errors));
    }

    @GetMapping("/history")
    public ResponseEntity<List<SettingsChangeRecord>> getChangeHistory(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "50") int limit,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty() || !isEnterprise(ctxOpt.get())) {
            return ResponseEntity.status(403).build();
        }
        
        List<SettingsChangeRecord> history;
        if (category != null && !category.isEmpty()) {
            String prefix = category + ".";
            history = changeHistory.stream()
                .filter(r -> r.key().startsWith(prefix))
                .toList();
        } else {
            history = new ArrayList<>(changeHistory);
        }
        
        if (history.size() > limit) {
            history = history.subList(history.size() - limit, history.size());
        }
        
        return ResponseEntity.ok(history);
    }

    @PostMapping("/{category}/{key}/reset")
    public ResponseEntity<SettingValue> resetSetting(
            @PathVariable String category, 
            @PathVariable String key,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty() || !isEnterprise(ctxOpt.get())) {
            return ResponseEntity.status(403).build();
        }
        
        AuthContext ctx = ctxOpt.get();
        String fullKey = category + "." + key;
        Object defaultValue = getDefaultValue(fullKey);
        
        if (defaultValue == null) {
            return ResponseEntity.notFound().build();
        }
        
        Object oldValue = systemSettings.get(fullKey);
        systemSettings.put(fullKey, defaultValue);
        
        SettingsChangeRecord record = new SettingsChangeRecord(
            UUID.randomUUID().toString(),
            fullKey,
            oldValue,
            defaultValue,
            ctx.getEmployeeId(),
            Instant.now(),
            "Reset to default"
        );
        changeHistory.add(record);
        
        log.info("Setting reset: key={}, to default={}, by={}", fullKey, defaultValue, ctx.getEmployeeId());

        if (settingsChangeImpactTracker != null) {
            settingsChangeImpactTracker.recordRollback(fullKey, "Reset to default");
        }
        
        return ResponseEntity.ok(new SettingValue(fullKey, defaultValue, defaultValue.getClass().getSimpleName()));
    }

    @GetMapping("/categories")
    public ResponseEntity<List<SettingCategory>> getCategories(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty() || !isEnterprise(ctxOpt.get())) {
            return ResponseEntity.status(403).build();
        }
        
        Map<String, Integer> categoryCount = new HashMap<>();
        
        systemSettings.keySet().forEach(key -> {
            int dotIndex = key.indexOf('.');
            if (dotIndex > 0) {
                String category = key.substring(0, dotIndex);
                categoryCount.merge(category, 1, Integer::sum);
            }
        });
        
        List<SettingCategory> categories = new ArrayList<>();
        categoryCount.forEach((name, count) -> 
            categories.add(new SettingCategory(name, name.toUpperCase(), count)));
        
        return ResponseEntity.ok(categories);
    }

    @GetMapping("/notification_bar/public")
    public ResponseEntity<ApiResponse<NotificationBarPublic>> getNotificationBarPublic() {
        NotificationBarPublic data = new NotificationBarPublic(
            "欢迎使用 Living Agent 企业管理平台",
            "如有问题请联系管理员",
            false,
            ""
        );
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @GetMapping("/versions")
    public ResponseEntity<List<SettingsVersion>> getVersionHistory(
            @RequestParam(defaultValue = "20") int limit,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty() || !isEnterprise(ctxOpt.get())) {
            return ResponseEntity.status(403).build();
        }
        List<SettingsVersion> history = new ArrayList<>(versionHistory);
        history.sort(Comparator.comparing(SettingsVersion::createdAt));
        if (history.size() > limit) {
            history = history.subList(history.size() - limit, history.size());
        }
        return ResponseEntity.ok(history);
    }

    @PostMapping("/versions/{versionId}/rollback")
    public ResponseEntity<BatchUpdateResult> rollbackToVersion(
            @PathVariable String versionId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty() || !isEnterprise(ctxOpt.get())) {
            return ResponseEntity.status(403).build();
        }
        AuthContext ctx = ctxOpt.get();

        Optional<SettingsVersion> targetOpt = versionHistory.stream()
                .filter(v -> v.versionId().equals(versionId))
                .findFirst();

        if (targetOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        createSettingsVersion("Rollback to " + versionId, ctx.getEmployeeId());
        systemSettings.clear();
        systemSettings.putAll(targetOpt.get().snapshot());

        SettingsChangeRecord record = new SettingsChangeRecord(
                UUID.randomUUID().toString(),
                "system.rollback",
                null,
                versionId,
                ctx.getEmployeeId(),
                Instant.now(),
                "Rollback executed"
        );
        changeHistory.add(record);

        if (settingsChangeImpactTracker != null) {
            settingsChangeImpactTracker.recordRollback("system.rollback", "Rollback to " + versionId);
        }

        return ResponseEntity.ok(new BatchUpdateResult(systemSettings.size(), 0, List.of()));
    }

    private void createSettingsVersion(String reason, String changedBy) {
        String versionId = "v" + (versionHistory.size() + 1);
        versionHistory.add(new SettingsVersion(
                versionId,
                reason,
                new HashMap<>(systemSettings),
                Instant.now(),
                changedBy
        ));
        if (versionHistory.size() > MAX_VERSION_HISTORY) {
            versionHistory.remove(0);
        }
    }

    private Object getDefaultValue(String key) {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("system.name", "Living Agent Service");
        defaults.put("system.version", "1.0.0");
        defaults.put("system.timezone", "Asia/Shanghai");
        defaults.put("system.language", "zh-CN");
        defaults.put("model.default", defaultModelDefault);
        defaults.put("model.fallback", defaultModelFallback);
        defaults.put("model.timeout", 30000);
        defaults.put("model.maxRetries", 3);
        defaults.put("security.sessionTimeout", 3600);
        defaults.put("security.maxLoginAttempts", 5);
        defaults.put("security.passwordMinLength", 8);
        defaults.put("notification.email.enabled", true);
        defaults.put("notification.sms.enabled", false);
        defaults.put("notification.push.enabled", true);
        defaults.put("budget.monthlyDefault", 10000.0);
        defaults.put("budget.alertThreshold", 0.8);
        defaults.put("knowledge.maxSize", 10000);
        defaults.put("knowledge.expirationDays", 365);
        defaults.put("company_intro.tenant_default", "");
        
        return defaults.get(key);
    }

    private Optional<AuthContext> getAuthContext(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return Optional.empty();
        }
        
        String sessionId = authorization.substring(7);
        Optional<AuthSession> sessionOpt = authService.validateSession(sessionId);
        
        return sessionOpt.map(AuthSession::authContext);
    }

    private boolean isEnterprise(AuthContext ctx) {
        return ctx.getAccessLevel() == AccessLevel.FULL || ctx.isFounder();
    }

    // ==================== 工作区热配置 API ====================

    @GetMapping("/workspace/config")
    public ResponseEntity<Map<String, Object>> getWorkspaceConfig(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty() || !isEnterprise(ctxOpt.get())) {
            return ResponseEntity.status(403).build();
        }

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("root", systemSettings.getOrDefault("workspace.root", "/app/workspace"));
        config.put("writeEnabled", systemSettings.getOrDefault("workspace.writeEnabled", true));
        config.put("allowedExtensions", systemSettings.getOrDefault("workspace.allowedExtensions", ""));
        return ResponseEntity.ok(config);
    }

    @PutMapping("/workspace/config")
    public ResponseEntity<Map<String, Object>> updateWorkspaceConfig(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Optional<AuthContext> ctxOpt = getAuthContext(authorization);
        if (ctxOpt.isEmpty() || !isEnterprise(ctxOpt.get())) {
            return ResponseEntity.status(403).build();
        }

        Map<String, Object> updated = new LinkedHashMap<>();

        if (request.containsKey("root")) {
            String newRoot = request.get("root").toString();
            systemSettings.put("workspace.root", newRoot);
            updated.put("root", newRoot);
            log.info("Workspace root updated to: {} (by {})", newRoot, ctxOpt.get().getEmployeeId());

            // 热同步到 FileEditTool
            toolRegistry.get("file_edit").ifPresent(tool -> {
                if (tool instanceof FileEditTool fileEditTool) {
                    fileEditTool.setWorkspaceRoot(newRoot);
                    log.info("FileEditTool workspace root hot-updated to: {}", newRoot);
                }
            });
            // 热同步到 BuildTool
            toolRegistry.get("build").ifPresent(tool -> {
                if (tool instanceof BuildTool buildTool) {
                    buildTool.setWorkspaceRoot(newRoot);
                    log.info("BuildTool workspace root hot-updated to: {}", newRoot);
                }
            });
        }

        if (request.containsKey("writeEnabled")) {
            boolean writeEnabled = Boolean.parseBoolean(request.get("writeEnabled").toString());
            systemSettings.put("workspace.writeEnabled", writeEnabled);
            updated.put("writeEnabled", writeEnabled);
        }

        if (request.containsKey("allowedExtensions")) {
            String extensions = request.get("allowedExtensions").toString();
            systemSettings.put("workspace.allowedExtensions", extensions);
            updated.put("allowedExtensions", extensions);
        }

        return ResponseEntity.ok(updated);
    }

    public record SettingValue(String key, Object value, String type) {}

    public record SettingsChangeRecord(
        String recordId,
        String key,
        Object oldValue,
        Object newValue,
        String changedBy,
        Instant changedAt,
        String reason
    ) {}

    public record BatchUpdateResult(int updated, int failed, List<String> errors) {}

    public record SettingCategory(String name, String displayName, int settingCount) {}

    public record SettingsVersion(
            String versionId,
            String reason,
            Map<String, Object> snapshot,
            Instant createdAt,
            String createdBy
    ) {}

    public record NotificationBarPublic(
            String title,
            String content,
            boolean show,
            String link
    ) {}

}
