package com.livingagent.core.autonomy;

/**
 * 反馈事件接口 - 用于编排流程中的反馈信号传递
 *
 * 反馈事件在以下场景产生：
 * - 员工执行回执提交后
 * - 大脑处理完成后
 * - 用户对结果不满意时
 * - 质量评分低于阈值时
 */
public interface FeedbackEvent {

    /**
     * 事件唯一ID
     */
    String eventId();

    /**
     * 关联的请求ID
     */
    String requestId();

    /**
     * 反馈类型
     */
    FeedbackType type();

    /**
     * 反馈来源
     */
    String source();

    /**
     * 反馈内容
     */
    String message();

    /**
     * 反馈严重程度
     */
    Severity severity();

    /**
     * 关联数据
     */
    java.util.Map<String, Object> metadata();

    enum FeedbackType {
        EXECUTION_COMPLETE,
        EXECUTION_FAILED,
        QUALITY_LOW,
        USER_REJECTION,
        RETRY_NEEDED,
        ESCALATION,
        DEGRADATION,
        TIMEOUT
    }

    enum Severity {
        INFO,
        WARNING,
        ERROR,
        CRITICAL
    }

    /**
     * 创建简单反馈事件
     */
    static FeedbackEvent of(String requestId, FeedbackType type, String source,
                            String message, Severity severity) {
        return new SimpleFeedbackEvent(
            "fb_" + System.currentTimeMillis(),
            requestId, type, source, message, severity, java.util.Map.of()
        );
    }

    /**
     * 创建带元数据的反馈事件
     */
    static FeedbackEvent of(String requestId, FeedbackType type, String source,
                            String message, Severity severity,
                            java.util.Map<String, Object> metadata) {
        return new SimpleFeedbackEvent(
            "fb_" + System.currentTimeMillis(),
            requestId, type, source, message, severity, metadata
        );
    }
}
