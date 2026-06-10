-- V14: Windows 自动化节点注册表
CREATE TABLE IF NOT EXISTS windows_automation_nodes (
    node_id         VARCHAR(64) PRIMARY KEY,
    ip_address      VARCHAR(64) NOT NULL,
    port            INTEGER DEFAULT 8765,
    hostname        VARCHAR(128),
    cpu_count       INTEGER,
    memory_gb       DECIMAL(5,1),
    applications    TEXT,
    description     VARCHAR(256),
    status          VARCHAR(16) DEFAULT 'offline',
    last_heartbeat  TIMESTAMP,
    registered_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    tenant_id       VARCHAR(64),
    user_id         VARCHAR(64),
    enabled         BOOLEAN DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_wan_tenant ON windows_automation_nodes(tenant_id);
CREATE INDEX IF NOT EXISTS idx_wan_status ON windows_automation_nodes(status);
CREATE INDEX IF NOT EXISTS idx_wan_user ON windows_automation_nodes(user_id);
