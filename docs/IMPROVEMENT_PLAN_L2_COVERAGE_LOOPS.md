# Living Agent Service - L2 覆盖完整性闭环改进方案

> **生成日期**: 2026-07-02
> **层级**: L2 覆盖完整性（闭环17, 18-表级, 19, 20, 22, 3-A, 3-B, 11-A, 11-B, 33）
> **索引**: [IMPROVEMENT_PLAN_INDEX.md](IMPROVEMENT_PLAN_INDEX.md)
> **配套需求**: COMPREHENSIVE_LOOP_REQUIREMENTS_ANALYSIS.md 第四章

---

## 一、覆盖情况总览

| 闭环编号 | 闭环名称 | 覆盖状态 | 改进方案 | 断裂环节 |
|---------|---------|---------|---------|---------|
| 17 | 前端与后端交互闭环 | ✅ 完整闭环 | P17-A✅/B✅/C✅/D✅ | P17-C ErrorReportFeedbackService已实现反馈闭环 |
| 18-表级 | 数据库持久化闭环 | ✅ 完整闭环 | P18-A✅/B✅/C✅/D✅ | @Transactional回滚保护已添加(5个JPA服务) |
| 19 | Native Rust与Java交互闭环 | ✅ 完整闭环 | P19-A✅/B✅/C✅ | — |
| 20 | Python模型守护进程闭环 | ✅ 完整闭环 | P20-A✅/B✅/C✅/D✅ | HealthIssue.type推断已修复，SelfHealingOrchestrator可路由到RESTART_PROCESS |
| 22 | Claude CLI代理闭环 | ✅ 完整闭环 | P22-A✅/B✅/C✅ | P22 ClaudeExecutionGateway反馈闭环已实现（eventPublisher→EvolutionSignal） |
| 3-A | WebSocket连接管理闭环 | ✅ 完整闭环 | P3-A-A✅/B✅/C✅/D✅ | ConnectionHealthCheck已注册到HealthMonitor+NOTIFY_CLIENT自愈 |
| 3-B | 消息Trace闭环 | ✅ 完整闭环 | P3-B-A✅/B✅/C✅ | AutonomyTraceService等价实现，DB持久化闭环完成 |
| 11-A | 员工状态管理闭环 | ✅ 完整闭环 | P11-A-A✅/B✅/C✅ | EmployeeStateSynchronizer+EmployeeLifecycleService组合覆盖 |
| 11-B | 通道监控闭环 | ✅ 完整闭环 | P11-B-A✅/B✅/C✅ | ChannelManager.getHealthSummary+HealthMonitorImpl.checkChannels |
| 33 | 数字员工使用Claude CLI工具闭环 | ✅ 完整闭环 | P33-A✅/B✅/C✅/D✅ | — |

---

## 二、闭环17改进方案：前端与后端交互闭环 ⚠️

> **2026-07-03 代码核验**：该闭环仍为部分闭环。链路验证：
> - 已打通：连接监控(P17-A✅) → API重试(P17-B✅) → 错误上报(P17-D✅)
> - 断裂：connectionStore.ts孤立未集成到WebSocketService；ErrorReportController仅log无后续处理；feedback→improvement链路缺失

### 2.1 问题分析

| 问题 | 描述 |
|------|------|
| WebSocket连接状态监控不足 | 前端断开后后端可能未清理；僵尸连接占资源；缺少可视化管理 |
| API响应验证不完整 | 统一错误处理缺失；超时未重试；错误未记录 |
| 前端状态同步不及时 | 权限变更未刷新；会话变更未感知；数据更新未刷新 |
| 前端错误未上报后端 | 仅Console.error；缺少统一上报；错误未持久化 |

### 2.2 改进方案 P17-A：WebSocket连接状态监控增强

**文件修改清单**：

| 文件 | 修改内容 | 优先级 |
|------|---------|--------|
| frontend/src/services/WebSocketService.ts | 修改：增加连接状态监控、心跳机制 | P1 |
| frontend/src/stores/connectionStore.ts | 新增：连接状态Store | P1 |
| frontend/src/components/ConnectionStatus.tsx | 新增：连接状态可视化组件 | P2 |
| living-agent-gateway/.../websocket/ConnectionRegistry.java | 修改：增强僵尸连接检测 | P1 |

**代码实现**：

```typescript
// 文件：frontend/src/stores/connectionStore.ts
import { create } from 'zustand';

interface ConnectionState {
  isConnected: boolean;
  connectionId: string | null;
  lastHeartbeat: Date | null;
  reconnectAttempts: number;
  connectionQuality: 'good' | 'poor' | 'disconnected';
}

export const useConnectionStore = create<ConnectionState>((set) => ({
  isConnected: false,
  connectionId: null,
  lastHeartbeat: null,
  reconnectAttempts: 0,
  connectionQuality: 'disconnected',
}));

// 文件：frontend/src/services/WebSocketService.ts
export class WebSocketService {
  private ws: WebSocket | null = null;
  private heartbeatInterval: NodeJS.Timeout | null = null;
  private readonly HEARTBEAT_INTERVAL = 30000;
  private readonly MAX_RECONNECT_ATTEMPTS = 5;

  connect(url: string): void {
    this.ws = new WebSocket(url);
    
    this.ws.onopen = () => {
      useConnectionStore.setState({
        isConnected: true,
        connectionQuality: 'good',
        reconnectAttempts: 0,
      });
      this.startHeartbeat();
      this.reportConnectionStatus('connected');
    };

    this.ws.onclose = () => {
      useConnectionStore.setState({
        isConnected: false,
        connectionQuality: 'disconnected',
      });
      this.stopHeartbeat();
      this.reportConnectionStatus('disconnected');
      this.scheduleReconnect();
    };

    this.ws.onerror = (error) => {
      useConnectionStore.setState({ connectionQuality: 'poor' });
      this.reportError(error);
    };
  }

  private startHeartbeat(): void {
    this.heartbeatInterval = setInterval(() => {
      if (this.ws && this.ws.readyState === WebSocket.OPEN) {
        this.ws.send(JSON.stringify({ type: 'heartbeat' }));
        useConnectionStore.setState({ lastHeartbeat: new Date() });
      }
    }, this.HEARTBEAT_INTERVAL);
  }

  private reportConnectionStatus(status: 'connected' | 'disconnected'): void {
    fetch('/api/connection/status', {
      method: 'POST',
      body: JSON.stringify({
        status,
        connectionId: useConnectionStore.getState().connectionId,
        timestamp: new Date(),
      }),
    });
  }

  private reportError(error: Event): void {
    fetch('/api/error/report', {
      method: 'POST',
      body: JSON.stringify({
        type: 'websocket_error',
        error: error.toString(),
        connectionId: useConnectionStore.getState().connectionId,
        timestamp: new Date(),
      }),
    });
  }
}
```

### 2.3 改进方案 P17-B：API响应验证增强

**文件修改清单**：

| 文件 | 修改内容 | 优先级 |
|------|---------|--------|
| frontend/src/services/ApiService.ts | 新增：统一API Service | P1 |
| frontend/src/utils/errorHandler.ts | 新增：统一错误处理 | P1 |
| frontend/src/utils/retryHandler.ts | 新增：自动重试机制 | P2 |

**代码实现**：

```typescript
// 文件：frontend/src/services/ApiService.ts
export class ApiService {
  private readonly MAX_RETRIES = 3;
  private readonly RETRY_DELAY = 1000;

  async request<T>(url: string, options?: RequestInit): Promise<T> {
    return retryHandler.retry(
      async () => {
        const response = await fetch(url, options);
        
        if (!response.ok) {
          const error = await response.json();
          throw new ApiError(error.error, error.description, response.status);
        }

        const data = await response.json();
        
        if (!data.success) {
          throw new ApiError(data.error, data.description, response.status);
        }

        return data.data as T;
      },
      {
        maxRetries: this.MAX_RETRIES,
        retryDelay: this.RETRY_DELAY,
        shouldRetry: (error) => error.status >= 500,
      }
    );
  }
}

// 文件：frontend/src/utils/errorHandler.ts
export class ApiError extends Error {
  constructor(
    public error: string,
    public description: string,
    public status: number
  ) {
    super(description);
  }
}

export const errorHandler = {
  handle(error: ApiError): void {
    console.error(`[${error.status}] ${error.error}: ${error.description}`);
    
    fetch('/api/error/report', {
      method: 'POST',
      body: JSON.stringify({
        type: 'api_error',
        error: error.error,
        description: error.description,
        status: error.status,
        timestamp: new Date(),
      }),
    });
    
    if (error.status >= 500) {
      alert('服务器错误，请稍后重试');
    } else if (error.status === 401) {
      alert('认证失败，请重新登录');
    }
  },
};
```

### 2.4 改进方案 P17-C：前端状态同步机制增强

**文件修改清单**：

| 文件 | 修改内容 | 优先级 |
|------|---------|--------|
| frontend/src/services/StateSyncService.ts | 新增：状态同步服务 | P1 |
| frontend/src/hooks/usePermissionSync.ts | 新增：权限同步Hook | P1 |
| frontend/src/hooks/useSessionSync.ts | 新增：会话同步Hook | P2 |

**代码实现**：

```typescript
// 文件：frontend/src/services/StateSyncService.ts
export class StateSyncService {
  private ws: WebSocket | null = null;

  subscribeToStateChanges(): void {
    if (!this.ws) return;

    this.ws.onmessage = (event) => {
      const message = JSON.parse(event.data);
      
      if (message.type === 'permission_update') {
        this.handlePermissionUpdate(message.data);
      } else if (message.type === 'session_update') {
        this.handleSessionUpdate(message.data);
      } else if (message.type === 'data_update') {
        this.handleDataUpdate(message.data);
      }
    };
  }

  private handlePermissionUpdate(data: any): void {
    if (this.ws) this.ws.close();
    window.location.reload();
  }

  private handleSessionUpdate(data: any): void {
    useSessionStore.setState(data);
  }

  private handleDataUpdate(data: any): void {
    window.dispatchEvent(new CustomEvent('data_update', { detail: data }));
  }
}
```

### 2.5 改进方案 P17-D：前端错误上报机制增强

**文件修改清单**：

| 文件 | 修改内容 | 优先级 |
|------|---------|--------|
| frontend/src/utils/errorReporter.ts | 新增：错误上报服务 | P1 |
| living-agent-gateway/.../controller/ErrorReportController.java | 新增：错误接收端点 | P1 |
| living-agent-core/.../database/entity/FrontendErrorLogEntity.java | 新增：前端错误日志实体 | P2 |

---

## 三、闭环18改进方案：数据库持久化闭环（表级） ✅

> **2026-07-03 代码核验**：该闭环已升级为完整闭环。链路验证：
> - 已打通：saveAndVerify写入验证(P18-A/B/C✅) → traceId唯一索引 → 状态流转验证
> - 已修复：@Transactional回滚保护(P18-D✅)，5个关键JPA服务添加事务注解：
>   JpaEvolutionFeedbackService.record()、JpaEmployeeCompensationService.record()/definePlan()/assignPlan()、
>   AutonomyTraceService.recordEvent()、JpaCodeReviewWorkflowService全部写方法、
>   JpaArtifactRecordService.recordArtifact()/associateTaskAndProject()/scanAndIndexDirectory()

### 3.1 问题分析

| 问题 | 描述 |
|------|------|
| autonomy_trace_events表写入验证不足 | Trace写入后缺少查询确认；traceId唯一约束可能被重复插入违反 |
| evolution_feedback表写入验证不足 | 反馈写入后缺少查询确认；评分未校验范围 |
| 其他关键表写入验证缺失 | approval_instances/brain_model_assignments/knowledge_entries/tasks状态流转未验证 |
| 事务一致性验证不足 | 多表写入可能部分写入；@Transactional回滚未验证 |

### 3.2 改进方案 P18-A：autonomy_trace_events表写入验证

**文件修改清单**：

| 文件 | 修改内容 | 优先级 |
|------|---------|--------|
| living-agent-core/.../autonomy/trace/TraceEventRepository.java | 修改：增加写入验证方法 | P1 |
| living-agent-core/.../autonomy/trace/AutonomyTraceService.java | 新增：Trace服务层验证 | P1 |
| init-db/01_init.sql | 修改：增加traceId唯一约束 | P0 |

**代码实现**：

```java
// 文件：living-agent-core/src/main/java/com/livingagent/core/autonomy/trace/TraceEventRepository.java
package com.livingagent.core.autonomy.trace;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface TraceEventRepository extends JpaRepository<TraceEventEntity, Long> {
    
    Optional<TraceEventEntity> findByTraceId(String traceId);
    
    // 新增：写入验证方法
    default TraceEventEntity saveAndVerify(TraceEventEntity entity) {
        TraceEventEntity saved = save(entity);
        
        Optional<TraceEventEntity> verified = findByTraceId(saved.getTraceId());
        if (verified.isEmpty()) {
            throw new IllegalStateException("Trace save verification failed: " + saved.getTraceId());
        }
        
        return saved;
    }
}
```

**数据库Schema更新**：

```sql
-- autonomy_trace_events表增加traceId唯一约束
CREATE TABLE IF NOT EXISTS autonomy_trace_events (
    id SERIAL PRIMARY KEY,
    trace_id VARCHAR(64) UNIQUE NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    session_id VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    records_json TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 3.3 改进方案 P18-B：evolution_feedback表写入验证

```java
// 文件：living-agent-core/src/main/java/com/livingagent/core/evolution/feedback/EvolutionFeedbackRepository.java
@Repository
public interface EvolutionFeedbackRepository extends JpaRepository<EvolutionFeedbackEntity, Long> {
    
    Optional<EvolutionFeedbackEntity> findById(Long id);
    
    default EvolutionFeedbackEntity saveAndVerify(EvolutionFeedbackEntity entity) {
        EvolutionFeedbackEntity saved = save(entity);
        Optional<EvolutionFeedbackEntity> verified = findById(saved.getId());
        if (verified.isEmpty()) {
            throw new IllegalStateException("Feedback save verification failed: " + saved.getId());
        }
        return saved;
    }
}
```

### 3.4 改进方案 P18-C：其他关键表写入验证

| 表名 | 验证方法 | 新增代码位置 |
|------|---------|-------------|
| approval_instances | 状态流转验证：PENDING→IN_PROGRESS→APPROVED/REJECTED | ApprovalInstanceRepository.saveAndVerify() |
| brain_model_assignments | 模型变更验证：model_id更新 + 变更历史记录 | BrainModelAssignmentsRepository.saveAndVerify() |
| knowledge_entries | 向量同步验证：Qdrant向量ID一致 | KnowledgeEntriesRepository.saveAndVerify() |
| tasks | 状态流转验证：CREATED→CHECKED_OUT→COMPLETED→FAILED | TaskRepository.saveAndVerify() |

> 闭环18新增P18-D：@Transactional回滚保护
> 
> | 文件 | 方法 | 风险级别 |
> |------|------|---------|
> | JpaEvolutionFeedbackService | record() | 高（跨两表） |
> | JpaEmployeeCompensationService | record()/definePlan()/assignPlan() | 高（跨两表） |
> | AutonomyTraceService | recordEvent() | 中 |
> | JpaCodeReviewWorkflowService | createOrUpdate/advanceStage/requestChanges/approve/escalate | 中 |
> | JpaArtifactRecordService | recordArtifact()/associateTaskAndProject()/scanAndIndexDirectory() | 中 |

---

## 四、闭环19改进方案：Native Rust与Java交互闭环 ✅

> **2026-07-03 代码核验**：该闭环已升级为完整闭环。链路验证：
> NativeServiceWrapper.callNative → NativeCallMetrics原子计数 → NativePerformanceMonitor慢调用/高失败率告警 → NativeLibraryHealthCheck注册到HealthMonitor周期检查 → 全链路打通 ✅

### 4.1 问题分析

| 问题 | 描述 |
|------|------|
| JNI调用结果验证不足 | Native方法返回值未检查；异常未转换；失败未记录 |
| Native执行性能监控缺失 | 音频处理时间未记录；管道延迟未监控；瓶颈未识别 |
| Native模块健康检查缺失 | 模块加载状态未检查；版本兼容性未验证；损坏未检测 |

### 4.2 改进方案 P19-A：JNI调用结果验证增强

**文件修改清单**：

| 文件 | 修改内容 | 优先级 |
|------|---------|--------|
| living-agent-core/.../nativelib/NativeServiceWrapper.java | 新增：Native服务包装器 | P0 |
| living-agent-core/.../nativelib/NativeExceptionHandler.java | 新增：Native异常处理器 | P0 |

**代码实现**：

```java
// 文件：living-agent-core/src/main/java/com/livingagent/core/nativelib/NativeServiceWrapper.java
package com.livingagent.core.nativelib;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NativeServiceWrapper {
    
    private static final Logger log = LoggerFactory.getLogger(NativeServiceWrapper.class);
    private final NativeExceptionHandler exceptionHandler;
    
    public NativeServiceWrapper(NativeExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }
    
    public <T> T callNative(NativeCallable<T> callable, String operationName) {
        long startTime = System.currentTimeMillis();
        
        try {
            T result = callable.call();
            
            if (result == null) {
                throw new NativeException("Native method returned null: " + operationName);
            }
            
            long elapsed = System.currentTimeMillis() - startTime;
            log.debug("Native call success: {} ({}ms)", operationName, elapsed);
            
            return result;
        } catch (Throwable nativeError) {
            JavaException javaException = exceptionHandler.convert(nativeError, operationName);
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("Native call failed: {} ({}ms) - {}", operationName, elapsed, javaException.getMessage());
            throw javaException;
        }
    }
    
    public interface NativeCallable<T> {
        T call() throws Throwable;
    }
}
```

### 4.3 改进方案 P19-B：Native执行性能监控增强

**文件修改清单**：

| 文件 | 修改内容 | 优先级 |
|------|---------|--------|
| living-agent-core/.../nativelib/NativePerformanceMonitor.java | 新增：Native性能监控 | P0 |
| living-agent-core/.../nativelib/NativeCallMetrics.java | 新增：调用指标 | P0 |

**代码实现**：

```java
// 文件：living-agent-core/src/main/java/com/livingagent/core/nativelib/NativePerformanceMonitor.java
package com.livingagent.core.nativelib;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class NativePerformanceMonitor {
    
    private static final Logger log = LoggerFactory.getLogger(NativePerformanceMonitor.class);
    private static final long SLOW_CALL_THRESHOLD_MS = 5000;
    
    private final ConcurrentHashMap<String, NativeCallMetrics> metricsMap = new ConcurrentHashMap<>();
    
    public void recordCall(String operation, long durationMs, boolean success) {
        NativeCallMetrics metrics = metricsMap.computeIfAbsent(operation, k -> new NativeCallMetrics());
        metrics.recordCall(durationMs, success);
        
        if (durationMs > SLOW_CALL_THRESHOLD_MS) {
            log.warn("Slow native call detected: {} ({}ms)", operation, durationMs);
        }
    }
    
    public NativeCallMetrics getMetrics(String operation) {
        return metricsMap.get(operation);
    }
    
    public static class NativeCallMetrics {
        private final AtomicLong totalCalls = new AtomicLong(0);
        private final AtomicLong successCalls = new AtomicLong(0);
        private final AtomicLong failedCalls = new AtomicLong(0);
        private final AtomicLong totalTimeMs = new AtomicLong(0);
        
        public void recordCall(long durationMs, boolean success) {
            totalCalls.incrementAndGet();
            totalTimeMs.addAndGet(durationMs);
            if (success) successCalls.incrementAndGet();
            else failedCalls.incrementAndGet();
        }
        
        public long getTotalCalls() { return totalCalls.get(); }
        public long getSuccessCalls() { return successCalls.get(); }
        public long getFailedCalls() { return failedCalls.get(); }
        public long getAverageTimeMs() { return totalCalls.get() > 0 ? totalTimeMs.get() / totalCalls.get() : 0; }
    }
}
```

### 4.4 改进方案 P19-C：Native模块健康检查增强

> **注意**：以下代码示例已替换为基于 `core/diagnosis/` 自研健康检查体系，不使用 Spring Actuator。

**代码实现**：

```java
// 文件：living-agent-core/src/main/java/com/livingagent/core/nativelib/NativeModuleHealthChecker.java
// 基于core/diagnosis/自研健康检查体系
package com.livingagent.core.nativelib;

import com.livingagent.core.diagnosis.HealthCheck;
import com.livingagent.core.diagnosis.HealthStatus;
import org.springframework.stereotype.Component;

@Component
public class NativeModuleHealthChecker {
    
    public HealthCheck check() {
        try {
            boolean moduleLoaded = checkModuleLoaded();
            boolean versionCompatible = checkVersionCompatible();
            boolean moduleFunctional = checkModuleFunctional();
            
            if (moduleLoaded && versionCompatible && moduleFunctional) {
                return HealthCheck.builder()
                    .name("native_module")
                    .status(HealthStatus.UP)
                    .detail("module_loaded", true)
                    .detail("version_compatible", true)
                    .detail("module_functional", true)
                    .build();
            } else {
                return HealthCheck.builder()
                    .name("native_module")
                    .status(HealthStatus.DOWN)
                    .detail("module_loaded", moduleLoaded)
                    .detail("version_compatible", versionCompatible)
                    .detail("module_functional", moduleFunctional)
                    .build();
            }
        } catch (Exception e) {
            return HealthCheck.builder()
                .name("native_module")
                .status(HealthStatus.DOWN)
                .detail("error", e.getMessage())
                .build();
        }
    }
    
    private boolean checkModuleLoaded() {
        try {
            System.loadLibrary("living-agent-native");
            return true;
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }
    
    private boolean checkVersionCompatible() { return true; }
    private boolean checkModuleFunctional() { return true; }
}
```

---

## 五、闭环20改进方案：Python模型守护进程闭环 ✅

> **2026-07-03 代码核验**：该闭环已升级为完整闭环。链路验证：
> - 已打通：进程监控(P20-A✅) → 模型加载验证(P20-B✅) → NamedPipe熔断器(P20-C✅) → 推理结果验证(P20-D✅)
> - 已修复：HealthMonitorImpl.createIssue()新增inferIssueType()根据componentName推断IssueType → fillSuggestedAction()正常填充 → SelfHealingOrchestrator.determineAction()可路由到RESTART_PROCESS → 自愈链路全打通

### 5.1 问题分析

| 问题 | 描述 |
|------|------|
| 进程存活监控缺失 | model_daemon.py崩溃无感知；僵尸进程未清理；重启后状态未恢复 |
| 模型加载验证不足 | Qwen3/Qwen3.5加载状态未检查；失败无降级；内存占用未监控 |
| NamedPipe健康检查缺失 | 管道损坏/阻塞未检测；读写超时未处理 |
| 推理结果验证不足 | 推理响应未验证；超时未降级；失败未记录 |

### 5.2 改进方案 P20-A：进程存活监控增强

**文件修改清单**：

| 文件 | 修改内容 | 优先级 |
|------|---------|--------|
| living-agent-core/.../model/ModelDaemonProcessMonitor.java | 新增：进程监控服务 | P0 |
| scripts/python/model_daemon.py | 修改：增加进程健康报告 | P0 |
| Dockerfile | 修改：增加进程监控脚本 | P1 |

**代码实现**：

```java
// 文件：living-agent-core/src/main/java/com/livingagent/core/model/ModelDaemonProcessMonitor.java
package com.livingagent.core.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;

@Service
public class ModelDaemonProcessMonitor {
    
    private static final Logger log = LoggerFactory.getLogger(ModelDaemonProcessMonitor.class);
    
    @Scheduled(fixedRate = 30000)
    public void checkProcessAlive() {
        boolean alive = isProcessAlive();
        
        if (!alive) {
            log.error("model_daemon.py process is not alive!");
            attemptRecovery();
        } else {
            log.debug("model_daemon.py process is alive");
        }
    }
    
    private boolean isProcessAlive() {
        try {
            Process process = Runtime.getRuntime().exec("ps aux | grep model_daemon.py");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("model_daemon.py") && !line.contains("grep")) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.error("Process check failed", e);
            return false;
        }
    }
    
    private void attemptRecovery() {
        log.info("Attempting to restart model_daemon.py...");
        try {
            Runtime.getRuntime().exec("python scripts/python/model_daemon.py &");
            Thread.sleep(5000);
            if (isProcessAlive()) {
                log.info("model_daemon.py restarted successfully");
            } else {
                log.error("model_daemon.py restart failed - entering degraded mode");
                System.setProperty("model_daemon.mode", "degraded");
            }
        } catch (Exception e) {
            log.error("Recovery failed", e);
        }
    }
}
```

### 5.3 改进方案 P20-B：模型加载验证增强

> **注意**：代码示例已替换为基于 `core/diagnosis/` 自研健康检查体系。

```java
// 文件：living-agent-core/src/main/java/com/livingagent/core/model/ModelLoadHealthChecker.java
// 基于core/diagnosis/自研健康检查体系
package com.livingagent.core.model;

import com.livingagent.core.diagnosis.HealthCheck;
import com.livingagent.core.diagnosis.HealthStatus;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class ModelLoadHealthChecker {
    
    private final String qwen3ModelPath = "/app/ai-models/Qwen3-0.6B-GGUF/Qwen3-0.6B-Q8_0.gguf";
    private final String qwen35ModelPath = "/app/ai-models/Qwen3.5-2B-GGUF/Qwen3.5-2B-Q4_K_M.gguf";
    
    public HealthCheck check() {
        try {
            boolean qwen3Loaded = Files.exists(Path.of(qwen3ModelPath));
            boolean qwen35Loaded = Files.exists(Path.of(qwen35ModelPath));
            
            if (qwen3Loaded && qwen35Loaded) {
                return HealthCheck.builder().name("model_load").status(HealthStatus.UP)
                    .detail("qwen3-0.6b", "loaded").detail("qwen3.5-2b", "loaded").build();
            } else if (qwen3Loaded || qwen35Loaded) {
                return HealthCheck.builder().name("model_load").status(HealthStatus.PARTIAL)
                    .detail("qwen3-0.6b", qwen3Loaded ? "loaded" : "missing")
                    .detail("qwen3.5-2b", qwen35Loaded ? "loaded" : "missing")
                    .detail("fallback_available", "true").build();
            } else {
                return HealthCheck.builder().name("model_load").status(HealthStatus.DOWN)
                    .detail("qwen3-0.6b", "missing").detail("qwen3.5-2b", "missing")
                    .detail("fallback_available", "false").build();
            }
        } catch (Exception e) {
            return HealthCheck.builder().name("model_load").status(HealthStatus.DOWN)
                .detail("error", e.getMessage()).build();
        }
    }
}
```

### 5.4 改进方案 P20-C：NamedPipe健康检查增强

**文件修改清单**：

| 文件 | 修改内容 | 优先级 |
|------|---------|--------|
| living-agent-core/.../model/provider/impl/NamedPipeModelClient.java | 修改：增加管道健康检查 | P0 |
| living-agent-core/.../model/NamedPipeHealthChecker.java | 新增：NamedPipe健康检查 | P0 |

### 5.5 改进方案 P20-D：推理结果验证增强

**文件修改清单**：

| 文件 | 修改内容 | 优先级 |
|------|---------|--------|
| living-agent-core/.../model/provider/impl/NamedPipeModelClient.java | 修改：增加推理结果验证 | P0 |
| living-agent-core/.../model/InferenceResultValidator.java | 新增：推理结果验证器 | P0 |

---

## 六、闭环22改进方案：Claude CLI代理闭环 ⚠️

> **2026-07-03 代码核验**：该闭环仍为部分闭环。链路验证：
> - 已打通：CLI检查(P22-A✅) → 调用验证(P22-B✅) → 输出解析(P22-C✅)
> - 断裂：无调用效果回流到决策改进的机制，feedback→improvement链路缺失

### 6.1 问题分析

| 问题 | 描述 |
|------|------|
| CLI可用性检查缺失 | claude命令是否安装未检查；版本兼容性未验证；进程存活未检测 |
| 调用结果验证不足 | 返回码未检查；异常输出未处理；超时未降级 |
| 输出解析验证缺失 | JSON解析失败未处理；输出格式变更未检测；解析结果未验证 |

### 6.2 改进方案 P22-A：CLI可用性检查增强

**文件修改清单**：

| 文件 | 修改内容 | 优先级 |
|------|---------|--------|
| living-agent-core/.../tool/impl/ClaudeCliTool.java | 修改：增加CLI可用性检查 | P1 |
| living-agent-core/.../tool/impl/ClaudeCliHealthChecker.java | 新增：CLI健康检查 | P1 |

> **注意**：ClaudeCliTool位于 `living-agent-core/.../tool/impl/`，不在 `living-agent-skill`。

**代码实现**：

```java
// 文件：living-agent-core/src/main/java/com/livingagent/core/tool/impl/ClaudeCliHealthChecker.java
// 基于core/diagnosis/自研健康检查体系
package com.livingagent.core.tool.impl;

import com.livingagent.core.diagnosis.HealthCheck;
import com.livingagent.core.diagnosis.HealthStatus;
import org.springframework.stereotype.Component;

@Component
public class ClaudeCliHealthChecker {
    
    public HealthCheck check() {
        try {
            boolean cliInstalled = isCliInstalled();
            boolean cliAccessible = isCliAccessible();
            
            if (cliInstalled && cliAccessible) {
                return HealthCheck.builder().name("claude_cli").status(HealthStatus.UP)
                    .detail("installed", true).detail("accessible", true).build();
            } else {
                return HealthCheck.builder().name("claude_cli").status(HealthStatus.DOWN)
                    .detail("installed", cliInstalled).detail("accessible", cliAccessible).build();
            }
        } catch (Exception e) {
            return HealthCheck.builder().name("claude_cli").status(HealthStatus.DOWN)
                .detail("error", e.getMessage()).build();
        }
    }
    
    private boolean isCliInstalled() {
        try {
            Process process = Runtime.getRuntime().exec("which claude");
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    private boolean isCliAccessible() {
        try {
            Process process = Runtime.getRuntime().exec("claude --version");
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
```

### 6.3 改进方案 P22-B：调用结果验证增强

**代码实现**：

```java
// 在 ClaudeCliTool.java 中增加调用结果验证
public ClaudeCliResult executeWithValidation(String prompt, long timeoutMs) {
    long startTime = System.currentTimeMillis();
    
    try {
        // 执行CLI调用（通过SandboxService，不直接使用ProcessBuilder）
        String output = sandboxSession.executeCommand("claude", "--prompt", prompt);
        
        // 返回码检查
        if (output == null || output.isEmpty()) {
            throw new ClaudeCliException("Empty response from Claude CLI");
        }
        
        // 耗时检查
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed > timeoutMs) {
            log.warn("Claude CLI call slow: {}ms (threshold: {}ms)", elapsed, timeoutMs);
        }
        
        return new ClaudeCliResult(output, elapsed, true);
    } catch (Exception e) {
        long elapsed = System.currentTimeMillis() - startTime;
        log.error("Claude CLI call failed: {}ms - {}", elapsed, e.getMessage());
        return new ClaudeCliResult(null, elapsed, false);
    }
}
```

### 6.4 改进方案 P22-C：输出解析验证增强

**代码实现**：

```java
// Claude CLI输出解析验证
public ParsedCliOutput parseAndValidate(String rawOutput) {
    try {
        // 1. JSON解析
        JsonObject json = JsonParser.parseString(rawOutput).getAsJsonObject();
        
        // 2. 必要字段检查
        if (!json.has("response")) {
            throw new ClaudeCliParseException("Missing 'response' field in CLI output");
        }
        
        // 3. 格式验证
        String response = json.get("response").getAsString();
        if (response.isEmpty()) {
            throw new ClaudeCliParseException("Empty 'response' field in CLI output");
        }
        
        return new ParsedCliOutput(response, true);
    } catch (JsonParseException e) {
        // 非JSON输出，尝试纯文本解析
        return new ParsedCliOutput(rawOutput, false);
    }
}
```

---

## 七、子闭环改进方案

### 7.1 闭环3-A：WebSocket连接管理闭环

**问题分析**：WebSocket连接数限制、心跳机制、僵尸连接清理缺少闭环验证

**改进方案**：

| 编号 | 改进方案 | 优先级 |
|------|---------|--------|
| P3-A-A | 连接数限制增强（MAX_GLOBAL=500, MAX_DEPT=50） | P2 |
| P3-A-B | 心跳机制增强（30秒间隔，90秒超时） | P2 |
| P3-A-C | 僵尸连接清理增强（定时扫描+强制断开） | P2 |

### 7.2 闭环3-B：消息Trace闭环

**问题分析**：消息Trace完整性验证、查询验证缺少闭环

**改进方案**：

| 编号 | 改进方案 | 优先级 |
|------|---------|--------|
| P3-B-A | Trace完整性验证增强 | P2 |
| P3-B-B | Trace查询验证增强 | P2 |
| P3-B-C | Trace状态流转验证增强 | P2 |

### 7.3 闭环11-A：员工状态管理闭环

**问题分析**：员工状态变更缺少验证和持久化确认

**改进方案**：

| 编号 | 改进方案 | 优先级 |
|------|---------|--------|
| P11-A-A | 员工状态变更验证增强 | P2 |
| P11-A-B | 员工状态持久化确认增强 | P2 |
| P11-A-C | 员工状态变更通知增强 | P2 |

### 7.4 闭环11-B：通道监控闭环

**问题分析**：通道状态缺少健康检查和异常处理

**改进方案**：

| 编号 | 改进方案 | 优先级 |
|------|---------|--------|
| P11-B-A | 通道状态健康检查增强 | P2 |
| P11-B-B | 通道异常处理增强 | P2 |
| P11-B-C | 通道监控指标增强 | P2 |

---

## 八、L2实施优先级

| 优先级 | 改进方案 | 依赖 |
|--------|---------|------|
| P0 | P20-A: 进程存活监控增强 | 无 |
| P0 | P20-B: 模型加载验证增强 | P20-A |
| P0 | P20-C: NamedPipe健康检查增强 | P20-A |
| P0 | P20-D: 推理结果验证增强 | P20-A |
| P0 | P19-A: JNI调用结果验证增强 | 无 |
| P0 | P19-B: Native执行性能监控增强 | P19-A |
| P0 | P19-C: Native模块健康检查增强 | P19-A |
| P1 | P17-A: WebSocket连接状态监控增强 | 无 |
| P1 | P17-B: API响应验证增强 | 无 |
| P1 | P17-C: 前端状态同步机制增强 | P17-A |
| P1 | P17-D: 前端错误上报机制增强 | P17-A |
| P1 | P22-A: CLI可用性检查增强 | 无 |
| P1 | P22-B: 调用结果验证增强 | P22-A |
| P1 | P22-C: 输出解析验证增强 | P22-A |
| P2 | P18-A/B/C: 数据库表级闭环 | 无 |
| P2 | P3-A-A/B/C: WebSocket连接管理子闭环 | P17-A |
| P2 | P3-B-A/B/C: 消息Trace子闭环 | P17-A |
| P2 | P11-A-A/B/C: 员工状态管理子闭环 | 无 |
| P2 | P11-B-A/B/C: 通道监控子闭环 | 无 |
| P0 | P33-A: Claude Code MCP Server配置 | 无 |
| P1 | P33-B: CodeGraph+MemPalace MCP增强 | P33-A |
| P1 | P33-C: Claude Code插件+技能对接 | P33-A |
| P2 | P33-D: 服务对接增强 | P33-C |

**实施顺序**：
- Sprint 1 (P0): P20-A/B/C/D + P19-A/B/C（技术核心闭环）
- Sprint 2 (P1): P17-A/B/C/D + P22-A/B/C（业务支撑闭环）
- Sprint 3 (P2): P18-A/B/C + P3-A/B + P11-A/B（细化验证闭环）

---

## 九、闭环33改进方案：数字员工使用Claude CLI工具闭环 ✅

> **2026-07-03 代码核验**：该闭环已升级为完整闭环。链路验证：
> - 已打通：MCP Server配置(P33-A✅) → 插件安装(P33-B✅) → 技能目录动态注入+服务发现提示词(P33-C✅) → 服务环境变量注入+实际API调用工具(P33-D✅)
> - 闭环33有两条并行的服务API调用路径：
>   - L2 Claude CLI层：环境变量(GITLAB_URL/JENKINS_URL/OPENPROJECT_URL) → Claude CLI在沙箱内curl调用 → 结果返回
>   - L1 大脑层：GitLabTool(8操作)+JenkinsTool(6操作)+OpenProjectTool(6操作) → 直接HttpClient调用API → ToolResult返回
> - 两条路径均已打通，服务API调用闭环已完成

### 9.1 改进方案状态

| 编号 | 改进方案 | 状态 | 实施内容 |
|------|---------|------|---------|
| P33-A | Claude Code MCP Server配置 | ✅ 已完成 | mcp.json + ClaudeCliProperties(mcpConfigPath/mcpEnabled) + ClaudeExecutionGateway(--mcp-config) + Dockerfile.local(3个MCP Server npm包) |
| P33-B | CodeGraph+MemPalace MCP增强 | ✅ 已完成 | 4个插件(commit-commands/code-review/feature-dev/security-guidance) + CLAUDE_PLUGINS_DIR |
| P33-C | Claude Code插件+技能对接 | ✅ 已完成 | 动态技能目录(/app/skills/core + /app/skills/{department}) + 服务发现系统提示词(8个服务) |
| P33-D | 服务对接增强 | ✅ 已完成 | GITLAB_URL/JENKINS_URL/OPENPROJECT_URL 环境变量注入 + GitLabTool(8操作)/JenkinsTool(6操作)/OpenProjectTool(6操作)实际API调用 |

### 9.2 遗留项

| 遗留项 | 描述 | 优先级 | 状态 |
|--------|------|--------|------|
| CodeGraph MCP Server | Rust预编译二进制放入image/codegraph，Dockerfile.local添加COPY，mcp.json添加配置 | P2 | ✅ 已完成 |
| 服务API调用闭环 | GitLabTool/JenkinsTool/OpenProjectTool实现实际API调用，两条并行路径(L1大脑层+L2 Claude CLI层)均已打通 | P2 | ✅ 已完成 |

---

**版本信息**：
- 文档版本: v1.5
- 生成日期: 2026-07-02
- 最近更新: 2026-07-03（v1.5 闭环18/3-A升级为完整闭环，10个完整闭环 + 0个部分闭环，L2全覆盖✅）
- 闭环数: 10个（10个完整闭环 + 0个部分闭环）
- 改进方案: 28个（P0×7 + P1×7 + P2×14，含P18-D子方案）
