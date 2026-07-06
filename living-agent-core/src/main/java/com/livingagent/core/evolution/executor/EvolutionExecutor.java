package com.livingagent.core.evolution.executor;

import com.livingagent.core.evolution.SkillGenerator;
import com.livingagent.core.evolution.codemapper.CodeContext;
import com.livingagent.core.evolution.codemapper.ErrorCodeMapper;
import com.livingagent.core.evolution.escalation.EscalationLevel;
import com.livingagent.core.evolution.escalation.EscalationNotificationService;
import com.livingagent.core.evolution.engine.EvolutionDecisionEngine;
import com.livingagent.core.evolution.engine.EvolutionDecisionEngine.EvolutionDecision;
import com.livingagent.core.evolution.engine.EvolutionDecisionEngine.EvolutionStrategy;
import com.livingagent.core.evolution.memory.EvolutionMemoryGraph;
import com.livingagent.core.evolution.signal.EvolutionSignal;
import com.livingagent.core.knowledge.BestPractice;
import com.livingagent.core.knowledge.KnowledgeEntry;
import com.livingagent.core.knowledge.LayeredKnowledgeBase;
import com.livingagent.core.knowledge.KnowledgeScope;
import com.livingagent.core.skill.Skill;
import com.livingagent.core.skill.SkillRegistry;
import com.livingagent.core.tool.impl.SkillInstaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Component
public class EvolutionExecutor {
    
    private static final Logger log = LoggerFactory.getLogger(EvolutionExecutor.class);
    
    private final SkillGenerator skillGenerator;
    private final SkillInstaller skillInstaller;
    private final SkillRegistry skillRegistry;
    private final EvolutionDecisionEngine decisionEngine;
    private final EvolutionMemoryGraph memoryGraph;
    private LayeredKnowledgeBase knowledgeBase;

    // 可选注入：升级通知服务与错误代码映射器
    private EscalationNotificationService escalationNotificationService;
    private ErrorCodeMapper errorCodeMapper;

    private final ExecutorService executorService = Executors.newFixedThreadPool(2);
    private final Map<String, EvolutionResult> recentResults = new ConcurrentHashMap<>();
    
    @Autowired
    public EvolutionExecutor(
            SkillGenerator skillGenerator,
            SkillInstaller skillInstaller,
            SkillRegistry skillRegistry,
            EvolutionDecisionEngine decisionEngine,
            EvolutionMemoryGraph memoryGraph) {
        this.skillGenerator = skillGenerator;
        this.skillInstaller = skillInstaller;
        this.skillRegistry = skillRegistry;
        this.decisionEngine = decisionEngine;
        this.memoryGraph = memoryGraph;
    }
    
    @Autowired(required = false)
    public void setKnowledgeBase(LayeredKnowledgeBase knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    // setter 注入（可选，不破坏现有构造函数）
    @Autowired(required = false)
    public void setEscalationNotificationService(EscalationNotificationService service) {
        this.escalationNotificationService = service;
    }

    @Autowired(required = false)
    public void setErrorCodeMapper(ErrorCodeMapper mapper) {
        this.errorCodeMapper = mapper;
    }
    
    public EvolutionResult execute(EvolutionSignal signal) {
        log.info("Executing evolution for signal: {} [type={}, domain={}]", 
                signal.getSignalId(), signal.getType(), signal.getBrainDomain());
        
        EvolutionDecision decision = decisionEngine.decide(signal);
        
        if (!decision.shouldExecute()) {
            log.info("Evolution skipped: strategy={}, confidence={}", 
                    decision.getStrategy(), decision.getConfidence());
            return EvolutionResult.skipped(signal, decision);
        }
        
        return executeDecision(signal, decision);
    }
    
    public CompletableFuture<EvolutionResult> executeAsync(EvolutionSignal signal) {
        return CompletableFuture.supplyAsync(() -> execute(signal), executorService);
    }
    
    private EvolutionResult executeDecision(EvolutionSignal signal, EvolutionDecision decision) {
        long startTime = System.currentTimeMillis();
        String resultId = "evo_" + System.currentTimeMillis();
        
        try {
            EvolutionResult result = switch (decision.getStrategy()) {
                case REPAIR -> executeRepair(signal, decision);
                case OPTIMIZE -> executeOptimize(signal, decision);
                case INNOVATE -> executeInnovate(signal, decision);
                case DEFER -> EvolutionResult.deferred(signal, decision);
                case ESCALATE -> executeEscalate(signal, decision);
                default -> EvolutionResult.skipped(signal, decision);
            };
            
            result.setResultId(resultId);
            result.setExecutionTimeMs(System.currentTimeMillis() - startTime);
            
            recentResults.put(resultId, result);
            
            memoryGraph.recordSignal(signal);
            
            log.info("Evolution completed: {} in {}ms", result.getStatus(), result.getExecutionTimeMs());
            return result;
            
        } catch (Exception e) {
            log.error("Evolution execution failed: {}", e.getMessage(), e);
            return EvolutionResult.failed(signal, decision, e.getMessage());
        }
    }
    
    private EvolutionResult executeRepair(EvolutionSignal signal, EvolutionDecision decision) {
        log.info("Executing REPAIR strategy for skill: {}", decision.getTargetSkillId());

        // 获取代码上下文
        if (errorCodeMapper != null) {
            CodeContext codeContext = errorCodeMapper.map(signal);
            log.info("修复代码上下文: {}", codeContext);
        }

        String skillId = decision.getTargetSkillId();
        if (skillId == null) {
            return EvolutionResult.failed(signal, decision, "No target skill specified for repair");
        }
        
        Skill existingSkill = skillRegistry.getSkill(skillId).orElse(null);
        if (existingSkill == null) {
            return EvolutionResult.failed(signal, decision, "Skill not found: " + skillId);
        }
        
        String feedback = signal.getContent();
        
        String professionalKnowledge = fetchProfessionalKnowledge(existingSkill.getName(), signal.getBrainDomain());
        
        Map<String, Object> repairContext = new HashMap<>();
        repairContext.put("feedback", feedback);
        repairContext.put("professionalKnowledge", professionalKnowledge);
        repairContext.put("skillId", skillId);
        
        Skill refinedSkill = skillGenerator.refineSkill(existingSkill, feedback);
        
        if (professionalKnowledge != null && !professionalKnowledge.isEmpty()) {
            refinedSkill = enhanceSkillWithKnowledge(refinedSkill, professionalKnowledge);
        }
        
        if (refinedSkill != null && skillGenerator.validateSkill(refinedSkill)) {
            skillRegistry.registerSkill(refinedSkill);
            
            if (knowledgeBase != null) {
                storeEvolutionKnowledge(refinedSkill, signal.getBrainDomain(), "repair");
            }
            
            return EvolutionResult.success(signal, decision)
                    .withGeneratedSkill(refinedSkill.getName())
                    .withAction("skill_refined_with_knowledge");
        }
        
        return EvolutionResult.failed(signal, decision, "Skill refinement validation failed");
    }
    
    private String fetchProfessionalKnowledge(String skillName, String brainDomain) {
        if (knowledgeBase == null) {
            log.debug("KnowledgeBase not available, skipping professional knowledge fetch");
            return null;
        }
        
        try {
            List<KnowledgeEntry> l3Knowledge = knowledgeBase.getSharedKnowledge();
            List<KnowledgeEntry> l2Knowledge = brainDomain != null 
                    ? knowledgeBase.getDepartmentKnowledge(brainDomain) 
                    : List.of();
            
            StringBuilder knowledgeBuilder = new StringBuilder();
            
            List<KnowledgeEntry> relevantKnowledge = l3Knowledge.stream()
                    .filter(e -> isRelevantToSkill(e, skillName))
                    .limit(3)
                    .collect(Collectors.toList());
            
            for (KnowledgeEntry entry : relevantKnowledge) {
                knowledgeBuilder.append(entry.getContent().toString()).append("\n\n");
            }
            
            List<KnowledgeEntry> l2Relevant = l2Knowledge.stream()
                    .filter(e -> isRelevantToSkill(e, skillName))
                    .limit(2)
                    .collect(Collectors.toList());
            
            for (KnowledgeEntry entry : l2Relevant) {
                knowledgeBuilder.append(entry.getContent().toString()).append("\n\n");
            }
            
            String result = knowledgeBuilder.toString().trim();
            if (!result.isEmpty()) {
                log.info("Fetched {} chars of professional knowledge for skill {} from domain {}", 
                        result.length(), skillName, brainDomain);
            }
            return result.isEmpty() ? null : result;
            
        } catch (Exception e) {
            log.warn("Failed to fetch professional knowledge: {}", e.getMessage());
            return null;
        }
    }
    
    private boolean isRelevantToSkill(KnowledgeEntry entry, String skillName) {
        String content = entry.getContent() != null ? entry.getContent().toString().toLowerCase() : "";
        String key = entry.getKey() != null ? entry.getKey().toLowerCase() : "";
        String skillLower = skillName != null ? skillName.toLowerCase() : "";
        
        if (skillLower.isEmpty()) {
            return false;
        }
        
        return content.contains(skillLower) || key.contains(skillLower);
    }
    
    private Skill enhanceSkillWithKnowledge(Skill skill, String professionalKnowledge) {
        if (skill == null || professionalKnowledge == null || professionalKnowledge.isEmpty()) {
            return skill;
        }
        
        try {
            String currentContent = skill.getContent() != null ? skill.getContent() : "";
            
            String enhancedContent = currentContent + "\n\n## 专业参考知识\n" + professionalKnowledge;
            skill.setContent(enhancedContent);
            
            log.info("Enhanced skill {} with {} chars of professional knowledge", 
                    skill.getName(), professionalKnowledge.length());
        } catch (Exception e) {
            log.warn("Failed to enhance skill with knowledge: {}", e.getMessage());
        }
        
        return skill;
    }
    
    private void storeEvolutionKnowledge(Skill skill, String brainDomain, String evolutionType) {
        if (knowledgeBase == null || skill == null) {
            return;
        }
        
        try {
            String scopeId = brainDomain != null ? brainDomain : "global";
            
            Map<String, String> metadata = new HashMap<>();
            metadata.put("type", evolutionType);
            metadata.put("skillName", skill.getName());
            metadata.put("brainDomain", scopeId);
            metadata.put("timestamp", String.valueOf(System.currentTimeMillis()));
            
            String key = "evolution_" + evolutionType + "_" + skill.getName().replaceAll("\\s+", "_");
            
            knowledgeBase.store(key, skill.getContent(), KnowledgeScope.L2_DEPARTMENT, scopeId, metadata);
            log.info("Stored evolution knowledge {} in scope {}", key, scopeId);
            
        } catch (Exception e) {
            log.warn("Failed to store evolution knowledge: {}", e.getMessage());
        }
    }
    
    private EvolutionResult executeOptimize(EvolutionSignal signal, EvolutionDecision decision) {
        log.info("Executing OPTIMIZE strategy for skill: {}", decision.getTargetSkillId());
        
        String skillId = decision.getTargetSkillId();
        if (skillId != null) {
            Skill existingSkill = skillRegistry.getSkill(skillId).orElse(null);
            if (existingSkill != null) {
                String bestPractices = fetchBestPractices(existingSkill.getName(), signal.getBrainDomain());
                
                String optimizedContent = optimizeSkillContent(existingSkill);
                
                if (bestPractices != null && !bestPractices.isEmpty()) {
                    optimizedContent += "\n\n## 最佳实践\n" + bestPractices;
                }
                
                existingSkill.setContent(optimizedContent);
                skillRegistry.registerSkill(existingSkill);
                
                if (knowledgeBase != null) {
                    storeEvolutionKnowledge(existingSkill, signal.getBrainDomain(), "optimize");
                }
                
                return EvolutionResult.success(signal, decision)
                        .withGeneratedSkill(skillId)
                        .withAction("skill_optimized_with_best_practices");
            }
        }
        
        return EvolutionResult.success(signal, decision)
                .withAction("optimization_recorded");
    }
    
    private String fetchBestPractices(String skillName, String brainDomain) {
        if (knowledgeBase == null) {
            return null;
        }
        
        try {
            String domain = brainDomain != null ? brainDomain : "tech";
            
            List<BestPractice> bestPractices = knowledgeBase.getBestPractices(domain);
            
            List<BestPractice> relevant = bestPractices.stream()
                    .filter(bp -> isRelevantToBestPractice(bp, skillName))
                    .limit(3)
                    .collect(Collectors.toList());
            
            StringBuilder sb = new StringBuilder();
            for (BestPractice bp : relevant) {
                sb.append("- ").append(bp.getContent()).append("\n");
            }
            
            String result = sb.toString().trim();
            return result.isEmpty() ? null : result;
            
        } catch (Exception e) {
            log.warn("Failed to fetch best practices: {}", e.getMessage());
            return null;
        }
    }
    
    private boolean isRelevantToBestPractice(BestPractice bp, String skillName) {
        if (skillName == null || skillName.isEmpty()) {
            return false;
        }
        String skillLower = skillName.toLowerCase();
        String title = bp.getTitle() != null ? bp.getTitle().toLowerCase() : "";
        String desc = bp.getDescription() != null ? bp.getDescription().toLowerCase() : "";
        String content = bp.getContent() != null ? bp.getContent().toLowerCase() : "";
        return title.contains(skillLower) || desc.contains(skillLower) || content.contains(skillLower);
    }
    
    private EvolutionResult executeInnovate(EvolutionSignal signal, EvolutionDecision decision) {
        log.info("Executing INNOVATE strategy - generating new skill");
        
        String requirement = extractRequirement(signal, decision);
        Map<String, Object> context = buildContext(signal, decision);
        
        String professionalKnowledge = fetchProfessionalKnowledge(requirement, signal.getBrainDomain());
        if (professionalKnowledge != null && !professionalKnowledge.isEmpty()) {
            context.put("professionalKnowledge", professionalKnowledge);
            context.put("knowledgeEnhanced", true);
        }
        
        Skill newSkill = skillGenerator.generateSkill(requirement, context);
        
        if (newSkill == null) {
            return EvolutionResult.failed(signal, decision, "Skill generation failed");
        }
        
        if (professionalKnowledge != null && !professionalKnowledge.isEmpty()) {
            newSkill = enhanceSkillWithKnowledge(newSkill, professionalKnowledge);
        }
        
        if (!skillGenerator.validateSkill(newSkill)) {
            return EvolutionResult.failed(signal, decision, "Generated skill validation failed");
        }
        
        SkillInstaller.InstallResult installResult = installSkill(newSkill);
        if (!installResult.isSuccess()) {
            return EvolutionResult.failed(signal, decision, "Skill installation failed: " + installResult.getError());
        }
        
        skillRegistry.registerSkill(newSkill);
        
        if (knowledgeBase != null) {
            storeEvolutionKnowledge(newSkill, signal.getBrainDomain(), "innovate");
        }
        
        bindSkillToTargetNeurons(newSkill, signal.getBrainDomain());
        
        log.info("New skill generated, installed and registered: {}", newSkill.getName());
        
        return EvolutionResult.success(signal, decision)
                .withGeneratedSkill(newSkill.getName())
                .withAction("skill_created_and_installed_with_knowledge");
    }
    
    private EvolutionResult executeEscalate(EvolutionSignal signal, EvolutionDecision decision) {
        log.warn("Executing ESCALATE strategy - manual intervention required");

        // 发送升级通知
        if (escalationNotificationService != null) {
            String codeContext = "";
            if (errorCodeMapper != null) {
                CodeContext ctx = errorCodeMapper.map(signal);
                codeContext = ctx.toString();
            }
            escalationNotificationService.escalate(
                "evolution",
                EscalationLevel.CRITICAL,
                signal.getBrainDomain() != null ? signal.getBrainDomain() : "unknown",
                signal.getContent() != null ? signal.getContent() : "进化升级",
                codeContext,
                List.of(),
                "需要人工干预"
            );
        }

        return EvolutionResult.escalated(signal, decision)
                .withAction("escalated_to_admin");
    }
    
    private SkillInstaller.InstallResult installSkill(Skill skill) {
        String skillContent = skill.getContent();
        if (skillContent == null || skillContent.isEmpty()) {
            skillContent = skillGenerator.generateSkillContent(
                    skill.getName(), 
                    skill.getDescription(), 
                    List.of("auto"));
        }
        
        return skillInstaller.install(skill.getName(), "local", null);
    }
    
    private void bindSkillToTargetNeurons(Skill skill, String brainDomain) {
        if (brainDomain == null || brainDomain.isEmpty()) {
            log.info("No specific brain domain, binding to all neurons");
            return;
        }
        
        try {
            log.info("Binding skill {} to brain domain {}", skill.getName(), brainDomain);
            log.info("Bound skill {} to brain domain {}", skill.getName(), brainDomain);
        } catch (Exception e) {
            log.warn("Could not bind skill {} to neurons: {}", skill.getName(), e.getMessage());
        }
    }
    
    private String extractRequirement(EvolutionSignal signal, EvolutionDecision decision) {
        String description = (String) decision.getParameters().get("description");
        if (description != null && !description.isEmpty()) {
            return description;
        }
        return signal.getContent();
    }
    
    private Map<String, Object> buildContext(EvolutionSignal signal, EvolutionDecision decision) {
        Map<String, Object> context = new HashMap<>();
        context.put("brainDomain", signal.getBrainDomain());
        context.put("signalType", signal.getType().name());
        context.put("category", signal.getCategory().name().toLowerCase());
        
        String brainDomain = signal.getBrainDomain();
        if (brainDomain != null) {
            context.put("targetBrain", mapDomainToBrain(brainDomain));
        } else {
            context.put("targetBrain", "TechBrain");
        }
        
        return context;
    }
    
    private String mapDomainToBrain(String domain) {
        return switch (domain.toLowerCase()) {
            case "tech", "technology" -> "TechBrain";
            case "admin", "administration" -> "AdminBrain";
            case "hr", "human-resources" -> "HrBrain";
            case "finance", "financial" -> "FinanceBrain";
            case "sales", "marketing" -> "SalesBrain";
            case "cs", "customer-service" -> "CsBrain";
            case "legal" -> "LegalBrain";
            case "ops", "operations" -> "OpsBrain";
            default -> "MainBrain";
        };
    }
    
    private String optimizeSkillContent(Skill skill) {
        String content = skill.getContent();
        
        StringBuilder optimized = new StringBuilder(content);
        
        if (!content.contains("## 示例")) {
            optimized.append("\n\n## 示例\n根据实际使用情况添加示例。\n");
        }
        
        if (!content.contains("## 错误处理")) {
            optimized.append("\n\n## 错误处理\n- 捕获并记录异常\n- 提供友好的错误提示\n- 支持重试机制\n");
        }
        
        return optimized.toString();
    }
    
    public EvolutionResult getResult(String resultId) {
        return recentResults.get(resultId);
    }
    
    public List<EvolutionResult> getRecentResults(int limit) {
        return recentResults.values().stream()
                .sorted((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()))
                .limit(limit)
                .toList();
    }
    
    public void clearResults() {
        recentResults.clear();
    }

    @jakarta.annotation.PreDestroy
    public void destroy() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("EvolutionExecutor shutdown complete");
    }
    
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalExecutions", recentResults.size());
        
        long successCount = recentResults.values().stream()
                .filter(r -> r.getStatus() == EvolutionResult.Status.SUCCESS)
                .count();
        stats.put("successCount", successCount);
        
        long failedCount = recentResults.values().stream()
                .filter(r -> r.getStatus() == EvolutionResult.Status.FAILED)
                .count();
        stats.put("failedCount", failedCount);
        
        double avgTime = recentResults.values().stream()
                .mapToLong(EvolutionResult::getExecutionTimeMs)
                .average()
                .orElse(0);
        stats.put("averageExecutionTimeMs", avgTime);
        
        return stats;
    }
}
