# 文件管理规范

> Living Agent Service 文件管理、产物记录、权限控制与桌面端同步的完整规范
>
> **文档状态**: 2026-06-25 初版 — 综合权限设计、产物记录、设备绑定与本地保存机制

---

## 一、概述

### 1.1 目标

- **统一文件管理**：所有工具创建的文件必须遵循统一的保存路径和记录机制
- **权限隔离**：用户只能看到自己权限范围内的产物
- **设备绑定**：桌面客户端与物理设备绑定，支持审计和任务路由
- **本地同步**：桌面客户端可配置本地保存路径，自动同步产物

### 1.2 适用范围

| 场景 | 说明 |
|------|------|
| 数字员工产物 | 固定数字员工使用工具创建的文件 |
| LLM 直接产物 | Brain 直接生成的文本、代码、报告等 |
| 用户本地副本 | 桌面客户端同步到本地的产物副本 |
| 项目文档 | 项目相关的文档、报告、设计稿等 |

---

## 二、目录结构规范

### 2.1 服务端数据目录总览

```
data/
├── artifacts/                                  # 产物目录（见 §2.2）
│
├── compact-outputs/                            # 工具输出压缩存储
│   └── tool-{hash}_{timestamp}.txt             # 压缩后的工具输出
│
├── conversations/                              # 对话历史（按 tenant/user/taskKey 组织）
│   └── {tenantId}/{userId}/{taskKey}/{executionId}/
│       ├── events.jsonl                        # 对话事件流
│       ├── session.json                        # 会话状态
│       ├── summary.json                        # 会话摘要
│       ├── receipts/                           # 执行回执
│       └── artifacts/                          # 对话产物
│
├── conversations-by-id/                        # 对话历史（按 conversationId 组织）
│   └── {tenantId}/{conversationId}/
│       ├── events.jsonl
│       ├── summary.json
│       └── artifacts/
│
├── indexes/                                    # 索引目录
│   ├── by-user/{userId}.json                   # 用户索引
│   ├── by-project/{projectId}.json             # 项目索引
│   ├── by-task/{taskKey}.json                  # 任务索引
│   └── by-execution/{executionId}.json         # 执行索引
│
├── knowledge.db                                 # 知识库数据库（SQLite）
├── memory.db                                    # 记忆数据库（SQLite）
│
├── personal-knowledge/                         # 个人知识库
│   └── {employeeCode}/experiences.jsonl
│
├── department-knowledge/                       # 部门知识库
│   └── {dept}/best-practices.jsonl
│
├── knowledge/                                  # 共享知识库
│   └── shared/policies.jsonl
│
├── projects/                                   # 项目数据
│   └── {tenantId}/{projectId}/
│       ├── events.jsonl                        # 项目事件流
│       ├── summary.json                        # 项目摘要
│       ├── phases/{phaseId}.json               # 项目阶段
│       ├── tasks/{taskId}.json                 # 项目任务
│       └── artifacts/                          # 项目产物
│
├── receipts/                                   # 执行回执（独立存储）
│   └── {receiptId}.json
│
├── repo/                                       # 代码仓库工作树
│   └── {repoId}/
│
└── tasks/                                      # 任务数据
    └── {tenantId}/{taskKey}/
        ├── events.jsonl                        # 任务事件流
        └── summary.json                        # 任务摘要
```

### 2.2 服务端产物目录（artifacts）

```
data/artifacts/
├── by-employee/{employeeCode}/{executionId}/   # 按员工分类（数字员工产物）
│   ├── Game2048.jsx
│   ├── Game2048.css
│   └── report.md
│
├── by-execution/{executionId}/{department}/    # 按执行分类（跨部门任务）
│   ├── index.html
│   └── analysis_report.md
│
├── tech/{executionId}/                         # 技术部门产物
│   ├── code_review.md
│   └── ci_build.log
│
├── ops/{executionId}/                          # 运营部门产物
│   ├── campaign_plan.md
│   └── data_analysis.xlsx
│
├── hr/{executionId}/                           # 人力资源产物
│   ├── recruitment_report.md
│   └── training_plan.docx
│
├── finance/{executionId}/                      # 财务部产物
│   ├── budget_report.xlsx
│   └── invoice_summary.pdf
│
├── sales/{executionId}/                        # 销售部产物
│   ├── sales_report.xlsx
│   └── customer_analysis.md
│
├── cs/{executionId}/                           # 客服部产物
│   ├── service_report.md
│   └── feedback_summary.xlsx
│
├── admin/{executionId}/                        # 行政部产物
│   ├── meeting_minutes.md
│   └── asset_inventory.xlsx
│
├── legal/{executionId}/                        # 法务部产物
│   ├── contract_review.md
│   └── compliance_report.pdf
│
└── mainbrain/{executionId}/                    # 主脑产物（跨部门协调）
    ├── coordination_plan.md
    └── summary_report.md
```

### 2.3 用户本地保存目录

桌面客户端配置的本地保存路径（用户可自定义）：

```
{basePath}/                                     # 用户设置的本地路径（如 D:\app\living）
├── artifacts/                                  # 产物副本
│   ├── {year}/                                 # 按年份分类
│   │   ├── {month}/                            # 按月份分类
│   │   │   ├── {executionId}/                  # 按执行 ID 分类
│   │   │   │   ├── Game2048.jsx
│   │   │   │   ├── Game2048.css
│   │   │   │   └── report.md
│
├── conversations/                              # 对话历史（可选）
│   └── {conversationId}/
│       └── events.jsonl
│
├── receipts/                                   # 执行回执（可选）
│   └── {receiptId}.json
│
└── screenshots/                                # 截图（可选）
    └── {timestamp}.png
```

### 2.4 工作区目录（LLM 工具）

```
/app/workspace/                                 # 默认工作区（映射到项目源码）
├── dialogue-frontend/                          # 示例：前端项目
│   ├── src/
│   │   ├── components/
│   │   │   ├── Game2048.jsx
│   │   │   └── Game2048.css
│   │   └── App.jsx
│   └── package.json
│
/app/user-workspace/                            # 用户工作区（映射到用户本地路径）
├── {userProject}/                              # 用户项目
│   ├── src/
│   └── dist/
```

---

## 三、产物记录规范

### 3.1 ArtifactRecord 数据结构

```java
public record ArtifactRecord(
    String artifactId,          // 产物唯一 ID (UUID)
    String executionId,         // 执行 ID
    String department,          // 所属部门
    String ownerEmployeeCode,   // 员工代码（数字员工）
    String ownerEmployeeNeuronId, // 神经元 ID
    String type,                // 产物类型（见 §3.2）
    String path,                // 文件路径（绝对路径）
    String name,                // 文件名
    String summary,             // 概要描述
    long sizeBytes,             // 文件大小
    String sha256,              // SHA-256 校验
    String taskId,              // 任务 ID
    String projectId,           // 项目 ID
    List<String> tags,          // 标签列表
    Instant createdAt,          // 创建时间
    Map<String, Object> metadata // 扩展元数据
);
```

### 3.2 产物类型枚举

| 类型 | 说明 | 文件扩展名示例 |
|------|------|---------------|
| `CODE` | 代码文件 | `.js`, `.ts`, `.py`, `.java` |
| `DOCUMENT` | 文档 | `.md`, `.docx`, `.pdf` |
| `REPORT` | 报告 | `.md`, `.xlsx`, `.pdf` |
| `HTML` | 网页 | `.html`, `.htm` |
| `CSS` | 样式 | `.css`, `.scss` |
| `IMAGE` | 图片 | `.png`, `.jpg`, `.svg` |
| `DATA` | 数据文件 | `.json`, `.csv`, `.xlsx` |
| `CONFIG` | 配置文件 | `.yaml`, `.json`, `.toml` |
| `SCRIPT` | 脚本 | `.sh`, `.bat`, `.ps1` |
| `LOG` | 日志 | `.log`, `.txt` |
| `ARCHIVE` | 压缩包 | `.zip`, `.tar`, `.gz` |
| `BUILD` | 构建产物 | `.jar`, `.exe`, `.dll` |
| `TEST` | 测试产物 | `.test.js`, `.spec.py` |
| `DIAGRAM` | 图表 | `.drawio`, `.svg` |
| `OTHER` | 其他 | - |

### 3.3 产物记录流程

```
工具创建文件 → 确定保存路径 → 写入文件 → 记录到 ArtifactRecordService → 设置权限字段 → WebSocket 推送通知
```

**关键步骤**：

1. **确定保存路径**：
   - 数字员工产物：`data/artifacts/by-employee/{employeeCode}/{executionId}/`
   - 部门产物：`data/artifacts/{department}/{executionId}/`
   - 跨部门产物：`data/artifacts/by-execution/{executionId}/{department}/`

2. **记录到 ArtifactRecordService**：
   ```java
   ArtifactRecord artifact = ArtifactRecord.of(
       executionId, department,
       ownerEmployeeCode, ownerEmployeeNeuronId,
       type, path, name, summary
   );
   artifactRecordService.recordArtifact(artifact);
   ```

3. **设置权限字段**（见 §4）：

4. **WebSocket 推送通知**：
   ```java
   departmentWebSocketHandler.sendArtifactMessage(
       sessionId, artifact.name(), artifact.path(), artifact.type(),
       Map.of("artifactId", artifact.artifactId(), "executionId", executionId)
   );
   ```

---

## 四、权限控制规范

### 4.1 权限字段

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `visibility` | String | `DEPARTMENT` | 可见性级别 |
| `createdBy` | String | - | 创建者 userId |
| `department` | String | - | 所属部门 |
| `participantIds` | String | - | 参与者 userId 列表（逗号分隔） |
| `viewerDepartments` | String | - | 额外可查看的部门列表（逗号分隔） |
| `visibleToLeader` | Boolean | `true` | 部门领导是否可见 |

### 4.2 可见性级别

| 级别 | 说明 | 可见范围 |
|------|------|----------|
| `PUBLIC` | 全员公开 | 所有登录用户 |
| `DEPARTMENT` | 部门可见 | 本部门全员 + 部门领导 |
| `PRIVATE` | 私有 | 仅创建者 + 参与者 |
| `RESTRICTED` | 受限 | 仅指定部门/人员 |

### 4.3 权限判断规则

```java
public boolean canView(ArtifactRecordEntity record, AuthContext user) {
    // 1. 董事长/FULL 全部可见
    if (isChairman(user)) return true;

    // 2. 创建者/参与者始终可见
    if (isCreatorOrParticipant(record, user)) return true;

    // 3. 根据 visibility 判断
    switch (record.getVisibility()) {
        case "PUBLIC": return true;
        case "DEPARTMENT": return isSameDepartment(record, user);
        case "PRIVATE": return false; // 已在第2步处理
        case "RESTRICTED": return isInViewerDepartments(record, user);
        default: return false;
    }
}
```

### 4.4 权限与身份映射

| 身份 | 可见产物范围 |
|------|-------------|
| 董事长/FULL | 所有产物 |
| 部门领导 | 本部门所有产物 + PUBLIC |
| 在职员工 | 本部门产物 + PUBLIC + 参与的 PRIVATE |
| 试用期员工 | 本部门产物 + PUBLIC（受限） |
| 离职员工 | 仅 PUBLIC |
| 外来访客 | 仅 PUBLIC |

---

## 五、设备绑定规范

### 5.1 ClientInfo 数据结构

```typescript
interface ClientInfo {
  clientId: string;      // 客户端唯一 ID (UUID)
  hostname: string;      // 主机名
  platform: NodeJS.Platform; // 操作系统平台
  osUser: string;        // 操作系统用户名
  macAddress: string;    // 主网卡 MAC 地址（硬件指纹）
  appVersion: string;    // 应用版本
  createdAt: string;     // 创建时间
}
```

### 5.2 设备绑定流程

```
首次启动 → 生成 clientId → 持久化到 userData/client-id.json → 所有请求携带 clientId
```

**关键规则**：

1. **首次启动**：生成 v4 UUID，记录 hostname、MAC 地址等元数据
2. **持久化**：保存到 `{userData}/client-id.json`
3. **后续启动**：直接读取，不再变更
4. **手动重置**：用户可在设置页重置（迁移机器时）

### 5.3 设备信息用途

| 用途 | 说明 |
|------|------|
| **审计** | 日志可追溯到具体物理机 |
| **路由** | WindowsAppTool 根据 clientId 派发任务到对应 PC |
| **排障** | 任务回执可追溯到具体物理机 |
| **安全** | 设备唯一性验证，防止多客户端冲突 |

### 5.4 WebSocket 连接携带设备信息

```typescript
wsClient.connect(url, {
  headers: {
    'X-Client-Id': clientId,
    'X-Hostname': hostname,
    'X-Mac-Address': macAddress,
    'X-Platform': platform,
    'X-Os-User': osUser,
    'X-App-Version': appVersion
  }
});
```

---

## 六、本地保存规范

### 6.1 LocalSaveConfig 配置

```typescript
interface LocalSaveConfig {
  enabled: boolean;           // 是否启用本地保存
  basePath: string;           // 本地保存路径（用户可自定义）
  scopes: {
    artifacts: boolean;       // 同步产物
    conversations: boolean;   // 同步对话历史
    receipts: boolean;        // 同步执行回执
    screenshots: boolean;     // 同步截图
  };
  syncStrategy: 'local-only' | 'cloud-sync' | 'hybrid'; // 同步策略
  capacity: {
    maxBytes: number;         // 最大存储容量（字节）
    retentionDays: number;    // 保留天数
  };
}
```

### 6.2 默认配置

| 配置项 | 默认值 |
|--------|--------|
| `enabled` | `false` |
| `basePath` | `~/Documents/LivingAgent` |
| `scopes.artifacts` | `true` |
| `scopes.conversations` | `true` |
| `scopes.receipts` | `true` |
| `scopes.screenshots` | `false` |
| `syncStrategy` | `local-only` |
| `maxBytes` | `10737418240` (10GB) |
| `retentionDays` | `30` |

### 6.3 本地同步流程

```
WebSocket 事件 → artifact_ready → 拉取产物 → 写入本地副本 → 记录到 inventory → 推送通知到渲染进程
```

**关键步骤**：

1. **监听 WebSocket 事件**：
   - `employee_task_update`（任务完成）
   - `execution_event`（`artifact_ready`）

2. **拉取产物**：
   - 调用 `listMyVisibleArtifacts` API 获取可见产物列表
   - 调用 `downloadArtifact` API 下载产物内容

3. **写入本地副本**：
   - 路径：`{basePath}/artifacts/{year}/{month}/{executionId}/{fileName}`
   - 计算 SHA-256 校验

4. **记录到 inventory**：
   - 维护本地产物索引（`Map<executionId/fileName, LocalArtifactEntry>`）

5. **推送通知**：
   - 发送 `localsave:saved` 事件到渲染进程

### 6.4 权限变更同步

当用户角色变更时：
- 调用 `onUserRoleChanged()` 重新同步
- 清理已无权限的本地副本

### 6.5 过期清理

- 每天清理一次
- 根据 `retentionDays` 删除过期文件

---

## 七、工具文件创建规范

### 7.1 数字员工产物归属原则

**核心原则**：每个数字员工使用工具产出的产物都必须记录该数字员工的信息，以便进行考核。

| 字段 | 来源 | 说明 |
|------|------|------|
| `ownerEmployeeCode` | `ToolContext.employeeCode` | 数字员工代码（如 T01, T02） |
| `ownerEmployeeNeuronId` | `ToolContext.neuronId` | 神经元 ID |
| `department` | `Tool.getDepartment()` | 所属部门 |
| `createdBy` | `ToolContext.clientId` 或用户 ID | 创建者 |

### 7.2 ToolContext 数据传递流程

```
BrainReActEngine → ToolContext.of(neuronId, sessionId, policy, employeeCode) → Tool.execute(params, context) → ArtifactRecordService.recordArtifact()
```

**关键代码**：

```java
// BrainReActEngine.java (第 288-290 行)
ToolContext context = empCode != null
    ? ToolContext.of(brain.getId(), sessionId, null, empCode)
    : ToolContext.of(brain.getId(), sessionId);
```

### 7.3 FileEditTool 规范

**当前问题**：
- 工作目录 `/app/workspace` 与产物目录 `data/artifacts` 分离
- 创建的文件未自动记录到 ArtifactRecordService
- 未使用 `ToolContext.employeeCode` 记录数字员工信息

**规范要求**：

1. **工作目录设置**：
   - 默认：`/app/workspace`（项目源码）
   - 用户工作区：`/app/user-workspace`（映射到用户本地路径）
   - 产物目录：`data/artifacts/{department}/{executionId}/`

2. **文件创建后必须记录**：
   ```java
   // FileEditTool 创建文件后
   if (context.employeeCode() != null) {
       ArtifactRecord artifact = ArtifactRecord.of(
           executionId, getDepartment(),
           context.employeeCode(), context.neuronId(),
           inferType(fileName), filePath, fileName, "工具创建"
       );
       artifact.setVisibility("DEPARTMENT");
       artifact.setCreatedBy(context.clientId());
       artifactRecordService.recordArtifact(artifact);
   }
   ```

3. **注入 ArtifactRecordService**：
   - `FileEditTool` 需要通过构造函数或 setter 注入 `ArtifactRecordService`
   - 或在 `ToolConfig` 中统一配置产物记录逻辑

### 7.4 BuildTool 规范

- 构建产物保存到 `data/artifacts/{department}/{executionId}/build/`
- 记录产物类型为 `BUILD`
- 设置 `visibility=DEPARTMENT`

### 7.5 其他工具规范

| 工具 | 产物类型 | 保存路径 |
|------|----------|----------|
| `web_preview` | `HTML` | `data/artifacts/{department}/{executionId}/` |
| `code_search` | 无产物 | - |
| `windows_automation` | `IMAGE`/`LOG` | `data/artifacts/{department}/{executionId}/` |

### 7.6 数字员工考核统计

**统计维度**：

| 维度 | 数据来源 | 说明 |
|------|----------|------|
| 产物数量 | `ArtifactRecordRepository.countByOwnerEmployeeCode()` | 数字员工产出的产物总数 |
| 产物类型分布 | `ArtifactRecordRepository.countByOwnerEmployeeCodeAndType()` | 各类型产物数量 |
| 产物质量评分 | `ArtifactRecordEntity.qualityScore` | 产物质量评分（可选） |
| 产物使用次数 | `ArtifactRecordEntity.downloadCount` | 产物下载/查看次数 |
| 产物成功率 | 任务执行成功率 | 成功产物数 / 总产物数 |

**考核 API**：

```java
// 数字员工产物统计
public interface ArtifactRecordRepository {
    @Query("SELECT COUNT(a) FROM ArtifactRecordEntity a WHERE a.ownerEmployeeCode = :employeeCode")
    long countByOwnerEmployeeCode(String employeeCode);

    @Query("SELECT a.type, COUNT(a) FROM ArtifactRecordEntity a WHERE a.ownerEmployeeCode = :employeeCode GROUP BY a.type")
    List<Object[]> countByOwnerEmployeeCodeAndType(String employeeCode);

    @Query("SELECT a FROM ArtifactRecordEntity a WHERE a.ownerEmployeeCode = :employeeCode AND a.createdAt >= :start AND a.createdAt < :end")
    List<ArtifactRecordEntity> findByEmployeeCodeAndDateRange(String employeeCode, Instant start, Instant end);
}
```

**考核报表**：

| 报表 | 内容 |
|------|------|
| 周报 | 本周产物数量、类型分布、成功率 |
| 月报 | 本月产物数量、质量评分、使用次数 |
| 年报 | 年度产物总数、贡献度排名 |

---

## 八、API 规范

### 8.1 产物查询 API

| API | 权限 | 说明 |
|-----|------|------|
| `GET /api/artifacts/my-visible` | 登录用户 | 列出当前用户可见的产物（推荐） |
| `GET /api/artifacts/by-department-accessible/{dept}` | 本部门成员 | 按部门列出产物（权限校验） |
| `GET /api/artifacts/{id}/download` | 有权限用户 | 下载产物（权限校验） |
| `GET /api/artifacts/{id}/preview` | 有权限用户 | 预览产物 |
| `GET /api/artifacts/by-task/{taskId}` | 有权限用户 | 按任务 ID 查询 |
| `GET /api/artifacts/by-project/{projectId}` | 项目成员 | 按项目 ID 查询 |

### 8.2 工作目录设置 API

```
PUT /api/v1/system/workspace/config
{
  "root": "/app/user-workspace"
}
```

- 桌面客户端设置本地路径时自动调用
- 热更新 FileEditTool 和 BuildTool 的工作目录

---

## 九、实施检查清单

### 9.1 产物记录检查

- [ ] 所有工具创建的文件是否记录到 ArtifactRecordService？
- [ ] 是否设置了正确的 visibility 字段？
- [ ] 是否设置了 createdBy 和 department 字段？
- [ ] 是否推送了 WebSocket 通知？

### 9.2 权限控制检查

- [ ] 用户只能看到自己权限范围内的产物？
- [ ] 董事长/FULL 可以看到所有产物？
- [ ] 部门领导可以看到本部门所有产物？
- [ ] PRIVATE 产物只有创建者/参与者可见？

### 9.3 设备绑定检查

- [ ] 桌面客户端是否生成并持久化 clientId？
- [ ] 所有请求是否携带 clientId？
- [ ] WebSocket 连接是否携带设备信息？
- [ ] 后端是否记录设备信息到 client_device_registry？

### 9.4 本地同步检查

- [ ] 桌面客户端是否监听 WebSocket 事件？
- [ ] 是否调用 `/api/artifacts/my-visible` 拉取产物？
- [ ] 是否正确解析服务器路径到本地路径？
- [ ] 是否清理过期文件？
- [ ] 角色变更时是否重新同步？

---

## 十、相关文档索引

| 文档 | 说明 |
|------|------|
| `权限与入口矩阵.md` | 权限与入口的完整规范 |
| `06-security-permission.md` | 安全与权限架构 |
| `MODULE_AUTONOMY_ORCHESTRATION.md` | 自治编排模块 |
| `ArtifactRecord.java` | 产物记录数据结构 |
| `ArtifactAccessService.java` | 产物权限判断服务 |
| `client-id.ts` | 设备绑定实现 |
| `local-save-config.ts` | 本地保存配置 |
| `local-save-sync.ts` | 本地同步服务 |

---

## 十一、代码中的目录配置

### 11.1 配置类与路径映射

| 配置类 | 配置项 | 默认值 | 说明 |
|--------|--------|--------|------|
| `DepartmentChatService` | `ARTIFACT_ROOT` | `data/artifacts` | 产物目录 |
| `BrainConfig` | `compactPersistDir` | `./data/compact-outputs` | 工具输出压缩存储 |
| `BrainConfig` | `repoRoot` | `./data/repo` | 代码仓库工作树 |
| `MemoryConfig` | `memory.db` | `data/memory.db` | 记忆数据库 |
| `MemoryConfig` | `knowledge.db` | `data/knowledge.db` | 知识库数据库 |
| `DataNamespaceService` | `baseDataDir` | `data` | 数据目录根路径 |

### 11.2 DataNamespaceService 路径生成方法

| 方法 | 生成的路径 |
|------|-----------|
| `getProjectEventsPath` | `data/projects/{tenantId}/{projectId}/events.jsonl` |
| `getTaskEventsPath` | `data/tasks/{tenantId}/{taskKey}/events.jsonl` |
| `getConversationEventsPath` | `data/conversations/{tenantId}/{userId}/{taskKey}/{executionId}/events.jsonl` |
| `getConversationIdEventsPath` | `data/conversations-by-id/{tenantId}/{conversationId}/events.jsonl` |
| `getArtifactsPath` | `data/artifacts/{tenantId}/{executionId}` |
| `getReceiptsPath` | `data/receipts/{tenantId}/{executionId}` |
| `getUserIndexPath` | `data/indexes/by-user/{userId}.json` |
| `getProjectIndexPath` | `data/indexes/by-project/{projectId}.json` |

### 11.3 KnowledgeFileMirrorService 路径生成

| 方法 | 生成的路径 |
|------|-----------|
| `getPersonalExperiencePath` | `data/personal-knowledge/{employeeCode}/experiences.jsonl` |
| `getDepartmentBestPracticesPath` | `data/department-knowledge/{dept}/best-practices.jsonl` |
| `getSharedPoliciesPath` | `data/knowledge/shared/policies.jsonl` |

### 11.4 配置文件示例（application.yml）

```yaml
living-agent:
  data-path: ./data
  artifact:
    dir: data/artifacts
  
  brain:
    compact:
      enabled: true
      context-limit: 50000
      persist-dir: ./data/compact-outputs
      native-enabled: false
  
  worktree:
    repo-root: ./data/repo
  
  memory:
    backend: sqlite
    db-path: data/memory.db
  
  knowledge:
    backend: sqlite
    db-path: data/knowledge.db
```

---

## 十二、修订记录

| 日期 | 修订内容 |
|------|----------|
| 2026-06-25 | 初版：综合权限设计、产物记录、设备绑定与本地保存机制 |
| 2026-06-25 | 补充：完整数据目录总览、代码中的目录配置、配置文件示例 |
| 2026-06-25 | 补充：数字员工产物归属原则、ToolContext 数据传递流程、考核统计规范 |