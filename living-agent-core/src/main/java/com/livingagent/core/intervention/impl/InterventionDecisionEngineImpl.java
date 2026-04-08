package com.livingagent.core.intervention.impl;

import com.livingagent.core.intervention.*;
import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.channel.ChannelPublisher;
import com.livingagent.core.knowledge.KnowledgeBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class InterventionDecisionEngineImpl implements InterventionDecisionEngine {

    private static final Logger log = LoggerFactory.getLogger(InterventionDecisionEngineImpl.class);

    private final Map<String, InterventionRule> rules = new ConcurrentHashMap<>();
    private final Map<String, InterventionDecision> pendingDecisions = new ConcurrentHashMap<>();
    private final Map<String, InterventionDecision> allDecisions = new ConcurrentHashMap<>();
    private final Map<String, AutonomousScope> autonomousScopes = new ConcurrentHashMap<>();

    private final RiskAssessmentService riskAssessmentService;
    private final ImpactAnalyzer impactAnalyzer;
    private final KnowledgeBase knowledgeBase;
    private final ChannelPublisher channelPublisher;

    private final AtomicLong decisionCounter = new AtomicLong(0);
    private final AtomicLong autoExecutedCounter = new AtomicLong(0);
    private final AtomicLong humanInterventionCounter = new AtomicLong(0);

    private static final String[][] DECISION_MATRIX = {
        {"AUTO_EXECUTE", "AUTO_EXECUTE", "AUTO_WITH_NOTIFICATION", "ASYNC_APPROVAL"},
        {"AUTO_EXECUTE", "AUTO_WITH_NOTIFICATION", "ASYNC_APPROVAL", "REALTIME_CONFIRM"},
        {"AUTO_WITH_NOTIFICATION", "ASYNC_APPROVAL", "REALTIME_CONFIRM", "MANDATORY_HUMAN"},
        {"ASYNC_APPROVAL", "REALTIME_CONFIRM", "MANDATORY_HUMAN", "MANDATORY_HUMAN"}
    };

    public InterventionDecisionEngineImpl(
            RiskAssessmentService riskAssessmentService,
            ImpactAnalyzer impactAnalyzer,
            KnowledgeBase knowledgeBase,
            ChannelPublisher channelPublisher) {
        this.riskAssessmentService = riskAssessmentService;
        this.impactAnalyzer = impactAnalyzer;
        this.knowledgeBase = knowledgeBase;
        this.channelPublisher = channelPublisher;
        initializeDefaultRules();
        initializeDefaultScopes();
    }

    private void initializeDefaultRules() {
        InterventionRule financeRule = new InterventionRule();
        financeRule.setRuleId("rule-finance-001");
        financeRule.setRuleName("高风险财务操作");
        financeRule.setOperationTypePrefixes(Arrays.asList("finance.payment.", "finance.transfer."));
        financeRule.setMinRiskLevel(InterventionDecision.RiskLevel.HIGH);
        financeRule.setInterventionType(InterventionDecision.InterventionType.REALTIME_CONFIRM);
        financeRule.setTimeoutSeconds(300);
        financeRule.setNotificationChannels(Arrays.asList("dingtalk", "sms"));
        financeRule.setEscalationTarget("CFO");
        rules.put(financeRule.getRuleId(), financeRule);

        InterventionRule gitMergeRule = new InterventionRule();
        gitMergeRule.setRuleId("rule-git-001");
        gitMergeRule.setRuleName("代码合并到主分支");
        gitMergeRule.setOperationTypePrefixes(Arrays.asList("git.merge.main", "git.merge.master"));
        gitMergeRule.setMinRiskLevel(InterventionDecision.RiskLevel.MEDIUM);
        gitMergeRule.setInterventionType(InterventionDecision.InterventionType.ASYNC_APPROVAL);
        gitMergeRule.setTimeoutSeconds(86400);
        gitMergeRule.setNotificationChannels(Arrays.asList("dingtalk"));
        gitMergeRule.setEscalationTarget("CTO");
        rules.put(gitMergeRule.getRuleId(), gitMergeRule);

        InterventionRule contractRule = new InterventionRule();
        contractRule.setRuleId("rule-contract-001");
        contractRule.setRuleName("合同签署");
        contractRule.setOperationTypePrefixes(Arrays.asList("contract.sign", "contract.create"));
        contractRule.setMinRiskLevel(InterventionDecision.RiskLevel.HIGH);
        contractRule.setInterventionType(InterventionDecision.InterventionType.MANDATORY_HUMAN);
        contractRule.setTimeoutSeconds(86400);
        contractRule.setNotificationChannels(Arrays.asList("dingtalk", "email"));
        contractRule.setEscalationTarget("CEO");
        rules.put(contractRule.getRuleId(), contractRule);

        InterventionRule deployRule = new InterventionRule();
        deployRule.setRuleId("rule-deploy-001");
        deployRule.setRuleName("生产环境部署");
        deployRule.setOperationTypePrefixes(Arrays.asList("deploy.production", "deploy.prod"));
        deployRule.setMinRiskLevel(InterventionDecision.RiskLevel.HIGH);
        deployRule.setInterventionType(InterventionDecision.InterventionType.REALTIME_CONFIRM);
        deployRule.setTimeoutSeconds(600);
        deployRule.setNotificationChannels(Arrays.asList("dingtalk", "sms"));
        deployRule.setEscalationTarget("CTO");
        rules.put(deployRule.getRuleId(), deployRule);

        log.info("Initialized {} default intervention rules", rules.size());
    }

    private void initializeDefaultScopes() {
        AutonomousScope dailyScope = new AutonomousScope();
        dailyScope.setScopeId("scope-daily-001");
        dailyScope.setOperationTypePrefix("daily.qa");
        dailyScope.setAutonomyLevel(AutonomyLevel.FULL);
        dailyScope.setMaxRiskLevel(InterventionDecision.RiskLevel.LOW);
        dailyScope.setMaxImpactLevel(InterventionDecision.ImpactLevel.SMALL);
        autonomousScopes.put(dailyScope.getScopeId(), dailyScope);

        AutonomousScope codeReviewScope = new AutonomousScope();
        codeReviewScope.setScopeId("scope-codereview-001");
        codeReviewScope.setOperationTypePrefix("code.review");
        codeReviewScope.setAutonomyLevel(AutonomyLevel.LIMITED);
        codeReviewScope.setMaxRiskLevel(InterventionDecision.RiskLevel.MEDIUM);
        codeReviewScope.setMaxImpactLevel(InterventionDecision.ImpactLevel.MODERATE);
        autonomousScopes.put(codeReviewScope.getScopeId(), codeReviewScope);

        log.info("Initialized {} default autonomous scopes", autonomousScopes.size());
    }

    @Override
    public InterventionDecision evaluate(InterventionRequest request) {
        return evaluate(
            request.getOperationType(),
            request.getOperationDetails(),
            request.getSourceNeuronId(),
            request.getSourceChannelId()
        );
    }

    @Override
    public InterventionDecision evaluate(String operationType, Map<String, Object> operationDetails, 
            String sourceNeuronId, String sourceChannelId) {
        
        log.debug("Evaluating operation: {} from neuron: {}", operationType, sourceNeuronId);

        InterventionDecision decision = InterventionDecision.builder()
            .decisionId(generateDecisionId())
            .operationType(operationType)
            .operationDetails(operationDetails != null ? operationDetails : new HashMap<>())
            .sourceNeuronId(sourceNeuronId)
            .sourceChannelId(sourceChannelId)
            .build();

        RiskAssessmentService.RiskAssessment riskAssessment = 
            riskAssessmentService.assess(operationType, operationDetails);
        decision.setRiskLevel(riskAssessment.getRiskLevel());
        decision.setRiskScore(riskAssessment.getRiskScore());
        decision.setRiskFactors(riskAssessment.getRiskFactors());

        ImpactAnalyzer.ImpactAnalysis impactAnalysis = 
            impactAnalyzer.analyze(operationType, operationDetails);
        decision.setImpactLevel(impactAnalysis.getImpactLevel());
        decision.setImpactScore(impactAnalysis.getImpactScore());
        decision.setImpactScope(impactAnalysis.getImpactScope());

        List<InterventionRule> applicableRules = getApplicableRules(operationType);
        if (!applicableRules.isEmpty()) {
            InterventionRule highestPriorityRule = applicableRules.stream()
                .max(Comparator.comparingInt(InterventionRule::getPriority))
                .orElse(null);
            
            if (highestPriorityRule != null) {
                decision.setInterventionType(highestPriorityRule.getInterventionType());
                decision.setTimeoutSeconds(highestPriorityRule.getTimeoutSeconds());
                highestPriorityRule.recordTrigger();
            }
        }

        if (decision.getInterventionType() == null) {
            decision = determineInterventionType(decision);
        }

        if (decision.getInterventionType() == InterventionDecision.InterventionType.AUTO_EXECUTE) {
            decision.setStatus(InterventionDecision.DecisionStatus.COMPLETED);
            decision.setFinalDecision(decision.getAiDecision());
            autoExecutedCounter.incrementAndGet();
        } else {
            decision.setStatus(InterventionDecision.DecisionStatus.AWAITING_RESPONSE);
            pendingDecisions.put(decision.getDecisionId(), decision);
            humanInterventionCounter.incrementAndGet();
        }

        allDecisions.put(decision.getDecisionId(), decision);
        decisionCounter.incrementAndGet();

        publishDecisionMessage(decision);

        log.info("Decision made: {} for operation: {} - Type: {}", 
            decision.getDecisionId(), operationType, decision.getInterventionType());

        return decision;
    }

    @Override
    public InterventionDecision determineInterventionType(InterventionDecision decision) {
        int riskIndex = decision.getRiskLevel().getLevel() - 1;
        int impactIndex = decision.getImpactLevel().getLevel() - 1;

        riskIndex = Math.max(0, Math.min(riskIndex, 3));
        impactIndex = Math.max(0, Math.min(impactIndex, 3));

        String typeStr = DECISION_MATRIX[riskIndex][impactIndex];
        InterventionDecision.InterventionType type = 
            InterventionDecision.InterventionType.valueOf(typeStr);

        for (AutonomousScope scope : autonomousScopes.values()) {
            if (scope.matches(decision.getOperationType()) && scope.isEnabled()) {
                if (decision.getRiskLevel().getLevel() <= scope.getMaxRiskLevel().getLevel() &&
                    decision.getImpactLevel().getLevel() <= scope.getMaxImpactLevel().getLevel()) {
                    
                    if (scope.getAutonomyLevel() == AutonomyLevel.FULL) {
                        type = InterventionDecision.InterventionType.AUTO_EXECUTE;
                    } else if (scope.getAutonomyLevel() == AutonomyLevel.LIMITED && 
                               type != InterventionDecision.InterventionType.MANDATORY_HUMAN) {
                        type = InterventionDecision.InterventionType.AUTO_WITH_NOTIFICATION;
                    }
                    break;
                }
            }
        }

        decision.setInterventionType(type);

        if (type == InterventionDecision.InterventionType.REALTIME_CONFIRM) {
            decision.setTimeoutSeconds(300);
        } else if (type == InterventionDecision.InterventionType.ASYNC_APPROVAL) {
            decision.setTimeoutSeconds(86400);
        }

        return decision;
    }

    @Override
    public boolean shouldEscalate(InterventionDecision decision) {
        if (decision.getStatus() == InterventionDecision.DecisionStatus.COMPLETED) {
            return false;
        }

        if (decision.isTimedOut()) {
            return true;
        }

        if (decision.getEscalationLevel() > 0 && 
            decision.getEscalationLevel() < 3 &&
            decision.isTimedOut()) {
            return true;
        }

        return false;
    }

    @Override
    public Optional<InterventionDecision> escalate(InterventionDecision decision) {
        if (!shouldEscalate(decision)) {
            return Optional.empty();
        }

        decision.setEscalationLevel(decision.getEscalationLevel() + 1);
        decision.setStatus(InterventionDecision.DecisionStatus.ESCALATED);

        String escalationTarget = determineEscalationTarget(decision);
        decision.setAssignedTo(escalationTarget);

        log.warn("Decision {} escalated to level {}, assigned to: {}", 
            decision.getDecisionId(), decision.getEscalationLevel(), escalationTarget);

        publishEscalationMessage(decision);

        return Optional.of(decision);
    }

    private String determineEscalationTarget(InterventionDecision decision) {
        List<InterventionRule> applicableRules = getApplicableRules(decision.getOperationType());
        if (!applicableRules.isEmpty()) {
            return applicableRules.get(0).getEscalationTarget();
        }

        return switch (decision.getEscalationLevel()) {
            case 1 -> "DEPARTMENT_MANAGER";
            case 2 -> "DIRECTOR";
            case 3 -> "VP";
            default -> "CEO";
        };
    }

    @Override
    public InterventionDecision applyLearning(InterventionDecision decision, String humanDecision) {
        decision.setHumanDecision(humanDecision);
        decision.setFinalDecision(humanDecision);
        decision.setStatus(InterventionDecision.DecisionStatus.COMPLETED);
        decision.setCompletedAt(Instant.now());

        pendingDecisions.remove(decision.getDecisionId());

        boolean decisionChanged = !Objects.equals(decision.getAiDecision(), humanDecision);
        
        if (decisionChanged) {
            String learningKey = "intervention:learning:" + decision.getOperationType();
            Map<String, Object> learningData = new HashMap<>();
            learningData.put("decisionId", decision.getDecisionId());
            learningData.put("operationType", decision.getOperationType());
            learningData.put("aiDecision", decision.getAiDecision());
            learningData.put("humanDecision", humanDecision);
            learningData.put("riskLevel", decision.getRiskLevel().name());
            learningData.put("impactLevel", decision.getImpactLevel().name());
            learningData.put("timestamp", Instant.now().toString());

            knowledgeBase.store(learningKey, learningData, Map.of(
                "category", "intervention_learning",
                "operationType", decision.getOperationType()
            ));

            decision.setLearningApplied(true);
            decision.setLearningNotes("Decision difference recorded for future learning");

            log.info("Learning applied for decision: {} - AI: {}, Human: {}", 
                decision.getDecisionId(), decision.getAiDecision(), humanDecision);
        }

        updateAutonomousScope(decision);

        return decision;
    }

    private void updateAutonomousScope(InterventionDecision decision) {
        for (AutonomousScope scope : autonomousScopes.values()) {
            if (scope.matches(decision.getOperationType())) {
                scope.incrementExecutionCount();
                
                if (decision.getStatus() == InterventionDecision.DecisionStatus.COMPLETED &&
                    decision.getHumanDecision() == null) {
                    scope.incrementSuccessCount();
                }

                if (scope.getSuccessRate() >= 95 && scope.getExecutionCount() >= 10) {
                    if (scope.getAutonomyLevel() == AutonomyLevel.LIMITED) {
                        scope.setAutonomyLevel(AutonomyLevel.FULL);
                        log.info("Autonomous scope {} upgraded to FULL autonomy", scope.getScopeId());
                    }
                }

                if (scope.getSuccessRate() < 80 && scope.getExecutionCount() >= 5) {
                    if (scope.getAutonomyLevel() == AutonomyLevel.FULL) {
                        scope.setAutonomyLevel(AutonomyLevel.LIMITED);
                        log.warn("Autonomous scope {} downgraded to LIMITED autonomy", scope.getScopeId());
                    }
                }
            }
        }
    }

    @Override
    public InterventionStatistics getStatistics(String department, long since) {
        InterventionStatistics stats = new InterventionStatistics();
        
        List<InterventionDecision> filteredDecisions = allDecisions.values().stream()
            .filter(d -> since <= 0 || d.getCreatedAt().toEpochMilli() >= since)
            .filter(d -> department == null || department.equals(d.getDepartment()))
            .collect(Collectors.toList());

        stats.setTotalEvaluations(filteredDecisions.size());
        stats.setAutoExecuted(filteredDecisions.stream()
            .filter(d -> d.getInterventionType() == InterventionDecision.InterventionType.AUTO_EXECUTE)
            .count());
        stats.setHumanInterventions(filteredDecisions.stream()
            .filter(d -> d.needsHumanIntervention())
            .count());
        stats.setPendingDecisions(filteredDecisions.stream()
            .filter(d -> d.getStatus() == InterventionDecision.DecisionStatus.AWAITING_RESPONSE)
            .count());
        stats.setEscalatedDecisions(filteredDecisions.stream()
            .filter(d -> d.getStatus() == InterventionDecision.DecisionStatus.ESCALATED)
            .count());
        stats.setTimeoutDecisions(filteredDecisions.stream()
            .filter(d -> d.getStatus() == InterventionDecision.DecisionStatus.TIMEOUT)
            .count());

        stats.setAverageRiskScore(filteredDecisions.stream()
            .mapToDouble(InterventionDecision::getRiskScore)
            .average()
            .orElse(0.0));
        stats.setAverageImpactScore(filteredDecisions.stream()
            .mapToDouble(InterventionDecision::getImpactScore)
            .average()
            .orElse(0.0));

        if (stats.getTotalEvaluations() > 0) {
            stats.setInterventionRate((double) stats.getHumanInterventions() / stats.getTotalEvaluations() * 100);
            stats.setAutoExecutionRate((double) stats.getAutoExecuted() / stats.getTotalEvaluations() * 100);
        }

        return stats;
    }

    @Override
    public List<InterventionDecision> getPendingDecisions(String department) {
        return pendingDecisions.values().stream()
            .filter(d -> department == null || department.equals(d.getDepartment()))
            .sorted(Comparator.comparing(InterventionDecision::getCreatedAt).reversed())
            .collect(Collectors.toList());
    }

    @Override
    public void registerRule(InterventionRule rule) {
        rules.put(rule.getRuleId(), rule);
        log.info("Registered intervention rule: {}", rule.getRuleId());
    }

    @Override
    public void unregisterRule(String ruleId) {
        rules.remove(ruleId);
        log.info("Unregistered intervention rule: {}", ruleId);
    }

    @Override
    public List<InterventionRule> getApplicableRules(String operationType) {
        return rules.values().stream()
            .filter(rule -> rule.matches(operationType, new HashMap<>()))
            .sorted(Comparator.comparingInt(InterventionRule::getPriority).reversed())
            .collect(Collectors.toList());
    }

    private String generateDecisionId() {
        return "intv-" + System.currentTimeMillis() + "-" + decisionCounter.incrementAndGet();
    }

    private void publishDecisionMessage(InterventionDecision decision) {
        if (channelPublisher != null && decision.needsHumanIntervention()) {
            ChannelMessage message = ChannelMessage.text(
                "channel://intervention/request",
                "InterventionDecisionEngine",
                "channel://notification/outbound",
                decision.getSessionId() != null ? decision.getSessionId() : "system",
                formatDecisionMessage(decision)
            );
            message.getMetadata().put("decisionId", decision.getDecisionId());
            message.getMetadata().put("interventionType", decision.getInterventionType().name());
            channelPublisher.publish("channel://intervention/request", message);
        }
    }

    private void publishEscalationMessage(InterventionDecision decision) {
        if (channelPublisher != null) {
            ChannelMessage message = ChannelMessage.text(
                "channel://intervention/escalation",
                "InterventionDecisionEngine",
                "channel://notification/outbound",
                decision.getSessionId() != null ? decision.getSessionId() : "system",
                formatEscalationMessage(decision)
            );
            message.getMetadata().put("decisionId", decision.getDecisionId());
            message.getMetadata().put("escalationLevel", decision.getEscalationLevel());
            message.getMetadata().put("assignedTo", decision.getAssignedTo());
            channelPublisher.publish("channel://intervention/escalation", message);
        }
    }

    private String formatDecisionMessage(InterventionDecision decision) {
        return String.format(
            "【干预决策请求】\n" +
            "决策ID: %s\n" +
            "操作类型: %s\n" +
            "风险等级: %s\n" +
            "影响等级: %s\n" +
            "干预类型: %s\n" +
            "AI建议: %s\n" +
            "请及时处理。",
            decision.getDecisionId(),
            decision.getOperationType(),
            decision.getRiskLevel().getDescription(),
            decision.getImpactLevel().getDescription(),
            decision.getInterventionType().getDescription(),
            decision.getAiDecision() != null ? decision.getAiDecision() : "无"
        );
    }

    private String formatEscalationMessage(InterventionDecision decision) {
        return String.format(
            "【干预决策升级通知】\n" +
            "决策ID: %s\n" +
            "操作类型: %s\n" +
            "升级级别: %d\n" +
            "分配给: %s\n" +
            "请尽快处理。",
            decision.getDecisionId(),
            decision.getOperationType(),
            decision.getEscalationLevel(),
            decision.getAssignedTo()
        );
    }

    public enum AutonomyLevel {
        NONE,
        LIMITED,
        FULL
    }

    public static class AutonomousScope {
        private String scopeId;
        private String operationTypePrefix;
        private AutonomyLevel autonomyLevel;
        private InterventionDecision.RiskLevel maxRiskLevel;
        private InterventionDecision.ImpactLevel maxImpactLevel;
        private boolean enabled;
        private long executionCount;
        private long successCount;

        public AutonomousScope() {
            this.enabled = true;
            this.executionCount = 0;
            this.successCount = 0;
        }

        public boolean matches(String operationType) {
            return operationType != null && operationType.startsWith(operationTypePrefix);
        }

        public double getSuccessRate() {
            if (executionCount == 0) return 0.0;
            return (double) successCount / executionCount * 100;
        }

        public void incrementExecutionCount() { executionCount++; }
        public void incrementSuccessCount() { successCount++; }

        public String getScopeId() { return scopeId; }
        public void setScopeId(String scopeId) { this.scopeId = scopeId; }

        public String getOperationTypePrefix() { return operationTypePrefix; }
        public void setOperationTypePrefix(String operationTypePrefix) { this.operationTypePrefix = operationTypePrefix; }

        public AutonomyLevel getAutonomyLevel() { return autonomyLevel; }
        public void setAutonomyLevel(AutonomyLevel autonomyLevel) { this.autonomyLevel = autonomyLevel; }

        public InterventionDecision.RiskLevel getMaxRiskLevel() { return maxRiskLevel; }
        public void setMaxRiskLevel(InterventionDecision.RiskLevel maxRiskLevel) { this.maxRiskLevel = maxRiskLevel; }

        public InterventionDecision.ImpactLevel getMaxImpactLevel() { return maxImpactLevel; }
        public void setMaxImpactLevel(InterventionDecision.ImpactLevel maxImpactLevel) { this.maxImpactLevel = maxImpactLevel; }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public long getExecutionCount() { return executionCount; }
        public void setExecutionCount(long executionCount) { this.executionCount = executionCount; }

        public long getSuccessCount() { return successCount; }
        public void setSuccessCount(long successCount) { this.successCount = successCount; }
    }
}
