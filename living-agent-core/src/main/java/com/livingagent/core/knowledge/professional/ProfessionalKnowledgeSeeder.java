package com.livingagent.core.knowledge.professional;

import com.livingagent.core.knowledge.Importance;
import com.livingagent.core.knowledge.KnowledgeBase;
import com.livingagent.core.knowledge.KnowledgeEntry;
import com.livingagent.core.knowledge.KnowledgeScope;
import com.livingagent.core.knowledge.KnowledgeType;
import com.livingagent.core.knowledge.Validity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProfessionalKnowledgeSeeder {

    private static final Logger log = LoggerFactory.getLogger(ProfessionalKnowledgeSeeder.class);

    private static final Pattern FRONTMATTER_PATTERN = Pattern.compile(
        "^---\\s*\\n(.*?)\\n---\\s*\\n", Pattern.DOTALL
    );
    private static final Pattern NAME_PATTERN = Pattern.compile("name:\\s*(.+)");
    private static final Pattern DESC_PATTERN = Pattern.compile("description:\\s*(.+)");

    private final KnowledgeBase knowledgeBase;

    public ProfessionalKnowledgeSeeder(KnowledgeBase knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    public int seedFromDirectory(Path agencyAgentsDir) {
        if (agencyAgentsDir == null || !Files.isDirectory(agencyAgentsDir)) {
            log.warn("Agency agents directory not found: {}", agencyAgentsDir);
            return 0;
        }

        int totalSeeded = 0;

        Map<String, String> deptMapping = new LinkedHashMap<>();
        deptMapping.put("engineering", "tech");
        deptMapping.put("marketing", "sales");
        deptMapping.put("sales", "sales");
        deptMapping.put("design", "admin");
        deptMapping.put("product", "ops");
        deptMapping.put("project-management", "ops");
        deptMapping.put("support", "cs");
        deptMapping.put("testing", "tech");
        deptMapping.put("paid-media", "sales");
        deptMapping.put("specialized", "main");

        for (Map.Entry<String, String> entry : deptMapping.entrySet()) {
            Path deptDir = agencyAgentsDir.resolve(entry.getKey());
            if (!Files.isDirectory(deptDir)) {
                continue;
            }

            String lasDept = entry.getValue();
            int count = seedDepartment(deptDir, lasDept);
            totalSeeded += count;
            log.info("Seeded {} professional knowledge entries from {}/ to department {}",
                count, entry.getKey(), lasDept);
        }

        return totalSeeded;
    }

    private int seedDepartment(Path deptDir, String lasDept) {
        int count = 0;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(deptDir, "*.md")) {
            for (Path file : stream) {
                try {
                    AgentKnowledge knowledge = parseAgentFile(file);
                    if (knowledge != null) {
                        seedKnowledge(knowledge, lasDept);
                        count++;
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse agent file: {}", file, e);
                }
            }
        } catch (IOException e) {
            log.warn("Failed to read department directory: {}", deptDir, e);
        }

        return count;
    }

    private AgentKnowledge parseAgentFile(Path file) throws IOException {
        String content = Files.readString(file);

        Matcher fmMatcher = FRONTMATTER_PATTERN.matcher(content);
        String name = "";
        String description = "";

        if (fmMatcher.find()) {
            String frontmatter = fmMatcher.group(1);
            Matcher nameMatcher = NAME_PATTERN.matcher(frontmatter);
            if (nameMatcher.find()) {
                name = nameMatcher.group(1).trim();
            }
            Matcher descMatcher = DESC_PATTERN.matcher(frontmatter);
            if (descMatcher.find()) {
                description = descMatcher.group(1).trim();
            }
        }

        String body = fmMatcher.find() ? content.substring(fmMatcher.end()) : content;

        if (name.isEmpty()) {
            String fileName = file.getFileName().toString();
            name = fileName.replace(".md", "").replace("-", " ");
        }

        return new AgentKnowledge(name, description, body);
    }

    private void seedKnowledge(AgentKnowledge knowledge, String department) {
        String key = "professional:" + department + ":" + knowledge.name().toLowerCase().replace(' ', '-');

        String content = buildKnowledgeContent(knowledge);

        KnowledgeEntry entry = new KnowledgeEntry(key, content);
        entry.setKnowledgeType(KnowledgeType.PROCESS);
        entry.setImportance(Importance.HIGH);
        entry.setValidity(Validity.LONG_TERM);
        entry.setBrainDomain(department);
        entry.setConfidence(1.0);
        entry.setVerified(true);
        entry.setScope(KnowledgeScope.L2_DEPARTMENT);
        entry.setScopeIdentifier(department);

        try {
            knowledgeBase.store(key, content, Map.of(
                "knowledgeType", "PROCESS",
                "importance", "HIGH",
                "brainDomain", department,
                "confidence", "1.0",
                "verified", "true"
            ));
        } catch (Exception e) {
            log.debug("Knowledge entry may already exist: {}", key);
        }
    }

    private String buildKnowledgeContent(AgentKnowledge knowledge) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(knowledge.name()).append("\n\n");

        if (!knowledge.description().isEmpty()) {
            sb.append(knowledge.description()).append("\n\n");
        }

        sb.append(knowledge.body());

        return sb.toString();
    }

    private record AgentKnowledge(String name, String description, String body) {}
}
