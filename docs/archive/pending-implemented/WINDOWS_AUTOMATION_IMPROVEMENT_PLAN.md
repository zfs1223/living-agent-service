# Windows 自动化桥接改进方案

> 编号：P3-1
> 优先级：P3
> 状态：✅ 已完成
> 创建日期：2026-05-21
> 完成日期：2026-05-21

---

## 一、现状分析

### 1.1 当前架构

```
AI 大脑 → ToolNeuron → WindowsAppTool (Java, 服务器端)
                              ↓ HTTP API (局域网)
                    server.py (Python, 客户端电脑)
                              ↓ pywinauto
                    Windows 桌面应用 (金蝶 KIS 等)
```

### 1.2 已完成

| 项目 | 状态 | 说明 |
|------|------|------|
| `WindowsAppTool.java` | ✅ 已注册到 ToolRegistry | 2026-05-21 修复，之前未注册 |
| `scripts/windows_automation/server.py` | ✅ 已有 | FastAPI + pywinauto HTTP 服务 |
| `scripts/windows_automation/config.json` | ✅ 已有 | 应用配置（金蝶 KIS 等） |
| `scripts/windows_automation/README.md` | ✅ 已有 | 部署文档 |
| `scripts/windows_automation/MULTI_NODE_DEPLOY.md` | ✅ 已有 | 多节点部署方案 |
| `CODE_STRUCTURE_AND_FILE_GUIDE.md` | ✅ 已更新 | 补充了 windows_automation 记录 |

### 1.3 存在的问题

| 问题 | 严重程度 | 说明 |
|------|----------|------|
| 节点配置硬编码 | 高 | `initializeDefaultNodes()` 写死了 3 个节点 IP，无法动态管理 |
| 技能权限未分离 | 中 | 项目内置技能和个人添加技能混在一起，无权限隔离 |
| 客户端需手动部署 | 中 | 每台客户端电脑需手动复制文件、安装依赖、启动服务 |
| 无自动注册机制 | 中 | 客户端启动后不会向服务器注册，需手动配置 IP |
| 无心跳检测 | 低 | 服务器无法感知客户端是否在线 |

---

## 二、改进方案

### 2.1 客户端自动注册（P3-1a）

**目标**：客户端 `server.py` 启动时自动向服务器注册，无需手动配置 IP。

#### 流程

```
客户端 server.py 启动
    ↓
1. 生成/读取 node_id（首次生成 UUID，保存到 node_id.txt）
2. 获取本机信息：
   - IP 地址（socket.gethostbyname）
   - 主机名（socket.gethostname）
   - CPU/内存（psutil）
   - 已安装的应用列表（从 config.json 读取）
3. POST /api/windows-automation/nodes/register → 服务器
    ↓
服务器收到注册请求
    ↓
4. 存入数据库 windows_automation_nodes 表
5. 返回注册成功 + 服务器配置（心跳间隔等）
    ↓
客户端开始定时心跳（每 60 秒）
```

#### server.py 新增代码

```python
# 启动时自动注册
def register_to_server():
    node_id = load_or_create_node_id()  # 从 node_id.txt 读取或生成
    host_info = {
        "node_id": node_id,
        "ip": get_local_ip(),
        "hostname": socket.gethostname(),
        "cpu_count": psutil.cpu_count(),
        "memory_gb": round(psutil.virtual_memory().total / (1024**3), 1),
        "applications": load_applications(),  # 从 config.json 读取
        "port": config.get("server", {}).get("port", 8765),
    }
    try:
        response = requests.post(
            f"{SERVER_URL}/api/windows-automation/nodes/register",
            json=host_info,
            timeout=10
        )
        if response.status_code == 200:
            logger.info(f"节点注册成功: {node_id}")
        else:
            logger.warning(f"节点注册失败: {response.status_code}")
    except Exception as e:
        logger.warning(f"无法连接服务器: {e}")

# 定时心跳
def heartbeat_loop():
    while True:
        try:
            requests.post(
                f"{SERVER_URL}/api/windows-automation/nodes/{node_id}/heartbeat",
                json={"status": "online", "active_sessions": len(active_sessions)},
                timeout=5
            )
        except:
            pass
        time.sleep(60)
```

#### 数据库表设计

```sql
-- V14__windows_automation_nodes.sql
CREATE TABLE IF NOT EXISTS windows_automation_nodes (
    node_id         VARCHAR(64) PRIMARY KEY,
    ip_address      VARCHAR(64) NOT NULL,
    port            INTEGER DEFAULT 8765,
    hostname        VARCHAR(128),
    cpu_count       INTEGER,
    memory_gb       DECIMAL(5,1),
    applications    JSONB,            -- 已安装的应用列表
    description     VARCHAR(256),
    status          VARCHAR(16) DEFAULT 'offline',  -- online/offline
    last_heartbeat  TIMESTAMP,
    registered_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    tenant_id       VARCHAR(64),      -- 所属租户
    user_id         VARCHAR(64),      -- 注册用户
    enabled         BOOLEAN DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_wan_tenant ON windows_automation_nodes(tenant_id);
CREATE INDEX IF NOT EXISTS idx_wan_status ON windows_automation_nodes(status);
```

#### 后端 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/windows-automation/nodes/register` | 客户端注册 |
| POST | `/api/windows-automation/nodes/{nodeId}/heartbeat` | 心跳上报 |
| GET | `/api/windows-automation/nodes` | 列出所有节点 |
| PUT | `/api/windows-automation/nodes/{nodeId}` | 更新节点信息 |
| DELETE | `/api/windows-automation/nodes/{nodeId}` | 删除节点 |
| GET | `/api/windows-automation/nodes/{nodeId}/status` | 检查节点在线状态 |

#### WindowsAppTool 改造

```java
// 改造前：硬编码
private void initializeDefaultNodes() {
    addNode("pc-finance-01", "http://192.168.1.101:8765", "财务电脑01-金蝶KIS");
    addNode("pc-hr-01", "http://192.168.1.102:8765", "人事电脑01");
    addNode("pc-admin-01", "http://192.168.1.103:8765", "行政电脑01");
}

// 改造后：从数据库动态加载
private void initializeDefaultNodes() {
    // 从 NodeRepository 加载所有 enabled 的节点
    List<WindowsAutomationNode> nodes = nodeRepository.findByEnabledTrue();
    for (WindowsAutomationNode node : nodes) {
        addNode(node.getNodeId(), 
                "http://" + node.getIpAddress() + ":" + node.getPort(), 
                node.getDescription());
    }
    log.info("从数据库加载 {} 个 Windows 自动化节点", nodes.size());
}
```

---

### 2.2 技能权限分离（P3-1b）

**目标**：区分项目内置技能和个人添加技能，实现权限隔离，对齐项目已有的 `AccessLevel` 权限体系。

#### 现有权限体系

项目已有完善的权限框架，技能权限应复用而非另起炉灶：

| 层级 | 类 | 值 | 说明 |
|------|---|-----|------|
| 访问级别 | `AccessLevel` | CHAT_ONLY(0) → LIMITED(1) → DEPARTMENT(2) → FULL(3) | 四级递增 |
| 用户身份 | `UserIdentity` | INTERNAL_ENTERPRISE(FULL) → INTERNAL_ACTIVE(DEPARTMENT) → INTERNAL_PROBATION(LIMITED) → EXTERNAL_VISITOR(CHAT_ONLY) | 身份→级别映射 |
| 部门访问 | `DepartmentAccessValidator` | FULL 可跨部门，DEPARTMENT 仅本部门，CHAT_ONLY 无权 | 部门级隔离 |
| 大脑访问 | `BrainAccessControl` | 每个大脑定义允许的 AccessLevel 集合 | 大脑级隔离 |

#### 技能权限模型

| 类型 | scope | 可见性 | 修改权限 | 来源 | 示例 |
|------|-------|--------|----------|------|------|
| 项目内置 | `global` | 全租户可见 | 仅 FULL 权限 | `skills/` 目录下的 SKILL.md | `finance-api-gateway`、`code-review` |
| 进化生成 | `evolved` | 所属部门可见 | 创建者 + FULL 权限 | `GeneratedSkill`（进化系统） | 进化系统自动生成的技能 |
| 个人添加 | `personal` | 仅自己可见 | 创建者 | ClawHub 安装 / 用户创建 | 用户从 ClawHub 安装的技能 |

**与 AccessLevel 的关系**：

| AccessLevel | 可见技能范围 | 可修改技能 |
|-------------|------------|-----------|
| FULL | global + evolved(全部) + personal(自己) | global + evolved(全部) + personal(自己) |
| DEPARTMENT | global + evolved(本部门) + personal(自己) | evolved(本部门) + personal(自己) |
| LIMITED | global + personal(自己) | personal(自己) |
| CHAT_ONLY | 无（仅闲聊，不涉及技能） | 无 |

#### Skill 接口变更

```java
// Skill.java 新增方法
public interface Skill {
    // ... 现有方法 ...

    /** 技能作用域：global=项目内置, evolved=进化生成, personal=个人添加 */
    default String getScope() { return "global"; }
    default void setScope(String scope) {}

    /** 技能所有者（仅 personal/evolved 有值） */
    default String getOwnerId() { return null; }
    default void setOwnerId(String ownerId) {}

    /** 技能所属部门（仅 evolved 有值） */
    default String getDepartmentId() { return null; }
    default void setDepartmentId(String departmentId) {}
}
```

#### 数据库变更

```sql
-- V15__skill_scope_separation.sql
-- 技能表新增作用域和所有者字段（如果 skills 表已存在）
ALTER TABLE skills ADD COLUMN IF NOT EXISTS scope VARCHAR(16) DEFAULT 'global';
ALTER TABLE skills ADD COLUMN IF NOT EXISTS owner_id VARCHAR(64);
ALTER TABLE skills ADD COLUMN IF NOT EXISTS department_id VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_skills_scope ON skills(scope);
CREATE INDEX IF NOT EXISTS idx_skills_owner ON skills(owner_id);
CREATE INDEX IF NOT EXISTS idx_skills_dept ON skills(department_id);

-- 将现有技能全部标记为 global
UPDATE skills SET scope = 'global' WHERE scope IS NULL;
```

#### SkillRegistry 改造

```java
// SkillRegistry 新增查询方法
List<Skill> getSkillsByScope(String scope);
List<Skill> getSkillsByOwnerId(String ownerId);
List<Skill> getVisibleSkills(String userId, AccessLevel accessLevel, String departmentId);

// getVisibleSkills 实现逻辑：
// FULL → 全部 global + evolved + personal(自己)
// DEPARTMENT → 全部 global + evolved(本部门) + personal(自己)
// LIMITED → 全部 global + personal(自己)
// CHAT_ONLY → 空列表
```

#### 前端改造

- 技能管理 Tab 分为三个子视图：
  - **项目技能**（`scope=global`）：所有用户可见，只有 FULL 权限可编辑
  - **部门技能**（`scope=evolved`）：本部门可见，创建者 + FULL 可编辑
  - **我的技能**（`scope=personal`）：仅自己可见，可自由管理
- 安装技能时默认 `scope=personal`，FULL 权限可提升为 `global`
- 技能可见性由后端 `getVisibleSkills()` 按 `AccessLevel` 过滤，前端只展示后端返回的列表

---

### 2.3 前端一键安装客户端（P3-1c）

**目标**：在前端公共页面添加"安装 Windows 控制客户端"按钮，用户点击后下载安装包并自动执行。

#### 可行性分析

| 方面 | 结论 | 说明 |
|------|------|------|
| 浏览器下载文件 | ✅ 可行 | `<a download>` 或 `fetch + Blob` |
| 用户指定保存位置 | ⚠️ 有限制 | 浏览器安全策略不允许 JS 指定保存路径，由浏览器下载设置决定 |
| 自动执行安装 | ❌ 不可行 | 浏览器无法直接执行本地程序，这是安全沙箱限制 |
| 替代方案：自解压安装包 | ✅ 可行 | 打包成 `.exe` 自解压安装程序，用户双击运行 |

#### 推荐方案：一键安装器

```
前端页面点击"安装客户端"
    ↓
下载 windows-automation-installer.exe（自解压安装包）
    ↓
用户双击运行安装器
    ↓
安装器自动执行：
1. 解压文件到安装目录（默认 C:\LivingAgent\windows_automation）
2. 检测 Python 环境，若无则自动安装
3. pip install -r requirements.txt
4. 生成 node_id
5. 修改 config.json（用户可选择要自动化的应用）
6. 注册为 Windows 服务（开机自启动）
7. 开放防火墙端口 8765
8. 向服务器注册节点
9. 启动 server.py
    ↓
安装完成，前端显示"已连接"
```

#### 安装器制作方式

| 方式 | 复杂度 | 说明 |
|------|--------|------|
| **NSIS** | 中 | 专业 Windows 安装器，支持自定义页面、服务注册 |
| **Inno Setup** | 低 | 简单易用，适合快速制作 |
| **PyInstaller + 自解压** | 低 | 将 Python 环境一起打包，无需用户安装 Python |
| **PowerShell 脚本** | 最低 | 一键脚本，但用户体验不如 GUI 安装器 |

**推荐**：PyInstaller + NSIS 组合
- PyInstaller 将 `server.py` 打包成独立 `.exe`（内含 Python 运行时）
- NSIS 制作安装器外壳（安装目录选择、服务注册、防火墙配置）

#### 后端支持

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/windows-automation/installer/download` | 下载安装包 |
| GET | `/api/windows-automation/installer/latest-version` | 获取最新版本号 |
| POST | `/api/windows-automation/installer/verify` | 安装后验证 |

#### 前端按钮位置

- **部门页面**（DepartmentDetail）— 工具栏区域
- **公司设置** → 工具 Tab → `windows_app_automation` 配置区
- **登录后首页** — 如果检测到当前用户没有关联节点，显示安装提示

---

### 2.4 前端节点管理界面（P3-1d）

**目标**：在"公司设置" → 工具 Tab 中，`windows_app_automation` 配置区显示已注册节点列表。

#### 功能

| 功能 | 说明 | 权限 |
|------|------|------|
| 查看节点列表 | 显示所有已注册的客户端电脑 | 所有用户 |
| 查看节点状态 | 在线/离线、活跃会话数 | 所有用户 |
| 启用/禁用节点 | 控制是否允许 AI 控制该电脑 | 管理员 |
| 删除节点 | 移除注册信息 | 管理员 |
| 测试连接 | 手动触发健康检查 | 所有用户 |

#### UI 设计

```
┌─────────────────────────────────────────────────┐
│ Windows 应用自动化                               │
│                                                  │
│ 已注册节点 (3)                    [安装新客户端]   │
│                                                  │
│ ┌──────────────────────────────────────────────┐ │
│ │ 🟢 财务电脑-01          node_id: a1b2c3      │ │
│ │    IP: 192.168.1.101     金蝶KIS             │ │
│ │    活跃会话: 1  │  上次心跳: 10秒前            │ │
│ │    [测试连接]  [禁用]  [删除]                  │ │
│ ├──────────────────────────────────────────────┤ │
│ │ 🟢 人事电脑-01          node_id: d4e5f6      │ │
│ │    IP: 192.168.1.102     人事系统             │ │
│ │    活跃会话: 0  │  上次心跳: 5秒前             │ │
│ │    [测试连接]  [禁用]  [删除]                  │ │
│ ├──────────────────────────────────────────────┤ │
│ │ 🔴 行政电脑-01          node_id: g7h8i9      │ │
│ │    IP: 192.168.1.103     离线                 │ │
│ │    最后在线: 2小时前                           │ │
│ │    [测试连接]  [启用]  [删除]                  │ │
│ └──────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────┘
```

---

## 三、实施优先级

| 编号 | 改进项 | 优先级 | 依赖 |
|------|--------|--------|------|
| P3-1a | 客户端自动注册 + 心跳 | P3 | 无 |
| P3-1b | 技能权限分离 | P3 | 无 |
| P3-1c | 一键安装客户端 | P3 | P3-1a（安装后需自动注册） |
| P3-1d | 前端节点管理界面 | P3 | P3-1a（需要注册数据） |

建议实施顺序：P3-1a → P3-1d → P3-1c → P3-1b

---

## 四、涉及修改的文件

### 后端 Java

| 文件 | 改动 |
|------|------|
| `core/tool/impl/WindowsAppTool.java` | 从数据库动态加载节点，删除硬编码 |
| `core/database/entity/WindowsAutomationNodeEntity.java` | 新增：节点实体 |
| `core/database/repository/WindowsAutomationNodeRepository.java` | 新增：节点仓储 |
| `gateway/controller/WindowsAutomationController.java` | 新增：节点管理 API |
| `core/config/LivingAgentCoreConfig.java` | 注入 NodeRepository 到 WindowsAppTool |

### 数据库迁移

| 文件 | 改动 |
|------|------|
| `db/migration/V14__windows_automation_nodes.sql` | 新增：节点表 |

### Python 客户端

| 文件 | 改动 |
|------|------|
| `scripts/windows_automation/server.py` | 新增：自动注册 + 心跳逻辑 |
| `scripts/windows_automation/requirements.txt` | 新增：requests, psutil 依赖 |

### 前端

| 文件 | 改动 |
|------|------|
| `pages/EnterpriseSettings.tsx` | 新增：节点管理 UI、安装按钮 |
| `services/api.ts` | 新增：节点管理 API 调用 |

---

## 五、安全注意事项

1. **节点注册需验证**：防止恶意节点注册，建议添加注册令牌或管理员审批
2. **通信加密**：生产环境建议 server.py 启用 HTTPS
3. **操作审计**：所有 Windows 自动化操作需记录审计日志
4. **权限最小化**：普通用户只能控制自己注册的节点，管理员可控制所有节点
5. **安装包签名**：一键安装器需代码签名，防止被杀毒软件拦截
