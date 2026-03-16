package com.livingagent.core.security.sync;

import com.livingagent.core.security.Department;
import com.livingagent.core.security.Employee;
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

public class DingTalkSyncAdapter implements HrSyncAdapter {

    private static final Logger log = LoggerFactory.getLogger(DingTalkSyncAdapter.class);

    private static final String DINGTALK_API = "https://oapi.dingtalk.com";
    
    private final HttpClient httpClient;
    private String appKey;
    private String appSecret;
    private String accessToken;
    private long tokenExpireTime;

    public DingTalkSyncAdapter() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public DingTalkSyncAdapter(String appKey, String appSecret) {
        this();
        this.appKey = appKey;
        this.appSecret = appSecret;
    }

    public void configure(String appKey, String appSecret) {
        this.appKey = appKey;
        this.appSecret = appSecret;
        this.accessToken = null;
        this.tokenExpireTime = 0;
    }

    @Override
    public String getAdapterName() {
        return "DingTalk";
    }

    @Override
    public boolean isConfigured() {
        return appKey != null && !appKey.isEmpty() && 
               appSecret != null && !appSecret.isEmpty();
    }

    @Override
    public boolean testConnection() {
        try {
            String token = getAccessToken();
            return token != null && !token.isEmpty();
        } catch (Exception e) {
            log.error("DingTalk connection test failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public List<Employee> fetchEmployees() {
        log.info("Fetching employees from DingTalk");
        
        List<Employee> employees = new ArrayList<>();
        
        try {
            String token = getAccessToken();
            if (token == null) {
                log.error("Failed to get DingTalk access token");
                return employees;
            }

            List<Map<String, Object>> userList = fetchUserList(token);
            
            for (Map<String, Object> user : userList) {
                Employee employee = convertToEmployee(user);
                if (employee != null) {
                    employees.add(employee);
                }
            }
            
            log.info("Fetched {} employees from DingTalk", employees.size());
            
        } catch (Exception e) {
            log.error("Failed to fetch employees from DingTalk: {}", e.getMessage());
        }
        
        return employees;
    }

    @Override
    public List<Department> fetchDepartments() {
        log.info("Fetching departments from DingTalk");
        
        List<Department> departments = new ArrayList<>();
        
        try {
            String token = getAccessToken();
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
            
            log.info("Fetched {} departments from DingTalk", departments.size());
            
        } catch (Exception e) {
            log.error("Failed to fetch departments from DingTalk: {}", e.getMessage());
        }
        
        return departments;
    }

    @Override
    public SyncResult syncEmployees() {
        log.info("Syncing employees from DingTalk");
        
        int created = 0, updated = 0, errors = 0;
        List<String> errorList = new ArrayList<>();
        
        try {
            List<Employee> employees = fetchEmployees();
            
            for (Employee emp : employees) {
                try {
                    created++;
                } catch (Exception e) {
                    errors++;
                    errorList.add("Failed to sync employee " + emp.getName() + ": " + e.getMessage());
                }
            }
            
        } catch (Exception e) {
            errorList.add("Sync failed: " + e.getMessage());
        }
        
        return new SyncResult(created + updated + errors, created, updated, 0, 0, errorList);
    }

    @Override
    public SyncResult syncDepartments() {
        log.info("Syncing departments from DingTalk");
        
        int created = 0, updated = 0;
        List<String> errorList = new ArrayList<>();
        
        try {
            List<Department> departments = fetchDepartments();
            created = departments.size();
        } catch (Exception e) {
            errorList.add("Department sync failed: " + e.getMessage());
        }
        
        return new SyncResult(created + updated, created, updated, 0, 0, errorList);
    }

    @Override
    public Employee fetchEmployeeById(String employeeId) {
        try {
            String token = getAccessToken();
            if (token == null) return null;

            String url = DINGTALK_API + "/topapi/v2/user/get?access_token=" + token;
            
            Map<String, Object> body = new HashMap<>();
            body.put("userid", employeeId);
            
            String response = postJson(url, body);
            Map<String, Object> result = parseJson(response);
            
            if (result != null && result.containsKey("result")) {
                return convertToEmployee((Map<String, Object>) result.get("result"));
            }
            
        } catch (Exception e) {
            log.error("Failed to fetch employee {}: {}", employeeId, e.getMessage());
        }
        
        return null;
    }

    @Override
    public List<Employee> fetchEmployeesByDepartment(String departmentId) {
        List<Employee> employees = new ArrayList<>();
        
        try {
            String token = getAccessToken();
            if (token == null) return employees;

            String url = DINGTALK_API + "/topapi/user/listbydepartment?access_token=" + token;
            
            Map<String, Object> body = new HashMap<>();
            body.put("dept_id", Long.parseLong(departmentId));
            body.put("cursor", 0);
            body.put("size", 100);
            
            String response = postJson(url, body);
            Map<String, Object> result = parseJson(response);
            
            if (result != null && result.containsKey("result")) {
                Map<String, Object> resultData = (Map<String, Object>) result.get("result");
                List<Map<String, Object>> userList = (List<Map<String, Object>>) resultData.get("user_list");
                
                if (userList != null) {
                    for (Map<String, Object> user : userList) {
                        Employee emp = convertToEmployee(user);
                        if (emp != null) {
                            employees.add(emp);
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to fetch employees by department: {}", e.getMessage());
        }
        
        return employees;
    }

    private synchronized String getAccessToken() {
        if (accessToken != null && System.currentTimeMillis() < tokenExpireTime) {
            return accessToken;
        }
        
        try {
            String url = DINGTALK_API + "/gettoken?appkey=" + appKey + "&appsecret=" + appSecret;
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(30))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> result = parseJson(response.body());
            
            if (result != null && result.containsKey("access_token")) {
                accessToken = (String) result.get("access_token");
                Integer expiresIn = (Integer) result.get("expires_in");
                tokenExpireTime = System.currentTimeMillis() + (expiresIn != null ? expiresIn * 1000L : 7200000L);
                return accessToken;
            }
            
        } catch (Exception e) {
            log.error("Failed to get DingTalk access token: {}", e.getMessage());
        }
        
        return null;
    }

    private List<Map<String, Object>> fetchUserList(String token) {
        List<Map<String, Object>> allUsers = new ArrayList<>();
        
        try {
            String url = DINGTALK_API + "/topapi/user/list?access_token=" + token;
            
            long cursor = 0;
            boolean hasMore = true;
            
            while (hasMore) {
                Map<String, Object> body = new HashMap<>();
                body.put("cursor", cursor);
                body.put("size", 100);
                
                String response = postJson(url, body);
                Map<String, Object> result = parseJson(response);
                
                if (result != null && result.containsKey("result")) {
                    Map<String, Object> resultData = (Map<String, Object>) result.get("result");
                    List<Map<String, Object>> userList = (List<Map<String, Object>>) resultData.get("user_list");
                    
                    if (userList != null) {
                        allUsers.addAll(userList);
                    }
                    
                    hasMore = Boolean.TRUE.equals(resultData.get("has_more"));
                    cursor = resultData.containsKey("next_cursor") ? 
                            ((Number) resultData.get("next_cursor")).longValue() : 0;
                } else {
                    hasMore = false;
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to fetch user list: {}", e.getMessage());
        }
        
        return allUsers;
    }

    private List<Map<String, Object>> fetchDepartmentList(String token) {
        List<Map<String, Object>> allDepts = new ArrayList<>();
        
        try {
            String url = DINGTALK_API + "/topapi/v2/department/listsub?access_token=" + token;
            
            Map<String, Object> body = new HashMap<>();
            body.put("dept_id", 1);
            
            String response = postJson(url, body);
            Map<String, Object> result = parseJson(response);
            
            if (result != null && result.containsKey("result")) {
                allDepts = (List<Map<String, Object>>) result.get("result");
            }
            
        } catch (Exception e) {
            log.error("Failed to fetch department list: {}", e.getMessage());
        }
        
        return allDepts;
    }

    private Employee convertToEmployee(Map<String, Object> user) {
        if (user == null) return null;
        
        Employee employee = new Employee();
        
        String userId = getString(user, "userid");
        if (userId == null) {
            userId = getString(user, "user_id");
        }
        employee.setEmployeeId("dingtalk_" + userId);
        
        employee.setName(getString(user, "name"));
        employee.setPhone(getString(user, "mobile"));
        employee.setEmail(getString(user, "email"));
        
        Object deptId = user.get("dept_id");
        if (deptId != null) {
            employee.setDepartment(String.valueOf(deptId));
        } else {
            employee.setDepartment(getString(user, "department"));
        }
        
        employee.setPosition(getString(user, "position"));
        employee.setIdentity(UserIdentity.INTERNAL_ACTIVE);
        employee.setOauthProvider("dingtalk");
        employee.setOauthUserId(userId);
        employee.setSyncSource("dingtalk");
        employee.setLastSyncTime(Instant.now());
        
        Object active = user.get("active");
        if (active != null && !Boolean.TRUE.equals(active)) {
            employee.setIdentity(UserIdentity.INTERNAL_DEPARTED);
            employee.setActive(false);
        }
        
        return employee;
    }

    private Department convertToDepartment(Map<String, Object> dept) {
        if (dept == null) return null;
        
        Department department = new Department();
        
        Object deptId = dept.get("dept_id");
        if (deptId == null) {
            deptId = dept.get("id");
        }
        department.setDepartmentId("dingtalk_" + deptId);
        
        department.setName(getString(dept, "name"));
        
        Object parentId = dept.get("parent_id");
        if (parentId != null) {
            department.setParentDepartmentId("dingtalk_" + parentId);
        }
        
        return department;
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private String postJson(String url, Map<String, Object> body) throws Exception {
        String jsonBody = toJson(body);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofSeconds(30))
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    private String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value == null) {
                sb.append("null");
            } else if (value instanceof String) {
                sb.append("\"").append(value).append("\"");
            } else if (value instanceof Number || value instanceof Boolean) {
                sb.append(value);
            } else {
                sb.append("\"").append(value).append("\"");
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private Map<String, Object> parseJson(String json) {
        Map<String, Object> result = new HashMap<>();
        if (json == null || json.isEmpty()) return result;
        
        json = json.trim();
        if (!json.startsWith("{") || !json.endsWith("}")) return result;
        
        json = json.substring(1, json.length() - 1);
        
        int depth = 0;
        StringBuilder current = new StringBuilder();
        String currentKey = null;
        boolean inString = false;
        boolean escaped = false;
        
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            
            if (escaped) {
                current.append(c);
                escaped = false;
                continue;
            }
            
            if (c == '\\') {
                escaped = true;
                current.append(c);
                continue;
            }
            
            if (c == '"') {
                inString = !inString;
                current.append(c);
                continue;
            }
            
            if (!inString) {
                if (c == '{' || c == '[') {
                    depth++;
                    current.append(c);
                } else if (c == '}' || c == ']') {
                    depth--;
                    current.append(c);
                } else if (c == ':' && depth == 0) {
                    currentKey = current.toString().trim();
                    if (currentKey.startsWith("\"") && currentKey.endsWith("\"")) {
                        currentKey = currentKey.substring(1, currentKey.length() - 1);
                    }
                    current = new StringBuilder();
                } else if (c == ',' && depth == 0) {
                    if (currentKey != null) {
                        result.put(currentKey, parseValue(current.toString().trim()));
                    }
                    currentKey = null;
                    current = new StringBuilder();
                } else {
                    current.append(c);
                }
            } else {
                current.append(c);
            }
        }
        
        if (currentKey != null) {
            result.put(currentKey, parseValue(current.toString().trim()));
        }
        
        return result;
    }

    private Object parseValue(String value) {
        if (value == null || value.isEmpty() || "null".equals(value)) {
            return null;
        }
        
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        
        if ("true".equals(value)) return true;
        if ("false".equals(value)) return false;
        
        try {
            if (value.contains(".")) {
                return Double.parseDouble(value);
            } else {
                return Long.parseLong(value);
            }
        } catch (NumberFormatException e) {
            return value;
        }
    }
}
