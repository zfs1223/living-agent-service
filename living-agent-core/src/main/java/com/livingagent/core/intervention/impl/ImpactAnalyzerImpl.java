package com.livingagent.core.intervention.impl;

import com.livingagent.core.intervention.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ImpactAnalyzerImpl implements ImpactAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(ImpactAnalyzerImpl.class);

    private final Map<String, List<ImpactRule>> impactRulesByOperation = new ConcurrentHashMap<>();

    public ImpactAnalyzerImpl() {
        initializeDefaultImpactRules();
    }

    private void initializeDefaultImpactRules() {
        registerFinanceImpactRules();
        registerGitImpactRules();
        registerDeployImpactRules();
        registerContractImpactRules();
        registerDataImpactRules();
        
        log.info("Initialized {} impact rule categories", impactRulesByOperation.size());
    }

    private void registerFinanceImpactRules() {
        List<ImpactRule> financeRules = new ArrayList<>();
        
        ImpactRule paymentImpact = new ImpactRule();
        paymentImpact.setRuleId("impact-finance-001");
        paymentImpact.setRuleName("财务支付影响");
        paymentImpact.setDescription("财务支付操作影响范围");
        paymentImpact.setWeight(2.0);
        paymentImpact.setOperationTypePrefixes(Arrays.asList("finance.payment", "finance.transfer"));
        paymentImpact.setImpactLevel(InterventionDecision.ImpactLevel.LARGE);
        paymentImpact.setReversible(false);
        financeRules.add(paymentImpact);

        ImpactRule budgetImpact = new ImpactRule();
        budgetImpact.setRuleId("impact-finance-002");
        budgetImpact.setRuleName("预算调整影响");
        budgetImpact.setDescription("预算调整影响部门运营");
        budgetImpact.setWeight(1.5);
        budgetImpact.setOperationTypePrefixes(Arrays.asList("finance.budget"));
        budgetImpact.setImpactLevel(InterventionDecision.ImpactLevel.MODERATE);
        budgetImpact.setReversible(true);
        financeRules.add(budgetImpact);

        impactRulesByOperation.put("finance.", financeRules);
    }

    private void registerGitImpactRules() {
        List<ImpactRule> gitRules = new ArrayList<>();
        
        ImpactRule mainMergeImpact = new ImpactRule();
        mainMergeImpact.setRuleId("impact-git-001");
        mainMergeImpact.setRuleName("主分支合并影响");
        mainMergeImpact.setDescription("合并到主分支影响所有开发人员");
        mainMergeImpact.setWeight(2.0);
        mainMergeImpact.setOperationTypePrefixes(Arrays.asList("git.merge.main", "git.merge.master"));
        mainMergeImpact.setImpactLevel(InterventionDecision.ImpactLevel.LARGE);
        mainMergeImpact.setReversible(true);
        gitRules.add(mainMergeImpact);

        ImpactRule forcePushImpact = new ImpactRule();
        forcePushImpact.setRuleId("impact-git-002");
        forcePushImpact.setRuleName("强制推送影响");
        forcePushImpact.setDescription("强制推送可能丢失代码历史");
        forcePushImpact.setWeight(2.5);
        forcePushImpact.setOperationTypePrefixes(Arrays.asList("git.push.force"));
        forcePushImpact.setImpactLevel(InterventionDecision.ImpactLevel.CRITICAL);
        forcePushImpact.setReversible(false);
        gitRules.add(forcePushImpact);

        impactRulesByOperation.put("git.", gitRules);
    }

    private void registerDeployImpactRules() {
        List<ImpactRule> deployRules = new ArrayList<>();
        
        ImpactRule prodDeployImpact = new ImpactRule();
        prodDeployImpact.setRuleId("impact-deploy-001");
        prodDeployImpact.setRuleName("生产环境部署影响");
        prodDeployImpact.setDescription("生产环境部署影响所有用户");
        prodDeployImpact.setWeight(3.0);
        prodDeployImpact.setOperationTypePrefixes(Arrays.asList("deploy.production", "deploy.prod"));
        prodDeployImpact.setImpactLevel(InterventionDecision.ImpactLevel.CRITICAL);
        prodDeployImpact.setReversible(true);
        deployRules.add(prodDeployImpact);

        ImpactRule stagingDeployImpact = new ImpactRule();
        stagingDeployImpact.setRuleId("impact-deploy-002");
        stagingDeployImpact.setRuleName("预发环境部署影响");
        stagingDeployImpact.setDescription("预发环境部署影响测试团队");
        stagingDeployImpact.setWeight(1.5);
        stagingDeployImpact.setOperationTypePrefixes(Arrays.asList("deploy.staging"));
        stagingDeployImpact.setImpactLevel(InterventionDecision.ImpactLevel.MODERATE);
        stagingDeployImpact.setReversible(true);
        deployRules.add(stagingDeployImpact);

        ImpactRule configChangeImpact = new ImpactRule();
        configChangeImpact.setRuleId("impact-deploy-003");
        configChangeImpact.setRuleName("配置变更影响");
        configChangeImpact.setDescription("配置变更可能影响系统稳定性");
        configChangeImpact.setWeight(2.0);
        configChangeImpact.setOperationTypePrefixes(Arrays.asList("deploy.config"));
        configChangeImpact.setImpactLevel(InterventionDecision.ImpactLevel.LARGE);
        configChangeImpact.setReversible(true);
        deployRules.add(configChangeImpact);

        impactRulesByOperation.put("deploy.", deployRules);
    }

    private void registerContractImpactRules() {
        List<ImpactRule> contractRules = new ArrayList<>();
        
        ImpactRule contractSignImpact = new ImpactRule();
        contractSignImpact.setRuleId("impact-contract-001");
        contractSignImpact.setRuleName("合同签署影响");
        contractSignImpact.setDescription("合同签署具有法律约束力");
        contractSignImpact.setWeight(2.5);
        contractSignImpact.setOperationTypePrefixes(Arrays.asList("contract.sign"));
        contractSignImpact.setImpactLevel(InterventionDecision.ImpactLevel.CRITICAL);
        contractSignImpact.setReversible(false);
        contractRules.add(contractSignImpact);

        ImpactRule contractTerminateImpact = new ImpactRule();
        contractTerminateImpact.setRuleId("impact-contract-002");
        contractTerminateImpact.setRuleName("合同终止影响");
        contractTerminateImpact.setDescription("合同终止影响合作关系");
        contractTerminateImpact.setWeight(2.0);
        contractTerminateImpact.setOperationTypePrefixes(Arrays.asList("contract.terminate"));
        contractTerminateImpact.setImpactLevel(InterventionDecision.ImpactLevel.LARGE);
        contractTerminateImpact.setReversible(false);
        contractRules.add(contractTerminateImpact);

        impactRulesByOperation.put("contract.", contractRules);
    }

    private void registerDataImpactRules() {
        List<ImpactRule> dataRules = new ArrayList<>();
        
        ImpactRule dataDeleteImpact = new ImpactRule();
        dataDeleteImpact.setRuleId("impact-data-001");
        dataDeleteImpact.setRuleName("数据删除影响");
        dataDeleteImpact.setDescription("数据删除可能导致数据丢失");
        dataDeleteImpact.setWeight(2.5);
        dataDeleteImpact.setOperationTypePrefixes(Arrays.asList("data.delete"));
        dataDeleteImpact.setImpactLevel(InterventionDecision.ImpactLevel.CRITICAL);
        dataDeleteImpact.setReversible(false);
        dataRules.add(dataDeleteImpact);

        ImpactRule dataExportImpact = new ImpactRule();
        dataExportImpact.setRuleId("impact-data-002");
        dataExportImpact.setRuleName("数据导出影响");
        dataExportImpact.setDescription("数据导出可能泄露敏感信息");
        dataExportImpact.setWeight(2.0);
        dataExportImpact.setOperationTypePrefixes(Arrays.asList("data.export"));
        dataExportImpact.setImpactLevel(InterventionDecision.ImpactLevel.LARGE);
        dataExportImpact.setReversible(true);
        dataRules.add(dataExportImpact);

        ImpactRule schemaChangeImpact = new ImpactRule();
        schemaChangeImpact.setRuleId("impact-data-003");
        schemaChangeImpact.setRuleName("Schema变更影响");
        schemaChangeImpact.setDescription("数据库Schema变更影响应用稳定性");
        schemaChangeImpact.setWeight(2.0);
        schemaChangeImpact.setOperationTypePrefixes(Arrays.asList("data.schema"));
        schemaChangeImpact.setImpactLevel(InterventionDecision.ImpactLevel.LARGE);
        schemaChangeImpact.setReversible(true);
        dataRules.add(schemaChangeImpact);

        impactRulesByOperation.put("data.", dataRules);
    }

    @Override
    public ImpactAnalysis analyze(String operationType, Map<String, Object> operationDetails) {
        return analyzeWithScope(operationType, operationDetails, null);
    }

    @Override
    public ImpactAnalysis analyzeWithScope(String operationType, Map<String, Object> operationDetails, 
            String department) {
        
        ImpactAnalysis analysis = new ImpactAnalysis();
        List<ImpactRule> applicableRules = getImpactRules(operationType);
        
        double totalImpact = 0.0;
        List<String> impactScopes = new ArrayList<>();
        boolean anyIrreversible = false;
        int maxAffectedUsers = 0;
        int affectedSystems = 0;

        for (ImpactRule rule : applicableRules) {
            if (rule.matches(operationType, operationDetails)) {
                totalImpact += rule.getWeight();
                impactScopes.add(rule.getRuleName());
                
                if (!rule.isReversible()) {
                    anyIrreversible = true;
                }
                
                if (rule.getImpactLevel().getLevel() >= InterventionDecision.ImpactLevel.LARGE.getLevel()) {
                    affectedSystems++;
                }
            }
        }

        if (operationDetails != null) {
            Object usersObj = operationDetails.get("affectedUsers");
            if (usersObj instanceof Number) {
                maxAffectedUsers = ((Number) usersObj).intValue();
            } else if (operationType != null && operationType.contains("production")) {
                maxAffectedUsers = 10000;
            }
        }

        double normalizedScore = Math.min(1.0, totalImpact / 5.0);
        analysis.setImpactScore(normalizedScore);
        analysis.setImpactScope(impactScopes);
        analysis.setImpactLevel(determineImpactLevel(normalizedScore, anyIrreversible));
        analysis.setReversible(!anyIrreversible);
        analysis.setAffectedUsersCount(maxAffectedUsers);
        analysis.setAffectedSystemsCount(affectedSystems);
        analysis.setAnalysisReason(buildAnalysisReason(impactScopes, normalizedScore, anyIrreversible));

        log.debug("Impact analysis for {}: score={}, level={}", 
            operationType, normalizedScore, analysis.getImpactLevel());

        return analysis;
    }

    @Override
    public void registerImpactRule(String operationType, ImpactRule rule) {
        String key = operationType.contains(".") ? operationType : operationType + ".";
        impactRulesByOperation.computeIfAbsent(key, k -> new ArrayList<>()).add(rule);
        log.info("Registered impact rule: {} for operation: {}", rule.getRuleId(), operationType);
    }

    @Override
    public void unregisterImpactRule(String ruleId) {
        impactRulesByOperation.values().forEach(rules -> 
            rules.removeIf(r -> r.getRuleId().equals(ruleId)));
        log.info("Unregistered impact rule: {}", ruleId);
    }

    @Override
    public List<ImpactRule> getImpactRules(String operationType) {
        List<ImpactRule> result = new ArrayList<>();
        
        for (Map.Entry<String, List<ImpactRule>> entry : impactRulesByOperation.entrySet()) {
            if (operationType != null && operationType.startsWith(entry.getKey())) {
                result.addAll(entry.getValue());
            }
        }
        
        return result;
    }

    private InterventionDecision.ImpactLevel determineImpactLevel(double score, boolean irreversible) {
        if (irreversible || score >= 0.9) {
            return InterventionDecision.ImpactLevel.CRITICAL;
        } else if (score >= 0.7) {
            return InterventionDecision.ImpactLevel.LARGE;
        } else if (score >= 0.4) {
            return InterventionDecision.ImpactLevel.MODERATE;
        } else if (score >= 0.2) {
            return InterventionDecision.ImpactLevel.SMALL;
        } else {
            return InterventionDecision.ImpactLevel.MINIMAL;
        }
    }

    private String buildAnalysisReason(List<String> scopes, double score, boolean irreversible) {
        StringBuilder sb = new StringBuilder();
        if (scopes.isEmpty()) {
            sb.append("无显著影响范围");
        } else {
            sb.append(String.format("影响%d个范围，综合评分%.2f", scopes.size(), score));
        }
        if (irreversible) {
            sb.append("，包含不可逆操作");
        }
        return sb.toString();
    }
}
