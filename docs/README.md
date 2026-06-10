# Living Agent Service 文档中心

> 版本：2026-05-15

## 核心模块文档（落地指南）

| 模块 | 文档 | 核心内容 |
|------|------|----------|
| **自治编排** | [core/MODULE_AUTONOMY_ORCHESTRATION.md](core/MODULE_AUTONOMY_ORCHESTRATION.md) | 意图分析→任务规划→员工分派→任务执行 |
| **大脑** | [core/MODULE_BRAIN.md](core/MODULE_BRAIN.md) | 9个大脑 + ReAct执行循环 |
| **员工** | [core/MODULE_EMPLOYEE.md](core/MODULE_EMPLOYEE.md) | 32人编制 + 能力验证 |
| **模型池** | [core/MODULE_MODEL_POOL.md](core/MODULE_MODEL_POOL.md) | 模型健康 + 熔断机制 |
| **网关** | [core/MODULE_GATEWAY.md](core/MODULE_GATEWAY.md) | WebSocket通道 + 权限检查 |
| **知识记忆** | [core/MODULE_KNOWLEDGE_MEMORY.md](core/MODULE_KNOWLEDGE_MEMORY.md) | 三层知识库 + 晋升机制 |
| **工具技能** | [core/MODULE_TOOL_SKILL.md](core/MODULE_TOOL_SKILL.md) | 76个技能 + 工具分类 |
| **安全** | [core/MODULE_SECURITY.md](core/MODULE_SECURITY.md) | 访问级别 + 沙箱执行 |

## 总览文档

| 文档 | 说明 |
|------|------|
| [ENTERPRISE_LIFEBODY_BUSINESS_FLOW.md](ENTERPRISE_LIFEBODY_BUSINESS_FLOW.md) | 企业生命体完整业务流程 |
| [CODE_STRUCTURE_AND_FILE_GUIDE.md](CODE_STRUCTURE_AND_FILE_GUIDE.md) | 代码结构详细索引 |

## 参考文档

| 文档 | 说明 |
|------|------|
| [权限与入口矩阵.md](权限与入口矩阵.md) | 权限入口设计 |
| [MODEL_RESPONSIBILITY_AND_EXECUTION_OPTIMIZATION_PLAN.md](MODEL_RESPONSIBILITY_AND_EXECUTION_OPTIMIZATION_PLAN.md) | 实施计划 |

## 快速导航

### 想改什么？
| 需求 | 文档 |
|------|------|
| 修改意图分类规则 | `MODULE_AUTONOMY_ORCHESTRATION.md` |
| 修改 ReAct 执行逻辑 | `MODULE_BRAIN.md` |
| 添加新员工 | `MODULE_EMPLOYEE.md` |
| 修改模型健康检查 | `MODULE_MODEL_POOL.md` |
| 修改 WebSocket 入口 | `MODULE_GATEWAY.md` |
| 修改知识晋升逻辑 | `MODULE_KNOWLEDGE_MEMORY.md` |
| 添加新工具 | `MODULE_TOOL_SKILL.md` |
| 修改权限检查 | `MODULE_SECURITY.md` |
