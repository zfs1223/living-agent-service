package com.livingagent.core.security.sync;

import com.livingagent.core.security.Employee;
import com.livingagent.core.security.Department;

import java.util.List;

public interface HrSyncAdapter {

    String getAdapterName();
    
    boolean isConfigured();
    
    boolean testConnection();
    
    List<Employee> fetchEmployees();
    
    List<Department> fetchDepartments();
    
    SyncResult syncEmployees();
    
    SyncResult syncDepartments();
    
    Employee fetchEmployeeById(String employeeId);
    
    List<Employee> fetchEmployeesByDepartment(String departmentId);
    
    record SyncResult(
            int totalProcessed,
            int created,
            int updated,
            int deleted,
            int skipped,
            List<String> errors
    ) {
        public static SyncResult empty() {
            return new SyncResult(0, 0, 0, 0, 0, List.of());
        }
        
        public static SyncResult success(int total, int created, int updated) {
            return new SyncResult(total, created, updated, 0, 0, List.of());
        }
        
        public static SyncResult withErrors(int total, int created, int updated, List<String> errors) {
            return new SyncResult(total, created, updated, 0, 0, errors);
        }
        
        public boolean isSuccess() {
            return errors.isEmpty();
        }
        
        public boolean hasChanges() {
            return created > 0 || updated > 0 || deleted > 0;
        }
    }
}
