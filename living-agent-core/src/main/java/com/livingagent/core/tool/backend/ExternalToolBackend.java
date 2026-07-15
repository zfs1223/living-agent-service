package com.livingagent.core.tool.backend;

/**
 * 外部工具后端接口（64-B-1）
 * 借鉴 CLI-Anything 的 utils/<software>_backend.py 模式，
 * 为 LAS 建立统一的外部工具发现、健康检查和降级指引抽象。
 */
public interface ExternalToolBackend {

    /** 发现工具位置/可用性 */
    DiscoveryResult discover();

    /** 健康检查 */
    HealthStatus healthCheck();

    /** 不可用时的安装/配置指引 */
    String installHint();

    /** 对应的 Tool 名称 */
    String toolName();

    record DiscoveryResult(
        boolean available,
        String version,
        String endpoint,
        String detail
    ) {
        public static DiscoveryResult available(String version, String endpoint) {
            return new DiscoveryResult(true, version, endpoint, null);
        }
        public static DiscoveryResult unavailable(String detail) {
            return new DiscoveryResult(false, null, null, detail);
        }
    }

    record HealthStatus(
        boolean healthy,
        long latencyMs,
        String detail
    ) {
        public static HealthStatus healthy(long latencyMs) {
            return new HealthStatus(true, latencyMs, null);
        }
        public static HealthStatus unhealthy(String detail) {
            return new HealthStatus(false, -1, detail);
        }
        public static HealthStatus unreachable() {
            return new HealthStatus(false, -1, "无法连接");
        }
    }
}
