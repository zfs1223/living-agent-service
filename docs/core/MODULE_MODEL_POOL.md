# 模型池模块

> 版本：2026-05-18 | 路径：living-agent-core/model/pool/

## 核心组件

| 组件 | 说明 |
|------|------|
| `ModelPoolManager` | 模型池管理器（Provider/Model CRUD） |
| `BrainModelResolver` | 根据大脑/员工解析模型 |
| `ModelHealthRegistry` | 模型健康状态/熔断 |
| `BrainModelAssigner` | 大脑模型分配 |
| `ModelCapabilityAssessor` | 模型能力评定（60% 能力 + 40% 性能加权） |
| `ModelPerformanceAssessor` | 模型性能测试（实际调用验证） |

## 模型状态

| 状态 | 说明 | 影响 |
|------|------|------|
| AVAILABLE | 可用 | 正常调度 |
| DEGRADED | 降级 | 低优先级使用 |
| COOLDOWN | 冷却中 | 临时不可用 |
| UNAVAILABLE | 不可用 | 排除调度 |
| UNKNOWN | 未知 | 尚无调用记录的初始状态 |

## ModelPoolManager 核心逻辑

```java
@PostConstruct
init() {
    1. 评估已有模型能力（modelCapabilityAssessor.assessModels()）
    2. 异步执行性能测试（延迟 15 秒，虚拟线程中执行）
       - modelPerformanceAssessor.assessAllEnabledModels()
       - 不可用模型自动 setEnabled(false)
}

addProvider(ProviderConfig config) {
    1. 保存配置
    2. 如果 autoDiscoverModels=true，触发模型发现
}

discoverModels(ProviderConfig provider) {
    1. 调用 Ollama /api/tags 接口动态发现
    2. 过滤内置模型
    3. 保存新模型
}

seedDefaults() {
    1. 创建默认 Provider（Ollama/ModelScope 等）
    2. 导入内置模型目录
    3. 评估模型能力
}
```

## BrainModelResolver 核心逻辑

```java
// BrainModelResolver.java
resolve(String brainId) {
    1. 查询大脑模型分配（BrainModelAssignment）
    2. 如果有分配，使用分配的模型
    3. 否则通过 BrainModelSelector 选择
    4. 检查模型健康状态
    5. 如果不可用，尝试备用模型
}

resolveDefault(String brainId) {
    // 返回大脑默认模型（不经过选择器）
}

resolveForEmployee(String employeeId, String departmentId, String departmentBrainId) {
    1. 确定员工角色和任务类型
    2. 按能力匹配最佳模型
    3. 优先使用部门分配的员工模型
}

resolveRaw(String providerId, String modelName) {
    // 直接按 Provider + 模型名解析
}
```

## 模型健康检查

```java
// ModelHealthRegistry.java
recordSuccess(String modelId, String providerId, long latencyMs) {
    1. 更新成功计数
    2. 更新平均延迟
    3. 重置连续失败计数
}

recordFailure(String modelId, String providerId, String failureReason) {
    1. 更新失败计数
    2. 记录失败原因
    3. 如果连续失败过多，触发熔断（COOLDOWN）
}

getHealth(String modelId) → ModelHealthRecord
isModelAvailable(String modelId) → boolean
getAvailableModels() → List<String>
getHealthSummary() → Map<String, ModelHealthRecord>
```

## 内置模型目录

```java
// BuiltinModelCatalog.java
// 本地 Ollama 模型通过 API 动态发现（/api/tags）
// 云端模型由用户通过前端手动添加
// 不硬编码任何模型名称
getAllModels() {
    return discoverOllamaModels(ollamaBaseUrl);
}
```

## 代码路径

```
model/
├── pool/
│   ├── ModelPoolManager.java
│   ├── BrainModelResolver.java
│   ├── ModelHealthRegistry.java
│   ├── BrainModelAssigner.java
│   ├── ResolvedBrainModel.java
│   ├── ModelCapabilityAssessor.java
│   ├── ModelPerformanceAssessor.java
│   ├── BuiltinModelCatalog.java
│   ├── impl/
│   │   ├── ModelCapabilityAssessorImpl.java
│   │   └── ModelPerformanceAssessorImpl.java
│   └── client/
│       ├── LlmClient.java
│       ├── AnthropicClient.java
│       └── OpenAiCompatibleClient.java
├── LlmModel.java
├── ProviderConfig.java
└── selector/
    ├── BrainModelSelector.java
    └── impl/
        ├── TechBrainModelSelector.java
        ├── FinanceBrainModelSelector.java
        └── *.java
```

## 快速定位

| 需求 | 文件 |
|------|------|
| 添加新模型 | `ModelPoolManager.addModel()` |
| 修改模型健康检查 | `ModelHealthRegistry.java` |
| 修改大脑模型分配 | `BrainModelAssigner.java` |
| 添加新 Provider | `ModelPoolManager.addProvider()` |
| 查看模型列表 | `ModelPoolManager.getAllModels()` |
| 修改模型能力评定 | `ModelCapabilityAssessorImpl.java` |
| 修改模型性能测试 | `ModelPerformanceAssessorImpl.java` |
