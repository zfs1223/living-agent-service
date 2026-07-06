package com.livingagent.core.proactive.duty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PR-3: 职责卡解析服务
 * 从 documents/shared/company/duty-cards/ 目录解析职责卡定义
 * 用于生成身份驱动的汇报内容
 */
@Component
public class DutyCardParser {

    private static final Logger log = LoggerFactory.getLogger(DutyCardParser.class);

    // 职责卡目录路径（相对于项目根目录）
    private static final String DUTY_CARDS_DIR = "documents/shared/company/duty-cards";

    // 缓存解析结果
    private final Map<String, DutyCard> dutyCardCache = new ConcurrentHashMap<>();
    private Instant lastLoadTime = null;
    private static final long CACHE_REFRESH_INTERVAL_MS = 60_000; // 1分钟刷新缓存

    // 部门代码映射
    private static final Map<String, String> DEPARTMENT_CODE_MAP = Map.of(
        "admin", "admin",
        "cs", "cs",
        "finance", "finance",
        "hr", "hr",
        "legal", "legal",
        "ops", "ops",
        "sales", "sales",
        "tech", "tech"
    );

    /**
     * 获取所有职责卡
     */
    public Map<String, DutyCard> getAllDutyCards() {
        refreshCacheIfNeeded();
        return new HashMap<>(dutyCardCache);
    }

    /**
     * 获取指定部门的职责卡
     */
    public Optional<DutyCard> getDutyCardByDepartment(String department) {
        refreshCacheIfNeeded();
        String normalizedDept = department.toLowerCase();
        return Optional.ofNullable(dutyCardCache.get(normalizedDept));
    }

    /**
     * 获取董事长关注的关键指标（从所有职责卡汇总）
     */
    public ChairmanReportSummary getChairmanReportSummary() {
        refreshCacheIfNeeded();
        List<String> allMissions = new ArrayList<>();
        List<String> allSuccessCriteria = new ArrayList<>();
        Map<String, List<String>> deptResponsibilities = new LinkedHashMap<>();

        for (Map.Entry<String, DutyCard> entry : dutyCardCache.entrySet()) {
            DutyCard card = entry.getValue();
            allMissions.add(card.coreMission());
            allSuccessCriteria.addAll(card.successCriteria());
            deptResponsibilities.put(entry.getKey(), card.mainResponsibilities());
        }

        return new ChairmanReportSummary(allMissions, allSuccessCriteria, deptResponsibilities);
    }

    /**
     * 刷新缓存（如果需要）
     */
    private void refreshCacheIfNeeded() {
        Instant now = Instant.now();
        if (lastLoadTime == null || now.toEpochMilli() - lastLoadTime.toEpochMilli() > CACHE_REFRESH_INTERVAL_MS) {
            loadDutyCards();
            lastLoadTime = now;
        }
    }

    /**
     * 加载所有职责卡
     */
    private void loadDutyCards() {
        try {
            Path dutyCardsPath = findDutyCardsPath();
            if (dutyCardsPath == null || !Files.exists(dutyCardsPath)) {
                log.warn("Duty cards directory not found: {}", DUTY_CARDS_DIR);
                return;
            }

            Files.list(dutyCardsPath)
                .filter(p -> p.toString().endsWith(".md"))
                .forEach(this::parseDutyCardFile);

            // 定期加载职责卡，改为DEBUG级别减少INFO日志噪音
            log.debug("Loaded {} duty cards from {}", dutyCardCache.size(), dutyCardsPath);
        } catch (IOException e) {
            log.error("Failed to load duty cards: {}", e.getMessage());
        }
    }

    /**
     * 查找职责卡目录路径
     */
    private Path findDutyCardsPath() {
        // 尝试多个可能的位置
        String[] possiblePaths = {
            DUTY_CARDS_DIR,
            "docker/living-agent-service/" + DUTY_CARDS_DIR,
            "../" + DUTY_CARDS_DIR,
            System.getProperty("user.dir") + "/" + DUTY_CARDS_DIR
        };

        for (String path : possiblePaths) {
            Path p = Paths.get(path);
            if (Files.exists(p) && Files.isDirectory(p)) {
                return p;
            }
        }
        return null;
    }

    /**
     * 解析单个职责卡文件
     */
    private void parseDutyCardFile(Path filePath) {
        try {
            String content = Files.readString(filePath);
            String fileName = filePath.getFileName().toString();
            String deptCode = fileName.replace(".md", "").toLowerCase();

            DutyCard card = parseDutyCardContent(content, deptCode);
            if (card != null) {
                dutyCardCache.put(deptCode, card);
            }
        } catch (IOException e) {
            log.warn("Failed to parse duty card file {}: {}", filePath, e.getMessage());
        }
    }

    /**
     * 解析职责卡内容
     */
    private DutyCard parseDutyCardContent(String content, String deptCode) {
        try {
            String roleName = extractSection(content, "角色名称");
            String coreMission = extractSection(content, "核心使命");
            List<String> mainResponsibilities = extractListSection(content, "主要职责");
            List<String> inputTypes = extractListSection(content, "输入类型");
            List<String> outputResults = extractListSection(content, "输出结果");
            List<String> autoRules = extractListSection(content, "自动处理规则");
            List<String> collaborators = extractListSection(content, "协作对象");
            List<String> successCriteria = extractListSection(content, "成功标准");

            return new DutyCard(
                deptCode,
                roleName,
                coreMission,
                mainResponsibilities,
                inputTypes,
                outputResults,
                autoRules,
                collaborators,
                successCriteria
            );
        } catch (Exception e) {
            log.warn("Failed to parse duty card content for {}: {}", deptCode, e.getMessage());
            return null;
        }
    }

    /**
     * 提取单个章节内容
     */
    private String extractSection(String content, String sectionName) {
        String pattern = "## " + sectionName + "\n";
        int start = content.indexOf(pattern);
        if (start < 0) return "";

        start += pattern.length();
        int end = content.indexOf("\n## ", start);
        if (end < 0) end = content.length();

        return content.substring(start, end).trim();
    }

    /**
     * 提取列表章节内容
     */
    private List<String> extractListSection(String content, String sectionName) {
        String sectionContent = extractSection(content, sectionName);
        if (sectionContent.isEmpty()) return List.of();

        return Arrays.stream(sectionContent.split("\n"))
            .map(line -> line.trim())
            .filter(line -> line.startsWith("-"))
            .map(line -> line.substring(1).trim())
            .filter(line -> !line.isEmpty())
            .toList();
    }

    /**
     * 职责卡数据结构
     */
    public record DutyCard(
        String departmentCode,
        String roleName,
        String coreMission,
        List<String> mainResponsibilities,
        List<String> inputTypes,
        List<String> outputResults,
        List<String> autoRules,
        List<String> collaborators,
        List<String> successCriteria
    ) {
        public boolean hasResponsibility(String keyword) {
            return mainResponsibilities.stream()
                .anyMatch(r -> r.toLowerCase().contains(keyword.toLowerCase()));
        }

        public List<String> getKeywordsForReport() {
            // 提取汇报关键词
            List<String> keywords = new ArrayList<>();
            keywords.add(roleName);
            keywords.addAll(mainResponsibilities.stream().limit(3).toList());
            keywords.addAll(successCriteria.stream().limit(2).toList());
            return keywords;
        }
    }

    /**
     * 董事长汇报摘要
     */
    public record ChairmanReportSummary(
        List<String> allMissions,
        List<String> allSuccessCriteria,
        Map<String, List<String>> deptResponsibilities
    ) {
        public int getTotalDepartmentCount() {
            return deptResponsibilities.size();
        }

        public List<String> getTopSuccessCriteria(int limit) {
            return allSuccessCriteria.stream().limit(limit).toList();
        }
    }
}