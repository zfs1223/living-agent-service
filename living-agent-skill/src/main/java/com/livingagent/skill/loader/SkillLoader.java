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
import java.util.Collections;
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
    private final List<Skill> quarantinedSkillsCache = new ArrayList<>();

    @Autowired
    public SkillLoader(SkillVetter skillVetter) {
        this.skillVetter = skillVetter;
    }

    public List<Skill> loadSkillsFromDirectory(Path skillsDir) {
        return loadSkillsWithResult(skillsDir).getSkills();
    }

    public SkillLoadResult loadSkillsWithResult(Path skillsDir) {
        SkillLoadResult result = new SkillLoadResult();
        quarantinedSkillsCache.clear();
        
        if (!Files.exists(skillsDir)) {
            log.warn("Skills directory does not exist: {}", skillsDir);
            return result;
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
                            skill.getMetadata().put("quarantinedAt", System.currentTimeMillis());
                            result.addQuarantinedSkill(skill);
                            quarantinedSkillsCache.add(skill);
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
                        result.addSkill(skill);
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
            result.getApprovedCount(), skillsDir, result.getQuarantinedCount());
        return result;
    }

    public List<Skill> getQuarantinedSkills() {
        return Collections.unmodifiableList(quarantinedSkillsCache);
    }

    public void clearQuarantinedSkills() {
        quarantinedSkillsCache.clear();
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
        
        int i = 0;
        while (i < lines.length) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#")) {
                i++;
                continue;
            }

            Matcher matcher = YAML_KEY_VALUE_PATTERN.matcher(line);
            if (matcher.matches()) {
                String key = matcher.group(1);
                String valueStr = matcher.group(2).trim();
                Object value = parseYamlValueAdvanced(valueStr, lines, i);
                
                if (value instanceof ParseResult pr) {
                    i = pr.nextLineIndex();
                    value = pr.value();
                } else {
                    i++;
                }
                
                switch (key) {
                    case "name":
                        skill.setName((String) value);
                        break;
                    case "description":
                        skill.setDescription((String) value);
                        break;
                    case "version":
                        skill.setVersion((String) value);
                        break;
                    case "author":
                        skill.setAuthor((String) value);
                        break;
                    default:
                        metadata.put(key, value);
                }
            } else {
                i++;
            }
        }

        skill.setMetadata(metadata);
    }

    private record ParseResult(Object value, int nextLineIndex) {}

    private Object parseYamlValueAdvanced(String valueStr, String[] lines, int currentLine) {
        if (valueStr == null || valueStr.isEmpty()) {
            return "";
        }

        valueStr = valueStr.trim();
        
        if (isQuotedString(valueStr)) {
            return unquoteString(valueStr);
        }
        
        if (valueStr.equals("|") || valueStr.equals(">")) {
            return parseMultilineString(lines, currentLine + 1, valueStr.equals(">"));
        }
        
        if (valueStr.startsWith("[")) {
            return parseYamlList(valueStr, lines, currentLine);
        }
        
        if (valueStr.startsWith("{")) {
            return parseYamlObject(valueStr);
        }
        
        return parsePrimitiveAdvanced(valueStr);
    }

    private String parseMultilineString(String[] lines, int startLine, boolean folded) {
        StringBuilder sb = new StringBuilder();
        int i = startLine;
        
        while (i < lines.length) {
            String line = lines[i];
            if (line.isEmpty() || (!line.startsWith(" ") && !line.startsWith("\t"))) {
                break;
            }
            
            String content = line.trim();
            if (folded) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append(content);
            } else {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(content);
            }
            i++;
        }
        
        return sb.toString();
    }

    private List<Object> parseYamlList(String valueStr, String[] lines, int currentLine) {
        List<Object> list = new ArrayList<>();
        
        if (valueStr.equals("[]")) {
            return list;
        }
        
        if (valueStr.startsWith("[") && valueStr.endsWith("]")) {
            String inner = valueStr.substring(1, valueStr.length() - 1).trim();
            if (!inner.isEmpty()) {
                for (String item : inner.split(",")) {
                    list.add(parsePrimitiveAdvanced(item.trim()));
                }
            }
            return list;
        }
        
        int i = currentLine + 1;
        while (i < lines.length) {
            String line = lines[i].trim();
            if (line.isEmpty() || (!line.startsWith("- ") && !line.startsWith("-\t"))) {
                break;
            }
            
            String item = line.substring(1).trim();
            list.add(parsePrimitiveAdvanced(item));
            i++;
        }
        
        return list;
    }

    private Map<String, Object> parseYamlObject(String valueStr) {
        Map<String, Object> map = new HashMap<>();
        
        if (valueStr.equals("{}")) {
            return map;
        }
        
        if (valueStr.startsWith("{") && valueStr.endsWith("}")) {
            String inner = valueStr.substring(1, valueStr.length() - 1).trim();
            if (!inner.isEmpty()) {
                for (String pair : inner.split(",")) {
                    String[] kv = pair.split(":", 2);
                    if (kv.length == 2) {
                        map.put(kv[0].trim(), parsePrimitiveAdvanced(kv[1].trim()));
                    }
                }
            }
        }
        
        return map;
    }

    private Object parsePrimitiveAdvanced(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        value = value.trim();
        
        if (isQuotedString(value)) {
            return unquoteString(value);
        }
        
        if ("null".equalsIgnoreCase(value) || "~".equals(value)) {
            return null;
        }
        
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        
        try {
            if (value.contains(".")) {
                return Double.parseDouble(value);
            } else {
                long longValue = Long.parseLong(value);
                if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                    return (int) longValue;
                }
                return longValue;
            }
        } catch (NumberFormatException e) {
            return value;
        }
    }
    
    private boolean isQuotedString(String value) {
        return (value.startsWith("\"") && value.endsWith("\"")) 
            || (value.startsWith("'") && value.endsWith("'"));
    }
    
    private String unquoteString(String value) {
        if (value.length() <= 2) return "";
        return value.substring(1, value.length() - 1);
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
