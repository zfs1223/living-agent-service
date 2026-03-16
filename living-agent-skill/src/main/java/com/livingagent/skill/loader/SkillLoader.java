package com.livingagent.skill.loader;

import com.livingagent.core.security.SkillVetter;
import com.livingagent.core.skill.Skill;
import com.livingagent.skill.model.SkillImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class SkillLoader {
    private static final Logger log = LoggerFactory.getLogger(SkillLoader.class);
    private static final Pattern YAML_FRONTMATTER_PATTERN = Pattern.compile(
            "^---\\s*\\n([\\s\\S]*?)\\n---\\s*\\n([\\s\\S]*)$"
    );
    private static final Pattern YAML_KEY_VALUE_PATTERN = Pattern.compile(
            "^(\\w+(?:\\.\\w+)*)\\s*:\\s*(.*)$"
    );

    private final SkillVetter skillVetter;

    @Autowired
    public SkillLoader(SkillVetter skillVetter) {
        this.skillVetter = skillVetter;
    }

    public List<Skill> loadSkillsFromDirectory(Path skillsDir) {
        List<Skill> skills = new ArrayList<>();
        List<Skill> quarantinedSkills = new ArrayList<>();
        
        if (!Files.exists(skillsDir)) {
            log.warn("Skills directory does not exist: {}", skillsDir);
            return skills;
        }

        try (Stream<Path> paths = Files.walk(skillsDir)) {
            List<Path> skillFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals("SKILL.md"))
                    .collect(Collectors.toList());

            for (Path skillFile : skillFiles) {
                try {
                    SkillImpl skill = loadSkill(skillFile);
                    if (skill != null) {
                        SkillVetter.VettingResult vettingResult = skillVetter.vetSkill(skill);
                        
                        if (vettingResult.status() == SkillVetter.VettingStatus.REJECTED) {
                            log.warn("Skill {} rejected by security vetter: {}", 
                                skill.getName(), vettingResult.summary());
                            continue;
                        }
                        
                        if (vettingResult.status() == SkillVetter.VettingStatus.QUARANTINED) {
                            log.warn("Skill {} quarantined: {}", skill.getName(), vettingResult.summary());
                            skill.getMetadata().put("quarantined", true);
                            skill.getMetadata().put("vettingResult", vettingResult);
                            quarantinedSkills.add(skill);
                            continue;
                        }
                        
                        if (vettingResult.status() == SkillVetter.VettingStatus.APPROVED_WITH_WARNINGS) {
                            log.info("Skill {} approved with warnings: {}", skill.getName(), vettingResult.summary());
                            skill.getMetadata().put("warnings", vettingResult.findings());
                        }
                        
                        String category = extractCategory(skillFile, skillsDir);
                        skill.setCategory(category);
                        skill.setTargetBrain(mapCategoryToBrain(category));
                        skill.setSkillPath(skillFile.getParent().toString());
                        skill.getMetadata().put("vettingId", vettingResult.vettingId());
                        skill.getMetadata().put("riskLevel", vettingResult.riskLevel().name());
                        skills.add(skill);
                        log.debug("Loaded skill: {} -> {} (risk: {})", 
                            skill.getName(), skill.getTargetBrain(), vettingResult.riskLevel());
                    }
                } catch (Exception e) {
                    log.error("Failed to load skill from {}: {}", skillFile, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("Failed to walk skills directory: {}", e.getMessage());
        }

        log.info("Loaded {} skills from {} ({} quarantined)", 
            skills.size(), skillsDir, quarantinedSkills.size());
        return skills;
    }

    public SkillImpl loadSkill(Path skillFile) throws IOException {
        String content = Files.readString(skillFile);
        Matcher matcher = YAML_FRONTMATTER_PATTERN.matcher(content);
        
        if (!matcher.matches()) {
            log.warn("Invalid SKILL.md format (missing frontmatter): {}", skillFile);
            return null;
        }

        String frontmatter = matcher.group(1);
        String body = matcher.group(2);

        SkillImpl skill = new SkillImpl();
        parseFrontmatter(frontmatter, skill);
        skill.setContent(body.trim());

        if (skill.getName() == null || skill.getName().isEmpty()) {
            log.warn("Skill missing name: {}", skillFile);
            return null;
        }

        return skill;
    }

    private void parseFrontmatter(String frontmatter, SkillImpl skill) {
        Map<String, Object> metadata = new HashMap<>();
        String[] lines = frontmatter.split("\n");
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            Matcher matcher = YAML_KEY_VALUE_PATTERN.matcher(line);
            if (matcher.matches()) {
                String key = matcher.group(1);
                Object value = parseYamlValue(matcher.group(2));
                
                switch (key) {
                    case "name":
                        skill.setName((String) value);
                        break;
                    case "description":
                        skill.setDescription((String) value);
                        break;
                    default:
                        metadata.put(key, value);
                }
            }
        }

        skill.setMetadata(metadata);
    }

    private Object parseYamlValue(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        value = value.trim();
        
        if (isQuotedString(value)) {
            return unquoteString(value);
        }
        
        if (isJsonObjectOrArray(value)) {
            return value;
        }
        
        return parsePrimitive(value);
    }

    private boolean isQuotedString(String value) {
        return (value.startsWith("\"") && value.endsWith("\"")) 
            || (value.startsWith("'") && value.endsWith("'"));
    }

    private String unquoteString(String value) {
        return value.substring(1, value.length() - 1);
    }

    private boolean isJsonObjectOrArray(String value) {
        return value.startsWith("{") || value.startsWith("[");
    }

    private Object parsePrimitive(String value) {
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        return value;
    }

    private String extractCategory(Path skillFile, Path skillsDir) {
        Path relative = skillsDir.relativize(skillFile.getParent());
        if (relative.getNameCount() > 0) {
            return relative.getName(0).toString();
        }
        return "general";
    }

    private String mapCategoryToBrain(String category) {
        return switch (category.toLowerCase()) {
            case "tech" -> "TechBrain";
            case "admin" -> "AdminBrain";
            case "sales" -> "SalesBrain";
            case "hr" -> "HrBrain";
            case "finance" -> "FinanceBrain";
            case "cs" -> "CsBrain";
            case "legal" -> "LegalBrain";
            case "ops" -> "OpsBrain";
            default -> "GeneralBrain";
        };
    }
}
