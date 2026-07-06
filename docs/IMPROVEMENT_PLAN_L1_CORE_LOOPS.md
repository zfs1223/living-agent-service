# Living Agent Service - L1 流程正确性闭环改进方案

> **生成日期**: 2026-07-02
> **层级**: L1 流程正确性（闭环1-14）
> **索引**: [IMPROVEMENT_PLAN_INDEX.md](IMPROVEMENT_PLAN_INDEX.md)
> **配套需求**: COMPREHENSIVE_LOOP_REQUIREMENTS_ANALYSIS.md 第三章

---

## 一、覆盖情况总览

| 闭环编号 | 闭环名称 | 覆盖状态 | 改进方案 |
|---------|---------|---------|---------|
| 1 | WebSocket对话闭环 | ✅ 完整闭环 | — |
| 2 | 审批流程闭环 | ✅ 完整闭环 | — |
| 3 | 任务分配闭环 | ✅ 完整闭环 | — |
| 4 | 进化调整闭环 | ✅ 完整闭环 | — |
| 5 | 知识注入闭环 | ✅ 完整闭环 | — |
| 6 | Windows自动化闭环 | ✅ 完整闭环 | — |
| 7 | PostgreSQL数据一致性闭环 | ✅ 完整闭环 | — |
| 8 | Qdrant向量一致性闭环 | ✅ 完整闭环 | — |
| 9 | Redis缓存一致性闭环 | ✅ 完整闭环 | — |
| 10 | 跨系统数据同步闭环 | ✅ 完整闭环 | — |
| 11 | 模型健康监控闭环 | ✅ 完整闭环 | — |
| 12 | 服务启动健康闭环 | ✅ 完整闭环 | P12-A✅/B✅/C✅/D✅ + Canary空转修复✅ |
| 13 | 主动预判健康闭环 | ✅ 完整闭环 | — |
| 14 | 权限管理闭环 | ✅ 完整闭环 | P14-A✅/B✅/C✅/D✅ |

---

## 二、完整闭环（13个）

### 闭环1: WebSocket对话闭环 ✅

**定义**：
```
流程: 用户请求 → 权限检查 → 路由分发 → 意图分析 → 大脑选择 → 模型调用 → 工具执行
       → 结果返回 → Trace记录 → 反馈收集 → 进化调整 → 下次对话优化
验证: 发送WebSocket消息 → 检查响应 → 查询autonomy_trace_events表
```

**改进方案来源**: MODEL_RESPONSIBILITY_AND_EXECUTION_OPTIMIZATION_PLAN.md 第9章

**改进内容**：
- P0-A修复: onReceiptRecorded使用正确的sessionId
- P0-B修复: DepartmentExecutionResult.metadata增加sessionId
- P0-C修复: 聊天历史查询统一使用部门代码
- WebSocket握手参数规范
- WebSocket连接与任务绑定设计

---

### 闭环2: 审批流程闭环 ✅

**定义**：
```
流程: 创建审批 → 工作流加载 → 步骤流转 → 审批人决策 → 状态更新 → 回调通知 → 业务执行
验证: POST /api/approvals创建 → GET查询状态 → POST approve执行
```

**改进方案来源**: DESKTOP_BACKEND_INTEGRATION_AUDIT_AND_IMPROVEMENT_PLAN.md 第7.2节

**改进内容**：
- 问题识别: 审批工作流存储为内存态（approvalStore/workflowStore ConcurrentHashMap）
- 风险: 重启丢失所有审批中实例
- 解决方案: 将ApprovalInstance和ApprovalWorkflow持久化到PostgreSQL

---

### 闭环3: 任务分配闭环 ✅

**定义**：
```
流程: 任务创建 → 员工匹配 → 任务领取 → 执行跟踪 → 结果提交 → 任务完成 → 统计更新
验证: 创建任务 → checkout领取 → complete完成 → 查询tasks表
```

**改进方案来源**: MODEL_RESPONSIBILITY_AND_EXECUTION_OPTIMIZATION_PLAN.md 第11章

**改进内容**：
- 分层自治执行闭环设计
- 员工自行领取机制
- 部门内审查闭环
- ExecutionReceiptTaskProjectBridge真实更新task状态

---

### 闭环4: 进化调整闭环 ✅

**定义**：
```
流程: 反馈收集 → 评分计算 → 阈值判断 → 策略选择 → 模型替换 → 变更记录 → 效果验证
阈值: AUTO_ADJUST_THRESHOLD=0.4, CONSECUTIVE_FAILURES_REPLACE=3
```

**改进方案来源**: MODEL_RESPONSIBILITY_AND_EXECUTION_OPTIMIZATION_PLAN.md 第3.3.2.1节

**改进内容**：
- 模型调用失败自动降级闭环
- 降级链路：员工专属模型 → fallback模型列表 → DEGRADED结果
- BrainModelResolver三级降级：configured → enabled → null
- BrainAutoAssigner启动自动分配
- AbstractBrain动态Provider解析

---

### 闭环5: 知识注入闭环 ✅

**定义**：
```
流程: 文档扫描 → 内容解析 → 向量生成 → 知识存储 → 向量索引 → 知识检索 → 知识应用
验证: 添加文档 → 触发注入 → 查询knowledge_entries表 → 查询Qdrant向量库
```

**改进方案来源**: DESKTOP_BACKEND_INTEGRATION_AUDIT_AND_IMPROVEMENT_PLAN.md 第7.1节

**改进内容**：
- LayeredKnowledgeBaseImpl列为完整闭环模块
- KnowledgePersistenceService + QdrantVectorService真实落地
- 向量检索 + 混合搜索真实落地

---

### 闭环6: Windows自动化闭环 ✅

**定义**：
```
流程: 用户指令 → 权限判断 → 工具调用 → WebSocket转发 → Electron接收 → Python执行
       → Windows API → 结果返回 → 状态确认 → 日志记录
验证: 发送"打开浏览器"指令 → 检查桌面 → 查询windows_automation_nodes表
```

**改进方案来源**: DESKTOP_BACKEND_INTEGRATION_AUDIT_AND_IMPROVEMENT_PLAN.md

**改进内容**：
- 闭环5: Windows自动化（双通道：HTTP + WebSocket）
- 桌面端主进程与渲染层双通道设计
- IPC注册与清理闭环

---

### 闭环7: PostgreSQL数据一致性闭环 ✅

**定义**：
```
流程: 写入请求 → JPA实体映射 → Repository.save() → PostgreSQL写入 → 数据持久化
       → 读取请求 → Repository.findById() → JPA实体映射 → Java对象返回
验证: 使用JPA Repository的CRUD操作 → @Transactional确保事务一致性
```

**改进方案来源**: DESKTOP_BACKEND_INTEGRATION_AUDIT_AND_IMPROVEMENT_PLAN.md 第8.3节

**改进内容**：
- 断点B3: 回执链路无事务修复方案
- recordReceipt + updateTaskFromReceipt包在同一个@Transactional方法
- runtimeEventStore文件写入改用@TransactionalEventListener(phase = AFTER_COMMIT)
- WebSocket推送改为AFTER_COMMIT

---

### 闭环8: Qdrant向量一致性闭环 ✅

**定义**：
```
流程: 知识注入 → 向量生成 → Qdrant.upsert() → 向量存储 → 向量搜索 → 结果返回
一致性: knowledge_entries表ID与Qdrant向量ID一致
验证: 注入知识后查询Qdrant确认向量存在 → 删除知识后确认向量删除
```

**改进方案来源**: DESKTOP_BACKEND_INTEGRATION_AUDIT_AND_IMPROVEMENT_PLAN.md 第7.1节

**改进内容**：
- LayeredKnowledgeBaseImpl完整闭环
- KnowledgePersistenceService + QdrantVectorService真实落地
- 向量检索 + 混合搜索真实落地

---

### 闭环9: Redis缓存一致性闭环 ✅

**定义**：
```
流程: 缓存写入 → RedisTemplate.opsForValue().set() → Redis存储 → 缓存读取
       → RedisTemplate.opsForValue().get() → 缓存返回 → 缓存过期 → 自动清理
验证: 写入缓存后查询Redis确认 → 更新数据后确认缓存更新
缓存类型: 会话缓存 + 权限缓存
```

**改进方案来源**: DESKTOP_BACKEND_INTEGRATION_AUDIT_AND_IMPROVEMENT_PLAN.md 第7.4节

**改进内容**：
- RuntimeEventStore基于文件 + tenantId硬编码修复方案
- 改为基于DB的EventStore（新建runtime_events表）
- tenantId从WorkItemContext或AuthContext获取，不硬编码

---

### 闭环10: 跨系统数据同步闭环 ✅

**定义**：
```
流程: Jira任务创建 → tasks写入 → Jira同步 → 状态一致 → 双向更新
一致性: Jira任务ID与tasks.jira_issue_id一致
验证: 创建任务后查询Jira确认 → 更新本地状态后确认Jira同步
```

**改进方案来源**: DESKTOP_BACKEND_INTEGRATION_AUDIT_AND_IMPROVEMENT_PLAN.md 第8.2节

**改进内容**：
- 断点B2: 项目统计未落库修复方案
- 已修复（阶段1）：在updateProjectFromExecution末尾增加了projectRepository.save(project)
- ExecutionReceiptTaskProjectBridge真实更新task状态

---

### 闭环11: 模型健康监控闭环 ✅

**定义**：
```
流程: 模型调用 → 响应时间记录 → 成功/失败统计 → 评分计算 → 阈值检查 → 自动调整
       → 模型替换 → 效果验证 → 恢复健康或继续调整
健康指标: 响应时间<30000ms, 成功率>95%, 平均评分>0.4, 连续失败次数<3
```

**改进方案来源**: MODEL_RESPONSIBILITY_AND_EXECUTION_OPTIMIZATION_PLAN.md 第3.3.2.1节

**改进内容**：
- 模型调用失败自动降级闭环
- 同一模型连续失败后进入短期cooldown
- receipt中可见model_provider、model_name、failure_reason、needs_retry
- 日志中能看到模型健康摘要

---

### 闭环13: 主动预判健康闭环 ✅

**定义**：
```
流程: 定时任务 → PatternPredictor分析 → RiskPredictor检查 → 建议生成 → 主动汇报
       → 用户确认 → 执行建议 → 效果记录 → 模式更新 → 下次预测优化
预判类型: PatternPredictor(用户行为模式) + RiskPredictor(风险识别)
          ProactiveSuggestionService(主动建议)
PR-1主动汇报: 定时生成(每小时) → WebSocket推送 → 用户确认执行或忽略
```

**改进方案来源**: DESKTOP_BACKEND_INTEGRATION_AUDIT_AND_IMPROVEMENT_PLAN.md 第7.1节

**改进内容**：
- 任务事件桥接服务真实调用RiskPredictor、ProactiveSuggestionService、DepartmentNotificationService
- EvolutionFeedbackBridgeService调用knowledgeManager.storeShared + auditLogRepository.save

---

## 三、部分闭环（0个）

> 原"闭环12: 服务启动健康闭环"已于2026-07-03升级为完整闭环，移至"二、完整闭环"。

---

## 四、闭环12补充改进方案

**定义**：
```
流程: Docker启动 → 依赖服务检查 → 数据库初始化 → Bean初始化 → 端点注册 → 健康检查
       → 服务就绪 → 接收请求 → 监控持续 → 异常报警
启动检查点: PostgreSQL启动 → Redis启动 → Qdrant启动 → Kafka启动
            Spring Boot启动 → model_daemon.py启动 → WebSocket端点注册
健康检查: /api/health → PostgreSQL/Redis/Qdrant/Kafka/NamedPipe连接检查
验证: GET /api/health确认服务健康 → 查看启动日志 → 连接WebSocket → 调用REST API
```

**已打通全部环节**：启动依赖检查(P12-A✅) → 健康检查端点增强(P12-B✅) → 启动顺序强制(P12-C✅) → 降级模式定时重试(P12-D✅) → 小流量回归(Canary修复✅) → 效果验证✅

**2026-07-03 修复内容**：
1. **Canary空转修复**：NamedPipeModelClient新增canary路由判断+recordProbeSuccess/Failure，ProviderConfig注入canary
2. **逻辑顺序修复**：ModelDaemonRecoveryService.clearDegraded()改为canary.startProbing()，先小流量探测再全量恢复

---

### 闭环14: 权限管理闭环 ✅

**定义**：
```
流程: 用户登录 → 权限加载 → 缓存存储 → 权限检查 → 路由判断 → 访问控制 → 权限变更
       → 缓存更新 → 新权限生效
权限级别闭环: CHAT_ONLY(0) → LIMITED(1) → DEPARTMENT(2) → FULL(3)
权限检查闭环: WebSocket连接 → REST API调用 → 工具执行 → 大脑选择 → 部门访问
验证: 不同权限用户连接不同WebSocket → 低权限用户尝试高权限操作确认拒绝
       权限变更后确认缓存更新 → 查询Redis缓存确认权限存储
```

**链路验证（2026-07-03 代码核验）**：
- 声纹注册/验证 → 权限校验 ✅
- PermissionChangeEvent → AgentWebSocketHandler.onPermissionChange → WebSocket断连 ✅
- PermissionServiceImpl.updateAccessLevel → recordAccess(5参数含detail) → 审计日志 ✅
- 五环节全打通 ✅

---

## 四、闭环12补充改进方案

### 4.1 P12-A：增强启动依赖检查

**文件修改清单**：

| 文件 | 修改内容 | 优先级 |
|------|---------|--------|
| living-agent-app/.../app/StartupDependencyChecker.java | 新增：启动依赖检查服务 | P0 |
| living-agent-core/.../model/ModelDaemonHealthChecker.java | 新增：model_daemon.py健康检查 | P0 |
| living-agent-core/.../model/provider/impl/NamedPipeModelClient.java | 修改：增加启动前管道检查 | P0 |
| living-agent-app/.../resources/application.yml | 修改：增加启动依赖配置 | P1 |

**代码实现**：

```java
// 文件：living-agent-app/src/main/java/com/livingagent/app/StartupDependencyChecker.java
package com.livingagent.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 启动依赖检查服务 - 闭环12增强
 * 确保所有关键依赖服务在应用启动前就绪
 */
@Component
public class StartupDependencyChecker implements ApplicationListener<ApplicationReadyEvent> {
    
    private static final Logger log = LoggerFactory.getLogger(StartupDependencyChecker.class);
    
    private final List<HealthCheckable> dependencies = new ArrayList<>();
    
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("=== Startup Dependency Check Begin ===");
        
        boolean allHealthy = true;
        for (HealthCheckable dep : dependencies) {
            try {
                boolean healthy = dep.checkHealth();
                if (!healthy) {
                    log.error("Dependency unhealthy: {}", dep.getName());
                    allHealthy = false;
                    if (dep.canAutoRecover()) {
                        log.info("Attempting auto recovery for: {}", dep.getName());
                        dep.autoRecover();
                    }
                } else {
                    log.info("Dependency healthy: {}", dep.getName());
                }
            } catch (Exception e) {
                log.error("Dependency check failed: {}", dep.getName(), e);
                allHealthy = false;
            }
        }
        
        if (!allHealthy) {
            log.warn("=== Startup Dependency Check FAILED ===");
            System.setProperty("app.mode", "degraded");
        } else {
            log.info("=== Startup Dependency Check PASSED ===");
            System.setProperty("app.mode", "full");
        }
    }
    
    public void registerDependency(HealthCheckable dependency) {
        dependencies.add(dependency);
    }
}

// 健康检查接口
public interface HealthCheckable {
    String getName();
    boolean checkHealth();
    boolean canAutoRecover();
    void autoRecover();
}
```

```java
// 文件：living-agent-core/src/main/java/com/livingagent/core/model/ModelDaemonHealthChecker.java
package com.livingagent.core.model;

import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * model_daemon.py健康检查器 - 闭环12增强
 */
@Component
public class ModelDaemonHealthChecker implements HealthCheckable {
    
    private final String inputPipePath = "/tmp/dialogue_daemon_control_request";
    private final String outputPipePath = "/tmp/dialogue_daemon_control_response";
    
    @Override
    public String getName() {
        return "model_daemon.py";
    }
    
    @Override
    public boolean checkHealth() {
        boolean inputPipeExists = Files.exists(Path.of(inputPipePath));
        boolean outputPipeExists = Files.exists(Path.of(outputPipePath));
        
        if (!inputPipeExists || !outputPipeExists) {
            return false;
        }
        
        try {
            File inputPipe = new File(inputPipePath);
            File outputPipe = new File(outputPipePath);
            if (!inputPipe.canWrite() || !outputPipe.canRead()) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
        
        return true;
    }
    
    @Override
    public boolean canAutoRecover() {
        return false;
    }
    
    @Override
    public void autoRecover() {
        // 无自动恢复能力，需要人工介入或重启服务
    }
}
```

---

### 4.2 P12-B：增强健康检查端点

> **注意**：以下代码示例使用 `Health`/`HealthIndicator` 风格API仅作逻辑参考。项目**未引入 Spring Actuator 依赖**，实际实现应基于 `core/diagnosis/` 自研健康检查体系（HealthMonitor/HealthCheck/HealthStatus），使用 `HealthCheck.builder().name(...).status(...).detail(...)` 替代 `Health.builder().up().withDetail(...)`。

**新增健康检查项**：

| 检查项 | 端点路径 | 实现位置 |
|--------|---------|---------|
| NamedPipe连接状态 | /api/health/namedpipe | NamedPipeHealthChecker |
| model_daemon.py进程 | /api/health/modeldaemon | ModelDaemonHealthChecker |
| Qwen模型加载状态 | /api/health/models | ModelLoadHealthChecker |
| Electron WebSocket桥接 | /api/health/winautomation | WinAutoHealthChecker |
| Claude CLI可用性 | /api/health/claudecli | ClaudeCliHealthChecker |

**代码实现（逻辑参考，需替换为自研体系API）**：

```java
// 文件：living-agent-core/src/main/java/com/livingagent/core/model/NamedPipeHealthChecker.java
// 实际实现应基于 core/diagnosis/ 自研健康检查体系
package com.livingagent.core.model;

import com.livingagent.core.diagnosis.HealthCheck;
import com.livingagent.core.diagnosis.HealthStatus;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * NamedPipe健康检查器 - 闭环12增强
 * 基于core/diagnosis/自研健康检查体系，非Spring Actuator
 */
@Component
public class NamedPipeHealthChecker {
    
    private final String inputPipePath = "/tmp/dialogue_daemon_control_request";
    private final String outputPipePath = "/tmp/dialogue_daemon_control_response";
    
    public HealthCheck check() {
        try {
            boolean inputExists = Files.exists(Path.of(inputPipePath));
            boolean outputExists = Files.exists(Path.of(outputPipePath));
            
            if (inputExists && outputExists) {
                return HealthCheck.builder()
                    .name("namedpipe")
                    .status(HealthStatus.UP)
                    .detail("input_pipe", "exists")
                    .detail("output_pipe", "exists")
                    .build();
            } else {
                return HealthCheck.builder()
                    .name("namedpipe")
                    .status(HealthStatus.DOWN)
                    .detail("input_pipe", inputExists ? "exists" : "missing")
                    .detail("output_pipe", outputExists ? "exists" : "missing")
                    .build();
            }
        } catch (Exception e) {
            return HealthCheck.builder()
                .name("namedpipe")
                .status(HealthStatus.DOWN)
                .detail("error", e.getMessage())
                .build();
        }
    }
}
```

```java
// 文件：living-agent-core/src/main/java/com/livingagent/core/model/ModelLoadHealthChecker.java
// 实际实现应基于 core/diagnosis/ 自研健康检查体系
package com.livingagent.core.model;

import com.livingagent.core.diagnosis.HealthCheck;
import com.livingagent.core.diagnosis.HealthStatus;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 模型加载健康检查器 - 闭环12增强
 * 基于core/diagnosis/自研健康检查体系，非Spring Actuator
 */
@Component
public class ModelLoadHealthChecker {
    
    private final String qwen3ModelPath = "/app/ai-models/Qwen3-0.6B-GGUF/Qwen3-0.6B-Q8_0.gguf";
    private final String qwen35ModelPath = "/app/ai-models/Qwen3.5-2B-GGUF/Qwen3.5-2B-Q4_K_M.gguf";
    
    public HealthCheck check() {
        try {
            boolean qwen3Loaded = Files.exists(Path.of(qwen3ModelPath));
            boolean qwen35Loaded = Files.exists(Path.of(qwen35ModelPath));
            
            if (qwen3Loaded && qwen35Loaded) {
                return HealthCheck.builder()
                    .name("model_load")
                    .status(HealthStatus.UP)
                    .detail("qwen3-0.6b", "loaded")
                    .detail("qwen3.5-2b", "loaded")
                    .build();
            } else if (qwen3Loaded || qwen35Loaded) {
                return HealthCheck.builder()
                    .name("model_load")
                    .status(HealthStatus.PARTIAL)
                    .detail("qwen3-0.6b", qwen3Loaded ? "loaded" : "missing")
                    .detail("qwen3.5-2b", qwen35Loaded ? "loaded" : "missing")
                    .detail("fallback_available", "true")
                    .build();
            } else {
                return HealthCheck.builder()
                    .name("model_load")
                    .status(HealthStatus.DOWN)
                    .detail("qwen3-0.6b", "missing")
                    .detail("qwen3.5-2b", "missing")
                    .detail("fallback_available", "false")
                    .build();
            }
        } catch (Exception e) {
            return HealthCheck.builder()
                .name("model_load")
                .status(HealthStatus.DOWN)
                .detail("error", e.getMessage())
                .build();
        }
    }
}
```

---

### 4.3 P12-C：强制启动顺序控制

**修改docker-compose.yml**：

```yaml
services:
  living-agent-service:
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
      qdrant:
        condition: service_healthy
      kafka:
        condition: service_started
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8382/api/health"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 60s
    
  postgres:
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 10s
      timeout: 5s
      retries: 5
  
  redis:
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 3s
      retries: 5
  
  qdrant:
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:6333/health"]
      interval: 10s
      timeout: 3s
      retries: 5
```

---

### 4.4 P12-D：启动失败自动恢复机制

**代码实现**：

```java
// 文件：living-agent-app/src/main/java/com/livingagent/app/StartupRecoveryService.java
package com.livingagent.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 启动失败自动恢复服务 - 闭环12增强
 */
@Service
public class StartupRecoveryService {
    
    private static final Logger log = LoggerFactory.getLogger(StartupRecoveryService.class);
    
    /**
     * 降级启动模式
     */
    public void startInDegradedMode() {
        log.info("Starting in degraded mode due to dependency failures");
        
        System.setProperty("windows.automation.enabled", "false");
        System.setProperty("claude.proxy.enabled", "false");
        System.setProperty("model.provider.default", "mock");
        System.setProperty("brain.available", "qwen3,tool");
        
        log.info("Degraded mode started: WinAuto=OFF, ClaudeCLI=OFF, ModelProvider=Mock, Brains=qwen3+tool");
    }
    
    /**
     * 尝试恢复完整服务
     */
    public void attemptFullRecovery() {
        log.info("Attempting full recovery from degraded mode");
        // 检查依赖是否恢复，逐步启用完整功能
    }
}
```

---

### 4.5 闭环12验收标准

| 验收项 | 验收方法 | 预期结果 |
|--------|---------|---------|
| 启动依赖检查 | 启动服务，查看日志 | 显示"Startup Dependency Check PASSED" |
| NamedPipe健康检查 | GET /api/health/namedpipe | 返回status=UP |
| 模型加载健康检查 | GET /api/health/models | 返回status=UP或PARTIAL |
| 降级启动模式 | 删除模型文件后启动 | 服务降级启动，日志显示"Degraded mode" |
| 自动恢复 | 恢复模型文件后调用attemptFullRecovery | 恢复完整服务 |

---

## 五、闭环14补充改进方案

### 5.1 P14-A：实现声纹验证闭环

**文件修改清单**：

| 文件 | 修改内容 | 优先级 |
|------|---------|--------|
| living-agent-core/.../security/VoiceVectorValidator.java | 新增：声纹验证服务 | P1 |
| living-agent-core/.../security/VoiceEmbeddingService.java | 新增：声纹嵌入生成服务 | P1 |
| living-agent-core/.../security/VoiceVectorRepository.java | 新增：声纹向量存储 | P1 |
| init-db/01_init.sql | 新增：voice_vectors表定义 | P1 |

**数据库表定义**：

```sql
-- 声纹向量存储表
CREATE TABLE voice_vectors (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL UNIQUE,
    voice_embedding BYTEA NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_voice_user ON voice_vectors(user_id);
```

**代码实现**：

```java
// 文件：living-agent-core/src/main/java/com/livingagent/core/security/VoiceVectorValidator.java
package com.livingagent.core.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 声纹验证服务 - 闭环14增强
 */
@Service
public class VoiceVectorValidator {
    
    private static final Logger log = LoggerFactory.getLogger(VoiceVectorValidator.class);
    private static final double SIMILARITY_THRESHOLD = 0.85;
    
    private final VoiceEmbeddingService embeddingService;
    private final VoiceVectorRepository repository;
    
    public VoiceVectorValidator(
            VoiceEmbeddingService embeddingService,
            VoiceVectorRepository repository) {
        this.embeddingService = embeddingService;
        this.repository = repository;
    }
    
    public VoiceValidationResult validateVoiceVector(String userId, byte[] audioData) {
        try {
            byte[] newEmbedding = embeddingService.generateEmbedding(audioData);
            Optional<VoiceVectorEntity> existingOpt = repository.findByUserId(userId);
            
            if (existingOpt.isEmpty()) {
                log.warn("No voice vector registered for user: {}", userId);
                return VoiceValidationResult.notRegistered(userId);
            }
            
            VoiceVectorEntity existing = existingOpt.get();
            double similarity = calculateSimilarity(newEmbedding, existing.getVoiceEmbedding());
            
            if (similarity >= SIMILARITY_THRESHOLD) {
                log.info("Voice validation passed for user: {}, similarity: {}", userId, similarity);
                return VoiceValidationResult.matched(userId, similarity);
            } else {
                log.warn("Voice validation failed for user: {}, similarity: {}", userId, similarity);
                return VoiceValidationResult.notMatched(userId, similarity);
            }
        } catch (Exception e) {
            log.error("Voice validation error for user: {}", userId, e);
            return VoiceValidationResult.error(userId, e.getMessage());
        }
    }
    
    public VoiceRegistrationResult registerVoiceVector(String userId, byte[] audioData) {
        try {
            byte[] embedding = embeddingService.generateEmbedding(audioData);
            VoiceVectorEntity entity = new VoiceVectorEntity();
            entity.setUserId(userId);
            entity.setVoiceEmbedding(embedding);
            repository.save(entity);
            log.info("Voice vector registered for user: {}", userId);
            return VoiceRegistrationResult.success(userId);
        } catch (Exception e) {
            log.error("Voice registration failed for user: {}", userId, e);
            return VoiceRegistrationResult.error(userId, e.getMessage());
        }
    }
    
    private double calculateSimilarity(byte[] embedding1, byte[] embedding2) {
        float[] vec1 = bytesToFloats(embedding1);
        float[] vec2 = bytesToFloats(embedding2);
        
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        
        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
            norm1 += vec1[i] * vec1[i];
            norm2 += vec2[i] * vec2[i];
        }
        
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
    
    private float[] bytesToFloats(byte[] bytes) {
        float[] floats = new float[bytes.length / 4];
        for (int i = 0; i < floats.length; i++) {
            floats[i] = Float.intBitsToFloat(
                ((bytes[i*4] & 0xFF) << 24) |
                ((bytes[i*4+1] & 0xFF) << 16) |
                ((bytes[i*4+2] & 0xFF) << 8) |
                (bytes[i*4+3] & 0xFF)
            );
        }
        return floats;
    }
}

// 验证结果记录
public record VoiceValidationResult(
    String userId,
    boolean matched,
    double similarity,
    String status,
    String errorMessage
) {
    public static VoiceValidationResult matched(String userId, double similarity) {
        return new VoiceValidationResult(userId, true, similarity, "matched", null);
    }
    
    public static VoiceValidationResult notMatched(String userId, double similarity) {
        return new VoiceValidationResult(userId, false, similarity, "not_matched", null);
    }
    
    public static VoiceValidationResult notRegistered(String userId) {
        return new VoiceValidationResult(userId, false, 0.0, "not_registered", null);
    }
    
    public static VoiceValidationResult error(String userId, String errorMessage) {
        return new VoiceValidationResult(userId, false, 0.0, "error", errorMessage);
    }
}
```

---

### 5.2 P14-B：权限变更强制实时生效

**文件修改清单**：

| 文件 | 修改内容 | 优先级 |
|------|---------|--------|
| living-agent-gateway/.../security/PermissionChangeNotifier.java | 新增：权限变更通知服务 | P0 |
| living-agent-gateway/.../websocket/WebSocketSessionRegistry.java | 新增：WebSocket会话注册表 | P0 |
| living-agent-gateway/.../controller/PermissionController.java | 修改：权限变更时强制WebSocket断开 | P0 |

**代码实现**：

```java
// 文件：living-agent-gateway/src/main/java/com/livingagent/gateway/security/PermissionChangeNotifier.java
package com.livingagent.gateway.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 权限变更通知服务 - 闭环14增强
 * 权限变更后强制实时生效，断开相关WebSocket连接
 */
@Service
public class PermissionChangeNotifier {
    
    private static final Logger log = LoggerFactory.getLogger(PermissionChangeNotifier.class);
    
    private final WebSocketSessionRegistry sessionRegistry;
    private final PermissionAuditLogService auditLogService;
    
    public PermissionChangeNotifier(
            WebSocketSessionRegistry sessionRegistry,
            PermissionAuditLogService auditLogService) {
        this.sessionRegistry = sessionRegistry;
        this.auditLogService = auditLogService;
    }
    
    /**
     * 权限变更后强制生效
     */
    public void enforcePermissionChange(String userId, int oldLevel, int newLevel, String changedBy) {
        log.info("Enforcing permission change: userId={}, oldLevel={}, newLevel={}, changedBy={}",
            userId, oldLevel, newLevel, changedBy);
        
        clearPermissionCache(userId);
        disconnectAllWebSocketSessions(userId);
        auditLogService.recordPermissionChange(userId, oldLevel, newLevel, changedBy);
        sendPermissionChangeNotification(userId, newLevel);
        
        log.info("Permission change enforced for user: {}", userId);
    }
    
    private void clearPermissionCache(String userId) {
        // 清除Redis中的权限缓存
    }
    
    private void disconnectAllWebSocketSessions(String userId) {
        List<WebSocketSession> sessions = sessionRegistry.getSessionsByUserId(userId);
        for (WebSocketSession session : sessions) {
            try {
                session.sendMessage(new TextMessage("{\"type\":\"permission_changed\",\"newLevel\":" + newLevel + "}"));
                Thread.sleep(100);
                session.close(CloseStatusPolicyChange.NEW_POLICY);
                log.info("WebSocket session closed due to permission change: sessionId={}, userId={}",
                    session.getId(), userId);
            } catch (Exception e) {
                log.error("Failed to close WebSocket session: sessionId={}", session.getId(), e);
            }
        }
        sessionRegistry.removeSessionsByUserId(userId);
    }
    
    private void sendPermissionChangeNotification(String userId, int newLevel) {
        // 发送通知（可选）
    }
}
```

---

### 5.3 P14-C：跨端权限实时同步

**文件修改清单**：

| 文件 | 修改内容 | 优先级 |
|------|---------|--------|
| living-agent-gateway/.../websocket/CrossPlatformPermissionSyncService.java | 新增：跨端权限同步服务 | P1 |
| living-agent-desktop/.../services/PermissionSyncClient.ts | 新增：桌面端权限同步客户端 | P1 |
| frontend/src/services/PermissionSyncService.ts | 新增：前端权限同步服务 | P1 |

**代码实现**：

```java
// 文件：living-agent-gateway/.../websocket/CrossPlatformPermissionSyncService.java
package com.livingagent.gateway.websocket;

import org.springframework.stereotype.Service;

/**
 * 跨端权限实时同步服务 - 闭环14增强
 */
@Service
public class CrossPlatformPermissionSyncService {
    
    public void syncPermissionToAllPlatforms(String userId, int permissionLevel) {
        syncToWebSocket(userId, permissionLevel);
        syncToDesktop(userId, permissionLevel);
        syncToMobile(userId, permissionLevel);
        updateDatabasePermission(userId, permissionLevel);
    }
    
    private void syncToWebSocket(String userId, int permissionLevel) { }
    private void syncToDesktop(String userId, int permissionLevel) { }
    private void syncToMobile(String userId, int permissionLevel) { }
    private void updateDatabasePermission(String userId, int permissionLevel) {
        // 注：permissions表不存在，权限为代码枚举(AccessLevel)，此处需要通过BrainAccessControl更新
    }
}
```

---

### 5.4 P14-D：权限审计日志完整记录

**文件修改清单**：

| 文件 | 修改内容 | 优先级 |
|------|---------|--------|
| living-agent-core/.../security/PermissionAuditLogService.java | 新增：权限审计日志服务 | P1 |
| living-agent-core/.../database/entity/PermissionAuditLogEntity.java | 新增：权限审计日志实体 | P1 |
| living-agent-core/.../database/repository/PermissionAuditLogRepository.java | 新增：权限审计日志Repository | P1 |
| init-db/01_init.sql | 新增：permission_audit_logs表定义 | P1 |

**数据库表定义**：

```sql
-- 权限审计日志表
CREATE TABLE permission_audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    old_level INTEGER NOT NULL,
    new_level INTEGER NOT NULL,
    changed_by VARCHAR(255) NOT NULL,
    change_reason VARCHAR(500),
    change_type VARCHAR(50) NOT NULL, -- upgrade/downgrade/grant/revoke
    timestamp TIMESTAMP NOT NULL DEFAULT NOW(),
    ip_address VARCHAR(50),
    platform VARCHAR(50), -- web/desktop/mobile/api
    session_id VARCHAR(255)
);

CREATE INDEX idx_audit_user ON permission_audit_logs(user_id);
CREATE INDEX idx_audit_timestamp ON permission_audit_logs(timestamp);
CREATE INDEX idx_audit_changed_by ON permission_audit_logs(changed_by);
```

**代码实现**：

```java
// 文件：living-agent-core/src/main/java/com/livingagent/core/security/PermissionAuditLogService.java
package com.livingagent.core.security;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 权限审计日志服务 - 闭环14增强
 */
@Service
public class PermissionAuditLogService {
    
    private final PermissionAuditLogRepository repository;
    
    public PermissionAuditLogService(PermissionAuditLogRepository repository) {
        this.repository = repository;
    }
    
    @Transactional
    public void recordPermissionChange(String userId, int oldLevel, int newLevel, String changedBy) {
        PermissionAuditLogEntity entity = new PermissionAuditLogEntity();
        entity.setUserId(userId);
        entity.setOldLevel(oldLevel);
        entity.setNewLevel(newLevel);
        entity.setChangedBy(changedBy);
        entity.setTimestamp(Instant.now());
        entity.setChangeType(determineChangeType(oldLevel, newLevel));
        repository.save(entity);
    }
    
    public List<PermissionAuditLogEntity> getPermissionHistory(String userId) {
        return repository.findByUserIdOrderByTimestampDesc(userId);
    }
    
    public List<PermissionAuditLogEntity> getPermissionChanges(Instant start, Instant end) {
        return repository.findByTimestampBetween(start, end);
    }
    
    private String determineChangeType(int oldLevel, int newLevel) {
        if (newLevel > oldLevel) return "upgrade";
        else if (newLevel < oldLevel) return "downgrade";
        else return "modify";
    }
}
```

---

### 5.5 闭环14验收标准

| 验收项 | 验收方法 | 预期结果 |
|--------|---------|---------|
| 声纹注册 | POST /api/voice/register 提供音频 | 注册成功，返回userId |
| 声纹验证 | POST /api/voice/validate 提供音频 | 返回matched/not_matched |
| 权限变更强制生效 | POST /api/permissions/update 后WebSocket连接断开 | WebSocket收到permission_changed消息后关闭 |
| 跨端权限同步 | 权限变更后桌面端刷新权限 | 桌面端显示新权限级别 |
| 权限审计日志查询 | GET /api/permissions/audit/{userId} | 返回权限变更历史列表 |

---

## 六、L1实施优先级

| 优先级 | 改进方案 | 依赖 |
|--------|---------|------|
| P0 | P12-A: 启动依赖检查增强 | 无 |
| P0 | P12-B: 健康检查端点增强 | P12-A |
| P0 | P12-C: 启动顺序强制控制 | 无 |
| P0 | P14-B: 权限变更强制实时生效 | 无 |
| P1 | P12-D: 启动失败自动恢复 | P12-A/B/C |
| P1 | P14-A: 声纹验证实现 | 无 |
| P1 | P14-C: 跨端权限实时同步 | P14-B |
| P1 | P14-D: 权限审计日志记录 | P14-B |

**实施顺序**：
- Sprint 1: P12-A + P12-B + P12-C（服务启动闭环）
- Sprint 2: P14-B（权限强制生效）
- Sprint 3: P12-D（启动自动恢复）
- Sprint 4: P14-A + P14-C + P14-D（权限完整闭环）

---

**版本信息**：
- 文档版本: v1.1
- 生成日期: 2026-07-02
- 最近更新: 2026-07-03（第三轮闭环链路验证，闭环14升级为完整闭环）
- 闭环数: 14个（13个完整闭环 + 1个部分闭环）
- 改进方案: 8个（P0×4 + P1×4，全部已完成）
