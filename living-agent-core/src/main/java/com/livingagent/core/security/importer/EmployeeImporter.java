package com.livingagent.core.security.importer;

import com.livingagent.core.security.Department;
import com.livingagent.core.security.SecurityIdentity;
import com.livingagent.core.security.UserIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class EmployeeImporter {

    private static final Logger log = LoggerFactory.getLogger(EmployeeImporter.class);

    private static final Set<String> REQUIRED_FIELDS = Set.of("name", "phone", "department");
    
    private static final Map<String, String> FIELD_MAPPINGS = new HashMap<>();
    static {
        FIELD_MAPPINGS.put("姓名", "name");
        FIELD_MAPPINGS.put("员工姓名", "name");
        FIELD_MAPPINGS.put("手机号", "phone");
        FIELD_MAPPINGS.put("手机", "phone");
        FIELD_MAPPINGS.put("电话", "phone");
        FIELD_MAPPINGS.put("部门", "department");
        FIELD_MAPPINGS.put("所属部门", "department");
        FIELD_MAPPINGS.put("职位", "position");
        FIELD_MAPPINGS.put("岗位", "position");
        FIELD_MAPPINGS.put("邮箱", "email");
        FIELD_MAPPINGS.put("电子邮箱", "email");
        FIELD_MAPPINGS.put("工号", "employeeId");
        FIELD_MAPPINGS.put("员工编号", "employeeId");
        FIELD_MAPPINGS.put("入职日期", "joinDate");
        FIELD_MAPPINGS.put("入职时间", "joinDate");
    }

    public ImportResult importFromCsv(byte[] csvData) {
        log.info("Importing employees from CSV, size: {} bytes", csvData.length);
        
        List<SecurityIdentity> employees = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int lineNumber = 0;
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(csvData), StandardCharsets.UTF_8))) {
            
            String headerLine = reader.readLine();
            lineNumber++;
            
            if (headerLine == null || headerLine.isEmpty()) {
                return ImportResult.failed("CSV file is empty");
            }
            
            String[] headers = parseCsvLine(headerLine);
            Map<Integer, String> fieldMapping = mapFields(headers);
            
            validateRequiredFields(fieldMapping, errors);
            
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                
                if (line.trim().isEmpty()) {
                    continue;
                }
                
                try {
                    String[] values = parseCsvLine(line);
                    SecurityIdentity employee = createEmployee(values, fieldMapping, lineNumber);
                    
                    if (employee != null) {
                        employees.add(employee);
                    }
                } catch (Exception e) {
                    errors.add("Line " + lineNumber + ": " + e.getMessage());
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to import CSV: {}", e.getMessage());
            return ImportResult.failed("Failed to parse CSV: " + e.getMessage());
        }
        
        log.info("CSV import completed: {} employees parsed, {} errors", employees.size(), errors.size());
        
        return new ImportResult(employees, errors, lineNumber - 1, employees.size());
    }

    public ImportResult importFromExcel(byte[] excelData) {
        log.info("Importing employees from Excel, size: {} bytes", excelData.length);
        
        List<SecurityIdentity> employees = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<Map<String, String>> rows = new ArrayList<>();
        
        try {
            rows = parseExcelData(excelData);
            
            int rowNum = 0;
            for (Map<String, String> row : rows) {
                rowNum++;
                
                try {
                    SecurityIdentity employee = createEmployeeFromMap(row);
                    
                    if (employee != null) {
                        employees.add(employee);
                    }
                } catch (Exception e) {
                    errors.add("Row " + rowNum + ": " + e.getMessage());
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to import Excel: {}", e.getMessage());
            return ImportResult.failed("Failed to parse Excel: " + e.getMessage());
        }
        
        log.info("Excel import completed: {} employees parsed, {} errors", employees.size(), errors.size());
        
        return new ImportResult(employees, errors, rows.size(), employees.size());
    }

    private String[] parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                values.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        
        values.add(current.toString().trim());
        
        return values.toArray(new String[0]);
    }

    private Map<Integer, String> mapFields(String[] headers) {
        Map<Integer, String> mapping = new HashMap<>();
        
        for (int i = 0; i < headers.length; i++) {
            String header = headers[i].trim();
            String fieldName = FIELD_MAPPINGS.get(header);
            
            if (fieldName == null) {
                fieldName = header.toLowerCase().replaceAll("[\\s\\-]", "_");
            }
            
            mapping.put(i, fieldName);
        }
        
        return mapping;
    }

    private void validateRequiredFields(Map<Integer, String> fieldMapping, List<String> errors) {
        Set<String> foundFields = new HashSet<>(fieldMapping.values());
        
        for (String required : REQUIRED_FIELDS) {
            if (!foundFields.contains(required)) {
                errors.add("Missing required field: " + required);
            }
        }
    }

    private SecurityIdentity createEmployee(String[] values, Map<Integer, String> fieldMapping, int lineNumber) {
        Map<String, String> data = new HashMap<>();
        
        for (Map.Entry<Integer, String> entry : fieldMapping.entrySet()) {
            int index = entry.getKey();
            if (index < values.length) {
                data.put(entry.getValue(), values[index]);
            }
        }
        
        String name = data.get("name");
        if (name == null || name.isEmpty()) {
            log.warn("Line {}: Missing employee name", lineNumber);
            return null;
        }
        
        String phone = data.get("phone");
        if (phone == null || phone.isEmpty()) {
            log.warn("Line {}: Missing phone number for {}", lineNumber, name);
            return null;
        }
        
        return createEmployeeFromMap(data);
    }

    private SecurityIdentity createEmployeeFromMap(Map<String, String> data) {
        String name = data.get("name");
        if (name == null || name.isEmpty()) {
            return null;
        }
        
        SecurityIdentity employee = new SecurityIdentity();
        employee.setName(name);
        
        String phone = data.get("phone");
        if (phone != null) {
            employee.setPhone(phone.replaceAll("[^0-9+]", ""));
        }
        
        employee.setEmail(data.get("email"));
        employee.setDepartment(data.get("department"));
        employee.setPosition(data.get("position"));
        
        String employeeId = data.get("employeeId");
        if (employeeId != null && !employeeId.isEmpty()) {
            employee.setEmployeeId(employeeId);
        } else {
            employee.setEmployeeId("emp_" + System.currentTimeMillis() + "_" + name.hashCode());
        }
        
        String joinDateStr = data.get("joinDate");
        if (joinDateStr != null && !joinDateStr.isEmpty()) {
            try {
                LocalDate joinDate = parseDate(joinDateStr);
                employee.setJoinDate(joinDate.atStartOfDay().toInstant(java.time.ZoneOffset.UTC));
            } catch (Exception e) {
                log.warn("Failed to parse join date: {}", joinDateStr);
            }
        }
        
        employee.setIdentity(UserIdentity.INTERNAL_ACTIVE);
        employee.setSyncSource("import");
        employee.setLastSyncTime(Instant.now());
        
        return employee;
    }

    private LocalDate parseDate(String dateStr) {
        dateStr = dateStr.trim();
        
        DateTimeFormatter[] formatters = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd"),
            DateTimeFormatter.ofPattern("yyyy年MM月dd日"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy")
        };
        
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDate.parse(dateStr, formatter);
            } catch (Exception ignored) {
            }
        }
        
        throw new IllegalArgumentException("Unable to parse date: " + dateStr);
    }

    /**
     * Excel 解析方法 - <b>未实现</b>。
     *
     * <p>此方法当前返回空列表，Excel 导入功能未完整实现。
     * 实现需要添加 Apache POI 依赖并编写解析逻辑。</p>
     *
     * <p>建议：如需 Excel 导入功能，请先添加依赖：</p>
     * <pre>
     * &lt;dependency&gt;
     *   &lt;groupId&gt;org.apache.poi&lt;/groupId&gt;
     *   &lt;artifactId&gt;poi-ooxml&lt;/artifactId&gt;
     *   &lt;version&gt;5.x&lt;/version&gt;
     * &lt;/dependency&gt;
     * </pre>
     *
     * <p>当前推荐使用 CSV 导入（importFromCsv 已完整实现）。</p>
     *
     * @param excelData Excel 文件字节数据
     * @return 空列表（未实现）
     * @throws UnsupportedOperationException 如果调用此方法
     */
    private List<Map<String, String>> parseExcelData(byte[] excelData) {
        List<Map<String, String>> rows = new ArrayList<>();

        log.warn("Excel parsing not fully implemented, using placeholder. Use CSV import instead.");

        // 返回空列表避免中断流程，但调用方应检查结果
        return rows;
    }

    public List<Department> extractDepartments(List<SecurityIdentity> employees) {
        Map<String, Department> departmentMap = new HashMap<>();
        
        for (SecurityIdentity emp : employees) {
            String deptName = emp.getDepartment();
            if (deptName != null && !deptName.isEmpty() && !departmentMap.containsKey(deptName)) {
                Department dept = new Department();
                dept.setDepartmentId("dept_" + deptName.hashCode());
                dept.setName(deptName);
                dept.setTargetBrain(Department.mapDepartmentToBrain(deptName));
                dept.addMember(emp.getEmployeeId());
                departmentMap.put(deptName, dept);
            } else if (deptName != null && departmentMap.containsKey(deptName)) {
                departmentMap.get(deptName).addMember(emp.getEmployeeId());
            }
        }
        
        return new ArrayList<>(departmentMap.values());
    }

    public record ImportResult(
            List<SecurityIdentity> employees,
            List<String> errors,
            int totalRows,
            int importedCount
    ) {
        public static ImportResult failed(String error) {
            return new ImportResult(List.of(), List.of(error), 0, 0);
        }
        
        public boolean isSuccess() {
            return errors.isEmpty();
        }
        
        public boolean hasWarnings() {
            return !errors.isEmpty() && !employees.isEmpty();
        }
        
        public boolean hasEmployees() {
            return !employees.isEmpty();
        }
    }
}
