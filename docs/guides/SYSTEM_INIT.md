# 系统初始化设计

## 概述

本文档描述了 Living Agent Service 的系统初始化流程，包括董事长注册、数据库持久化和用户画像创建。

## 初始化流程

```
┌─────────────────────────────────────────────────────────────┐
│                     系统启动                                 │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│              FounderService.initialize()                    │
│         检查数据库是否存在董事长记录                          │
│         SELECT COUNT(*) > 0 FROM enterprise_employees       │
│         WHERE is_founder = true                             │
└─────────────────────────────────────────────────────────────┘
                              ↓
              ┌───────────────┴───────────────┐
              ↓                               ↓
┌─────────────────────────┐     ┌─────────────────────────┐
│    已有董事长            │     │    无董事长              │
│    hasFounder = true    │     │    isFirstUser = true   │
│    系统正常运行          │     │    显示注册页面          │
└─────────────────────────┘     └─────────────────────────┘
                                              ↓
                              ┌───────────────────────────────┐
                              │      用户提交注册表单          │
                              │      POST /api/system/register│
                              └───────────────────────────────┘
                                              ↓
                              ┌───────────────────────────────┐
                              │   EnterpriseEmployeeService   │
                              │   .createEmployee(founder)    │
                              │   INSERT INTO enterprise_     │
                              │   employees (...)             │
                              └───────────────────────────────┘
                                              ↓
                              ┌───────────────────────────────┐
                              │   数据库持久化完成             │
                              │   返回 sessionId              │
                              │   系统初始化完成               │
                              └───────────────────────────────┘
```

## 数据持久化

### 董事长注册数据存储

| 字段 | 值 | 说明 |
|------|-----|------|
| `employee_id` | `founder_xxxxxxxx` | 自动生成的唯一ID |
| `name` | 用户输入 | 董事长姓名 |
| `email` | 用户输入 | 邮箱地址 |
| `identity` | `INTERNAL_CHAIRMAN` | 身份标识 |
| `access_level` | `FULL` | 最高访问权限 |
| `is_founder` | `true` | 董事长标志 |
| `position` | `董事长` | 职位 |
| `active` | `true` | 激活状态 |
| `join_date` | `CURRENT_TIMESTAMP` | 入职时间 |

### 数据库表结构

```sql
-- enterprise_employees 表
CREATE TABLE IF NOT EXISTS enterprise_employees (
    employee_id VARCHAR(100) PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    phone VARCHAR(20),
    email VARCHAR(128),
    department_id VARCHAR(64),
    department_name VARCHAR(128),
    position VARCHAR(64),
    identity VARCHAR(32) NOT NULL DEFAULT 'INTERNAL_ACTIVE',
    access_level VARCHAR(16) NOT NULL DEFAULT 'DEPARTMENT',
    is_founder BOOLEAN DEFAULT FALSE,
    voice_print_id VARCHAR(64),
    oauth_provider VARCHAR(32),
    oauth_user_id VARCHAR(128),
    avatar_url VARCHAR(512),
    join_date TIMESTAMP WITH TIME ZONE,
    leave_date TIMESTAMP WITH TIME ZONE,
    active BOOLEAN DEFAULT TRUE,
    sync_source VARCHAR(32),
    last_sync_time TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
```

## 核心服务

### EnterpriseEmployeeService

统一员工数据持久化服务，负责所有员工数据的 CRUD 操作：

```java
@Service
@Transactional
public class EnterpriseEmployeeService {
    
    // 创建员工（持久化到数据库）
    public Employee createEmployee(Employee employee);
    
    // 更新员工信息
    public Employee updateEmployee(Employee employee);
    
    // 查找员工
    public Optional<Employee> findById(String employeeId);
    public Optional<Employee> findByPhone(String phone);
    public Optional<Employee> findByEmail(String email);
    
    // 检查董事长状态
    public boolean hasAnyEmployee();
    public boolean hasFounder();
    
    // 设置声纹ID
    public void setVoicePrintId(String employeeId, String voicePrintId);
    
    // 关联OAuth账号
    public void linkOAuthAccount(String employeeId, String provider, String oauthUserId);
}
```

### FounderService

董事长状态检查服务，启动时从数据库加载状态：

```java
public class FounderService {
    
    private final FounderCheckStrategy checkStrategy;
    private final AtomicBoolean founderExists = new AtomicBoolean(false);
    
    // 初始化时检查数据库
    public synchronized void initialize() {
        founderExists.set(checkStrategy.hasFounder());
    }
    
    // 检查是否已有董事长
    public boolean hasFounder() {
        initialize();
        return founderExists.get();
    }
    
    // 检查是否是第一个用户
    public boolean isFirstUser() {
        return !hasFounder() && !checkStrategy.hasAnyEmployee();
    }
    
    // 标记董事长已注册
    public void markFounderRegistered() {
        founderExists.set(true);
    }
}
```

### FounderCheckStrategy

数据库检查策略接口：

```java
public interface FounderCheckStrategy {
    boolean hasAnyEmployee();
    boolean hasFounder();
}

// 实现 - 使用 JPA Repository
@Bean
public FounderService founderService(EnterpriseEmployeeRepository repo) {
    return new FounderService(new FounderCheckStrategy() {
        @Override
        public boolean hasAnyEmployee() {
            return repo.hasAnyEmployee();
        }
        
        @Override
        public boolean hasFounder() {
            return repo.hasFounder();
        }
    });
}
```

## 用户画像

### SpeakerProfileService

用户画像服务，管理声纹相关的用户画像：

```java
public class SpeakerProfileService {
    
    // 创建用户画像
    public SpeakerProfile createProfile(String speakerId, String name, float[] embedding);
    
    // 查找画像
    public Optional<SpeakerProfile> findBySpeakerId(String speakerId);
    public Optional<SpeakerProfile> findByName(String name);
    
    // 更新画像
    public SpeakerProfile updateProfile(String speakerId, float[] newEmbedding);
    
    // 更新匹配统计
    public void updateLastMatched(String speakerId);
}
```

### speaker_profiles 表

```sql
CREATE TABLE IF NOT EXISTS speaker_profiles (
    speaker_id VARCHAR(100) PRIMARY KEY,
    name VARCHAR(100),
    embedding BYTEA,
    embedding_dimension INTEGER DEFAULT 192,
    employee_id VARCHAR(100),
    active BOOLEAN DEFAULT TRUE,
    match_count INTEGER DEFAULT 0,
    last_matched_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    metadata VARCHAR(2000)
);
```

## 验证方法

### 使用 Navicat Premium Lite 17

1. **连接数据库**
   - 主机: `localhost`
   - 端口: `5432`
   - 数据库: `livingagent`
   - 用户名: `livingagent`
   - 密码: `livingagent123`

2. **查询董事长数据**
   ```sql
   -- 查看所有董事长
   SELECT employee_id, name, email, identity, access_level, is_founder, created_at
   FROM enterprise_employees
   WHERE is_founder = true;
   
   -- 查看所有员工
   SELECT employee_id, name, identity, access_level, active, created_at
   FROM enterprise_employees
   ORDER BY created_at DESC;
   
   -- 查看声纹画像
   SELECT speaker_id, name, employee_id, match_count, created_at
   FROM speaker_profiles;
   ```

3. **验证数据持久化**
   ```sql
   -- 重启服务后执行
   SELECT COUNT(*) FROM enterprise_employees WHERE is_founder = true;
   -- 应该返回 1（如果已注册董事长）
   ```

## 重启后数据保留

| 数据类型 | 存储位置 | 重启后状态 |
|---------|---------|-----------|
| 董事长信息 | PostgreSQL | ✅ 保留 |
| 员工信息 | PostgreSQL | ✅ 保留 |
| 声纹画像 | PostgreSQL | ✅ 保留 |
| 会话缓存 | 内存 | ❌ 清除（需重新登录） |

## 配置

### application.yml

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/livingagent
    username: livingagent
    password: livingagent123
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.PostgreSQLDialect
```

## API 端点

### 系统状态

```http
GET /api/system/status

Response:
{
  "success": true,
  "data": {
    "hasFounder": true,
    "isFirstUser": false,
    "isConfigured": true,
    "configuredProviders": ["dingtalk", "feishu"]
  }
}
```

### 董事长注册

```http
POST /api/system/register
Content-Type: application/json

{
  "name": "张三",
  "email": "zhangsan@example.com",
  "companyName": "示例公司"
}

Response:
{
  "success": true,
  "data": {
    "employeeId": "founder_abc12345",
    "name": "张三",
    "identity": "INTERNAL_CHAIRMAN",
    "accessLevel": "FULL",
    "sessionId": "sess_xxxxxxxx"
  }
}
```

## 错误处理

| 错误码 | 说明 |
|-------|------|
| `already_registered` | 系统已有董事长，无法重复注册 |
| `invalid_name` | 姓名不能为空 |
| `not_initialized` | 系统尚未初始化 |
| `database_error` | 数据库操作失败 |

## 安全考虑

1. **注册限制**: 只允许第一个用户注册为董事长
2. **权限隔离**: 董事长拥有 FULL 权限，可管理所有资源
3. **会话管理**: 注册成功后自动创建会话，无需再次登录
4. **数据加密**: 敏感字段（如声纹向量）使用 BYTEA 存储
