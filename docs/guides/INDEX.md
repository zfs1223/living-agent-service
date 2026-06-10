# 开发指南索引

> 本目录包含 Living Agent 的开发指南和验收标准文档。
> 版本：2026-05-20

---

## 文档列表

| 文档 | 说明 |
| --- | --- |
| [END_TO_END_ACCEPTANCE.md](./END_TO_END_ACCEPTANCE.md) | 端到端验收标准 |
| [DEVELOPMENT_ORDER.md](./DEVELOPMENT_ORDER.md) | 推荐开发顺序 |
| [CODE_FILE_GUIDE.md](./CODE_FILE_GUIDE.md) | 代码文件落点指南 |
| [TROUBLESHOOTING.md](./TROUBLESHOOTING.md) | 问题排查指南 |

---

## 快速参考

### 端到端链路

```text
用户请求
  -> 主脑识别意图并拆解任务
  -> 路由到技术部门
  -> 部门大脑选择数字员工和模型
  -> 员工执行并回执
  -> 回执聚合和 completion gate 验收
  -> 生成真实 artifact 文件
  -> 主脑/响应编排器总结交付
  -> 知识、绩效、产物记录沉淀
```

### 验收样例

以"帮我做一个红色小球跳动的网页"为验收样例。

---

## 相关文档

| 文档 | 说明 |
| --- | --- |
| `docs/core/INDEX.md` | 核心定义文档索引 |
| `docs/implemented/INDEX.md` | 已完成方案索引 |
| `docs/pending/INDEX.md` | 待实施方案索引 |
