package com.livingagent.gateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.evolution.SkillGenerator;
import com.livingagent.core.evolution.executor.EvolutionExecutor;
import com.livingagent.core.evolution.executor.EvolutionResult;
import com.livingagent.core.evolution.signal.EvolutionSignal;
import com.livingagent.core.evolution.signal.SignalExtractor;
import com.livingagent.core.tool.impl.SkillInstaller;
import com.livingagent.core.skill.Skill;
import com.livingagent.core.skill.SkillRegistry;
import com.livingagent.skill.service.SkillBindingService;
import com.livingagent.skill.service.SkillService;
import com.livingagent.skill.hotreload.SkillHotReloader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/admin")
public class EvolutionAdminController {
    
    private static final Logger log = LoggerFactory.getLogger(EvolutionAdminController.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private final SkillService skillService;
    private final SkillRegistry skillRegistry;
    private final SkillGenerator skillGenerator;
    private final SkillInstaller skillInstaller;
    private final SkillBindingService skillBindingService;
    private final SkillHotReloader skillHotReloader;
    private final EvolutionExecutor evolutionExecutor;
    private final SignalExtractor signalExtractor;
    
    public EvolutionAdminController(
            SkillService skillService,
            SkillRegistry skillRegistry,
            SkillGenerator skillGenerator,
            SkillInstaller skillInstaller,
            SkillBindingService skillBindingService,
            SkillHotReloader skillHotReloader,
            EvolutionExecutor evolutionExecutor,
            SignalExtractor signalExtractor) {
        this.skillService = skillService;
        this.skillRegistry = skillRegistry;
        this.skillGenerator = skillGenerator;
        this.skillInstaller = skillInstaller;
        this.skillBindingService = skillBindingService;
        this.skillHotReloader = skillHotReloader;
        this.evolutionExecutor = evolutionExecutor;
        this.signalExtractor = signalExtractor;
    }
    
    @GetMapping("/skills")
    public ResponseEntity<Map<String, Object>> listSkills(
            @RequestParam(required = false) String brain,
            @RequestParam(required = false) String category) {
        
        List<Skill> skills;
        if (brain != null && !brain.isEmpty()) {
            skills = skillService.getSkillsForBrain(brain);
        } else if (category != null && !category.isEmpty()) {
            skills = skillService.getSkillsForCategory(category);
        } else {
            skills = skillService.getAllSkills();
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("total", skills.size());
        result.put("skills", skills.stream().map(this::skillToMap).toList());
        
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/skills/{name}")
    public ResponseEntity<Map<String, Object>> getSkill(@PathVariable String name) {
        return skillService.getSkill(name)
                .map(skill -> ResponseEntity.ok(skillToMap(skill)))
                .orElse(ResponseEntity.notFound().build());
    }
    
    @PostMapping("/skills/reload")
    public ResponseEntity<Map<String, Object>> reloadSkills() {
        log.info("Manual skill reload triggered via API");
        
        long startTime = System.currentTimeMillis();
        int beforeCount = skillRegistry.getAllSkills().size();
        
        skillService.reloadSkills();
        
        int afterCount = skillRegistry.getAllSkills().size();
        long duration = System.currentTimeMillis() - startTime;
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("beforeCount", beforeCount);
        result.put("afterCount", afterCount);
        result.put("newSkills", afterCount - beforeCount);
        result.put("durationMs", duration);
        result.put("message", "Skills reloaded successfully");
        
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/skills/generate")
    public ResponseEntity<Map<String, Object>> generateSkill(@RequestBody SkillGenerateRequest request) {
        log.info("Generating skill via API: {}", request.getRequirement());
        
        Skill skill = skillGenerator.generateSkill(request.getRequirement(), request.getContext());
        
        if (skill == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Skill generation failed");
            return ResponseEntity.badRequest().body(error);
        }
        
        if (!skillGenerator.validateSkill(skill)) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Generated skill validation failed");
            return ResponseEntity.badRequest().body(error);
        }
        
        SkillInstaller.InstallResult installResult = skillInstaller.install(skill.getName(), "local", null);
        
        skillRegistry.registerSkill(skill);
        
        if (request.getTargetNeuron() != null && !request.getTargetNeuron().isEmpty()) {
            skillBindingService.bindSkillToNeuron(skill.getName(), request.getTargetNeuron());
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("skill", skillToMap(skill));
        result.put("installed", installResult.isSuccess());
        result.put("immediateEffective", true);
        result.put("message", "Skill generated, installed and registered successfully");
        
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/skills/{skillName}/install")
    public ResponseEntity<Map<String, Object>> installSkill(
            @PathVariable String skillName,
            @RequestParam(defaultValue = "local") String source,
            @RequestParam(required = false) String version) {
        
        log.info("Installing skill: {} from {}", skillName, source);
        
        SkillInstaller.InstallResult installResult = skillInstaller.install(skillName, source, version);
        
        if (installResult.isSuccess()) {
            skillService.reloadSkills();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("skillId", installResult.getSkillId());
            result.put("source", installResult.getSource());
            result.put("version", installResult.getVersion());
            result.put("immediateEffective", true);
            result.put("message", "Skill installed and loaded successfully");
            
            return ResponseEntity.ok(result);
        } else {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", installResult.getError());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    @DeleteMapping("/skills/{skillName}")
    public ResponseEntity<Map<String, Object>> uninstallSkill(@PathVariable String skillName) {
        log.info("Uninstalling skill: {}", skillName);
        
        boolean uninstalled = skillInstaller.uninstall(skillName);
        
        if (uninstalled) {
            skillService.reloadSkills();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("skillName", skillName);
            result.put("message", "Skill uninstalled successfully");
            
            return ResponseEntity.ok(result);
        } else {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Skill not found or uninstall failed");
            return ResponseEntity.notFound().build();
        }
    }
    
    @PostMapping("/skills/{skillName}/bind/{neuronId}")
    public ResponseEntity<Map<String, Object>> bindSkillToNeuron(
            @PathVariable String skillName,
            @PathVariable String neuronId) {
        
        log.info("Binding skill {} to neuron {}", skillName, neuronId);
        
        skillBindingService.bindSkillToNeuron(skillName, neuronId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("skillName", skillName);
        result.put("neuronId", neuronId);
        result.put("immediateEffective", true);
        result.put("message", "Skill bound to neuron successfully");
        
        return ResponseEntity.ok(result);
    }
    
    @DeleteMapping("/skills/{skillName}/bind/{neuronId}")
    public ResponseEntity<Map<String, Object>> unbindSkillFromNeuron(
            @PathVariable String skillName,
            @PathVariable String neuronId) {
        
        log.info("Unbinding skill {} from neuron {}", skillName, neuronId);
        
        skillBindingService.unbindSkillFromNeuron(skillName, neuronId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("skillName", skillName);
        result.put("neuronId", neuronId);
        result.put("message", "Skill unbound from neuron successfully");
        
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/bindings")
    public ResponseEntity<Map<String, Object>> getBindings() {
        Map<String, Object> result = new HashMap<>();
        result.put("bindings", skillBindingService.getAllBindings());
        result.put("stats", skillBindingService.getBindingStats());
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/hotreload/status")
    public ResponseEntity<Map<String, Object>> getHotReloadStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", skillHotReloader.isHotReloadEnabled());
        status.put("running", skillHotReloader.isRunning());
        status.put("watchedPath", skillHotReloader.getWatchedPath());
        return ResponseEntity.ok(status);
    }
    
    @PostMapping("/hotreload/trigger")
    public ResponseEntity<Map<String, Object>> triggerHotReload() {
        log.info("Manual hot reload triggered via API");
        
        skillHotReloader.manualReload();
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Hot reload triggered successfully");
        
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/evolution/trigger")
    public ResponseEntity<Map<String, Object>> triggerEvolution(@RequestBody EvolutionTriggerRequest request) {
        log.info("Evolution triggered via API: type={}, domain={}", request.getType(), request.getBrainDomain());
        
        EvolutionSignal signal = new EvolutionSignal(
                EvolutionSignal.SignalType.valueOf(request.getType()),
                request.getContent()
        );
        signal.setBrainDomain(request.getBrainDomain());
        signal.setConfidence(request.getConfidence() != null ? request.getConfidence() : 0.8);
        
        EvolutionResult result = evolutionExecutor.execute(signal);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", result.isSuccess());
        response.put("result", result.toMap());
        response.put("immediateEffective", result.isImmediateEffective());
        
        if (result.isSuccess() && result.getGeneratedSkillId() != null) {
            response.put("message", "Evolution completed, new skill available immediately: " + result.getGeneratedSkillId());
        } else if (result.isSuccess()) {
            response.put("message", "Evolution completed successfully");
        } else {
            response.put("message", "Evolution failed: " + result.getErrorMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/evolution/results")
    public ResponseEntity<Map<String, Object>> getEvolutionResults(
            @RequestParam(defaultValue = "10") int limit) {
        
        Map<String, Object> result = new HashMap<>();
        result.put("results", evolutionExecutor.getRecentResults(limit).stream().map(EvolutionResult::toMap).toList());
        result.put("statistics", evolutionExecutor.getStatistics());
        
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/evolution/results/{resultId}")
    public ResponseEntity<Map<String, Object>> getEvolutionResult(@PathVariable String resultId) {
        EvolutionResult result = evolutionExecutor.getResult(resultId);
        
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("result", result.toMap());
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/evolution/extract-signals")
    public ResponseEntity<Map<String, Object>> extractSignals(@RequestBody SignalExtractRequest request) {
        log.info("Extracting evolution signals from: {}", request.getSource());
        
        List<EvolutionSignal> signals;
        switch (request.getSource().toLowerCase()) {
            case "logs" -> signals = signalExtractor.extractFromLogs(request.getContent());
            case "feedback" -> signals = signalExtractor.extractFromUserFeedback(request.getContent());
            case "metrics" -> {
                Map<String, Object> metricsMap = parseMetricsContent(request.getContent());
                signals = signalExtractor.extractFromMetrics(metricsMap);
            }
            default -> signals = signalExtractor.extractFromLogs(request.getContent());
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("signals", signals.stream().map(this::signalToMap).toList());
        result.put("count", signals.size());
        
        return ResponseEntity.ok(result);
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMetricsContent(String content) {
        try {
            return objectMapper.readValue(content, Map.class);
        } catch (Exception e) {
            log.warn("Failed to parse metrics content as JSON, returning empty map: {}", e.getMessage());
            return Map.of("raw", content);
        }
    }
    
    private Map<String, Object> skillToMap(Skill skill) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", skill.getName());
        map.put("description", skill.getDescription());
        map.put("category", skill.getCategory());
        map.put("targetBrain", skill.getTargetBrain());
        map.put("skillPath", skill.getSkillPath());
        map.put("contentLength", skill.getContent() != null ? skill.getContent().length() : 0);
        return map;
    }
    
    private Map<String, Object> signalToMap(EvolutionSignal signal) {
        Map<String, Object> map = new HashMap<>();
        map.put("signalId", signal.getSignalId());
        map.put("type", signal.getType().name());
        map.put("category", signal.getCategory().name());
        map.put("brainDomain", signal.getBrainDomain());
        map.put("confidence", signal.getConfidence());
        map.put("content", signal.getContent());
        return map;
    }
    
    public static class SkillGenerateRequest {
        private String requirement;
        private Map<String, Object> context = new HashMap<>();
        private String targetNeuron;
        
        public String getRequirement() { return requirement; }
        public void setRequirement(String requirement) { this.requirement = requirement; }
        
        public Map<String, Object> getContext() { return context; }
        public void setContext(Map<String, Object> context) { this.context = context; }
        
        public String getTargetNeuron() { return targetNeuron; }
        public void setTargetNeuron(String targetNeuron) { this.targetNeuron = targetNeuron; }
    }
    
    public static class EvolutionTriggerRequest {
        private String type;
        private String content;
        private String brainDomain;
        private Double confidence;
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        
        public String getBrainDomain() { return brainDomain; }
        public void setBrainDomain(String brainDomain) { this.brainDomain = brainDomain; }
        
        public Double getConfidence() { return confidence; }
        public void setConfidence(Double confidence) { this.confidence = confidence; }
    }
    
    public static class SignalExtractRequest {
        private String source;
        private String content;
        
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }
}
