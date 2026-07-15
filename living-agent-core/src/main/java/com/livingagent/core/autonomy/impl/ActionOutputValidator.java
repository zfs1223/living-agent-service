package com.livingagent.core.autonomy.impl;

import com.livingagent.core.autonomy.EmployeeWorkAssignment;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 行动输出验证器（64-D-1）
 * 借鉴 CLI-Anything 4层测试策略：
 * L1 结构验证 — 格式是否正确（HTML/JSON/PDF/Markdown）
 * L2 内容验证 — 关键信息是否完整（非空、有实质内容）
 * L3 交付物验证 — 是否满足验收标准（对照 assignment）
 * L4 工具结果验证 — 工具返回码/exit code/成功率（由调用方完成）
 */
public class ActionOutputValidator {

    private final ObjectMapper objectMapper;

    public ActionOutputValidator() {
        this.objectMapper = new ObjectMapper();
    }

    public ActionOutputValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public record ValidationResult(
        boolean valid,
        List<String> issues,
        List<String> warnings
    ) {
        public static ValidationResult ok() {
            return new ValidationResult(true, List.of(), List.of());
        }
        public static ValidationResult fail(List<String> issues) {
            return new ValidationResult(false, issues, List.of());
        }
        public static ValidationResult warn(List<String> warnings) {
            return new ValidationResult(true, List.of(), warnings);
        }
    }

    /**
     * 4层输出验证
     *
     * @param content     执行输出内容
     * @param artifactType 产物类型：html/markdown/json/text
     * @param assignment  任务分配（用于 L3 交付物验证）
     */
    public ValidationResult validate(String content, String artifactType,
                                      EmployeeWorkAssignment assignment) {
        if (content == null || content.isBlank()) {
            return ValidationResult.fail(List.of("输出内容为空"));
        }

        List<String> issues = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // L1: 结构验证
        validateStructure(content, artifactType, issues);

        // L2: 内容验证
        validateContent(content, issues, warnings);

        // L3: 交付物验证
        validateDeliverables(content, assignment, issues, warnings);

        // L4: 工具结果验证（由调用方在工具执行后完成）

        if (!issues.isEmpty()) {
            return new ValidationResult(false, issues, warnings);
        }
        if (!warnings.isEmpty()) {
            return new ValidationResult(true, List.of(), warnings);
        }
        return ValidationResult.ok();
    }

    /** L1: 结构验证 */
    private void validateStructure(String content, String artifactType, List<String> issues) {
        if (artifactType == null) return;

        switch (artifactType.toLowerCase()) {
            case "html" -> validateHtmlStructure(content, issues);
            case "markdown" -> validateMarkdownStructure(content, issues);
            case "json" -> validateJsonStructure(content, issues);
            case "text" -> { /* text 无结构要求 */ }
        }
    }

    private void validateHtmlStructure(String html, List<String> issues) {
        String lower = html.toLowerCase();
        if (!lower.contains("<!doctype") && !lower.contains("<html")) {
            issues.add("非完整HTML文档（缺少 <!DOCTYPE> 或 <html>）");
        }
        if (!lower.contains("<body")) {
            issues.add("HTML缺少 <body> 标签");
        }
        if (!lower.contains("</html>")) {
            issues.add("HTML未闭合（缺少 </html>）");
        }
        // 检查 body 内有实质内容
        int bodyStart = lower.indexOf("<body");
        if (bodyStart >= 0) {
            String bodyContent = html.substring(bodyStart);
            // 去掉标签后检查文本长度
            String textOnly = bodyContent.replaceAll("<[^>]+>", "").trim();
            if (textOnly.length() < 50) {
                issues.add("HTML body 内容过短（纯文本<50字符），可能为空壳模板");
            }
        }
    }

    private void validateMarkdownStructure(String md, List<String> issues) {
        boolean hasHeading = md.contains("# ");
        boolean hasList = md.contains("- ") || md.contains("* ") || md.contains("* ");
        boolean hasParagraph = md.lines().anyMatch(line -> !line.isBlank() && !line.startsWith("#") && !line.startsWith("-") && !line.startsWith("*"));
        if (!hasHeading && !hasList && !hasParagraph) {
            issues.add("Markdown 内容缺少标题、列表或段落结构");
        }
    }

    private void validateJsonStructure(String json, List<String> issues) {
        try {
            var node = objectMapper.readTree(json);
            if (node == null || node.isMissingNode()) {
                issues.add("JSON 解析结果为 null");
            } else if (node.isObject() && node.isEmpty()) {
                issues.add("JSON 为空对象 {}");
            } else if (node.isArray() && node.isEmpty()) {
                issues.add("JSON 为空数组 []");
            }
        } catch (Exception e) {
            issues.add("JSON 解析失败: " + e.getMessage());
        }
    }

    /** L2: 内容验证 */
    private void validateContent(String content, List<String> issues, List<String> warnings) {
        // 空内容拦截（isBlank 已在入口处理）

        // 过短内容警告
        if (content.length() < 50) {
            issues.add("输出内容过短（<50字符），可能不完整");
        } else if (content.length() < 200) {
            warnings.add("输出内容较短（<200字符），可能缺少细节");
        }

        // 拒绝性语句检测
        String lower = content.toLowerCase();
        String[] refusalPhrases = {"我无法", "i cannot", "我做不到", "as an ai", "i'm unable", "无法完成此任务"};
        for (String phrase : refusalPhrases) {
            if (lower.contains(phrase)) {
                issues.add("输出包含拒绝性语句 '" + phrase + "'，任务可能未完成");
                break;
            }
        }

        // 占位符检测
        String[] placeholderPatterns = {"todo:", "placeholder", "待填写", "待补充", "insert here", "lorem ipsum"};
        for (String pattern : placeholderPatterns) {
            if (lower.contains(pattern)) {
                warnings.add("输出包含占位符 '" + pattern + "'，可能未完成");
                break;
            }
        }
    }

    /** L3: 交付物验证 */
    private void validateDeliverables(String content, EmployeeWorkAssignment assignment,
                                       List<String> issues, List<String> warnings) {
        if (assignment == null || assignment.expectedDeliverables() == null) return;

        for (String deliverable : assignment.expectedDeliverables()) {
            if (deliverable == null || deliverable.isBlank()) continue;
            // 模糊匹配：交付物关键词出现在输出中
            if (!content.toLowerCase().contains(deliverable.toLowerCase())) {
                warnings.add("期望交付物 '" + deliverable + "' 未在输出中体现");
            }
        }
    }
}
