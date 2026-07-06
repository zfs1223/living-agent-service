package com.livingagent.core.knowledge.professional;

import com.livingagent.core.knowledge.Importance;
import com.livingagent.core.knowledge.KnowledgeBase;
import com.livingagent.core.knowledge.KnowledgeEntry;
import com.livingagent.core.knowledge.KnowledgeScope;
import com.livingagent.core.knowledge.KnowledgeType;
import com.livingagent.core.knowledge.Validity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * 架构文档知识播种器
 * 将 docs/ 和 documents/ 下的架构文档播种到知识库
 * 让大脑能"看到"自己的代码结构、API参考、治理规则
 */
public class ArchitectureKnowledgeSeeder {

    private static final Logger log = LoggerFactory.getLogger(ArchitectureKnowledgeSeeder.class);

    /** YAML frontmatter 解析模式 */
    private static final String FRONTMATTER_REGEX = "^---\\s*\\n(.*?)\\n---\\s*\\n";
    private static final java.util.regex.Pattern FRONTMATTER_PATTERN =
        java.util.regex.Pattern.compile(FRONTMATTER_REGEX, java.util.regex.Pattern.DOTALL);

    /** 二级标题分块模式 */
    private static final java.util.regex.Pattern SECTION_PATTERN =
        java.util.regex.Pattern.compile("^(##\\s+.+)$", java.util.regex.Pattern.MULTILINE);

    /** docs/ 目录下文件到 category 的映射规则 */
    private static final Map<String, String> DOCS_CATEGORY_MAP = new LinkedHashMap<>();
    static {
        DOCS_CATEGORY_MAP.put("CODE_STRUCTURE_AND_FILE_GUIDE", "code-structure");
        DOCS_CATEGORY_MAP.put("BRAIN_AND_EMPLOYEE_STANDARDS_INDEX", "standards");
        DOCS_CATEGORY_MAP.put("API_REFERENCE", "api");
        DOCS_CATEGORY_MAP.put("ARCHITECTURE_INDEX", "architecture");
    }

    /** documents/ 目录下子目录到 category 的映射规则 */
    private static final Map<String, String> DOCUMENTS_CATEGORY_MAP = new LinkedHashMap<>();
    static {
        DOCUMENTS_CATEGORY_MAP.put("governance", "governance");
        DOCUMENTS_CATEGORY_MAP.put("company", "employee-standard");
    }

    private final KnowledgeBase knowledgeBase;
    private final int chunkSize; // 分块大小，默认2000字符

    public ArchitectureKnowledgeSeeder(KnowledgeBase knowledgeBase) {
        this(knowledgeBase, 2000);
    }

    public ArchitectureKnowledgeSeeder(KnowledgeBase knowledgeBase, int chunkSize) {
        this.knowledgeBase = knowledgeBase;
        this.chunkSize = chunkSize;
    }

    /**
     * 从 docs 目录播种架构文档知识
     * @param docsPath docs/ 目录路径
     * @return 播种的知识条目数
     */
    public int seedFromDocsDirectory(Path docsPath) {
        if (docsPath == null || !Files.isDirectory(docsPath)) {
            log.warn("docs 目录不存在: {}", docsPath);
            return 0;
        }

        int totalSeeded = 0;

        // 扫描 docs/ 根目录下的 .md 文件
        totalSeeded += scanDirectory(docsPath, "docs-root");

        // 扫描 docs/ 子目录（如 references/、core/、pending/ 等）
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(docsPath)) {
            for (Path child : stream) {
                if (Files.isDirectory(child)) {
                    String dirName = child.getFileName().toString();
                    totalSeeded += scanDirectory(child, "docs-" + dirName);
                }
            }
        } catch (IOException e) {
            log.warn("扫描 docs 子目录失败: {}", docsPath, e);
        }

        log.info("从 docs 目录播种了 {} 条架构知识", totalSeeded);
        return totalSeeded;
    }

    /**
     * 从 documents 目录播种企业知识源
     * @param documentsPath documents/ 目录路径
     * @return 播种的知识条目数
     */
    public int seedFromDocumentsDirectory(Path documentsPath) {
        if (documentsPath == null || !Files.isDirectory(documentsPath)) {
            log.warn("documents 目录不存在: {}", documentsPath);
            return 0;
        }

        int totalSeeded = 0;

        // 递归扫描 documents/ 目录下的所有 .md 文件
        totalSeeded += scanDocumentsRecursive(documentsPath, documentsPath);

        log.info("从 documents 目录播种了 {} 条企业知识", totalSeeded);
        return totalSeeded;
    }

    /**
     * 递归扫描 documents 目录，根据路径推断 category
     */
    private int scanDocumentsRecursive(Path currentDir, Path rootPath) {
        int count = 0;

        // 先处理当前目录下的 .md 文件
        String relativePath = rootPath.relativize(currentDir).toString().replace('\\', '/');
        String category = inferDocumentsCategory(relativePath);

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(currentDir, "*.md")) {
            for (Path file : stream) {
                count += seedFile(file, category);
            }
        } catch (IOException e) {
            log.warn("扫描 documents 目录失败: {}", currentDir, e);
        }

        // 递归处理子目录
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(currentDir)) {
            for (Path child : stream) {
                if (Files.isDirectory(child)) {
                    count += scanDocumentsRecursive(child, rootPath);
                }
            }
        } catch (IOException e) {
            log.warn("扫描 documents 子目录失败: {}", currentDir, e);
        }

        return count;
    }

    /**
     * 根据 documents 下的相对路径推断 category
     * 如 shared/governance/ → governance, shared/company/ → employee-standard
     */
    private String inferDocumentsCategory(String relativePath) {
        String normalized = relativePath.replace('\\', '/');
        for (Map.Entry<String, String> entry : DOCUMENTS_CATEGORY_MAP.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        // 默认归为 governance 类别
        return "governance";
    }

    /**
     * 扫描指定目录下的 .md 文件并播种
     */
    private int scanDirectory(Path dir, String locationLabel) {
        int count = 0;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.md")) {
            for (Path file : stream) {
                String fileName = file.getFileName().toString().replace(".md", "");
                String category = inferDocsCategory(fileName);
                count += seedFile(file, category);
            }
        } catch (IOException e) {
            log.warn("扫描目录失败 [{}]: {}", locationLabel, dir, e);
        }

        return count;
    }

    /**
     * 根据 docs/ 下的文件名推断 category
     */
    private String inferDocsCategory(String fileName) {
        String upperName = fileName.toUpperCase();
        for (Map.Entry<String, String> entry : DOCS_CATEGORY_MAP.entrySet()) {
            if (upperName.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        // 默认归为 architecture 类别
        return "architecture";
    }

    /**
     * 播种单个文件，大文件自动分块
     * @param file 文件路径
     * @param category 知识类别
     * @return 播种的知识条目数
     */
    private int seedFile(Path file, String category) {
        try {
            String content = Files.readString(file);

            // 去除 YAML frontmatter
            String body = stripFrontmatter(content);

            if (body.isBlank()) {
                log.debug("文件内容为空，跳过: {}", file);
                return 0;
            }

            String fileName = file.getFileName().toString().replace(".md", "");
            String nameSlug = fileName.toLowerCase().replace('_', '-').replace(' ', '-');

            if (body.length() <= chunkSize) {
                // 内容不大，整体存储
                String key = buildKnowledgeKey(category, nameSlug, -1);
                storeKnowledge(key, body, category, file.toString());
                return 1;
            } else {
                // 内容超过阈值，按 ## 标题分块
                List<String> chunks = chunkContent(body, chunkSize);
                for (int i = 0; i < chunks.size(); i++) {
                    String key = buildKnowledgeKey(category, nameSlug, i);
                    // 分块开头附加来源文件路径信息
                    String chunkWithSource = String.format("> 来源: %s (分块 %d/%d)\n\n%s",
                        file.toString(), i + 1, chunks.size(), chunks.get(i));
                    storeKnowledge(key, chunkWithSource, category, file.toString());
                }
                log.debug("文件 {} 分为 {} 块存储", file.getFileName(), chunks.size());
                return chunks.size();
            }
        } catch (IOException e) {
            log.warn("读取文件失败: {}", file, e);
            return 0;
        }
    }

    /**
     * 将大内容按 ## 标题分块
     * 策略：按二级标题切分，每个分块尽量不超过 chunkSize
     * 如果单个章节超过 chunkSize，则按字符数硬切
     */
    private List<String> chunkContent(String content, int chunkSize) {
        List<String> chunks = new ArrayList<>();

        // 找到所有 ## 标题的位置
        java.util.regex.Matcher matcher = SECTION_PATTERN.matcher(content);
        List<Integer> sectionStarts = new ArrayList<>();
        List<String> sectionTitles = new ArrayList<>();

        while (matcher.find()) {
            sectionStarts.add(matcher.start());
            sectionTitles.add(matcher.group(1).trim());
        }

        // 如果没有 ## 标题，按字符数硬切
        if (sectionStarts.isEmpty()) {
            return chunkBySize(content, chunkSize);
        }

        // 按 ## 标题分块，合并小章节
        StringBuilder currentChunk = new StringBuilder();
        // 先加入第一个标题之前的内容（文件头部）
        String header = content.substring(0, sectionStarts.get(0)).trim();
        if (!header.isEmpty()) {
            currentChunk.append(header).append("\n\n");
        }

        for (int i = 0; i < sectionStarts.size(); i++) {
            int start = sectionStarts.get(i);
            int end = (i + 1 < sectionStarts.size()) ? sectionStarts.get(i + 1) : content.length();
            String section = content.substring(start, end).trim();

            // 如果当前块加上新章节不超过阈值，合并
            if (currentChunk.length() + section.length() + 2 <= chunkSize) {
                currentChunk.append(section).append("\n\n");
            } else {
                // 当前块已满，先保存
                if (!currentChunk.isEmpty()) {
                    chunks.add(currentChunk.toString().trim());
                    currentChunk = new StringBuilder();
                }

                // 如果单个章节超过阈值，按字符数硬切
                if (section.length() > chunkSize) {
                    List<String> subChunks = chunkBySize(section, chunkSize);
                    for (int j = 0; j < subChunks.size() - 1; j++) {
                        chunks.add(subChunks.get(j).trim());
                    }
                    // 最后一个子块作为当前块的起始
                    currentChunk.append(subChunks.get(subChunks.size() - 1)).append("\n\n");
                } else {
                    currentChunk.append(section).append("\n\n");
                }
            }
        }

        // 保存最后一个块
        if (!currentChunk.isEmpty()) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }

    /**
     * 按字符数硬切内容
     */
    private List<String> chunkBySize(String content, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < content.length()) {
            int end = Math.min(start + chunkSize, content.length());
            chunks.add(content.substring(start, end));
            start = end;
        }
        return chunks;
    }

    /**
     * 生成知识条目 key
     * 格式: arch:{category}:{name} 或 arch:{category}:{name}:chunk-{index}
     * @param category 类别
     * @param name 名称（已转为小写短横线格式）
     * @param chunkIndex 分块索引，-1 表示不分块
     */
    private String buildKnowledgeKey(String category, String name, int chunkIndex) {
        String base = "arch:" + category + ":" + name;
        if (chunkIndex >= 0) {
            return base + ":chunk-" + chunkIndex;
        }
        return base;
    }

    /**
     * 存储知识到知识库
     * 使用 KnowledgeEntry 设置强类型属性，确保 getByCategory() 等方法能正确检索
     */
    private void storeKnowledge(String key, String content, String category, String source) {
        try {
            KnowledgeEntry entry = new KnowledgeEntry(key, content);
            entry.setKnowledgeType(KnowledgeType.PROCESS);
            entry.setImportance(Importance.HIGH);
            entry.setValidity(Validity.LONG_TERM);
            entry.setScope(KnowledgeScope.L3_SHARED);
            entry.setConfidence(1.0);
            entry.setVerified(true);
            entry.setSource("architecture-docs");
            entry.setCategory(category);
            entry.setBrainDomain("architecture");

            Map<String, String> metadata = new HashMap<>();
            metadata.put("source", "architecture-docs");
            metadata.put("sourceFile", source);
            metadata.put("category", category);
            metadata.put("knowledgeScope", "L3_SHARED");

            knowledgeBase.store(key, entry, metadata);
            log.debug("播种架构知识: {}", key);
        } catch (Exception e) {
            log.debug("知识条目可能已存在: {}", key);
        }
    }

    /**
     * 去除 YAML frontmatter
     */
    private String stripFrontmatter(String content) {
        java.util.regex.Matcher matcher = FRONTMATTER_PATTERN.matcher(content);
        if (matcher.find()) {
            return content.substring(matcher.end());
        }
        return content;
    }
}
