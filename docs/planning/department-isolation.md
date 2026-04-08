# 部门页面隔离设计

> 按部门隔离页面路由，API按权限隔离，实现部门群聊功能

---

## 一、设计背景

### 1.1 问题分析

现有系统存在以下问题：

| 问题 | 影响 | 解决方案 |
|------|------|---------|
| 跨部门数据混乱 | 不同部门用户可能访问到其他部门数据 | 部门API隔离 |
| 权限控制不直观 | 用户难以理解自己的权限范围 | 部门专属页面 |
| 缺少部门协作 | 同部门员工无法群聊协作 | 部门群聊频道 |

### 1.2 设计目标

1. **页面隔离**: 不同部门使用不同页面路由
2. **API隔离**: 部门API按权限过滤响应数据
3. **群聊频道**: 每个部门独立的WebSocket频道
4. **董事长专属**: 全局管理页面和API

---

## 二、架构设计

### 2.1 三层页面架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      部门页面隔离架构                                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  【访客层 - 无需登录】                                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  / (Reception)                                                       │   │
│  │  ├── 前台接待页面                                                      │   │
│  │  ├── 公开API: /api/public/*                                          │   │
│  │  └── 使用模型: Qwen3-0.6B (CHAT_ONLY)                                │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  【部门层 - 需要登录 + 部门权限】                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  /dept/tech (技术部)                                                  │   │
│  │  ├── 部门专属页面                                                      │   │
│  │  ├── 部门API: /api/tech/*                                            │   │
│  │  ├── 部门群聊: /ws/dept/tech                                         │   │
│  │  └── 使用大脑: TechBrain                                              │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  【董事长层 - 需要登录 + 董事长身份】                                           │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  /chairman (董事长专属)                                               │   │
│  │  ├── 全局管理页面                                                      │   │
│  │  ├── 管理API: /api/chairman/*                                        │   │
│  │  ├── 员工管理、部门管理、系统配置                                         │   │
│  │  └── 使用大脑: MainBrain + 所有大脑                                    │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 部门路由映射

| 部门 | 前端路由 | API前缀 | WebSocket频道 | 对应大脑 |
|------|---------|---------|--------------|---------|
| 访客 | `/` | `/api/public` | `/ws/public` | Qwen3Neuron |
| 技术部 | `/dept/tech` | `/api/tech` | `/ws/dept/tech` | TechBrain |
| 行政部 | `/dept/admin` | `/api/admin` | `/ws/dept/admin` | AdminBrain |
| 人力资源 | `/dept/hr` | `/api/hr` | `/ws/dept/hr` | HrBrain |
| 财务部 | `/dept/finance` | `/api/finance` | `/ws/dept/finance` | FinanceBrain |
| 销售部 | `/dept/sales` | `/api/sales` | `/ws/dept/sales` | SalesBrain |
| 客服部 | `/dept/cs` | `/api/cs` | `/ws/dept/cs` | CsBrain |
| 法务部 | `/dept/legal` | `/api/legal` | `/ws/dept/legal` | LegalBrain |
| 运营部 | `/dept/ops` | `/api/ops` | `/ws/dept/ops` | OpsBrain |
| 董事长 | `/chairman` | `/api/chairman` | `/ws/chairman` | MainBrain |

---

## 三、后端实现设计

### 3.1 部门API控制器

```java
@RestController
@RequestMapping("/api/{department}")
@RequiredArgsConstructor
public class DepartmentApiController {

    private final DepartmentService departmentService;
    private final BrainRouter brainRouter;

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(
            @PathVariable String department,
            @RequestBody ChatRequest request,
            @AuthenticationPrincipal User user) {
        
        // 1. 验证部门权限
        if (!hasDepartmentAccess(user, department)) {
            return ResponseEntity.status(403).build();
        }
        
        // 2. 路由到对应大脑
        Brain brain = brainRouter.route(department);
        
        // 3. 处理请求
        ChatResponse response = brain.process(request, user);
        
        return ResponseEntity.ok(response);
    }
    
    private boolean hasDepartmentAccess(User user, String department) {
        // 董事长可以访问所有部门
        if (user.getAccessLevel() == AccessLevel.FULL) {
            return true;
        }
        // 检查用户是否属于该部门
        return user.getDepartment().equals(department);
    }
}
```

### 3.2 部门权限拦截器

```java
@Component
public class DepartmentPermissionInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) throws Exception {
        
        // 1. 提取部门参数
        String department = extractDepartment(request.getRequestURI());
        
        // 2. 获取当前用户
        User user = getCurrentUser(request);
        
        // 3. 验证权限
        if (!hasPermission(user, department)) {
            response.sendError(403, "无权访问该部门");
            return false;
        }
        
        return true;
    }
    
    private String extractDepartment(String uri) {
        // /api/tech/chat -> tech
        Matcher matcher = Pattern.compile("/api/(\\w+)/").matcher(uri);
        return matcher.find() ? matcher.group(1) : null;
    }
}
```

### 3.3 部门WebSocket处理器

```java
@Component
public class DepartmentWebSocketHandler extends TextWebSocketHandler {

    private final Map<String, Set<WebSocketSession>> departmentChannels = new ConcurrentHashMap<>();
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String department = extractDepartment(session.getUri());
        User user = getUser(session);
        
        // 验证部门权限
        if (!hasDepartmentAccess(user, department)) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        
        // 加入部门频道
        departmentChannels.computeIfAbsent(department, k -> ConcurrentHashMap.newKeySet())
            .add(session);
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String department = extractDepartment(session.getUri());
        User user = getUser(session);
        
        // 广播给部门所有成员
        broadcast(department, user, message.getPayload());
    }
    
    private void broadcast(String department, User sender, String message) {
        Set<WebSocketSession> sessions = departmentChannels.get(department);
        if (sessions != null) {
            String broadcastMessage = formatMessage(sender, message);
            sessions.forEach(session -> {
                try {
                    session.sendMessage(new TextMessage(broadcastMessage));
                } catch (IOException e) {
                    log.error("广播消息失败", e);
                }
            });
        }
    }
}
```

---

## 四、前端实现设计

### 4.1 路由配置

```typescript
const routes = [
  // 访客路由
  {
    path: '/',
    name: 'Reception',
    component: ReceptionView,
    meta: { guestAccess: true },
  },
  
  // 部门路由
  {
    path: '/dept/:department',
    component: DepartmentLayout,
    meta: { requiresAuth: true },
    beforeEnter: (to, from, next) => {
      const user = useAuthStore().user
      const dept = to.params.department
      
      // 董事长可以访问所有部门
      if (user.accessLevel === 'FULL') {
        next()
        return
      }
      
      // 检查部门权限
      if (user.department !== dept) {
        next({ name: 'Forbidden' })
        return
      }
      
      next()
    },
    children: [
      { path: '', name: 'DeptHome', component: DeptHomeView },
      { path: 'chat', name: 'DeptChat', component: DeptChatView },
      { path: 'knowledge', name: 'DeptKnowledge', component: DeptKnowledgeView },
    ]
  },
  
  // 董事长路由
  {
    path: '/chairman',
    component: ChairmanLayout,
    meta: { requiresAuth: true, requiresChairman: true },
    children: [
      { path: '', name: 'ChairmanHome', component: ChairmanHomeView },
      { path: 'employees', name: 'EmployeeManagement', component: EmployeeManagementView },
      { path: 'settings', name: 'SystemSettings', component: SystemSettingsView },
    ]
  }
]
```

### 4.2 部门群聊Composable

```typescript
export function useDepartmentChat(department: string) {
  const ws = ref<WebSocket | null>(null)
  const messages = ref<ChatMessage[]>([])
  const members = ref<DepartmentMember[]>([])
  
  function connect() {
    const token = useAuthStore().token
    ws.value = new WebSocket(`wss://api.example.com/ws/dept/${department}?token=${token}`)
    
    ws.value.onmessage = (event) => {
      const data = JSON.parse(event.data)
      if (data.type === 'message') {
        messages.value.push(data.payload)
      } else if (data.type === 'member_update') {
        members.value = data.payload
      }
    }
  }
  
  function sendMessage(content: string) {
    ws.value?.send(JSON.stringify({
      type: 'message',
      content
    }))
  }
  
  return { messages, members, connect, sendMessage }
}
```

---

## 五、权限验证流程

### 5.1 API权限验证

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      API权限验证流程                                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  前端请求                                                                    │
│     │                                                                       │
│     ▼                                                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  DepartmentPermissionInterceptor                                     │   │
│  │  ├── 提取部门参数: /api/tech/chat -> tech                           │   │
│  │  ├── 验证Token                                                      │   │
│  │  └── 检查权限                                                       │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│     │                                                                       │
│     ├── 权限验证通过 ──────────────────────────────────────────────────────▶│
│     │                                                                       │
│     └── 权限验证失败                                                         │
│         │                                                                   │
│         ▼                                                                   │
│     返回 403 Forbidden                                                      │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 5.2 WebSocket权限验证

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      WebSocket权限验证流程                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  WebSocket连接请求                                                           │
│     │                                                                       │
│     ▼                                                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  DepartmentWebSocketHandler                                          │   │
│  │  ├── 提取部门参数: /ws/dept/tech -> tech                            │   │
│  │  ├── 验证Token                                                      │   │
│  │  └── 检查部门权限                                                   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│     │                                                                       │
│     ├── 权限验证通过                                                        │
│     │   │                                                                   │
│     │   ▼                                                                   │
│     │   加入部门频道                                                        │
│     │   接收/发送消息                                                       │
│     │                                                                       │
│     └── 权限验证失败                                                         │
│         │                                                                   │
│         ▼                                                                   │
│     关闭连接 (CloseStatus.NOT_ACCEPTABLE)                                   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 六、数据库设计

### 6.1 部门聊天历史表

```sql
CREATE TABLE department_chat_history (
    id BIGSERIAL PRIMARY KEY,
    department VARCHAR(50) NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    message_type VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_dept_created (department, created_at)
);
```

### 6.2 部门成员在线状态表

```sql
CREATE TABLE department_online_status (
    user_id VARCHAR(100) PRIMARY KEY,
    department VARCHAR(50) NOT NULL,
    online BOOLEAN DEFAULT TRUE,
    last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_dept_online (department, online)
);
```

---

## 七、实施计划

### 7.1 开发任务

| 阶段 | 任务 | 预计时间 |
|------|------|---------|
| 1 | 后端API权限隔离 | 2天 |
| 2 | 部门WebSocket频道 | 2天 |
| 3 | 前端部门页面 | 3天 |
| 4 | 部门群聊功能 | 2天 |
| 5 | 董事长专属页面 | 2天 |
| 6 | 测试与调试 | 2天 |

### 7.2 验收标准

- [ ] 访客可以访问前台页面
- [ ] 登录用户只能访问自己部门的页面
- [ ] 董事长可以访问所有部门页面
- [ ] 部门群聊功能正常
- [ ] 跨部门访问被正确阻止
- [ ] API权限过滤正确

---

## 八、相关文档

- [前端框架文档](../../living-agent-frontend/FRONTEND_FRAMEWORK.md)
- [后端框架文档](../PROJECT_FRAMEWORK.md)
- [统一员工模型](./07-unified-employee-model.md)
- [数据库设计](./08-database-design.md)
