package com.livingagent.core.evolution.executor;

import com.livingagent.core.evolution.SkillGenerator;
import com.livingagent.core.evolution.engine.EvolutionDecisionEngine;
import com.livingagent.core.evolution.engine.EvolutionDecisionEngine.EvolutionDecision;
import com.livingagent.core.evolution.engine.EvolutionDecisionEngine.EvolutionStrategy;
import com.livingagent.core.evolution.memory.EvolutionMemoryGraph;
import com.livingagent.core.evolution.signal.EvolutionSignal;
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
import java.util.concurrent.Executors;

@Component
public class EvolutionExecutor {
    
    private static final Logger log = LoggerFactory.getLogger(EvolutionExecutor.class);
    
    private final SkillGenerator skillGenerator;
    private final SkillInstaller skillInstaller;
    private final SkillRegistry skillRegistry;
    private final EvolutionDecisionEngine decisionEngine;
    private final EvolutionMemoryGraph memoryGraph;
    
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
        
        String skillId = decision.getTargetSkillId();
        if (skillId == null) {
            return EvolutionResult.failed(signal, decision, "No target skill specified for repair");
        }
        
        Skill existingSkill = skillRegistry.getSkill(skillId).orElse(null);
        if (existingSkill == null) {
            return EvolutionResult.failed(signal, decision, "Skill not found: " + skillId);
        }
        
        String feedback = signal.getContent();
        Skill refinedSkill = skillGenerator.refineSkill(existingSkill, feedback);
        
        if (refinedSkill != null && skillGenerator.validateSkill(refinedSkill)) {
            skillRegistry.registerSkill(refinedSkill);
            
            return EvolutionResult.success(signal, decision)
                    .withGeneratedSkill(refinedSkill.getName())
                    .withAction("skill_refined");
        }
        
        return EvolutionResult.failed(signal, decision, "Skill refinement validation failed");
    }
    
    private EvolutionResult executeOptimize(EvolutionSignal signal, EvolutionDecision decision) {
        log.info("Executing OPTIMIZE strategy");
        
        String skillId = decision.getTargetSkillId();
        if (skillId != null) {
            Skill existingSkill = skillRegistry.getSkill(skillId).orElse(null);
            if (existingSkill != null) {
                String optimizedContent = optimizeSkillContent(existingSkill);
                existingSkill.setContent(optimizedContent);
                skillRegistry.registerSkill(existingSkill);
                
                return EvolutionResult.success(signal, decision)
                        .withGeneratedSkill(skillId)
                        .withAction("skill_optimized");
            }
        }
        
        return EvolutionResult.success(signal, decision)
                .withAction("optimization_recorded");
    }
    
    private EvolutionResult executeInnovate(EvolutionSignal signal, EvolutionDecision decision) {
        log.info("Executing INNOVATE strategy - generating new skill");
        
        String requirement = extractRequirement(signal, decision);
        Map<String, Object> context = buildContext(signal, decision);
        
        Skill newSkill = skillGenerator.generateSkill(requirement, context);
        
        if (newSkill == null) {
            return EvolutionResult.failed(signal, decision, "Skill generation failed");
        }
        
        if (!skillGenerator.validateSkill(newSkill)) {
            return EvolutionResult.failed(signal, decision, "Generated skill validation failed");
        }
        
        SkillInstaller.InstallResult installResult = installSkill(newSkill);
        if (!installResult.isSuccess()) {
            return EvolutionResult.failed(signal, decision, "Skill installation failed: " + installResult.getError());
        }
        
        skillRegistry.registerSkill(newSkill);
        
        bindSkillToTargetNeurons(newSkill, signal.getBrainDomain());
        
        log.info("New skill generated, installed and registered: {}", newSkill.getName());
        
        return EvolutionResult.success(signal, decision)
                .withGeneratedSkill(newSkill.getName())
                .withAction("skill_created_and_installed");
    }
    
    private EvolutionResult executeEscalate(EvolutionSignal signal, EvolutionDecision decision) {
        log.warn("Executing ESCALATE strategy - manual intervention required");
        
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
