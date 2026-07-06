package com.livingagent.core.security.sync;

import com.livingagent.core.security.SecurityIdentity;
import com.livingagent.core.security.Department;

import java.time.Instant;
import java.util.List;

public interface HrSyncAdapter {

    String getAdapterName();
    
    boolean isConfigured();
    
    boolean testConnection();
    
    List<SecurityIdentity> fetchEmployees();
    
    List<Department> fetchDepartments();
    
    SyncResult syncEmployees();
    
    SyncResult syncDepartments();

    SecurityIdentity fetchEmployeeById(String employeeId);

    List<SecurityIdentity> fetchEmployeesByDepartment(String departmentId);

    /**
     * P10: 同步状态确认。
     * 同步完成后向源系统发送确认回执，实现双向闭环。
     */
    SyncConfirmation confirmSync(String syncId);

    /**
     * P10: 获取最近的同步状态。
     */
    SyncStatus getSyncStatus();
    
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

    /**
     * P10: 同步确认回执。
     */
    record SyncConfirmation(
            String syncId,
            String adapterName,
            boolean confirmed,
            Instant confirmedAt,
            String message
    ) {
        public static SyncConfirmation confirmed(String syncId, String adapterName) {
            return new SyncConfirmation(syncId, adapterName, true, Instant.now(), "同步确认成功");
        }

        public static SyncConfirmation failed(String syncId, String adapterName, String reason) {
            return new SyncConfirmation(syncId, adapterName, false, Instant.now(), reason);
        }
    }

    /**
     * P10: 同步状态枚举。
     */
    enum SyncPhase {
        NOT_STARTED,    // 未开始
        IN_PROGRESS,    // 同步中
        SYNCED,         // 已同步（本地完成）
        CONFIRMED,      // 已确认（源系统确认收到）
        FAILED          // 同步失败
    }

    /**
     * P10: 同步状态记录。
     */
    record SyncStatus(
            String lastSyncId,
            SyncPhase phase,
            Instant lastSyncTime,
            Instant lastConfirmedAt,
            int totalSynced,
            int totalConfirmed,
            List<String> pendingConfirmations
    ) {
        public static SyncStatus initial() {
            return new SyncStatus(null, SyncPhase.NOT_STARTED, null, null, 0, 0, List.of());
        }

        public boolean isFullyConfirmed() {
            return phase == SyncPhase.CONFIRMED;
        }
    }
}
