package com.livingagent.core.knowledge.testing;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface KnowledgeBaseTestFramework {

    TestResult runTest(TestConfig config);
    
    TestSuiteResult runTestSuite(TestSuiteConfig config);
    
    BenchmarkResult runBenchmark(BenchmarkConfig config);
    
    StressTestResult runStressTest(StressTestConfig config);
    
    List<TestResult> getTestHistory(String testName);
    
    TestReport generateReport(String testRunId);
    
    void loadDataSet(DataSetConfig config);
    
    DataSetStats getDataSetStats(String dataSetId);
    
    record TestConfig(
        String testName,
        String testType,
        Map<String, Object> parameters,
        Duration timeout,
        int iterations
    ) {}
    
    record TestResult(
        String testRunId,
        String testName,
        boolean passed,
        long durationMs,
        Map<String, Object> metrics,
        String errorMessage,
        Instant executedAt
    ) {}
    
    record TestSuiteConfig(
        String suiteName,
        List<TestConfig> tests,
        boolean parallel,
        boolean stopOnFailure
    ) {}
    
    record TestSuiteResult(
        String suiteRunId,
        String suiteName,
        int totalTests,
        int passedTests,
        int failedTests,
        long totalDurationMs,
        List<TestResult> results,
        Instant executedAt
    ) {}
    
    record BenchmarkConfig(
        String benchmarkName,
        int vectorCount,
        int vectorDimension,
        int queryCount,
        int topK,
        Duration warmupDuration,
        Duration testDuration,
        int concurrentThreads
    ) {
        public static BenchmarkConfig small() {
            return new BenchmarkConfig(
                "small-benchmark",
                10000,
                1024,
                1000,
                10,
                Duration.ofSeconds(10),
                Duration.ofSeconds(30),
                4
            );
        }
        
        public static BenchmarkConfig medium() {
            return new BenchmarkConfig(
                "medium-benchmark",
                100000,
                1024,
                5000,
                10,
                Duration.ofSeconds(30),
                Duration.ofMinutes(2),
                8
            );
        }
        
        public static BenchmarkConfig large() {
            return new BenchmarkConfig(
                "large-benchmark",
                1000000,
                1024,
                10000,
                10,
                Duration.ofMinutes(1),
                Duration.ofMinutes(5),
                16
            );
        }
    }
    
    record BenchmarkResult(
        String benchmarkRunId,
        String benchmarkName,
        long totalOperations,
        double operationsPerSecond,
        double averageLatencyMs,
        double p50LatencyMs,
        double p95LatencyMs,
        double p99LatencyMs,
        double minLatencyMs,
        double maxLatencyMs,
        double memoryUsageMB,
        double cpuUsagePercent,
        Duration totalDuration,
        Instant executedAt
    ) {}
    
    record StressTestConfig(
        String testName,
        int initialLoad,
        int maxLoad,
        int loadIncrement,
        Duration stepDuration,
        double failureThreshold,
        int concurrentUsers
    ) {}
    
    record StressTestResult(
        String testRunId,
        String testName,
        int maxSupportedLoad,
        double breakingPointLoad,
        List<LoadTestStep> steps,
        String failureReason,
        Instant executedAt
    ) {}
    
    record LoadTestStep(
        int currentLoad,
        double throughput,
        double averageLatencyMs,
        double errorRate,
        double cpuUsage,
        double memoryUsageMB
    ) {}
    
    record DataSetConfig(
        String dataSetId,
        String source,
        String format,
        int recordCount,
        Map<String, Object> options
    ) {}
    
    record DataSetStats(
        String dataSetId,
        int recordCount,
        int vectorCount,
        double avgVectorNorm,
        Map<String, Integer> categoryDistribution,
        long sizeBytes,
        Instant loadedAt
    ) {}
    
    record TestReport(
        String reportId,
        String testRunId,
        Instant generatedAt,
        String summary,
        Map<String, Object> statistics,
        List<Chart> charts,
        List<Recommendation> recommendations
    ) {}
    
    record Chart(
        String title,
        String type,
        List<String> labels,
        List<DataSeries> series
    ) {}
    
    record DataSeries(
        String name,
        List<Double> values
    ) {}
    
    record Recommendation(
        String category,
        String description,
        String impact,
        int priority
    ) {}
}
