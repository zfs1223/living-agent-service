# Knowledge Graph

> 知识图谱技能，结构化记忆和实体关系管理

## 一、技能描述

知识图谱技能使数字员工能够创建和管理结构化知识，包括：
- 实体创建和查询
- 关系建立和管理
- 知识推理
- 记忆持久化

## 二、适用编制

| 编制代码 | 职位 | 主要用途 |
|---------|------|---------|
| T02 | 架构师 | 系统架构知识管理 |
| O01 | 数据分析师 | 数据关系分析 |
| A02 | 文档管理员 | 文档知识管理 |

## 三、触发词

- 知识图谱、knowledge graph
- 实体、关系、ontology
- 记忆、memory
- 人、项目、任务、事件

## 四、实体类型

### 4.1 内置实体类型

| 类型 | 说明 | 属性示例 |
|------|------|---------|
| Person | 人员 | name, email, role, department |
| Project | 项目 | name, status, start_date, end_date |
| Task | 任务 | title, status, assignee, due_date |
| Event | 事件 | name, date, location, participants |
| Document | 文档 | title, type, author, created_at |
| Organization | 组织 | name, type, parent |

### 4.2 自定义实体

```json
{
  "type": "Customer",
  "properties": {
    "name": { "type": "string", "required": true },
    "industry": { "type": "string" },
    "size": { "type": "string", "enum": ["small", "medium", "large"] },
    "contact": { "type": "string" }
  }
}
```

## 五、关系类型

| 关系 | 说明 | 示例 |
|------|------|------|
| works_for | 工作于 | Person → Organization |
| manages | 管理 | Person → Person |
| belongs_to | 属于 | Task → Project |
| depends_on | 依赖 | Task → Task |
| participates_in | 参与 | Person → Project |
| located_in | 位于 | Event → Location |

## 六、使用示例

### 6.1 创建实体

```
用户: 记录张三是技术部的工程师

技能执行:
1. 创建Person实体: 张三
2. 创建Organization实体: 技术部
3. 建立works_for关系
4. 返回实体ID
```

### 6.2 查询关系

```
用户: 查询张三参与的所有项目

技能执行:
1. 查找Person实体: 张三
2. 遍历participates_in关系
3. 返回项目列表
```

### 6.3 推理查询

```
用户: 谁可能知道这个项目的细节？

技能执行:
1. 查找Project实体
2. 查找参与人员
3. 查找管理人员
4. 返回相关人员列表
```

## 七、查询语法

```cypher
// 创建实体
CREATE (p:Person {name: "张三", role: "工程师"})
CREATE (d:Organization {name: "技术部"})
CREATE (p)-[:works_for]->(d)

// 查询
MATCH (p:Person)-[:works_for]->(d:Organization {name: "技术部"})
RETURN p.name

// 推理
MATCH (p:Person)-[:participates_in]->(proj:Project {name: "项目A"})
MATCH (p)-[:works_with]->(colleague:Person)
RETURN DISTINCT colleague.name
```

## 八、存储后端

| 后端 | 说明 | 适用场景 |
|------|------|---------|
| SQLite | 本地文件存储 | 单机部署 |
| PostgreSQL | 关系数据库 | 生产环境 |
| Neo4j | 图数据库 | 复杂图查询 |
| Qdrant | 向量数据库 | 语义搜索 |

## 九、配置要求

```yaml
knowledge_graph:
  storage:
    type: postgresql  # sqlite/postgresql/neo4j/qdrant
    
    postgresql:
      url: jdbc:postgresql://localhost:5432/knowledge
      user: ${DB_USER}
      password: ${DB_PASSWORD}
  
  entities:
    auto_create: true
    strict_typing: true
  
  relations:
    bidirectional: true
  
  inference:
    enabled: true
    max_depth: 3
```

---

*来源: 基于 ClawHub ontology 技能转换*
*更新时间: 2026-03-13*
