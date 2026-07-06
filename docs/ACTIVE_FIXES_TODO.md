# Active Fixes TODO

> 本文档记录当前待处理的问题清单。
>
> 创建日期：2026-06-24
> 历史归档：[ACTIVE_FIXES_TODO_2026-06-09.md](./old/ACTIVE_FIXES_TODO_2026-06-09.md)（已全部完成）

---

## 当前待处理项

无待处理项。

---

## 历史记录

### 2026-06-09 归档项（已全部完成）

详见 [ACTIVE_FIXES_TODO_2026-06-09.md](./old/ACTIVE_FIXES_TODO_2026-06-09.md)

主要完成项：
- FIX-1~11：核心流程修复（receipt缓存、前端事件、NPE、迭代限制、WebSocket心跳等）
- P0-1~2：降级验收严格化、端到端 Trace
- P1-1~4：WebSocket心跳、部门匹配、跨部门协调
- P2-1~4：模型健康摘要、日志降噪、需求状态展示、需求冻结

### 2026-06-24 新增项

| 日期 | 内容 | 状态 |
|------|------|------|
| 2026-06-24 | application.yml 配置整理（移除冗余模型列表） | ✅ 已完成 |
| 2026-06-24 | OllamaProvider 改进（启动检查、无模型提示） | ✅ 已完成 |
| 2026-06-24 | TTS 配置修正（统一使用 melotts） | ✅ 已完成 |
| 2026-06-24 | Claude CLI 配置统一（enabled 默认值一致） | ✅ 已完成 |
| 2026-06-24 | Docker Socket Proxy 实施 | ✅ 已完成 |

---

## Docker Socket Proxy 实施详情

### 架构

```
数字员工 → HybridSandboxService → DockerSandboxService
                                      ↓
                              DOCKER_HOST=tcp://docker-socket-proxy:2375
                                      ↓
                              docker-socket-proxy（白名单限制）
                                      ↓
                              /var/run/docker.sock（宿主机）
                                      ↓
                              独立沙箱容器（完全隔离）
```

### docker-compose.yml 配置

```yaml
# Docker Socket Proxy 服务
docker-socket-proxy:
  image: tecnativa/docker-socket-proxy:latest
  environment:
    CONTAINERS: 1  # 允许创建/删除/查询容器
    EXEC: 1        # 允许在容器内执行命令
    IMAGES: 1      # 允许拉取镜像
    NETWORKS: 1    # 允许网络管理
    VOLUMES: 1     # 允许卷管理
    BUILD: 0       # 禁止构建镜像
    SECRETS: 0     # 禁止 Secrets 管理

# living-agent-service 环境变量
living-agent-service:
  environment:
    - DOCKER_HOST=tcp://docker-socket-proxy:2375
  depends_on:
    - docker-socket-proxy
```

### 安全隔离策略

沙箱容器挂载：
- ✅ `/app/artifacts/{executionId}` — 产物输出目录（可写）
- ✅ `/app/test-code` — 待执行代码（只读）
- ❌ `workspace` — 源码（不挂载）
- ❌ `documents` — 企业文档（不挂载）
- ❌ 敏感环境变量 — 不传递

---

## 更新记录

| 日期 | 更新内容 |
|------|----------|
| 2026-06-24 | 创建文档，引用历史归档，整理当前待处理项 |
| 2026-06-24 | Docker Socket Proxy 实施完成，所有待处理项清零 |