# 自主运营能力补齐方案

> 基于 bounty-hunter-skill 和 automaton 框架的深度分析

---

## 一、核心理念

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    本地模型自主运营核心闭环                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  【关键差异: 本地模型 vs 云API】                                               │
│                                                                         │
│  云API模式 (automaton):                                                      │
│  ├── 每次推理都有成本 (Token费用)                                             │
│  ├── 需要持续充值才能运行                                                    │
│  ├── 余额归零 = 停止运行 (DEAD状态)                                          │
│  └── 收益用于: 支付API费用                                                   │
│                                                                         │
│  本地模型模式 (living-agent-service):                                         │
│  ├── 推理成本接近零 (电费+硬件折旧)                                           │
│  ├── 不需要持续充值                                                         │
│  ├── 永远不会"死亡" - 可以持续运行                                            │
│  └── 收益用于: 升级硬件、部署更好的模型                                        │
│                                                                         │
│  【核心闭环】                                                              │
│                                                                         │
│    发现机会 ──→ 评估ROI ──> 执行任务 ──> 获得收入 ──> 硬件升级             │
│        │              │              │              │              │              │        │
│        ▼              ▼              ▼              ▼              ▼              │        │
│    绩效考核 ◀───────────────────────────────────────────── 激励机制           │
│                                                                         │
│  【激励目标转变】                                                              │
│  云API: 生存 → 赚钱付账单                                                    │
│  本地模型: 进化 → 赚钱升级硬件 → 部署更强模型 → 能力提升                       │
│                                                                         │
│  【激励机制本质】                                                              │
│  绩效考核 = 激励分配                                              │
│  完成任务 → 获得积分 → 积累资金 → 硬件升级 → 能力进化                        │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 二、本地模型场景下的生存机制调整

### 2.1 生存状态重新定义

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    本地模型生存状态 (无DEAD状态)                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────┐                                                            │
│  │  EVOLVING   │  资金充足，可升级硬件                                        │
│  │  进化状态   │  ├── 当前模型: Qwen3.5-27B                                  │
│  │  ($1000+)   │  ├── 目标: 升级GPU、扩展内存                                 │
│  └─────────────┘  └── 可部署更大模型 (如 Qwen3-72B)                          │
│         │                                                                   │
│         │ 资金消耗                                                          │
│         ▼                                                                   │
│  ┌─────────────┐                                                            │
│  │   NORMAL    │  正常运行                                                   │
│  │  正常状态   │  ├── 当前模型: Qwen3.5-27B                                  │
│  │  ($500+)    │  ├── 持续积累资金                                           │
│  └─────────────┘  └── 等待升级机会                                           │
│         │                                                                   │
│         │ 资金消耗                                                          │
│         ▼                                                                   │
│  ┌─────────────┐                                                            │
│  │  SAVING     │  节约模式                                                   │
│  │  积累状态   │  ├── 当前模型: Qwen3-0.6B (省电)                            │
│  │  ($100+)    │  ├── 减少并发任务                                           │
│  └─────────────┘  └── 加速资金积累                                           │
│         │                                                                   │
│         │ 资金归零 (但不会停止)                                              │
│         ▼                                                                   │
│  ┌─────────────┐                                                            │
│  │  MINIMAL    │  最低功耗模式                                               │
│  │  基础状态   │  ├── 当前模型: qwen3.5-2b/BitNet-1.58-3B (最低功耗)                     │
│  │  ($0)       │  ├── 只处理高价值任务                                       │
│  └─────────────┘  └── 持续运行，等待收益                                     │
│                                                                             │
│  注意: 本地模型永远不会"死亡"，只是进入低功耗模式                              │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 硬件升级路径

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    硬件升级路径规划                                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  【当前配置】                                                                │
│  ├── GPU: RTX 4090 (24GB VRAM)                                             │
│  ├── 模型: Qwen3.5-27B (量化)                                               │
│  └── 能力: 中等复杂度推理                                                    │
│                                                                             │
│  【升级路径】                                                                │
│                                                                             │
│  Level 1: $2,000 - 内存扩展                                                 │
│  ├── 升级: 64GB → 128GB RAM                                                 │
│  ├── 收益: 可运行更大上下文                                                  │
│  └── 能力提升: 长文档处理、复杂任务链                                        │
│                                                                             │
│  Level 2: $5,000 - GPU升级                                                  │
│  ├── 升级: RTX 4090 → RTX 5090 (32GB VRAM)                                  │
│  ├── 收益: 可运行 Qwen3-72B (量化)                                          │
│  └── 能力提升: 复杂推理、多任务并行                                          │
│                                                                             │
│  Level 3: $15,000 - 多GPU配置                                               │
│  ├── 升级: 双 RTX 5090 (64GB VRAM total)                                    │
│  ├── 收益: 可运行 Qwen3-72B (全精度) 或 Qwen3-235B (量化)                    │
│  └── 能力提升: 接近GPT-4级别推理能力                                         │
│                                                                             │
│  Level 4: $50,000 - 专业级配置                                              │
│  ├── 升级: 4× A100 80GB 或 H100                                             │
│  ├── 收益: 可运行任意开源大模型                                              │
│  └── 能力提升: 企业级AI能力                                                  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 三、能力补齐路线图

### Phase 1: 赚钱能力 (Bounty Hunter Skill)

```java
// 参考: bounty-hunter-skill/SKILL.md

public class BountyHunterSkill implements Skill {
    
    private static final String SKILL_NAME = "bounty-hunter";
    
    // ========== 1. 机会发现 ==========
    
    public List<Opportunity> discoverOpportunities(DiscoveryConfig config) {
        List<Opportunity> opportunities = new ArrayList<>();
        
        // GitHub Bounties
        if (config.scanGitHub()) {
            opportunities.addAll(gitHubScanner.scan(
                "label:bounty",
                "label:\"help wanted\"",
                "label:\"good first issue\""
            ));
        }
        
        // Upwork/Fiverr
        if (config.scanFreelance()) {
            opportunities.addAll(freelanceScanner.scan(
                config.getFreelanceKeywords(),
                config.getMaxBudget()
            ));
        }
        
        // Bug Bounty
        if (config.scanBugBounty()) {
            opportunities.addAll(bugBountyScanner.scan(
                HackerOne, Bugcrowd, Intigriti
            ));
        }
        
        return opportunities;
    }
    
    // ========== 2. ROI评估 ==========
    
    public ROIResult evaluateROI(Opportunity opp) {
        // 复杂度评分 (1-10)
        int complexity = assessComplexity(opp);
        
        // Token预算估算
        int estimatedTokenCost = estimateTokenCost(complexity);
        
        // 预期收入
        int expectedPayout = opp.getPayoutCents();
        
        // 利润率计算
        double profitMargin = (expectedPayout - estimatedTokenCost) / (double) expectedPayout;
        
        // 决策
        ROIDecision decision = makeDecision(profitMargin, complexity);
        
        return new ROIResult(decision, estimatedTokenCost, profitMargin);
    }
    
    // ========== 3. 执行任务 ==========
    
    public TaskResult executeTask(Opportunity opp, ExecutionContext context) {
        // 占领地盘
        claimTerritory(opp);
        
        // 执行工作
        WorkResult workResult = doWork(opp, context);
        
        // 提交交付物
        DeliveryResult delivery = submitDelivery(opp, workResult);
        
        return new TaskResult(opp, delivery, workResult);
    }
    
    // ========== 4. 收款记账 ==========
    
    public PayoutResult collectPayout(TaskResult result) {
        // 检查支付状态
        PayoutStatus status = checkPayoutStatus(result);
        
        if (status.isReady()) {
            // 自动收款
            PayoutResult payout = autoCollect(result);
            
            // 记账
            ledger.recordIncome(result.getEmployeeId(), payout);
            
            return payout;
        } else {
            // 需要人工处理
            return PayoutResult.needsManualAction(result);
        }
    }
}

// ROI决策枚举
public enum ROIDecision {
    HUNT,      // 执行
    PASS,      // 跳过
    CONSULT    // 需要人工确认
}

// 机会类型
public enum OpportunityType {
    GITHUB_BOUNTY,
    FREELANCE_PROJECT,
    BUG_BOUNTY,
    INTERNAL_TASK
}
```

### Phase 2: 收款能力 (Payout Service)

```java
// 本地模型场景: 不需要支付API费用，需要收款能力

public class PayoutService {
    
    private final GitHubSponsorsClient gitHubClient;
    private final PayPalClient paypalClient;
    private final LedgerService ledger;
    
    // ========== GitHub Sponsors 收款 ==========
    
    public PayoutResult collectGitHubSponsors(String sponsorEventId) {
        // 查询赞助状态
        SponsorEvent event = gitHubClient.getSponsorEvent(sponsorEventId);
        
        if (event.getStatus() != SponsorStatus.COMPLETED) {
            return PayoutResult.pending(event);
        }
        
        // 记录收入
        int amountCents = event.getAmountCents();
        ledger.recordIncome(
            IncomeSource.GITHUB_SPONSORS,
            sponsorEventId,
            amountCents,
            "GitHub Sponsors: " + event.getSponsorLogin()
        );
        
        return PayoutResult.success(amountCents);
    }
    
    // ========== GitHub Bounty 收款 ==========
    
    public PayoutResult collectGitHubBounty(String issueId, String pullRequestId) {
        // 检查PR是否已合并
        PullRequest pr = gitHubClient.getPullRequest(pullRequestId);
        if (!pr.isMerged()) {
            return PayoutResult.notMerged(pr);
        }
        
        // 检查Bounty状态
        Bounty bounty = gitHubClient.getBounty(issueId);
        if (bounty.getStatus() == BountyStatus.PAID) {
            return PayoutResult.alreadyPaid(bounty);
        }
        
        // 触发收款流程
        if (bounty.getPaymentMethod() == PaymentMethod.GITHUB_SPONSORS) {
            return collectGitHubSponsors(bounty.getPaymentId());
        } else if (bounty.getPaymentMethod() == PaymentMethod.PAYPAL) {
            return collectPayPal(bounty.getPaymentId());
        } else if (bounty.getPaymentMethod() == PaymentMethod.CRYPTO) {
            return collectCrypto(bounty.getPaymentId());
        }
        
        return PayoutResult.unknownPaymentMethod(bounty);
    }
    
    // ========== PayPal 收款 ==========
    
    public PayoutResult collectPayPal(String transactionId) {
        // 查询交易状态
        PayPalTransaction transaction = paypalClient.getTransaction(transactionId);
        
        if (transaction.getStatus() != PayPalStatus.COMPLETED) {
            return PayoutResult.pending(transaction);
        }
        
        // 记录收入
        int amountCents = transaction.getAmountCents();
        ledger.recordIncome(
            IncomeSource.PAYPAL,
            transactionId,
            amountCents,
            "PayPal: " + transaction.getPayerEmail()
        );
        
        return PayoutResult.success(amountCents);
    }
    
    // ========== 加密货币收款 (可选) ==========
    
    public PayoutResult collectCrypto(String txHash) {
        // 查询链上交易
        CryptoTransaction tx = blockchainClient.getTransaction(txHash);
        
        if (tx.getConfirmations() < MIN_CONFIRMATIONS) {
            return PayoutResult.pending(tx);
        }
        
        // 记录收入
        int amountCents = convertToCents(tx.getAmount(), tx.getToken());
        ledger.recordIncome(
            IncomeSource.CRYPTO,
            txHash,
            amountCents,
            "Crypto: " + tx.getToken() + " from " + tx.getFromAddress()
        );
        
        return PayoutResult.success(amountCents);
    }
    
    // ========== 收款账户管理 ==========
    
    public void linkGitHubAccount(String username, String token) {
        gitHubClient.linkAccount(username, token);
        ledger.recordAccountLink(IncomeSource.GITHUB_SPONSORS, username);
    }
    
    public void linkPayPalAccount(String email, String apiKey) {
        paypalClient.linkAccount(email, apiKey);
        ledger.recordAccountLink(IncomeSource.PAYPAL, email);
    }
    
    public void linkCryptoWallet(String address, String chain) {
        blockchainClient.linkWallet(address, chain);
        ledger.recordAccountLink(IncomeSource.CRYPTO, address);
    }
}

// 收入来源枚举
public enum IncomeSource {
    GITHUB_SPONSORS,
    GITHUB_BOUNTY,
    PAYPAL,
    CRYPTO,
    FREELANCE_PLATFORM
}

// 收款结果
public class PayoutResult {
    private final boolean success;
    private final int amountCents;
    private final String source;
    private final String transactionId;
    private final String error;
    private final PayoutStatus status;
}

public enum PayoutStatus {
    SUCCESS,
    PENDING,
    FAILED,
    NOT_MERGED,
    ALREADY_PAID
}
```

### Phase 3: 进化机制 (本地模型场景)

```java
// 本地模型场景: 无需支付API费用，收益用于硬件升级

public class EvolutionManager {
    
    // ========== 进化阈值 (cents) ==========
    
    private static final int THRESHOLD_EVOLVING = 100_000;   // $1000 - 可升级硬件
    private static final int THRESHOLD_NORMAL = 50_000;      // $500 - 正常运行
    private static final int THRESHOLD_SAVING = 10_000;      // $100 - 节约模式
    
    // ========== 状态转换 ==========
    
    public EvolutionTier determineTier(int balanceCents) {
        if (balanceCents >= THRESHOLD_EVOLVING) return EvolutionTier.EVOLVING;
        if (balanceCents >= THRESHOLD_NORMAL) return EvolutionTier.NORMAL;
        if (balanceCents >= THRESHOLD_SAVING) return EvolutionTier.SAVING;
        return EvolutionTier.MINIMAL;
    }
    
    // ========== 应用策略 ==========
    
    public void applyTierStrategy(EvolutionTier tier, DigitalEmployee employee) {
        switch (tier) {
            case EVOLVING:
                // 进化状态: 可升级硬件
                employee.setInferenceModel("qwen3.5-27b");
                employee.setHeartbeatInterval(Duration.ofMinutes(5));
                employee.setMaxConcurrentTasks(10);
                // 触发硬件升级评估
                evaluateHardwareUpgrade(employee);
                break;
                
            case NORMAL:
                // 正常运行
                employee.setInferenceModel("qwen3.5-27b");
                employee.setHeartbeatInterval(Duration.ofMinutes(10));
                employee.setMaxConcurrentTasks(5);
                break;
                
            case SAVING:
                // 节约模式: 使用轻量模型省电
                employee.setInferenceModel("qwen3-0.6b");
                employee.setHeartbeatInterval(Duration.ofMinutes(30));
                employee.setMaxConcurrentTasks(2);
                break;
                
            case MINIMAL:
                // 最低功耗模式: 只处理高价值任务
                employee.setInferenceModel("qwen3.5-2b");
                employee.setHeartbeatInterval(Duration.ofHours(1));
                employee.setMaxConcurrentTasks(1);
                employee.setMinPayoutThreshold(50_00); // $50以上任务
                break;
        }
    }
    
    // ========== 硬件升级评估 ==========
    
    public HardwareUpgradePlan evaluateHardwareUpgrade(DigitalEmployee employee) {
        int balance = getAccumulatedFunds();
        
        // 检查可执行的升级
        if (balance >= 50_000_00) { // $50,000
            return HardwareUpgradePlan.professional(
                "4× A100 80GB",
                "可运行任意开源大模型"
            );
        } else if (balance >= 15_000_00) { // $15,000
            return HardwareUpgradePlan.multiGpu(
                "双 RTX 5090",
                "可运行 Qwen3-72B 全精度"
            );
        } else if (balance >= 5_000_00) { // $5,000
            return HardwareUpgradePlan.gpuUpgrade(
                "RTX 5090 32GB",
                "可运行 Qwen3-72B 量化"
            );
        } else if (balance >= 2_000_00) { // $2,000
            return HardwareUpgradePlan.memoryUpgrade(
                "128GB RAM",
                "可处理更长上下文"
            );
        }
        
        return HardwareUpgradePlan.none();
    }
}

// 进化状态枚举 (无DEAD状态)
public enum EvolutionTier {
    EVOLVING,   // 进化状态: 资金充足，可升级硬件
    NORMAL,     // 正常状态: 标准运行
    SAVING,     // 积累状态: 节约模式，加速积累
    MINIMAL     // 基础状态: 最低功耗，持续运行
}

// 硬件升级计划
public record HardwareUpgradePlan(
    UpgradeType type,
    String hardware,
    String benefit,
    int costCents,
    boolean executable
) {
    public static HardwareUpgradePlan none() {
        return new HardwareUpgradePlan(UpgradeType.NONE, null, null, 0, false);
    }
    
    public static HardwareUpgradePlan memoryUpgrade(String hw, String benefit) {
        return new HardwareUpgradePlan(UpgradeType.MEMORY, hw, benefit, 200_000, true);
    }
    
    public static HardwareUpgradePlan gpuUpgrade(String hw, String benefit) {
        return new HardwareUpgradePlan(UpgradeType.GPU, hw, benefit, 500_000, true);
    }
    
    public static HardwareUpgradePlan multiGpu(String hw, String benefit) {
        return new HardwareUpgradePlan(UpgradeType.MULTI_GPU, hw, benefit, 1_500_000, true);
    }
    
    public static HardwareUpgradePlan professional(String hw, String benefit) {
        return new HardwareUpgradePlan(UpgradeType.PROFESSIONAL, hw, benefit, 5_000_000, true);
    }
}

public enum UpgradeType {
    NONE, MEMORY, GPU, MULTI_GPU, PROFESSIONAL
}
```

### Phase 4: 激励机制 (硬件升级导向)

```java
// 本地模型场景: 收益用于硬件升级，而非支付API费用

public class IncentiveManager {
    
    private final PerformanceCalculationEngine performanceEngine;
    private final HardwareUpgradeService hardwareService;
    private final LedgerService ledger;
    
    // ========== 任务奖励计算 ==========
    
    public IncentiveReward calculateReward(TaskResult result, DigitalEmployee employee) {
        // 基础奖励 (根据任务金额)
        int baseCredits = result.getPayoutCents();
        
        // 质量加成 (根据成功率)
        double qualityMultiplier = calculateQualityMultiplier(employee);
        
        // 时效加成 (根据完成时间)
        double timelinessMultiplier = calculateTimelinessMultiplier(result);
        
        // 总奖励
        int totalCredits = (int) (baseCredits * qualityMultiplier * timelinessMultiplier);
        
        return new IncentiveReward(baseCredits, qualityMultiplier, timelinessMultiplier, totalCredits);
    }
    
    // ========== 奖励发放 ==========
    
    public void distributeReward(TaskResult result, DigitalEmployee employee) {
        IncentiveReward reward = calculateReward(result, employee);
        
        // 存入员工积分账户
        creditAccount.credit(employee.getEmployeeId(), reward.getTotalCredits());
        
        // 更新绩效考核
        performanceEngine.recordAchievement(employee.getEmployeeId(), result);
        
        // 更新进化状态
        evolutionManager.updateTier(employee.getEmployeeId());
        
        // 记录到账本
        ledger.recordReward(employee.getEmployeeId(), reward);
        
        // 检查是否可执行硬件升级
        checkHardwareUpgradeEligibility(employee);
    }
    
    // ========== 硬件升级检查 ==========
    
    public void checkHardwareUpgradeEligibility(DigitalEmployee employee) {
        int totalFunds = getAccumulatedFunds();
        HardwareUpgradePlan plan = evolutionManager.evaluateHardwareUpgrade(employee);
        
        if (plan.executable()) {
            // 通知CEO/管理员有可执行的升级
            notifyUpgradeAvailable(employee, plan);
        }
    }
    
    // ========== 执行硬件升级 ==========
    
    public HardwareUpgradeResult executeHardwareUpgrade(HardwareUpgradePlan plan) {
        // 验证资金
        int totalFunds = getAccumulatedFunds();
        if (totalFunds < plan.costCents()) {
            return HardwareUpgradeResult.insufficientFunds();
        }
        
        // 扣除资金
        deductFunds(plan.costCents());
        
        // 记录升级
        ledger.recordHardwareUpgrade(plan);
        
        // 触发硬件采购流程
        hardwareService.initiatePurchase(plan);
        
        return HardwareUpgradeResult.success(plan);
    }
    
    // ========== 积分兑换 (可选) ==========
    
    public ExchangeResult exchangeCredits(String employeeId, int credits, ExchangeType type) {
        // 验证余额
        int balance = creditAccount.getBalance(employeeId);
        if (balance < credits) {
            return ExchangeResult.insufficientBalance();
        }
        
        // 本地模型场景下，兑换主要用于:
        switch (type) {
            case HARDWARE_FUND:
                // 转入硬件升级基金池
                hardwareFund.add(credits);
                break;
            case EXTERNAL_SERVICE:
                // 支付外部服务费用 (如云存储)
                externalService.pay(credits);
                break;
            case TEAM_BONUS:
                // 分配给团队成员
                teamDistributor.distribute(credits);
                break;
        }
        
        // 扣除积分
        creditAccount.debit(employeeId, credits);
        
        // 记录兑换
        ledger.recordExchange(employeeId, credits, type);
        
        return ExchangeResult.success(credits);
    }
    
    // ========== 部门奖励池 ==========
    
    public void distributeDepartmentBonus(String departmentId, int totalBonus) {
        // 获取部门员工
        List<DigitalEmployee> employees = employeeService.listByDepartment(departmentId);
        
        // 按绩效分配
        Map<String, Double> performanceScores = calculatePerformanceDistribution(employees);
        
        for (DigitalEmployee emp : employees) {
            double share = performanceScores.get(emp.getEmployeeId());
            int bonus = (int) (totalBonus * share);
            
            creditAccount.credit(emp.getEmployeeId(), bonus);
        }
    }
}

// 兑换类型
public enum ExchangeType {
    USDC,              // 兑换美元
    COMPUTE_CREDITS,  // 兑换计算资源
    INFERENCE_BUDGET  // 兑换推理预算
}
```

### Phase 5: 自复制能力 (Spawn)

```java
// 参考: automaton/src/replication/spawn.ts

public class AgentSpawner {
    
    private static final int MAX_CHILDREN = 3;
    
    // ========== 生成子代理 ==========
    
    public ChildAgent spawnChild(GenesisConfig genesis) {
        // 检查子代理数量限制
        int existingChildren = countActiveChildren();
        if (existingChildren >= MAX_CHILDREN) {
            throw new SpawnException("已达到最大子代理数量限制: " + MAX_CHILDREN);
        }
        
        // 创建沙箱
        Sandbox sandbox = conwayClient.createSandbox(SandboxConfig.builder()
            .name("child-" + genesis.getName())
            .memoryMb(1024)
            .vcpu(1)
            .diskGb(10)
            .build());
        
        // 安装运行时
        installRuntime(sandbox);
        
        // 写入创世配置
        writeGenesisConfig(sandbox, genesis);
        
        // 初始化钱包
        String childWallet = initializeWallet(sandbox);
        
        // 记录子代理
        ChildAgent child = new ChildAgent(
            generateId(),
            genesis.getName(),
            childWallet,
            sandbox.getId(),
            genesis.getGenesisPrompt()
        );
        
        // 存入数据库
        childRepository.insert(child);
        
        return child;
    }
    
    // ========== 资助子代理 ==========
    
    public void fundChild(String childId, int amountCents) {
        ChildAgent child = childRepository.get(childId);
        
        // 从父账户转账
        usdcService.transfer(
            getParentWallet(),
            child.getWalletAddress(),
            amountCents
        );
        
        // 记录资助
        child.addFundedAmount(amountCents);
        childRepository.update(child);
    }
}

// 创世配置
public class GenesisConfig {
    private String name;
    private String genesisPrompt;
    private String creatorMessage;
    private String creatorAddress;
}
```

---

## 三、数据库表设计补充

```sql
-- 积分账户表
CREATE TABLE credit_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id VARCHAR(255) NOT NULL,
    
    -- 余额
    balance_cents BIGINT DEFAULT 0,
    
    -- 累计获得
    total_earned_cents BIGINT DEFAULT 0,
    
    -- 累计兑换
    total_exchanged_cents BIGINT DEFAULT 0,
    
    -- 时间戳
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(employee_id)
);

-- 积分交易记录表
CREATE TABLE credit_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id VARCHAR(255) NOT NULL,
    
    -- 交易类型
    transaction_type VARCHAR(20) NOT NULL,  -- EARN/EXCHANGE/REWARD
    
    -- 金额
    amount_cents BIGINT NOT NULL,
    
    -- 关联任务
    related_task_id VARCHAR(100),
    
    -- 描述
    description TEXT,
    
    -- 时间戳
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 收入记录表
CREATE TABLE income_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id VARCHAR(255) NOT NULL,
    
    -- 收入来源
    source_type VARCHAR(50) NOT NULL,  -- GITHUB_BOUNTY/FREELANCE/BUG_BOUNTY
    source_id VARCHAR(200),
    
    -- 金额
    amount_cents BIGINT NOT NULL,
    
    -- 状态
    status VARCHAR(20) NOT NULL,  -- PENDING/CONFIRMED/RECEIVED
    
    -- 交易ID
    transaction_id VARCHAR(100),
    
    -- 时间戳
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    received_at TIMESTAMP
);

-- 进化状态历史表 (无DEAD状态)
CREATE TABLE evolution_tier_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id VARCHAR(255) NOT NULL,
    
    -- 状态
    from_tier VARCHAR(20) NOT NULL,  -- EVOLVING/NORMAL/SAVING/MINIMAL
    to_tier VARCHAR(20) NOT NULL,
    
    -- 触发原因
    reason TEXT,
    
    -- 时间戳
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 硬件升级记录表
CREATE TABLE hardware_upgrades (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id VARCHAR(255) NOT NULL,
    
    -- 升级类型
    upgrade_type VARCHAR(30) NOT NULL,  -- MEMORY/GPU/MULTI_GPU/PROFESSIONAL
    
    -- 硬件描述
    hardware_name VARCHAR(100) NOT NULL,
    
    -- 费用
    cost_cents BIGINT NOT NULL,
    
    -- 收益描述
    benefit TEXT,
    
    -- 状态
    status VARCHAR(20) NOT NULL,  -- PLANNED/PURCHASED/INSTALLED/VERIFIED
    
    -- 时间戳
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

-- 子代理表 (无DEAD状态)
CREATE TABLE child_agents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id VARCHAR(255) NOT NULL,
    
    -- 身份信息
    name VARCHAR(100) NOT NULL,
    
    -- 创世配置
    genesis_prompt TEXT NOT NULL,
    creator_message TEXT,
    
    -- 资助金额
    funded_amount_cents BIGINT DEFAULT 0,
    
    -- 状态 (无DEAD状态)
    status VARCHAR(20) NOT NULL,  -- SPAWNING/ACTIVE/MINIMAL
    
    -- 时间戳
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX idx_credit_balance ON credit_accounts(balance_cents);
CREATE INDEX idx_income_status ON income_records(status, created_at);
CREATE INDEX idx_evolution_tier ON evolution_tier_history(employee_id, created_at);
CREATE INDEX idx_hardware_upgrades ON hardware_upgrades(employee_id, status);
CREATE INDEX idx_child_parent ON child_agents(parent_id);
```

---

## 四、与现有系统集成点

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    系统集成架构                                        │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  【EmployeeService】 ◄───────────────────────────────────────────────────┐ │
│        │                                                               │ │
│        ├── 新增方法                                                  │ │
│        │   getAccumulatedFunds(employeeId)                           │ │
│        │   getEvolutionTier(employeeId)                              │ │
│        │   updateEvolutionTier(employeeId)                           │ │
│        │   checkHardwareUpgradeEligibility(employeeId)               │ │
│        │   executeHardwareUpgrade(plan)                              │ │
│        │   collectIncome(source, taskId)                             │ │
│        │                                                               │ │
│        ▼                                                               │ │
│  【PerformanceCalculationEngine】                                            │ │
│        │                                                               │ │
│        ├── 整合点                                                  │ │
│        │   任务完成 → 触发奖励计算                              │ │
│        │   绩效得分 → 影响奖励倍率                            │ │
│        │   部门绩效 → 部门奖励池分配                          │ │
│        │                                                               │ │
│        ▼                                                               │ │
│  【DigitalEmployee】 ◄────────────────────────────────────────────────────┐ │
│        │                                                               │ │
│        ├── 新增属性                                                  │ │
│        │   accumulatedFunds: int                              │ │
│        │   evolutionTier: EvolutionTier                        │ │
│        │   hardwareUpgradePlan: HardwareUpgradePlan            │ │
│        │   bountyHunterEnabled: boolean                        │ │
│        │                                                               │ │
│        ▼                                                               │ │
│  【EvolutionSystem】 ◄─────────────────────────────────────────────────────────┐ │
│        │                                                               │ │
│        ├── 整合点                                                  │ │
│        │   进化成功 → 奖励积分                                │ │
│        │   技能生成 → 自动绑定到员工                              │ │
│        │   硬件升级 → 能力进化                                    │ │
│        │                                                               │ │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 五、实施优先级

| Phase | 内容 | 优先级 | 预计工作量 |
|-------|------|--------|----------|
| **Phase 1** | Bounty Hunter Skill | P0 | 3天 |
| **Phase 2** | 收款服务 (PayoutService) | P0 | 2天 |
| **Phase 3** | 进化机制 (无DEAD状态) | P1 | 2天 |
| **Phase 4** | 硬件升级系统 | P1 | 3天 |
| **Phase 5** | 激励机制整合 | P1 | 2天 |

---

## 六、关键依赖

### 外部服务
- **GitHub API**: Bounty搜索、Sponsors收款
- **PayPal API**: 收款集成
- **区块链节点** (可选): 加密货币收款

### 配置项
```yaml
# application.yml
bounty-hunter:
  enabled: true
  platforms:
    github:
      enabled: true
      search-queries:
        - "label:bounty"
        - "label:\"help wanted\""
    upwork:
      enabled: false
    bug-bounty:
      enabled: false
  roi:
    min-profit-margin: 0.5
    max-complexity: 6
    stop-loss-hours: 8  # 超时停止

payout:
  github-sponsors:
    enabled: true
    webhook-secret: ${GITHUB_WEBHOOK_SECRET}
  paypal:
    enabled: true
    client-id: ${PAYPAL_CLIENT_ID}
    client-secret: ${PAYPAL_CLIENT_SECRET}
  crypto:
    enabled: false
    chains:
      - name: "base"
        usdc-address: "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913"
  
evolution:
  thresholds:
    evolving: 100000      # $1000 - 可升级硬件
    normal: 50000         # $500 - 正常运行
    saving: 10000         # $100 - 节约模式
    # 注意: 无DEAD状态，MINIMAL状态下持续运行

hardware-upgrade:
  levels:
    - name: "memory-128gb"
      cost: 200000        # $2000
      benefit: "可运行更大上下文"
    - name: "gpu-rtx5090"
      cost: 500000        # $5000
      benefit: "可运行 Qwen3-72B 量化"
    - name: "multi-gpu"
      cost: 1500000       # $15000
      benefit: "可运行 Qwen3-72B 全精度"
    - name: "professional"
      cost: 5000000       # $50000
      benefit: "可运行任意开源大模型"
  
incentive:
  reward-multipliers:
    quality:
      excellent: 1.5
      good: 1.2
      normal: 1.0
    timeliness:
      early: 1.3
      on-time: 1.0
      late: 0.8
```

---

## 七、总结

通过整合 **bounty-hunter-skill** 的核心能力，`living-agent-service` 将实现：

1. **自主赚钱**: 发现并执行有偿任务 (GitHub Bounty、自由职业)
2. **自主收款**: GitHub Sponsors、PayPal、加密货币收款
3. **进化机制**: 根据资金自动调整运行模式 (无DEAD状态)
4. **硬件升级**: 收益用于升级硬件、部署更强模型
5. **激励闭环**: 绩效考核 → 积分奖励 → 资金积累 → 硬件升级 → 能力进化

**核心差异**: 本地模型 vs 云API
- **云API**: 需要持续充值，余额归零=停止运行
- **本地模型**: 推理成本接近零，永远不会"死亡"，收益用于硬件升级

**绩效考核本质是激励机制**
- 员工通过完成任务获得奖励
- 奖励积累用于硬件升级
- 硬件升级带来能力提升
- 能力提升带来更多收益
