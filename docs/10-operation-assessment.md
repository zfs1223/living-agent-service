# 公司运营评判与绩效考核系统

> 自主运行的公司运营监控与员工绩效管理系统

---

## 一、系统概述

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    公司运营评判系统架构                                        │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  核心理念: 数据驱动、自主运行、实时透明                                        │
│                                                                             │
│  系统组成:                                                                   │
│  ├── 运营指标体系 - 公司级/部门级/个人级三级指标                              │
│  ├── 绩效考核系统 - 自动采集、计算、评级                                      │
│  ├── 自主运行机制 - 定时任务、事件驱动、智能预警                              │
│  └── CEO仪表盘 - 实时数据、趋势分析、决策支持                                 │
│                                                                             │
│  数据来源:                                                                   │
│  ├── 业务系统 (GitLab/Jira/钉钉/飞书/ERP/CRM)                               │
│  ├── 数字员工 (自动记录工作产出)                                             │
│  ├── 真实员工 (手动填报/系统采集)                                            │
│  └── 外部数据 (市场/客户/供应商)                                             │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 二、公司运营指标体系

### 2.1 公司级指标

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    公司级运营指标                                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  【财务指标】                                                                │
│  ├── 营业收入 (月度/季度/年度)                                               │
│  ├── 净利润率                                                               │
│  ├── 现金流状况                                                             │
│  ├── 成本控制率                                                             │
│  └── 预算执行率                                                             │
│                                                                             │
│  【业务指标】                                                                │
│  ├── 订单量/签约额                                                          │
│  ├── 客户增长率                                                             │
│  ├── 客户留存率                                                             │
│  ├── 市场占有率                                                             │
│  └── 项目交付率                                                             │
│                                                                             │
│  【效率指标】                                                                │
│  ├── 人均产出                                                               │
│  ├── 资源利用率                                                             │
│  ├── 流程效率                                                               │
│  └── 数字化程度                                                             │
│                                                                             │
│  【风险指标】                                                                │
│  ├── 合规风险指数                                                           │
│  ├── 运营风险指数                                                           │
│  ├── 财务风险指数                                                           │
│  └── 安全风险指数                                                           │
│                                                                             │
│  【创新指标】                                                                │
│  ├── 新产品/服务数量                                                        │
│  ├── 专利/知识产权                                                          │
│  ├── 技术创新投入比                                                         │
│  └── 数字化转型进度                                                         │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 部门级指标

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    部门级运营指标                                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  【技术部 TechBrain】                                                        │
│  ├── 代码提交量/代码质量                                                     │
│  ├── Bug修复率/Bug引入率                                                    │
│  ├── 项目按时交付率                                                         │
│  ├── 系统稳定性 (SLA)                                                       │
│  ├── 技术债务处理率                                                         │
│  └── 技术创新贡献                                                           │
│                                                                             │
│  【销售部 SalesBrain】                                                       │
│  ├── 销售额/签约额                                                          │
│  ├── 新客户开发数量                                                         │
│  ├── 客户转化率                                                             │
│  ├── 销售周期                                                               │
│  ├── 客户满意度                                                             │
│  └── 市场拓展贡献                                                           │
│                                                                             │
│  【人力资源 HrBrain】                                                        │
│  ├── 招聘完成率                                                             │
│  ├── 员工留存率                                                             │
│  ├── 培训覆盖率                                                             │
│  ├── 员工满意度                                                             │
│  ├── 绩效考核完成率                                                         │
│  └── 人才梯队建设                                                           │
│                                                                             │
│  【财务部 FinanceBrain】                                                     │
│  ├── 账务处理及时率                                                         │
│  ├── 财务报表准确率                                                         │
│  ├── 成本控制达成率                                                         │
│  ├── 预算执行偏差率                                                         │
│  ├── 审计问题整改率                                                         │
│  └── 税务合规率                                                             │
│                                                                             │
│  【客服部 CsBrain】                                                          │
│  ├── 工单处理量                                                             │
│  ├── 工单解决率                                                             │
│  ├── 平均响应时间                                                           │
│  ├── 客户满意度                                                             │
│  ├── 投诉处理率                                                             │
│  └── 知识库贡献                                                             │
│                                                                             │
│  【行政部 AdminBrain】                                                       │
│  ├── 行政服务满意度                                                         │
│  ├── 资产管理效率                                                           │
│  ├── 会议组织效率                                                           │
│  ├── 文档处理及时率                                                         │
│  └── 成本节约贡献                                                           │
│                                                                             │
│  【法务部 LegalBrain】                                                       │
│  ├── 合同审查及时率                                                         │
│  ├── 法律风险预警率                                                         │
│  ├── 合规检查完成率                                                         │
│  ├── 纠纷处理成功率                                                         │
│  └── 知识产权保护                                                           │
│                                                                             │
│  【运营部 OpsBrain】                                                         │
│  ├── 运营活动执行率                                                         │
│  ├── 数据分析产出                                                           │
│  ├── 用户增长贡献                                                           │
│  ├── 内容产出量                                                             │
│  └── 跨部门协作贡献                                                         │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.3 个人级指标

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    个人级绩效指标                                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  【通用指标 - 所有员工】                                                      │
│  ├── 出勤率                                                                 │
│  ├── 工作日志完成率                                                         │
│  ├── 协作响应时间                                                           │
│  ├── 知识分享贡献                                                           │
│  └── 流程合规率                                                             │
│                                                                             │
│  【技术岗指标】                                                              │
│  ├── 代码提交量                                                             │
│  ├── 代码审查参与度                                                         │
│  ├── Bug修复数量                                                            │
│  ├── 技术文档产出                                                           │
│  ├── 项目贡献度                                                             │
│  └── 技术学习进度                                                           │
│                                                                             │
│  【销售岗指标】                                                              │
│  ├── 销售业绩                                                               │
│  ├── 客户拜访量                                                             │
│  ├── 商机转化率                                                             │
│  ├── 客户维护率                                                             │
│  └── 市场信息贡献                                                           │
│                                                                             │
│  【管理岗指标】                                                              │
│  ├── 团队目标达成率                                                         │
│  ├── 团队满意度                                                             │
│  ├── 人才培养贡献                                                           │
│  ├── 跨部门协作                                                             │
│  └── 决策效率                                                               │
│                                                                             │
│  【支持岗指标】                                                              │
│  ├── 服务响应时间                                                           │
│  ├── 服务满意度                                                             │
│  ├── 问题解决率                                                             │
│  ├── 流程优化贡献                                                           │
│  └── 知识库贡献                                                             │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 三、绩效考核系统设计

### 3.1 绩效模型

```java
// 绩效考核模型
public class PerformanceAssessment {
    // 基本信息
    private String assessmentId;
    private String employeeId;           // 员工ID (真实/数字)
    private String employeeName;
    private String department;
    private String position;
    
    // 考核周期
    private AssessmentPeriod period;     // DAILY/WEEKLY/MONTHLY/QUARTERLY/YEARLY
    private LocalDate startDate;
    private LocalDate endDate;
    
    // 指标得分
    private List<IndicatorScore> indicatorScores;
    private double totalScore;           // 总分 (0-100)
    private String grade;                // 等级 (S/A/B/C/D)
    
    // 自动采集数据
    private AutoCollectedData autoData;
    
    // 人工评价
    private List<ManualEvaluation> manualEvaluations;
    
    // 综合评价
    private String summary;
    private List<String> strengths;      // 优点
    private List<String> improvements;   // 待改进
    
    // 时间戳
    private Instant generatedAt;
    private Instant reviewedAt;
}

// 指标得分
public class IndicatorScore {
    private String indicatorId;
    private String indicatorName;
    private String category;             // 财务/业务/效率/创新
    
    // 目标与实际
    private double targetValue;
    private double actualValue;
    private double achievementRate;      // 达成率
    
    // 得分
    private double weight;               // 权重
    private double score;                // 得分 (0-100)
    
    // 数据来源
    private String dataSource;           // AUTO/MANUAL/HYBRID
    private String sourceSystem;         // GitLab/Jira/钉钉/...
    
    // 趋势
    private TrendDirection trend;        // UP/DOWN/STABLE
}

// 自动采集数据
public class AutoCollectedData {
    // 系统数据
    private Map<String, Object> systemData;
    
    // 数字员工记录
    private List<DigitalEmployeeRecord> digitalRecords;
    
    // 业务系统数据
    private Map<String, BusinessSystemData> businessData;
    
    // 采集时间
    private Instant collectedAt;
}

// 考核等级
public enum PerformanceGrade {
    S("卓越", 95, 100),
    A("优秀", 85, 94),
    B("良好", 70, 84),
    C("合格", 60, 69),
    D("待改进", 0, 59);
    
    private String description;
    private int minScore;
    private int maxScore;
}
```

### 3.2 绩效计算引擎

```java
// 绩效计算引擎
@Service
public class PerformanceCalculationEngine {
    
    @Autowired private IndicatorRegistry indicatorRegistry;
    @Autowired private DataCollectionService dataCollectionService;
    @Autowired private WeightConfigurationService weightService;
    
    // 计算个人绩效
    public PerformanceAssessment calculateEmployeePerformance(
            String employeeId, 
            AssessmentPeriod period,
            LocalDate startDate, 
            LocalDate endDate) {
        
        // 1. 获取员工信息
        Employee employee = employeeService.getEmployee(employeeId);
        
        // 2. 确定适用指标
        List<Indicator> indicators = indicatorRegistry.getIndicatorsForPosition(
            employee.getPosition(), 
            employee.getDepartment()
        );
        
        // 3. 自动采集数据
        AutoCollectedData autoData = dataCollectionService.collectData(
            employeeId, 
            indicators, 
            startDate, 
            endDate
        );
        
        // 4. 计算各指标得分
        List<IndicatorScore> scores = new ArrayList<>();
        for (Indicator indicator : indicators) {
            IndicatorScore score = calculateIndicatorScore(indicator, autoData, period);
            scores.add(score);
        }
        
        // 5. 加权计算总分
        double totalScore = calculateWeightedScore(scores);
        
        // 6. 确定等级
        String grade = determineGrade(totalScore);
        
        // 7. 生成综合评价
        String summary = generateSummary(employee, scores, totalScore);
        
        return PerformanceAssessment.builder()
            .employeeId(employeeId)
            .period(period)
            .indicatorScores(scores)
            .totalScore(totalScore)
            .grade(grade)
            .autoData(autoData)
            .summary(summary)
            .build();
    }
    
    // 计算指标得分
    private IndicatorScore calculateIndicatorScore(
            Indicator indicator, 
            AutoCollectedData data, 
            AssessmentPeriod period) {
        
        // 获取目标值
        double target = indicator.getTarget(period);
        
        // 获取实际值
        double actual = extractActualValue(indicator, data);
        
        // 计算达成率
        double achievementRate = actual / target;
        
        // 计算得分 (根据指标类型调整计算方式)
        double score = calculateScore(indicator, achievementRate);
        
        return IndicatorScore.builder()
            .indicatorId(indicator.getId())
            .indicatorName(indicator.getName())
            .targetValue(target)
            .actualValue(actual)
            .achievementRate(achievementRate)
            .weight(indicator.getWeight())
            .score(score)
            .dataSource(indicator.getDataSource())
            .trend(calculateTrend(indicator, data))
            .build();
    }
}
```

### 3.3 数据自动采集

```java
// 数据采集服务
@Service
public class DataCollectionService {
    
    @Autowired private GitLabClient gitLabClient;
    @Autowired private JiraClient jiraClient;
    @Autowired private DingTalkClient dingTalkClient;
    @Autowired private FeishuClient feishuClient;
    @Autowired private ErpClient erpClient;
    @Autowired private CrmClient crmClient;
    
    // 自动采集数据
    public AutoCollectedData collectData(
            String employeeId,
            List<Indicator> indicators,
            LocalDate startDate,
            LocalDate endDate) {
        
        AutoCollectedData data = new AutoCollectedData();
        
        for (Indicator indicator : indicators) {
            Object value = collectIndicatorData(indicator, employeeId, startDate, endDate);
            data.addSystemData(indicator.getId(), value);
        }
        
        return data;
    }
    
    // 采集指标数据
    private Object collectIndicatorData(
            Indicator indicator,
            String employeeId,
            LocalDate startDate,
            LocalDate endDate) {
        
        return switch (indicator.getSourceSystem()) {
            case "GITLAB" -> collectFromGitLab(indicator, employeeId, startDate, endDate);
            case "JIRA" -> collectFromJira(indicator, employeeId, startDate, endDate);
            case "DINGTALK" -> collectFromDingTalk(indicator, employeeId, startDate, endDate);
            case "FEISHU" -> collectFromFeishu(indicator, employeeId, startDate, endDate);
            case "ERP" -> collectFromErp(indicator, employeeId, startDate, endDate);
            case "CRM" -> collectFromCrm(indicator, employeeId, startDate, endDate);
            case "DIGITAL_EMPLOYEE" -> collectFromDigitalEmployee(indicator, employeeId, startDate, endDate);
            default -> null;
        };
    }
    
    // 从GitLab采集
    private Object collectFromGitLab(Indicator indicator, String employeeId, LocalDate start, LocalDate end) {
        Employee employee = employeeService.getEmployee(employeeId);
        String gitlabUsername = employee.getHumanConfig().getGitlabUsername();
        
        return switch (indicator.getId()) {
            case "CODE_COMMITS" -> gitLabClient.getCommitCount(gitlabUsername, start, end);
            case "CODE_QUALITY" -> gitLabClient.getCodeQualityScore(gitlabUsername, start, end);
            case "MR_COUNT" -> gitLabClient.getMergeRequestCount(gitlabUsername, start, end);
            case "REVIEW_COUNT" -> gitLabClient.getReviewCount(gitlabUsername, start, end);
            default -> null;
        };
    }
    
    // 从数字员工采集
    private Object collectFromDigitalEmployee(Indicator indicator, String employeeId, LocalDate start, LocalDate end) {
        // 数字员工自动记录的工作产出
        List<DigitalEmployeeRecord> records = digitalEmployeeRecordRepository
            .findByEmployeeIdAndDateRange(employeeId, start, end);
        
        return switch (indicator.getId()) {
            case "TASK_COMPLETION" -> records.stream().mapToInt(r -> r.getTaskCount()).sum();
            case "WORK_QUALITY" -> calculateAverageQuality(records);
            case "RESPONSE_TIME" -> calculateAverageResponseTime(records);
            default -> null;
        };
    }
}
```

---

## 四、自主运行机制

### 4.1 定时任务调度

```java
// 绩效考核定时任务
@Service
public class PerformanceAssessmentScheduler {
    
    @Autowired private PerformanceCalculationEngine calculationEngine;
    @Autowired private NotificationService notificationService;
    @Autowired private CEOReportGenerator reportGenerator;
    
    // 每日绩效快报
    @Scheduled(cron = "0 0 18 * * ?")  // 每天18:00
    public void generateDailyPerformance() {
        LocalDate today = LocalDate.now();
        
        // 计算所有员工当日绩效
        List<PerformanceAssessment> assessments = new ArrayList<>();
        for (Employee emp : employeeService.getAllActiveEmployees()) {
            PerformanceAssessment assessment = calculationEngine.calculateEmployeePerformance(
                emp.getEmployeeId(), 
                AssessmentPeriod.DAILY,
                today, 
                today
            );
            assessments.add(assessment);
        }
        
        // 生成日报
        DailyPerformanceReport report = reportGenerator.generateDailyReport(assessments);
        
        // 发送给CEO
        notificationService.sendToCEO(report);
        
        // 存储到数据库
        performanceRepository.saveAll(assessments);
    }
    
    // 每周绩效报告
    @Scheduled(cron = "0 0 17 * * FRI")  // 每周五17:00
    public void generateWeeklyPerformance() {
        LocalDate weekStart = LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDate weekEnd = LocalDate.now();
        
        // 计算部门绩效
        List<DepartmentPerformance> deptPerformances = new ArrayList<>();
        for (String dept : departments) {
            DepartmentPerformance perf = calculationEngine.calculateDepartmentPerformance(
                dept, weekStart, weekEnd
            );
            deptPerformances.add(perf);
        }
        
        // 生成周报
        WeeklyPerformanceReport report = reportGenerator.generateWeeklyReport(deptPerformances);
        
        // 发送给CEO和管理层
        notificationService.sendToManagement(report);
    }
    
    // 每月绩效考核
    @Scheduled(cron = "0 0 9 1 * ?")  // 每月1日9:00
    public void generateMonthlyPerformance() {
        LocalDate monthStart = LocalDate.now().minusMonths(1).withDayOfMonth(1);
        LocalDate monthEnd = LocalDate.now().minusMonths(1).withDayOfMonth(
            LocalDate.now().minusMonths(1).lengthOfMonth()
        );
        
        // 计算所有员工月度绩效
        for (Employee emp : employeeService.getAllActiveEmployees()) {
            PerformanceAssessment assessment = calculationEngine.calculateEmployeePerformance(
                emp.getEmployeeId(),
                AssessmentPeriod.MONTHLY,
                monthStart,
                monthEnd
            );
            
            // 发送绩效通知
            notificationService.sendPerformanceNotification(emp.getEmployeeId(), assessment);
        }
        
        // 生成月度公司报告
        MonthlyCompanyReport report = reportGenerator.generateMonthlyCompanyReport(monthStart, monthEnd);
        notificationService.sendToCEO(report);
    }
}
```

### 4.2 实时监控与预警

```java
// 运营监控服务
@Service
public class OperationMonitoringService {
    
    @Autowired private AlertNotifier alertNotifier;
    @Autowired private CEOReportGenerator reportGenerator;
    
    // 实时监控指标
    @Scheduled(fixedRate = 300000)  // 每5分钟
    public void monitorKeyIndicators() {
        // 监控公司级指标
        for (CompanyIndicator indicator : companyIndicators) {
            IndicatorStatus status = checkIndicatorStatus(indicator);
            
            if (status.isAbnormal()) {
                // 发送预警
                alertNotifier.sendAlert(
                    Alert.builder()
                        .level(status.getAlertLevel())
                        .type("INDICATOR_ABNORMAL")
                        .title(indicator.getName() + " 异常")
                        .content(status.getDescription())
                        .suggestion(status.getSuggestion())
                        .build()
                );
                
                // 更新CEO仪表盘
                ceoDashboardService.updateIndicatorStatus(indicator.getId(), status);
            }
        }
    }
    
    // 检查指标状态
    private IndicatorStatus checkIndicatorStatus(CompanyIndicator indicator) {
        double currentValue = indicator.getCurrentValue();
        double targetValue = indicator.getTargetValue();
        double threshold = indicator.getAlertThreshold();
        
        double deviation = (currentValue - targetValue) / targetValue;
        
        if (Math.abs(deviation) > threshold) {
            return IndicatorStatus.builder()
                .status("ABNORMAL")
                .alertLevel(deviation < 0 ? AlertLevel.WARNING : AlertLevel.INFO)
                .description(String.format("当前值 %.2f，偏离目标 %.1f%%", currentValue, deviation * 100))
                .suggestion(generateSuggestion(indicator, deviation))
                .build();
        }
        
        return IndicatorStatus.builder().status("NORMAL").build();
    }
    
    // 生成建议
    private String generateSuggestion(CompanyIndicator indicator, double deviation) {
        if (deviation < -0.2) {
            return "建议立即召开专项会议，分析原因并制定改进措施";
        } else if (deviation < -0.1) {
            return "建议关注相关部门工作进展，必要时提供支持";
        } else if (deviation > 0.2) {
            return "表现优异，建议总结经验并推广";
        } else {
            return "持续关注，保持当前节奏";
        }
    }
}
```

### 4.3 智能分析与建议

```java
// 智能分析服务
@Service
public class IntelligentAnalysisService {
    
    @Autowired private MainBrain mainBrain;
    
    // 分析公司运营状况
    public CompanyAnalysis analyzeCompanyOperation(LocalDate startDate, LocalDate endDate) {
        // 收集数据
        CompanyOperationData data = collectOperationData(startDate, endDate);
        
        // 使用MainBrain进行分析
        String analysisPrompt = buildAnalysisPrompt(data);
        String analysis = mainBrain.analyze(analysisPrompt);
        
        return CompanyAnalysis.builder()
            .period(startDate + " 至 " + endDate)
            .overallScore(data.getOverallScore())
            .strengths(extractStrengths(analysis))
            .weaknesses(extractWeaknesses(analysis))
            .opportunities(extractOpportunities(analysis))
            .threats(extractThreats(analysis))
            .recommendations(extractRecommendations(analysis))
            .build();
    }
    
    // 生成CEO建议
    public List<CEORecommendation> generateCEORecommendations(CompanyAnalysis analysis) {
        List<CEORecommendation> recommendations = new ArrayList<>();
        
        // 基于分析结果生成建议
        if (analysis.getOverallScore() < 70) {
            recommendations.add(new CEORecommendation(
                "URGENT",
                "整体运营状况需关注",
                "建议召开管理层会议，分析问题根源"
            ));
        }
        
        for (String weakness : analysis.getWeaknesses()) {
            recommendations.add(new CEORecommendation(
                "IMPORTANT",
                "待改进项: " + weakness,
                generateImprovementSuggestion(weakness)
            ));
        }
        
        return recommendations;
    }
}
```

---

## 五、CEO仪表盘设计

### 5.1 仪表盘概览

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    CEO运营仪表盘                                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  公司整体健康度: 85分 (A)                    更新时间: 2024-01-15 18:00  │   │
│  │  ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐                              │   │
│  │  │财务 │ │业务 │ │效率 │ │风险 │ │创新 │                              │   │
│  │  │ 88  │ │ 82  │ │ 85  │ │ 90  │ │ 78  │                              │   │
│  │  │ ↑  │ │ ↓  │ │ →  │ │ ↑  │ │ ↑  │                              │   │
│  │  └─────┘ └─────┘ └─────┘ └─────┘ └─────┘                              │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────┐ ┌─────────────────────────────────────┐   │
│  │  部门绩效排名                │ │  今日预警 (3条)                      │   │
│  │  1. 技术部    92分 ↑        │ │ ⚠️ 销售部本月目标达成率偏低 (78%)     │   │
│  │  2. 财务部    88分 →        │ │ ⚠️ 客服部响应时间超标                 │   │
│  │  3. 人力资源  85分 ↑        │ │ ℹ️ 新项目启动需要资源协调             │   │
│  │  4. 运营部    82分 ↓        │ │                                     │   │
│  │  5. 销售部    78分 ↓        │ │ [查看全部预警] [处理建议]             │   │
│  │  6. 客服部    75分 →        │ │                                     │   │
│  │  7. 行政部    80分 →        │ │                                     │   │
│  │  8. 法务部    85分 ↑        │ │                                     │   │
│  └─────────────────────────────┘ └─────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  本周重点工作进展                                                    │   │
│  │  ├── 项目A: 进度85% (正常)                                          │   │
│  │  ├── 项目B: 进度60% (延期风险) ⚠️                                   │   │
│  │  ├── 新产品发布: 准备中                                              │   │
│  │  └── 季度报告: 已完成                                                │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  AI建议                                                              │   │
│  │  1. 销售部需要关注，建议本周召开销售策略会议                          │   │
│  │  2. 项目B存在延期风险，建议增加资源或调整计划                         │   │
│  │  3. 客服部响应效率可优化，建议分析高峰时段并调整排班                  │   │
│  │  [查看详细分析] [生成报告] [安排会议]                                │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 5.2 实时数据API

```java
// CEO仪表盘API
@RestController
@RequestMapping("/api/ceo/dashboard")
public class CEODashboardController {
    
    @Autowired private CEODashboardService dashboardService;
    
    // 获取仪表盘概览
    @GetMapping("/overview")
    public DashboardOverview getOverview() {
        return dashboardService.getOverview();
    }
    
    // 获取公司健康度
    @GetMapping("/health")
    public CompanyHealth getCompanyHealth() {
        return dashboardService.getCompanyHealth();
    }
    
    // 获取部门排名
    @GetMapping("/departments/ranking")
    public List<DepartmentRanking> getDepartmentRanking() {
        return dashboardService.getDepartmentRanking();
    }
    
    // 获取预警列表
    @GetMapping("/alerts")
    public List<Alert> getAlerts(
            @RequestParam(required = false) AlertLevel level,
            @RequestParam(defaultValue = "10") int limit) {
        return dashboardService.getAlerts(level, limit);
    }
    
    // 获取重点项目进展
    @GetMapping("/projects/key")
    public List<ProjectProgress> getKeyProjectProgress() {
        return dashboardService.getKeyProjectProgress();
    }
    
    // 获取AI建议
    @GetMapping("/recommendations")
    public List<CEORecommendation> getRecommendations() {
        return dashboardService.getRecommendations();
    }
    
    // 获取趋势分析
    @GetMapping("/trends")
    public TrendAnalysis getTrends(
            @RequestParam String indicator,
            @RequestParam(defaultValue = "30") int days) {
        return dashboardService.getTrendAnalysis(indicator, days);
    }
    
    // 生成报告
    @PostMapping("/reports/generate")
    public Report generateReport(@RequestBody ReportRequest request) {
        return dashboardService.generateReport(request);
    }
    
    // 安排会议
    @PostMapping("/meetings/schedule")
    public Meeting scheduleMeeting(@RequestBody MeetingRequest request) {
        return dashboardService.scheduleMeeting(request);
    }
}
```

### 5.3 实时推送

```java
// CEO仪表盘实时推送
@Service
public class CEODashboardPushService {
    
    @Autowired private SimpMessagingTemplate messagingTemplate;
    
    // 推送指标更新
    public void pushIndicatorUpdate(String indicatorId, IndicatorValue value) {
        messagingTemplate.convertAndSend(
            "/topic/ceo/indicators/" + indicatorId,
            value
        );
    }
    
    // 推送预警
    public void pushAlert(Alert alert) {
        messagingTemplate.convertAndSend(
            "/topic/ceo/alerts",
            alert
        );
    }
    
    // 推送部门绩效更新
    public void pushDepartmentUpdate(DepartmentPerformance performance) {
        messagingTemplate.convertAndSend(
            "/topic/ceo/departments/" + performance.getDepartmentId(),
            performance
        );
    }
    
    // 推送AI建议
    public void pushRecommendation(CEORecommendation recommendation) {
        messagingTemplate.convertAndSend(
            "/topic/ceo/recommendations",
            recommendation
        );
    }
}
```

---

## 六、员工绩效考核详情

### 6.1 技术岗绩效考核

```yaml
# 技术岗绩效考核配置
position: "技术岗"
department: "技术部"

indicators:
  # 代码贡献 (权重30%)
  - id: CODE_COMMITS
    name: "代码提交量"
    weight: 0.15
    source: GITLAB
    target:
      daily: 5
      weekly: 25
      monthly: 100
    scoring:
      - achievement: 1.2
        score: 100
      - achievement: 1.0
        score: 85
      - achievement: 0.8
        score: 70
      - achievement: 0.6
        score: 50
      
  - id: CODE_QUALITY
    name: "代码质量"
    weight: 0.15
    source: GITLAB
    target:
      min_score: 80
    scoring:
      - value: 95
        score: 100
      - value: 85
        score: 85
      - value: 75
        score: 70
      
  # 项目贡献 (权重40%)
  - id: PROJECT_CONTRIBUTION
    name: "项目贡献度"
    weight: 0.25
    source: JIRA
    calculation: "story_points_completed / total_story_points"
    
  - id: BUG_FIX_RATE
    name: "Bug修复率"
    weight: 0.15
    source: JIRA
    target: 0.95
    
  # 协作能力 (权重20%)
  - id: CODE_REVIEW
    name: "代码审查参与"
    weight: 0.10
    source: GITLAB
    target:
      weekly: 10
      
  - id: TEAM_COLLABORATION
    name: "团队协作"
    weight: 0.10
    source: MANUAL
    evaluation:
      - peer_review: 40%
      - manager_review: 60%
      
  # 学习成长 (权重10%)
  - id: TECH_LEARNING
    name: "技术学习"
    weight: 0.10
    source: MANUAL
    indicators:
      - training_hours
      - certification
      - knowledge_sharing
```

### 6.2 销售岗绩效考核

```yaml
# 销售岗绩效考核配置
position: "销售岗"
department: "销售部"

indicators:
  # 业绩指标 (权重60%)
  - id: SALES_REVENUE
    name: "销售额"
    weight: 0.40
    source: CRM
    target:
      monthly: 1000000
    scoring:
      - achievement: 1.5
        score: 100
      - achievement: 1.2
        score: 90
      - achievement: 1.0
        score: 80
      - achievement: 0.8
        score: 60
        
  - id: NEW_CUSTOMERS
    name: "新客户开发"
    weight: 0.20
    source: CRM
    target:
      monthly: 5
      
  # 过程指标 (权重25%)
  - id: CUSTOMER_VISITS
    name: "客户拜访量"
    weight: 0.15
    source: DINGTALK
    target:
      weekly: 15
      
  - id: CONVERSION_RATE
    name: "商机转化率"
    weight: 0.10
    source: CRM
    target: 0.30
    
  # 客户满意度 (权重15%)
  - id: CUSTOMER_SATISFACTION
    name: "客户满意度"
    weight: 0.15
    source: SURVEY
    target: 4.5
```

### 6.3 管理岗绩效考核

```yaml
# 管理岗绩效考核配置
position: "管理岗"

indicators:
  # 团队绩效 (权重40%)
  - id: TEAM_GOAL_ACHIEVEMENT
    name: "团队目标达成率"
    weight: 0.25
    source: AGGREGATE
    calculation: "average_team_member_performance"
    
  - id: TEAM_SATISFACTION
    name: "团队满意度"
    weight: 0.15
    source: SURVEY
    target: 4.0
    
  # 人才培养 (权重25%)
  - id: TALENT_DEVELOPMENT
    name: "人才培养"
    weight: 0.15
    source: MANUAL
    indicators:
      - training_provided
      - promotion_rate
      - retention_rate
      
  - id: KNOWLEDGE_SHARING
    name: "知识分享"
    weight: 0.10
    source: MANUAL
    indicators:
      - sessions_conducted
      - documentation_contributed
      
  # 跨部门协作 (权重20%)
  - id: CROSS_DEPT_COLLABORATION
    name: "跨部门协作"
    weight: 0.20
    source: MANUAL
    evaluation:
      - peer_departments: 50%
      - upper_management: 50%
      
  # 战略贡献 (权重15%)
  - id: STRATEGIC_CONTRIBUTION
    name: "战略贡献"
    weight: 0.15
    source: MANUAL
    indicators:
      - strategic_initiatives_led
      - innovation_proposals
      - process_improvements
```

---

## 七、数据库表设计

```sql
-- 绩效考核表
CREATE TABLE performance_assessments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assessment_id VARCHAR(100) UNIQUE NOT NULL,
    
    -- 员工信息
    employee_id VARCHAR(255) NOT NULL,
    employee_name VARCHAR(100),
    department VARCHAR(100),
    position VARCHAR(100),
    
    -- 考核周期
    period VARCHAR(20) NOT NULL,           -- DAILY/WEEKLY/MONTHLY/QUARTERLY/YEARLY
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    
    -- 得分
    total_score DECIMAL(5,2),
    grade VARCHAR(10),                     -- S/A/B/C/D
    
    -- 详细得分
    indicator_scores JSONB,
    
    -- 自动采集数据
    auto_collected_data JSONB,
    
    -- 人工评价
    manual_evaluations JSONB,
    
    -- 综合评价
    summary TEXT,
    strengths TEXT[],
    improvements TEXT[],
    
    -- 状态
    status VARCHAR(20) DEFAULT 'COMPLETED',
    
    -- 时间戳
    generated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP,
    
    UNIQUE(employee_id, period, start_date, end_date)
);

-- 公司运营指标表
CREATE TABLE company_indicators (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    indicator_id VARCHAR(100) UNIQUE NOT NULL,
    
    -- 基本信息
    name VARCHAR(200) NOT NULL,
    category VARCHAR(50),                  -- FINANCIAL/BUSINESS/EFFICIENCY/RISK/INNOVATION
    description TEXT,
    
    -- 目标配置
    target_value DECIMAL(15,2),
    target_period VARCHAR(20),             -- DAILY/WEEKLY/MONTHLY/QUARTERLY/YEARLY
    
    -- 预警配置
    alert_threshold DECIMAL(5,4),          -- 偏离阈值
    alert_level VARCHAR(20),               -- INFO/WARNING/CRITICAL
    
    -- 数据来源
    source_system VARCHAR(50),
    source_config JSONB,
    
    -- 当前值
    current_value DECIMAL(15,2),
    current_value_updated_at TIMESTAMP,
    
    -- 时间戳
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 部门绩效表
CREATE TABLE department_performances (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    performance_id VARCHAR(100) UNIQUE NOT NULL,
    
    -- 部门信息
    department_id VARCHAR(100) NOT NULL,
    department_name VARCHAR(100),
    
    -- 考核周期
    period VARCHAR(20) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    
    -- 得分
    total_score DECIMAL(5,2),
    rank INTEGER,
    
    -- 详细指标
    indicator_scores JSONB,
    
    -- 团队成员绩效汇总
    member_summary JSONB,
    
    -- 时间戳
    generated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(department_id, period, start_date, end_date)
);

-- CEO预警表
CREATE TABLE ceo_alerts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_id VARCHAR(100) UNIQUE NOT NULL,
    
    -- 预警信息
    level VARCHAR(20) NOT NULL,            -- INFO/WARNING/CRITICAL
    type VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    
    -- 关联
    related_indicator VARCHAR(100),
    related_department VARCHAR(100),
    related_employee VARCHAR(255),
    
    -- 建议
    suggestion TEXT,
    
    -- 状态
    status VARCHAR(20) DEFAULT 'PENDING',  -- PENDING/ACKNOWLEDGED/RESOLVED
    acknowledged_at TIMESTAMP,
    resolved_at TIMESTAMP,
    
    -- 时间戳
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- CEO建议表
CREATE TABLE ceo_recommendations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recommendation_id VARCHAR(100) UNIQUE NOT NULL,
    
    -- 建议信息
    priority VARCHAR(20) NOT NULL,         -- URGENT/IMPORTANT/NORMAL
    title VARCHAR(200) NOT NULL,
    description TEXT,
    
    -- 来源
    source VARCHAR(50),                    -- AI/MANUAL/SYSTEM
    analysis_basis TEXT,
    
    -- 执行状态
    status VARCHAR(20) DEFAULT 'PENDING',  -- PENDING/ACCEPTED/REJECTED/EXECUTING/COMPLETED
    action_taken TEXT,
    
    -- 时间戳
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX idx_performance_employee ON performance_assessments(employee_id);
CREATE INDEX idx_performance_period ON performance_assessments(period, start_date);
CREATE INDEX idx_department_perf ON department_performances(department_id, period);
CREATE INDEX idx_ceo_alerts_status ON ceo_alerts(status, created_at);
```

---

## 八、自主运行流程图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    自主运行流程                                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  【数据采集层】                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  业务系统                    数字员工                    真实员工      │   │
│  │  ├── GitLab (代码)          ├── 工作记录               ├── 日志填报   │   │
│  │  ├── Jira (项目)            ├── 任务产出               ├── 审批确认   │   │
│  │  ├── 钉钉/飞书 (协作)       ├── 响应时间               └── 反馈评价   │   │
│  │  ├── ERP (财务)             └── 质量指标                             │   │
│  │  └── CRM (客户)                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                     │                                       │
│                                     ▼                                       │
│  【计算处理层】                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  PerformanceCalculationEngine                                        │   │
│  │  ├── 指标计算 (自动)                                                 │   │
│  │  ├── 权重计算 (配置)                                                 │   │
│  │  ├── 等级评定 (规则)                                                 │   │
│  │  └── 趋势分析 (AI)                                                   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                     │                                       │
│                                     ▼                                       │
│  【监控预警层】                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  OperationMonitoringService                                          │   │
│  │  ├── 实时监控 (5分钟)                                                │   │
│  │  ├── 阈值检测                                                        │   │
│  │  ├── 预警生成                                                        │   │
│  │  └── 建议生成 (AI)                                                   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                     │                                       │
│                                     ▼                                       │
│  【报告推送层】                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  报告生成                                                            │   │
│  │  ├── 日报 (每日18:00)                                               │   │
│  │  ├── 周报 (每周五17:00)                                             │   │
│  │  ├── 月报 (每月1日9:00)                                             │   │
│  │  └── 预警报告 (实时)                                                 │   │
│  │                                                                     │   │
│  │  推送渠道                                                            │   │
│  │  ├── CEO仪表盘 (WebSocket实时)                                       │   │
│  │  ├── 钉钉/飞书 (重要通知)                                            │   │
│  │  ├── 邮件 (正式报告)                                                 │   │
│  │  └── 短信 (紧急预警)                                                 │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 九、相关文档

- [07-unified-employee-model.md](./07-unified-employee-model.md) - 统一员工模型
- [08-database-design.md](./08-database-design.md) - 数据库设计
- [09-proactive-prediction.md](./09-proactive-prediction.md) - 主动预判
