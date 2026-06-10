-- V24: 审计日志持久化表
-- 将 PermissionServiceImpl 中的内存 ConcurrentHashMap 审计日志迁移为 JPA 持久化

CREATE TABLE IF NOT EXISTS access_audit_logs (
    log_id          VARCHAR(50)     NOT NULL PRIMARY KEY,
    employee_id     VARCHAR(255)    NOT NULL,
    employee_name   VARCHAR(255),
    resource        VARCHAR(255)    NOT NULL,
    action          VARCHAR(255)    NOT NULL,
    granted         BOOLEAN         NOT NULL DEFAULT FALSE,
    reason          VARCHAR(500),
    timestamp       BIGINT          NOT NULL,
    session_id      VARCHAR(100),
    ip_address      VARCHAR(50),
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_employee_id ON access_audit_logs(employee_id);
CREATE INDEX IF NOT EXISTS idx_audit_timestamp ON access_audit_logs(timestamp);
CREATE INDEX IF NOT EXISTS idx_audit_resource_action ON access_audit_logs(resource, action);