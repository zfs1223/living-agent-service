package com.livingagent.core.intervention.impl;

import com.livingagent.core.intervention.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RiskAssessmentServiceImpl implements RiskAssessmentService {

    private static final Logger log = LoggerFactory.getLogger(RiskAssessmentServiceImpl.class);

    private final Map<String, List<RiskFactor>> riskFactorsByOperation = new ConcurrentHashMap<>();

    public RiskAssessmentServiceImpl() {
        initializeDefaultRiskFactors();
    }

    private void initializeDefaultRiskFactors() {
        registerFinanceRiskFactors();
        registerGitRiskFactors();
        registerDeployRiskFactors();
        registerContractRiskFactors();
        registerDataRiskFactors();
        
        log.info("Initialized {} risk factor categories", riskFactorsByOperation.size());
    }

    private void registerFinanceRiskFactors() {
        List<RiskFactor> financeFactors = new ArrayList<>();
        
        RiskFactor largeAmount = new RiskFactor();
        largeAmount.setFactorId("risk-finance-001");
        largeAmount.setFactorName("大额资金操作");
        largeAmount.setDescription("涉及金额超过阈值的财务操作");
        largeAmount.setWeight(2.0);
        largeAmount.setApplicableOperations(Arrays.asList("finance.payment", "finance.transfer"));
        financeFactors.add(largeAmount);

        RiskFactor crossBorder = new RiskFactor();
        crossBorder.setFactorId("risk-finance-002");
        crossBorder.setFactorName("跨境转账");
        crossBorder.setDescription("涉及跨境资金流动");
        crossBorder.setWeight(1.5);
        crossBorder.setApplicableOperations(Arrays.asList("finance.transfer"));
        financeFactors.add(crossBorder);

        RiskFactor firstTimePayee = new RiskFactor();
        firstTimePayee.setFactorId("risk-finance-003");
        firstTimePayee.setFactorName("首次收款方");
        firstTimePayee.setDescription("首次向该账户付款");
        firstTimePayee.setWeight(1.2);
        firstTimePayee.setApplicableOperations(Arrays.asList("finance.payment"));
        financeFactors.add(firstTimePayee);

        riskFactorsByOperation.put("finance.", financeFactors);
    }

    private void registerGitRiskFactors() {
        List<RiskFactor> gitFactors = new ArrayList<>();
        
        RiskFactor mainBranch = new RiskFactor();
        mainBranch.setFactorId("risk-git-001");
        mainBranch.setFactorName("主分支操作");
        mainBranch.setDescription("对主分支进行合并或强制推送");
        mainBranch.setWeight(1.5);
        mainBranch.setApplicableOperations(Arrays.asList("git.merge", "git.push"));
        gitFactors.add(mainBranch);

        RiskFactor noReview = new RiskFactor();
        noReview.setFactorId("risk-git-002");
        noReview.setFactorName("跳过代码审查");
        noReview.setDescription("未经代码审查直接合并");
        noReview.setWeight(1.3);
        noReview.setApplicableOperations(Arrays.asList("git.merge"));
        gitFactors.add(noReview);

        riskFactorsByOperation.put("git.", gitFactors);
    }

    private void registerDeployRiskFactors() {
        List<RiskFactor> deployFactors = new ArrayList<>();
        
        RiskFactor productionEnv = new RiskFactor();
        productionEnv.setFactorId("risk-deploy-001");
        productionEnv.setFactorName("生产环境部署");
        productionEnv.setDescription("部署到生产环境");
        productionEnv.setWeight(2.5);
        productionEnv.setApplicableOperations(Arrays.asList("deploy.production", "deploy.prod"));
        deployFactors.add(productionEnv);

        RiskFactor weekendDeploy = new RiskFactor();
        weekendDeploy.setFactorId("risk-deploy-002");
        weekendDeploy.setFactorName("非工作时间部署");
        weekendDeploy.setDescription("在周末或非工作时间部署");
        weekendDeploy.setWeight(1.3);
        weekendDeploy.setApplicableOperations(Arrays.asList("deploy."));
        deployFactors.add(weekendDeploy);

        RiskFactor noRollback = new RiskFactor();
        noRollback.setFactorId("risk-deploy-003");
        noRollback.setFactorName("无回滚计划");
        noRollback.setDescription("部署没有配置回滚方案");
        noRollback.setWeight(1.5);
        noRollback.setApplicableOperations(Arrays.asList("deploy."));
        deployFactors.add(noRollback);

        riskFactorsByOperation.put("deploy.", deployFactors);
    }

    private void registerContractRiskFactors() {
        List<RiskFactor> contractFactors = new ArrayList<>();
        
        RiskFactor highValue = new RiskFactor();
        highValue.setFactorId("risk-contract-001");
        highValue.setFactorName("高价值合同");
        highValue.setDescription("合同金额超过阈值");
        highValue.setWeight(2.0);
        highValue.setApplicableOperations(Arrays.asList("contract.sign", "contract.create"));
        contractFactors.add(highValue);

        RiskFactor longTerm = new RiskFactor();
        longTerm.setFactorId("risk-contract-002");
        longTerm.setFactorName("长期合同");
        longTerm.setDescription("合同期限超过一年");
        longTerm.setWeight(1.3);
        longTerm.setApplicableOperations(Arrays.asList("contract.sign"));
        contractFactors.add(longTerm);

        RiskFactor newPartner = new RiskFactor();
        newPartner.setFactorId("risk-contract-003");
        newPartner.setFactorName("新合作伙伴");
        newPartner.setDescription("首次与新合作伙伴签约");
        newPartner.setWeight(1.5);
        newPartner.setApplicableOperations(Arrays.asList("contract.sign"));
        contractFactors.add(newPartner);

        riskFactorsByOperation.put("contract.", contractFactors);
    }

    private void registerDataRiskFactors() {
        List<RiskFactor> dataFactors = new ArrayList<>();
        
        RiskFactor sensitiveData = new RiskFactor();
        sensitiveData.setFactorId("risk-data-001");
        sensitiveData.setFactorName("敏感数据操作");
        sensitiveData.setDescription("涉及敏感数据的增删改查");
        sensitiveData.setWeight(2.0);
        sensitiveData.setApplicableOperations(Arrays.asList("data.export", "data.delete", "data.modify"));
        dataFactors.add(sensitiveData);

        RiskFactor bulkDelete = new RiskFactor();
        bulkDelete.setFactorId("risk-data-002");
        bulkDelete.setFactorName("批量删除");
        bulkDelete.setDescription("批量删除数据记录");
        bulkDelete.setWeight(2.5);
        bulkDelete.setApplicableOperations(Arrays.asList("data.delete"));
        dataFactors.add(bulkDelete);

        RiskFactor externalShare = new RiskFactor();
        externalShare.setFactorId("risk-data-003");
        externalShare.setFactorName("外部共享");
        externalShare.setDescription("数据共享给外部系统或人员");
        externalShare.setWeight(1.8);
        externalShare.setApplicableOperations(Arrays.asList("data.share", "data.export"));
        dataFactors.add(externalShare);

        riskFactorsByOperation.put("data.", dataFactors);
    }

    @Override
    public RiskAssessment assess(String operationType, Map<String, Object> operationDetails) {
        return assessWithHistory(operationType, operationDetails, Collections.emptyList());
    }

    @Override
    public RiskAssessment assessWithHistory(String operationType, Map<String, Object> operationDetails, 
            List<InterventionDecision> history) {
        
        RiskAssessment assessment = new RiskAssessment();
        List<RiskFactor> applicableFactors = getRiskFactors(operationType);
        List<String> triggeredFactors = new ArrayList<>();
        double totalScore = 0.0;

        for (RiskFactor factor : applicableFactors) {
            if (factor.isApplicable(operationType, operationDetails)) {
                triggeredFactors.add(factor.getFactorName());
                totalScore += factor.getWeight();
                assessment.addRiskDetail(factor.getFactorId(), factor.getWeight());
            }
        }

        if (history != null && !history.isEmpty()) {
            long recentFailures = history.stream()
                .filter(d -> d.getStatus() == InterventionDecision.DecisionStatus.TIMEOUT ||
                            d.getStatus() == InterventionDecision.DecisionStatus.ESCALATED)
                .count();
            totalScore += recentFailures * 0.5;
            if (recentFailures > 0) {
                triggeredFactors.add("历史失败记录: " + recentFailures + "次");
            }
        }

        double normalizedScore = Math.min(1.0, totalScore / 5.0);
        assessment.setRiskScore(normalizedScore);
        assessment.setRiskFactors(triggeredFactors);
        assessment.setRiskLevel(determineRiskLevel(normalizedScore));
        assessment.setAssessmentReason(buildAssessmentReason(triggeredFactors, normalizedScore));

        log.debug("Risk assessment for {}: score={}, level={}", 
            operationType, normalizedScore, assessment.getRiskLevel());

        return assessment;
    }

    @Override
    public void registerRiskFactor(String operationType, RiskFactor factor) {
        String key = operationType.contains(".") ? operationType : operationType + ".";
        riskFactorsByOperation.computeIfAbsent(key, k -> new ArrayList<>()).add(factor);
        log.info("Registered risk factor: {} for operation: {}", factor.getFactorId(), operationType);
    }

    @Override
    public void unregisterRiskFactor(String factorId) {
        riskFactorsByOperation.values().forEach(factors -> 
            factors.removeIf(f -> f.getFactorId().equals(factorId)));
        log.info("Unregistered risk factor: {}", factorId);
    }

    @Override
    public List<RiskFactor> getRiskFactors(String operationType) {
        List<RiskFactor> result = new ArrayList<>();
        
        for (Map.Entry<String, List<RiskFactor>> entry : riskFactorsByOperation.entrySet()) {
            if (operationType != null && operationType.startsWith(entry.getKey())) {
                result.addAll(entry.getValue());
            }
        }
        
        return result;
    }

    private InterventionDecision.RiskLevel determineRiskLevel(double score) {
        if (score >= 0.9) {
            return InterventionDecision.RiskLevel.CRITICAL;
        } else if (score >= 0.7) {
            return InterventionDecision.RiskLevel.HIGH;
        } else if (score >= 0.4) {
            return InterventionDecision.RiskLevel.MEDIUM;
        } else {
            return InterventionDecision.RiskLevel.LOW;
        }
    }

    private String buildAssessmentReason(List<String> factors, double score) {
        if (factors.isEmpty()) {
            return "无风险因素触发";
        }
        return String.format("触发%d个风险因素，综合评分%.2f", factors.size(), score);
    }
}
