# CI/CD Pipeline

> CI/CD流水线技能，自动化构建、测试、部署流程

## 一、技能描述

CI/CD流水线技能使技术部数字员工能够管理持续集成和持续部署流程，包括：
- 流水线创建和管理
- 构建状态监控
- 自动化测试执行
- 部署策略管理

## 二、适用编制

| 编制代码 | 职位 | 主要用途 |
|---------|------|---------|
| T03 | DevOps工程师 | 流水线设计、部署管理 |
| T04 | 运维工程师 | 环境管理、监控告警 |
| T09 | 前端工程师 | 前端构建、静态部署 |
| T10 | 后端工程师 | 服务构建、容器部署 |

## 三、触发词

- CI/CD、流水线、构建
- Jenkins、GitLab CI、GitHub Actions
- 部署、发布、上线
- 自动化测试、单元测试

## 四、支持的平台

### 4.1 CI/CD平台

| 平台 | API支持 | 功能 |
|------|--------|------|
| Jenkins | ✅ | 任务管理、构建触发 |
| GitLab CI | ✅ | 流水线管理、Runner管理 |
| GitHub Actions | ✅ | Workflow管理 |
| ArgoCD | ✅ | GitOps部署 |

### 4.2 部署平台

| 平台 | API支持 | 功能 |
|------|--------|------|
| Kubernetes | ✅ | 部署、扩缩容 |
| Docker | ✅ | 镜像构建、容器管理 |
| 阿里云ACK | ✅ | 托管K8s |
| 腾讯云TKE | ✅ | 托管K8s |

## 五、使用示例

### 5.1 触发构建

```
用户: 触发main分支的构建

技能执行:
1. 调用Jenkins API触发构建
2. 监控构建状态
3. 构建完成后发送通知
4. 返回构建结果和日志链接
```

### 5.2 查看流水线状态

```
用户: 查看今天的构建状态

技能执行:
1. 查询所有流水线最近执行记录
2. 统计成功/失败率
3. 列出失败的构建及原因
4. 返回状态汇总
```

### 5.3 部署到生产环境

```
用户: 将v1.2.0版本部署到生产环境

技能执行:
1. 验证版本是否存在
2. 检查测试是否通过
3. 执行蓝绿部署
4. 健康检查
5. 返回部署结果
```

## 六、流水线模板

### 6.1 标准Java服务流水线

```yaml
stages:
  - name: Build
    steps:
      - mvn clean package -DskipTests
  - name: Test
    steps:
      - mvn test
      - mvn sonar:sonar
  - name: Build Image
    steps:
      - docker build -t app:${BUILD_NUMBER} .
      - docker push registry/app:${BUILD_NUMBER}
  - name: Deploy
    steps:
      - kubectl set image deployment/app app=registry/app:${BUILD_NUMBER}
```

### 6.2 前端项目流水线

```yaml
stages:
  - name: Install
    steps:
      - npm ci
  - name: Build
    steps:
      - npm run build
  - name: Test
    steps:
      - npm run test
      - npm run lint
  - name: Deploy
    steps:
      - aws s3 sync dist/ s3://bucket/
```

## 七、部署策略

| 策略 | 说明 | 适用场景 |
|------|------|---------|
| 滚动更新 | 逐步替换Pod | 常规发布 |
| 蓝绿部署 | 新旧版本并行 | 重要发布 |
| 金丝雀发布 | 小流量测试 | 风险发布 |
| A/B测试 | 流量分流 | 功能验证 |

## 八、监控告警

### 8.1 构建监控

| 指标 | 告警阈值 |
|------|---------|
| 构建失败率 | > 10% |
| 构建时长 | > 30分钟 |
| 队列等待 | > 10分钟 |

### 8.2 部署监控

| 指标 | 告警阈值 |
|------|---------|
| 部署失败 | 立即告警 |
| 回滚次数 | 24小时内>2次 |
| 错误率上升 | > 基线2倍 |

## 九、配置要求

```yaml
cicd:
  jenkins:
    url: ${JENKINS_URL}
    user: ${JENKINS_USER}
    token: ${JENKINS_TOKEN}
  
  gitlab:
    url: ${GITLAB_URL}
    token: ${GITLAB_TOKEN}
  
  kubernetes:
    kubeconfig: ${KUBECONFIG_PATH}
  
  notification:
    webhook: ${SLACK_WEBHOOK}
    email: devops@company.com
```

---

*来源: 基于 ClawHub automation-workflows 和 github 技能扩展*
*更新时间: 2026-03-13*
