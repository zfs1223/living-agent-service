# P2-2：WebSocket 断线重连完善

> 状态：✅ 已完成
> 完成日期：2026-05-21

---

## 实现概述

本方案实现了 WebSocket 断线重连的完整能力：

1. **会话持久化**：SessionContext 持久化到数据库
2. **事件队列**：断线期间的事件缓存到数据库
3. **自动补发**：重连后自动补发待处理事件
4. **历史恢复**：重连后可恢复对话历史

---

## 新增文件

| 文件 | 说明 |
| --- | --- |
| `core/session/SessionPersistenceService.java` | 会话持久化服务接口 |
| `core/session/impl/SessionPersistenceServiceImpl.java` | 会话持久化服务实现 |
| `core/session/EventQueueService.java` | 事件队列服务接口 |
| `core/session/impl/EventQueueServiceImpl.java` | 事件队列服务实现 |
| `core/database/entity/SessionContextEntity.java` | 会话上下文实体 |
| `core/database/entity/PendingEventEntity.java` | 待处理事件实体 |
| `core/database/repository/SessionContextRepository.java` | 会话上下文仓库 |
| `core/database/repository/PendingEventRepository.java` | 待处理事件仓库 |
| `gateway/websocket/PersistentConnectionRegistry.java` | 持久化连接注册表 |

---

## 数据库表

### session_contexts

```sql
CREATE TABLE session_contexts (
    session_id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255),
    tenant_id VARCHAR(255),
    department_code VARCHAR(100),
    task_key VARCHAR(255),
    execution_id VARCHAR(255),
    project_id VARCHAR(255),
    project_key VARCHAR(255),
    conversation_id VARCHAR(255),
    connected_at TIMESTAMP,
    last_activity TIMESTAMP,
    attributes_json TEXT
);

CREATE INDEX idx_sess_user_id ON session_contexts(user_id);
CREATE INDEX idx_sess_tenant_id ON session_contexts(tenant_id);
CREATE INDEX idx_sess_conversation_id ON session_contexts(conversation_id);
CREATE INDEX idx_sess_last_activity ON session_contexts(last_activity);
```

### pending_events

```sql
CREATE TABLE pending_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id VARCHAR(255),
    event_id VARCHAR(255),
    event_type VARCHAR(100),
    payload TEXT,
    timestamp BIGINT,
    sent BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP,
    sent_at TIMESTAMP
);

CREATE INDEX idx_pending_session ON pending_events(session_id);
CREATE INDEX idx_pending_timestamp ON pending_events(timestamp);
```

---

## 验收标准

- [x] ✅ 断线后可恢复最近 5 分钟内的上下文
- [x] ✅ 重连后自动补发待处理的 execution 状态
- [x] ✅ 用户无需刷新页面即可继续对话（通过 conversationId）
- [x] ✅ 对话历史可加载并作为上下文

---

## 使用方式

### 前端重连

```javascript
// 重连时携带 conversationId
const ws = new WebSocket(`/ws/dept/tech?conversationId=${conversationId}&token=${token}`);
```

### 后端事件入队

```java
// 在处理长时间任务时，将进度事件入队
if (connectionRegistry instanceof PersistentConnectionRegistry persistent) {
    String eventJson = objectMapper.writeValueAsString(progressMsg);
    persistent.enqueueEvent(sessionId, "execution_progress", eventJson);
}
```

---

## 相关文件

| 文件 | 说明 |
| --- | --- |
| `gateway/websocket/DepartmentWebSocketHandler.java` | WebSocket 处理器（含重连逻辑） |
| `gateway/websocket/ConnectionRegistry.java` | 连接注册表接口 |
| `gateway/websocket/InMemoryConnectionRegistry.java` | 内存连接注册表（原有） |
| `gateway/websocket/PersistentConnectionRegistry.java` | 持久化连接注册表（新增） |
