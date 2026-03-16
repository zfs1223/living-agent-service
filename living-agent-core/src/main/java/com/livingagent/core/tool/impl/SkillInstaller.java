package com.livingagent.core.tool.impl;

import com.livingagent.core.skill.Skill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SkillInstaller {

    private static final Logger log = LoggerFactory.getLogger(SkillInstaller.class);

    private static final String GITHUB_RAW_URL = "https://raw.githubusercontent.com";
    
    @Value("${skill.data.path:./data/skills}")
    private String skillsDataDir;
    
    private final HttpClient httpClient;
    private final Map<String, String> remoteRepositories;

    public SkillInstaller() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        this.remoteRepositories = new ConcurrentHashMap<>();
        
        this.remoteRepositories.put("clawhub", "https://hub.claw.ai/api/skills");
        this.remoteRepositories.put("github", GITHUB_RAW_URL);
    }

    public InstallResult install(String skillId, String source, String version) {
        log.info("Installing skill: {} from {} (version: {})", skillId, source, version);
        
        try {
            Path skillDir = Paths.get(skillsDataDir, skillId);
            
            if (Files.exists(skillDir)) {
                log.info("Skill {} already installed locally, updating...", skillId);
                return updateExistingSkill(skillId, skillDir);
            }
            
            Files.createDirectories(skillDir);
            
            switch (source.toLowerCase()) {
                case "local":
                    return installFromLocal(skillId, skillDir);
                case "remote":
                case "clawhub":
                    return installFromRemote(skillId, skillDir, version);
                case "github":
                    return installFromGithub(skillId, skillDir, version);
                case "memory":
                case "generated":
                    return InstallResult.success(skillId, "memory", "generated");
                default:
                    return InstallResult.failure("Unknown source: " + source);
            }
            
        } catch (Exception e) {
            log.error("Failed to install skill {}: {}", skillId, e.getMessage());
            return InstallResult.failure(e.getMessage());
        }
    }
    
    public InstallResult installFromSkillObject(Skill skill) {
        if (skill == null || skill.getName() == null) {
            return InstallResult.failure("Invalid skill object");
        }
        
        log.info("Installing skill from object: {}", skill.getName());
        
        try {
            Path skillDir = Paths.get(skillsDataDir, skill.getName());
            Files.createDirectories(skillDir);
            
            String content = skill.getContent();
            if (content == null || content.isEmpty()) {
                content = generateDefaultContent(skill);
            }
            
            Path skillFile = skillDir.resolve("SKILL.md");
            Files.writeString(skillFile, content);
            
            log.info("Skill {} installed from memory object", skill.getName());
            return InstallResult.success(skill.getName(), "generated", "1.0.0");
            
        } catch (Exception e) {
            log.error("Failed to install skill from object: {}", e.getMessage());
            return InstallResult.failure(e.getMessage());
        }
    }
    
    public InstallResult installFromContent(String skillId, String content) {
        if (skillId == null || skillId.isEmpty()) {
            return InstallResult.failure("Skill ID is required");
        }
        
        if (content == null || content.isEmpty()) {
            return InstallResult.failure("Skill content is required");
        }
        
        log.info("Installing skill from content: {}", skillId);
        
        try {
            Path skillDir = Paths.get(skillsDataDir, skillId);
            Files.createDirectories(skillDir);
            
            Path skillFile = skillDir.resolve("SKILL.md");
            Files.writeString(skillFile, content);
            
            log.info("Skill {} installed from content", skillId);
            return InstallResult.success(skillId, "content", "1.0.0");
            
        } catch (Exception e) {
            log.error("Failed to install skill from content: {}", e.getMessage());
            return InstallResult.failure(e.getMessage());
        }
    }
    
    private InstallResult updateExistingSkill(String skillId, Path skillDir) {
        try {
            Path skillFile = skillDir.resolve("SKILL.md");
            if (Files.exists(skillFile)) {
                return InstallResult.success(skillId, "local", "existing");
            }
            return InstallResult.success(skillId, "local", "existing_dir");
        } catch (Exception e) {
            return InstallResult.failure("Failed to update skill: " + e.getMessage());
        }
    }

    private InstallResult installFromLocal(String skillId, Path skillDir) {
        Path sourceDir = Paths.get("./skills", skillId);
        
        if (!Files.exists(sourceDir)) {
            return InstallResult.failure("Skill not found in local directory: " + sourceDir);
        }
        
        try {
            copyDirectory(sourceDir, skillDir);
            log.info("Skill {} installed from local", skillId);
            return InstallResult.success(skillId, "local", "1.0.0");
        } catch (Exception e) {
            return InstallResult.failure("Failed to copy skill: " + e.getMessage());
        }
    }

    private InstallResult installFromRemote(String skillId, Path skillDir, String version) {
        String hubUrl = remoteRepositories.get("clawhub");
        if (hubUrl == null) {
            return InstallResult.failure("Remote hub not configured");
        }
        
        try {
            String apiUrl = hubUrl + "/" + skillId + (version != null ? "/" + version : "/latest");
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                return InstallResult.failure("Skill not found on remote hub: HTTP " + response.statusCode());
            }
            
            String skillContent = response.body();
            Path skillFile = skillDir.resolve("SKILL.md");
            Files.writeString(skillFile, skillContent);
            
            log.info("Skill {} installed from remote hub", skillId);
            return InstallResult.success(skillId, "remote", version != null ? version : "latest");
            
        } catch (Exception e) {
            return InstallResult.failure("Failed to download skill: " + e.getMessage());
        }
    }

    private InstallResult installFromGithub(String skillId, Path skillDir, String version) {
        String[] parts = skillId.split("/");
        String skillPath;
        
        if (parts.length >= 3) {
            skillPath = String.join("/", parts);
        } else {
            skillPath = "living-agent-skills/skills/" + skillId;
        }
        
        try {
            String rawUrl = GITHUB_RAW_URL + "/" + skillPath + "/main/SKILL.md";
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(rawUrl))
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                return InstallResult.failure("Skill not found on GitHub: HTTP " + response.statusCode());
            }
            
            String skillContent = response.body();
            Path skillFile = skillDir.resolve("SKILL.md");
            Files.writeString(skillFile, skillContent);
            
            log.info("Skill {} installed from GitHub", skillId);
            return InstallResult.success(skillId, "github", version != null ? version : "main");
            
        } catch (Exception e) {
            return InstallResult.failure("Failed to download from GitHub: " + e.getMessage());
        }
    }
    
    private String generateDefaultContent(Skill skill) {
        StringBuilder content = new StringBuilder();
        content.append("---\n");
        content.append("name: ").append(skill.getName()).append("\n");
        if (skill.getDescription() != null) {
            content.append("description: ").append(skill.getDescription()).append("\n");
        }
        if (skill.getCategory() != null) {
            content.append("category: ").append(skill.getCategory()).append("\n");
        }
        if (skill.getTargetBrain() != null) {
            content.append("targetBrain: ").append(skill.getTargetBrain()).append("\n");
        }
        content.append("---\n\n");
        content.append("# ").append(skill.getName()).append("\n\n");
        if (skill.getDescription() != null) {
            content.append(skill.getDescription()).append("\n\n");
        }
        content.append("## 触发条件\n- 自动生成\n\n");
        content.append("## 执行步骤\n1. 分析需求\n2. 执行操作\n3. 返回结果\n\n");
        content.append("## 注意事项\n- 自动生成的技能\n");
        return content.toString();
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source)
            .forEach(sourcePath -> {
                try {
                    Path targetPath = target.resolve(source.relativize(sourcePath));
                    Files.copy(sourcePath, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
    }

    public boolean uninstall(String skillId) {
        log.info("Uninstalling skill: {}", skillId);
        
        try {
            Path skillDir = Paths.get(skillsDataDir, skillId);
            
            if (Files.exists(skillDir)) {
                deleteDirectory(skillDir);
                log.info("Skill {} uninstalled", skillId);
                return true;
            }
            
            return false;
        } catch (Exception e) {
            log.error("Failed to uninstall skill {}: {}", skillId, e.getMessage());
            return false;
        }
    }
    
    public boolean exists(String skillId) {
        Path skillDir = Paths.get(skillsDataDir, skillId);
        return Files.exists(skillDir) && Files.exists(skillDir.resolve("SKILL.md"));
    }
    
    public Path getSkillPath(String skillId) {
        return Paths.get(skillsDataDir, skillId);
    }

    private void deleteDirectory(Path path) throws IOException {
        Files.walk(path)
            .sorted((a, b) -> b.compareTo(a))
            .forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
    }

    public static class InstallResult {
        private final boolean success;
        private final String skillId;
        private final String source;
        private final String version;
        private final String error;

        private InstallResult(boolean success, String skillId, String source, String version, String error) {
            this.success = success;
            this.skillId = skillId;
            this.source = source;
            this.version = version;
            this.error = error;
        }

        public static InstallResult success(String skillId, String source, String version) {
            return new InstallResult(true, skillId, source, version, null);
        }

        public static InstallResult failure(String error) {
            return new InstallResult(false, null, null, null, error);
        }

        public boolean isSuccess() { return success; }
        public String getSkillId() { return skillId; }
        public String getSource() { return source; }
        public String getVersion() { return version; }
        public String getError() { return error; }
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("success", success);
            map.put("skillId", skillId);
            map.put("source", source);
            map.put("version", version);
            map.put("error", error);
            return map;
        }
    }
}
