package com.livingagent.core.codereview.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;

/**
 * 闭环49-P49-C: fuck-u-code 客户端
 * 通过 docker exec 调用 living-agent-fuck-u-code 容器的 analyze/ai-review 命令
 *
 * <p>数据流：
 * <pre>
 * Java服务 → docker exec fuck-u-code analyze/ai-review → JSON结果解析 → 闭环49度量
 * </pre>
 */
public class FuckUCodeClient {

    private static final Logger log = LoggerFactory.getLogger(FuckUCodeClient.class);
    private static final String CONTAINER_NAME = "living-agent-fuck-u-code";
    private static final String WORKSPACE_PATH = "/workspace";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String containerName;
    private final boolean enabled;

    public FuckUCodeClient() {
        this(CONTAINER_NAME, true);
    }

    public FuckUCodeClient(String containerName, boolean enabled) {
        this.containerName = containerName;
        this.enabled = enabled;
    }

    /**
     * 执行代码质量分析
     *
     * @param projectPath 项目路径（容器内路径）
     * @param topN 返回评分最低的 N 个文件
     * @return 分析结果
     */
    public AnalyzeResult analyze(String projectPath, int topN) {
        if (!enabled) {
            return AnalyzeResult.disabled();
        }

        log.info("[P49-C] 执行 fuck-u-code analyze: path={}, top={}", projectPath, topN);
        long startTime = System.currentTimeMillis();

        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("docker");
            cmd.add("exec");
            cmd.add(containerName);
            cmd.add("node");
            cmd.add("/app/bin/fuck-u-code.js");
            cmd.add("analyze");
            cmd.add(projectPath);
            cmd.add("-f");
            cmd.add("json");
            cmd.add("--top");
            cmd.add(String.valueOf(topN));

            String output = executeCommand(cmd);
            JsonNode root = objectMapper.readTree(output);

            double overallScore = root.path("overallScore").asDouble(0);
            List<FileScore> fileScores = new ArrayList<>();

            JsonNode files = root.path("fileResults");
            if (files.isArray()) {
                for (JsonNode file : files) {
                    fileScores.add(new FileScore(
                        file.path("filePath").asText(),
                        file.path("score").asInt(0),
                        parseMetrics(file.path("metrics"))
                    ));
                }
            }

            long latencyMs = System.currentTimeMillis() - startTime;
            log.info("[P49-C] analyze 完成: score={}, files={}, latency={}ms",
                overallScore, fileScores.size(), latencyMs);

            return new AnalyzeResult(true, overallScore, fileScores, latencyMs, null);

        } catch (Exception e) {
            log.error("[P49-C] analyze 失败: {}", e.getMessage(), e);
            return new AnalyzeResult(false, 0, Collections.emptyList(),
                System.currentTimeMillis() - startTime, e.getMessage());
        }
    }

    /**
     * 执行 AI 代码审查
     *
     * @param projectPath 项目路径
     * @param topN 审查评分最低的 N 个文件
     * @return 审查结果
     */
    public AiReviewResult aiReview(String projectPath, int topN) {
        if (!enabled) {
            return AiReviewResult.disabled();
        }

        log.info("[P49-C] 执行 fuck-u-code ai-review: path={}, top={}", projectPath, topN);
        long startTime = System.currentTimeMillis();

        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("docker");
            cmd.add("exec");
            cmd.add(containerName);
            cmd.add("node");
            cmd.add("/app/bin/fuck-u-code.js");
            cmd.add("ai-review");
            cmd.add(projectPath);
            cmd.add("--top");
            cmd.add(String.valueOf(topN));
            cmd.add("-f");
            cmd.add("json");

            String output = executeCommand(cmd);
            JsonNode root = objectMapper.readTree(output);

            List<ReviewReport> reviews = new ArrayList<>();
            JsonNode reviewsNode = root.path("reviews");
            if (reviewsNode.isArray()) {
                for (JsonNode review : reviewsNode) {
                    reviews.add(new ReviewReport(
                        review.path("filePath").asText(),
                        review.path("score").asInt(0),
                        review.path("summary").asText(),
                        review.path("recommendations").asText()
                    ));
                }
            }

            long latencyMs = System.currentTimeMillis() - startTime;
            log.info("[P49-C] ai-review 完成: reviews={}, latency={}ms", reviews.size(), latencyMs);

            return new AiReviewResult(true, reviews, latencyMs, null);

        } catch (Exception e) {
            log.error("[P49-C] ai-review 失败: {}", e.getMessage(), e);
            return new AiReviewResult(false, Collections.emptyList(),
                System.currentTimeMillis() - startTime, e.getMessage());
        }
    }

    /**
     * 检查 fuck-u-code 容器是否可用
     */
    public boolean isAvailable() {
        if (!enabled) return false;
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "exec", containerName, "node", "/app/bin/fuck-u-code.js", "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            log.debug("[P49-C] fuck-u-code 容器不可用: {}", e.getMessage());
            return false;
        }
    }

    private String executeCommand(List<String> cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Command failed with exit code " + exitCode + ": " + output);
        }

        return output.toString();
    }

    private Map<String, Double> parseMetrics(JsonNode metricsNode) {
        Map<String, Double> metrics = new HashMap<>();
        if (metricsNode.isObject()) {
            metrics.put("complexity", metricsNode.path("complexity").asDouble(0));
            metrics.put("duplication", metricsNode.path("duplication").asDouble(0));
            metrics.put("size", metricsNode.path("size").asDouble(0));
            metrics.put("structure", metricsNode.path("structure").asDouble(0));
            metrics.put("error", metricsNode.path("error").asDouble(0));
            metrics.put("documentation", metricsNode.path("documentation").asDouble(0));
            metrics.put("naming", metricsNode.path("naming").asDouble(0));
        }
        return metrics;
    }

    // ==================== 数据类 ====================

    public record AnalyzeResult(
        boolean success,
        double overallScore,
        List<FileScore> fileScores,
        long latencyMs,
        String error
    ) {
        public static AnalyzeResult disabled() {
            return new AnalyzeResult(false, 0, Collections.emptyList(), 0, "FuckUCode disabled");
        }
    }

    public record FileScore(
        String filePath,
        int score,
        Map<String, Double> metrics
    ) {}

    public record AiReviewResult(
        boolean success,
        List<ReviewReport> reviews,
        long latencyMs,
        String error
    ) {
        public static AiReviewResult disabled() {
            return new AiReviewResult(false, Collections.emptyList(), 0, "FuckUCode disabled");
        }
    }

    public record ReviewReport(
        String filePath,
        int score,
        String summary,
        String recommendations
    ) {}
}