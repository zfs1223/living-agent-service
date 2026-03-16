---
name: mcp-client
description: MCP 客户端技能 - 连接和使用 MCP (Model Context Protocol) 服务器提供的工具和资源
metadata: { "brain": "core", "category": "protocol" }
---

# MCP 客户端技能

连接 MCP (Model Context Protocol) 服务器，使用其提供的工具、资源和提示词。

## 适用场景

- 连接外部 MCP 服务器获取工具能力
- 访问 MCP 服务器提供的资源
- 使用 MCP 提示词模板
- 动态扩展智能体能力

## MCP 协议概述

MCP 是一种标准协议，用于 LLM 与外部服务之间的交互：

```
┌─────────────┐     MCP Protocol     ┌─────────────┐
│   Client    │ ◄──────────────────► │   Server    │
│ (智能体)     │                      │ (外部服务)   │
└─────────────┘                      └─────────────┘
```

### 核心功能

| 功能 | 说明 |
|------|------|
| Tools | 可调用的工具函数 |
| Resources | 可读取的资源内容 |
| Prompts | 预定义的提示词模板 |

## 配置要求

在 `application.yml` 中配置 MCP 服务器：

```yaml
mcp:
  servers:
    - name: filesystem
      url: ${MCP_FILESYSTEM_URL:}
      enabled: true
    - name: database
      url: ${MCP_DATABASE_URL:}
      enabled: true
```

## 常用操作

### 列出可用工具

```bash
mcp-client list-tools --server filesystem
```

输出示例：
```
Tool: read_file
  Description: Read the contents of a file
  Parameters:
    - path (string, required): File path to read

Tool: write_file
  Description: Write content to a file
  Parameters:
    - path (string, required): File path to write
    - content (string, required): Content to write
```

### 调用工具

```bash
mcp-client call-tool --server filesystem --tool read_file --args '{"path": "/data/report.txt"}'
```

### 列出资源

```bash
mcp-client list-resources --server filesystem
```

### 读取资源

```bash
mcp-client read-resource --server filesystem --uri "file:///data/config.json"
```

### 获取提示词

```bash
mcp-client get-prompt --server assistant --name analyze-code --args '{"language": "python"}'
```

## 企业应用场景

### 文件系统集成

连接文件系统 MCP 服务器，实现：
- 读取/写入文件
- 目录浏览
- 文件搜索

### 数据库访问

连接数据库 MCP 服务器，实现：
- 执行 SQL 查询
- 数据分析
- 报表生成

### API 集成

连接 API MCP 服务器，实现：
- 外部服务调用
- 数据同步
- 自动化流程

## 安全注意事项

1. MCP 服务器需要认证
2. 工具调用需要权限验证
3. 敏感操作需要审批
4. 所有调用记录审计日志

## 与 mcp-builder 技能的关系

- **mcp-builder**: 用于创建 MCP 服务器
- **mcp-client**: 用于连接和使用 MCP 服务器

## 依赖

- MCP 服务器实例
- 网络连接
- 认证凭据
