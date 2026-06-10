# 待实施方案索引

> 本目录包含 Living Agent 后续需要实施的方案文档。
> 版本：2026-05-21

---

## 待实施项总览

| 编号 | 方案 | 优先级 | 状态 | 关键文件 |
| --- | --- | --- | --- | --- |
| P1-1 | 部门对话作为长期会话 | P1 | ✅ 已完成 | `DepartmentConversationEntity.java` |
| P1-2 | 任务/项目模块数据持久化 | P1 | ✅ 已完成 | `TaskEntity.java`, `ProjectEntity.java` |
| P2-1 | 部门对话 REST API | P2 | ✅ 已完成 | `ConversationController.java` |
| P2-2 | WebSocket 断线重连完善 | P2 | ✅ 已完成 | `PersistentConnectionRegistry.java` |
| P3-1 | Windows 自动化桥接改进 | P3 | ✅ 已完成 | `WINDOWS_AUTOMATION_IMPROVEMENT_PLAN.md` |

---

## P1-1 ✅ 部门对话作为长期会话

**完成日期**: 2026-05-21

### 已实现功能

1. **对话实体**: `DepartmentConversationEntity` 完整字段
2. **对话仓储**: `DepartmentConversationRepository` 支持多种查询
3. **对话服务**: `ConversationService` 接口和实现
4. **对话控制器**: `ConversationController` REST API
5. **历史消息关联**: `DepartmentChatMessageEntity` 关联 `conversationId`
6. **对话历史恢复**: `DepartmentChatService.getConversationHistory()`

### 关联字段

- `DepartmentConversationEntity.conversationId`
- `DepartmentChatMessageEntity.conversationId`
- `TaskEntity.conversationId`
- `SessionContextEntity.conversationId`

---

## P1-2 ✅ 任务/项目模块数据持久化

**完成日期**: 2026-05-21

### 已实现功能

1. **任务实体**: `TaskEntity` 完整字段（taskKey, executionId, conversationId 等）
2. **任务仓储**: `TaskRepository` 支持多种查询
3. **任务服务**: `TaskCheckout` 服务实现
4. **任务控制器**: `TaskController` REST API

### 关联字段

- `TaskEntity.conversationId`
- `TaskEntity.projectId`
- `TaskEntity.executionId`
- `ProjectEntity.projectKey`

---

## P2-1 ✅ 部门对话 REST API

**完成日期**: 2026-05-21

### 新增文件

| 文件 |
| --- |
| `core/conversation/ConversationService.java` |
| `core/conversation/impl/ConversationServiceImpl.java` |
| `core/util/IdUtils.java` (新增 generateConversationId) |

### 端点

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/conversations` | 对话列表 |
| GET | `/api/conversations/{id}` | 对话详情 |
| POST | `/api/conversations` | 创建对话 |
| PUT | `/api/conversations/{id}` | 更新对话 |
| POST | `/api/conversations/{id}/archive` | 归档对话 |
| POST | `/api/conversations/{id}/restore` | 恢复对话 |
| DELETE | `/api/conversations/{id}` | 软删除 |
| POST | `/api/conversations/{id}/destroy` | 彻底销毁 |

---

## P2-2 ✅ WebSocket 断线重连完善

**完成日期**: 2026-05-21

### 新增文件

| 文件 | 说明 |
| --- | --- |
| `core/session/SessionPersistenceService.java` | 会话持久化服务接口 |
| `core/session/impl/SessionPersistenceServiceImpl.java` | 会话持久化服务实现 |
| `core/session/EventQueueService.java` | 事件队列服务接口 |
| `core/session/impl/EventQueueServiceImpl.java` | 事件队列服务实现 |
| `core/database/entity/SessionContextEntity.java` | 会话上下文实体 |
| `core/database/entity/PendingEventEntity.java` | 待补发事件实体 |
| `core/database/repository/SessionContextRepository.java` | 会话仓储 |
| `core/database/repository/PendingEventRepository.java` | 事件仓储 |
| `gateway/websocket/PersistentConnectionRegistry.java` | 持久化连接注册表接口 |
| `db/migration/V2026_05_21_001__session_contexts.sql` | 会话上下文表 |
| `db/migration/V2026_05_21_002__pending_events.sql` | 待补发事件表 |

### 功能特性

1. **会话持久化**: WebSocket 断线后会话上下文持久化到数据库
2. **事件队列**: 5分钟TTL的待补发事件缓存
3. **自动补发**: 重连后自动补发断线期间的事件
4. **对话历史**: 支持通过 conversationId 恢复对话历史

---

## 进度追踪

| 项目 | 开始 | 完成 | 状态 |
| --- | --- | --- | --- |
| P1-1 | - | 2026-05-21 | ✅ 已完成 |
| P1-2 | - | 2026-05-21 | ✅ 已完成 |
| P2-1 | 2026-05-21 | 2026-05-21 | ✅ 已完成 |
| P2-2 | 2026-05-21 | 2026-05-21 | ✅ 已完成 |

---

## 相关文档

| 文档 | 说明 |
| --- | --- |
| `docs/core/INDEX.md` | 核心定义文档索引 |
| `docs/implemented/INDEX.md` | 已完成方案索引 |
| `docs/guides/INDEX.md` | 开发指南索引 |
