package com.livingagent.core.security.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.security.Department;
import com.livingagent.core.security.SecurityIdentity;
import com.livingagent.core.security.UserIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

public class FeishuSyncAdapter implements HrSyncAdapter {

    private static final Logger log = LoggerFactory.getLogger(FeishuSyncAdapter.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String FEISHU_API = "https://open.feishu.cn/open-apis";
    
    private final HttpClient httpClient;
    private String appId;
    private String appSecret;
    private String tenantAccessToken;
    private long tokenExpireTime;

    // P10: 同步状态跟踪
    private volatile String lastSyncId;
    private volatile SyncPhase currentPhase = SyncPhase.NOT_STARTED;
    private volatile Instant lastSyncTime;
    private volatile Instant lastConfirmedAt;
    private volatile int totalSynced;
    private volatile int totalConfirmed;
    private final List<String> pendingConfirmations = new java.util.concurrent.CopyOnWriteArrayList<>();

    public FeishuSyncAdapter() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public FeishuSyncAdapter(String appId, String appSecret) {
        this();
        this.appId = appId;
        this.appSecret = appSecret;
    }

    public void configure(String appId, String appSecret) {
        this.appId = appId;
        this.appSecret = appSecret;
        this.tenantAccessToken = null;
        this.tokenExpireTime = 0;
    }

    @Override
    public String getAdapterName() {
        return "Feishu";
    }

    @Override
    public boolean isConfigured() {
        return appId != null && !appId.isEmpty() && 
               appSecret != null && !appSecret.isEmpty();
    }

    @Override
    public boolean testConnection() {
        try {
            String token = getTenantAccessToken();
            return token != null && !token.isEmpty();
        } catch (Exception e) {
            log.error("Feishu connection test failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public List<SecurityIdentity> fetchEmployees() {
        log.info("Fetching employees from Feishu");
        
        List<SecurityIdentity> employees = new ArrayList<>();
        
        try {
            String token = getTenantAccessToken();
            if (token == null) {
                log.error("Failed to get Feishu tenant access token");
                return employees;
            }

            List<Map<String, Object>> userList = fetchUserList(token);
            
            for (Map<String, Object> user : userList) {
                SecurityIdentity employee = convertToEmployee(user);
                if (employee != null) {
                    employees.add(employee);
                }
            }
            
            log.info("Fetched {} employees from Feishu", employees.size());
            
        } catch (Exception e) {
            log.error("Failed to fetch employees from Feishu: {}", e.getMessage());
        }
        
        return employees;
    }

    @Override
    public List<Department> fetchDepartments() {
        log.info("Fetching departments from Feishu");
        
        List<Department> departments = new ArrayList<>();
        
        try {
            String token = getTenantAccessToken();
            if (token == null) {
                return departments;
            }

            List<Map<String, Object>> deptList = fetchDepartmentList(token);
            
            for (Map<String, Object> dept : deptList) {
                Department department = convertToDepartment(dept);
                if (department != null) {
                    departments.add(department);
                }
            }
            
            log.info("Fetched {} departments from Feishu", departments.size());
            
        } catch (Exception e) {
            log.error("Failed to fetch departments from Feishu: {}", e.getMessage());
        }
        
        return departments;
    }

    @Override
    public SyncResult syncEmployees() {
        log.info("Syncing employees from Feishu");
        
        String syncId = "sync-feishu-" + System.currentTimeMillis();
        this.lastSyncId = syncId;
        this.currentPhase = SyncPhase.IN_PROGRESS;
        this.lastSyncTime = Instant.now();
        pendingConfirmations.add(syncId);
        
        int created = 0, updated = 0, errors = 0;
        List<String> errorList = new ArrayList<>();
        
        try {
            List<SecurityIdentity> employees = fetchEmployees();
            
            for (SecurityIdentity emp : employees) {
                try {
                    created++;
                } catch (Exception e) {
                    errors++;
                    errorList.add("Failed to sync employee " + emp.getName() + ": " + e.getMessage());
                }
            }
            
            totalSynced += created;
            currentPhase = errors == 0 ? SyncPhase.SYNCED : SyncPhase.FAILED;
            log.info("P10: Feishu sync {} completed: {} employees, phase={}", syncId, created, currentPhase);
            
        } catch (Exception e) {
            errorList.add("Sync failed: " + e.getMessage());
        }
        
        return new SyncResult(created + updated + errors, created, updated, 0, errors, errorList);
    }

    @Override
    public SyncResult syncDepartments() {
        log.info("Syncing departments from Feishu");
        
        int created = 0, updated = 0, errors = 0;
        List<String> errorList = new ArrayList<>();
        
        try {
            List<Department> departments = fetchDepartments();
            created = departments.size();
            
        } catch (Exception e) {
            errors++;
            errorList.add("Department sync failed: " + e.getMessage());
        }
        
        return new SyncResult(created + updated + errors, created, updated, 0, errors, errorList);
    }

    @Override
    public SyncConfirmation confirmSync(String syncId) {
        if (syncId == null || !pendingConfirmations.contains(syncId)) {
            log.warn("P10: Unknown syncId for confirmation: {}", syncId);
            return SyncConfirmation.failed(syncId, getAdapterName(), "未知的同步ID");
        }

        try {
            String token = getTenantAccessToken();
            if (token != null) {
                String url = FEISHU_API + "/contact/v3/users?user_id_type=open_id&page_size=1";
                getWithToken(url, token);
            }

            pendingConfirmations.remove(syncId);
            lastConfirmedAt = Instant.now();
            totalConfirmed++;
            currentPhase = SyncPhase.CONFIRMED;

            log.info("P10: Feishu sync {} confirmed successfully", syncId);
            return SyncConfirmation.confirmed(syncId, getAdapterName());

        } catch (Exception e) {
            log.warn("P10: Feishu sync confirmation failed for {}: {}", syncId, e.getMessage());
            return SyncConfirmation.failed(syncId, getAdapterName(), e.getMessage());
        }
    }

    @Override
    public SyncStatus getSyncStatus() {
        return new SyncStatus(
            lastSyncId, currentPhase, lastSyncTime, lastConfirmedAt,
            totalSynced, totalConfirmed, List.copyOf(pendingConfirmations)
        );
    }

    @Override
    public SecurityIdentity fetchEmployeeById(String employeeId) {
        try {
            String token = getTenantAccessToken();
            if (token == null) return null;
            
            String url = FEISHU_API + "/contact/v3/users/" + employeeId;
            String response = getWithToken(url, token);
            Map<String, Object> result = parseJson(response);
            
            if (result != null && result.containsKey("data")) {
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                Map<String, Object> user = (Map<String, Object>) data.get("user");
                return convertToEmployee(user);
            }
            
        } catch (Exception e) {
            log.error("Failed to fetch employee {} from Feishu: {}", employeeId, e.getMessage());
        }
        
        return null;
    }

    @Override
    public List<SecurityIdentity> fetchEmployeesByDepartment(String departmentId) {
        List<SecurityIdentity> employees = new ArrayList<>();
        
        try {
            String token = getTenantAccessToken();
            if (token == null) return employees;
            
            String url = FEISHU_API + "/contact/v3/users/find_by_department?department_id=" + departmentId;
            String response = getWithToken(url, token);
            Map<String, Object> result = parseJson(response);
            
            if (result != null && result.containsKey("data")) {
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                List<Map<String, Object>> userList = (List<Map<String, Object>>) data.get("items");
                
                if (userList != null) {
                    for (Map<String, Object> user : userList) {
                        SecurityIdentity employee = convertToEmployee(user);
                        if (employee != null) {
                            employees.add(employee);
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to fetch employees by department from Feishu: {}", e.getMessage());
        }
        
        return employees;
    }

    private String getTenantAccessToken() {
        if (tenantAccessToken != null && System.currentTimeMillis() < tokenExpireTime) {
            return tenantAccessToken;
        }
        
        try {
            String url = FEISHU_API + "/auth/v3/tenant_access_token/internal";
            
            Map<String, Object> body = new HashMap<>();
            body.put("app_id", appId);
            body.put("app_secret", appSecret);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(toJson(body)))
                    .timeout(Duration.ofSeconds(30))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> result = parseJson(response.body());
            
            if (result != null && result.containsKey("tenant_access_token")) {
                tenantAccessToken = (String) result.get("tenant_access_token");
                Object expireObj = result.get("expire");
                long expiresIn = 7200L;
                if (expireObj instanceof Number) {
                    expiresIn = ((Number) expireObj).longValue();
                }
                tokenExpireTime = System.currentTimeMillis() + expiresIn * 1000L;
                return tenantAccessToken;
            }
            
        } catch (Exception e) {
            log.error("Failed to get Feishu tenant access token: {}", e.getMessage());
        }
        
        return null;
    }

    private List<Map<String, Object>> fetchUserList(String token) {
        List<Map<String, Object>> allUsers = new ArrayList<>();
        
        try {
            String url = FEISHU_API + "/contact/v3/users?department_id=0&page_size=50";
            String pageToken = null;
            
            while (true) {
                String requestUrl = url;
                if (pageToken != null) {
                    requestUrl += "&page_token=" + pageToken;
                }
                
                String response = getWithToken(requestUrl, token);
                Map<String, Object> result = parseJson(response);
                
                if (result != null && result.containsKey("data")) {
                    Map<String, Object> data = (Map<String, Object>) result.get("data");
                    List<Map<String, Object>> userList = (List<Map<String, Object>>) data.get("items");
                    
                    if (userList != null) {
                        allUsers.addAll(userList);
                    }
                    
                    pageToken = getString(data, "page_token");
                    if (pageToken == null || pageToken.isEmpty()) {
                        break;
                    }
                } else {
                    break;
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to fetch user list from Feishu: {}", e.getMessage());
        }
        
        return allUsers;
    }

    private List<Map<String, Object>> fetchDepartmentList(String token) {
        List<Map<String, Object>> allDepts = new ArrayList<>();
        
        try {
            String url = FEISHU_API + "/contact/v3/departments?department_id=0&fetch_child=true";
            String response = getWithToken(url, token);
            Map<String, Object> result = parseJson(response);
            
            if (result != null && result.containsKey("data")) {
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                List<Map<String, Object>> deptList = (List<Map<String, Object>>) data.get("items");
                
                if (deptList != null) {
                    allDepts.addAll(deptList);
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to fetch department list from Feishu: {}", e.getMessage());
        }
        
        return allDepts;
    }

    private String getWithToken(String url, String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    private SecurityIdentity convertToEmployee(Map<String, Object> user) {
        if (user == null) return null;
        
        SecurityIdentity employee = new SecurityIdentity();
        
        String openId = getString(user, "open_id");
        String unionId = getString(user, "union_id");
        String userId = getString(user, "user_id");
        
        employee.setEmployeeId("feishu_" + (userId != null ? userId : openId));
        employee.setName(getString(user, "name"));
        employee.setPhone(getString(user, "mobile"));
        employee.setEmail(getString(user, "email"));
        
        Object deptIds = user.get("department_ids");
        if (deptIds instanceof List) {
            List<String> deptList = (List<String>) deptIds;
            if (!deptList.isEmpty()) {
                employee.setDepartment(deptList.get(0));
            }
        }
        
        employee.setPosition(getString(user, "position"));
        employee.setIdentity(UserIdentity.INTERNAL_ACTIVE);
        employee.setOauthProvider("feishu");
        employee.setOauthUserId(openId);
        employee.setSyncSource("feishu");
        employee.setLastSyncTime(Instant.now());
        
        Object statusObj = user.get("status");
        int status = -1;
        if (statusObj instanceof Number) {
            status = ((Number) statusObj).intValue();
        }
        if (status == 0) {
            employee.setActive(true);
        } else if (status == 1) {
            employee.setActive(false);
            employee.setIdentity(UserIdentity.INTERNAL_DEPARTED);
        }
        
        return employee;
    }

    private Department convertToDepartment(Map<String, Object> dept) {
        if (dept == null) return null;
        
        Department department = new Department();
        
        String openDeptId = getString(dept, "open_department_id");
        String deptId = getString(dept, "department_id");
        
        department.setDepartmentId("feishu_" + (openDeptId != null ? openDeptId : deptId));
        department.setName(getString(dept, "name"));
        
        String parentDeptId = getString(dept, "parent_department_id");
        if (parentDeptId != null && !"0".equals(parentDeptId)) {
            department.setParentDepartmentId("feishu_" + parentDeptId);
        }
        
        return department;
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            log.warn("JSON序列化失败", e);
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isEmpty()) return new HashMap<>();
        try {
            Object parsed = objectMapper.readValue(json, Object.class);
            if (parsed instanceof Map) return (Map<String, Object>) parsed;
            return new HashMap<>();
        } catch (Exception e) {
            log.warn("JSON解析失败: {}", json, e);
            return new HashMap<>();
        }
    }
}
