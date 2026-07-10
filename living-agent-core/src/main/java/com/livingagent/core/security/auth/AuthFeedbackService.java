package com.livingagent.core.security.auth;

import com.livingagent.core.evolution.signal.EvolutionSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 闭环38-P38-B/C: 认证反馈服务
 * 认证失败率>30%时触发EvolutionSignal，自动建议调整策略
 * 包含声纹质量闭环(P38-C)
 */
public class AuthFeedbackService implements AuthMetricsService.AuthAlertCallback {

    private static final Logger log = LoggerFactory.getLogger(AuthFeedbackService.class);

    private final AuthMetricsService metricsService;
    private final Map<String, StrategyAdjustment> adjustments = new ConcurrentHashMap<>();

    private FeedbackActionHandler actionHandler;

    public interface FeedbackActionHandler {
        void onStrategyAdjustment(String method, StrategyAdjustment adjustment);
    }

    public AuthFeedbackService(AuthMetricsService metricsService) {
        this.metricsService = metricsService;
        this.metricsService.setAlertCallback(this);
    }

    public void setActionHandler(FeedbackActionHandler handler) {
        this.actionHandler = handler;
    }

    @Override
    public void onHighFailureRate(String method, double failureRate, EvolutionSignal signal) {
        StrategyAdjustment adjustment = determineAdjustment(method, failureRate);
        adjustments.put(method, adjustment);

        log.info("[闭环38] 认证策略调整: method={}, action={}, reason={}",
            method, adjustment.action(), adjustment.reason());

        if (actionHandler != null) {
            actionHandler.onStrategyAdjustment(method, adjustment);
        }
    }

    private StrategyAdjustment determineAdjustment(String method, double failureRate) {
        return switch (method.toLowerCase()) {
            case "voiceprint" -> new StrategyAdjustment(
                method, "SUGGEST_RE_REGISTRATION",
                String.format("声纹验证失败率%.1f%%，建议用户重新注册声纹", failureRate * 100),
                failureRate
            );
            case "phone" -> new StrategyAdjustment(
                method, "INCREASE_VERIFICATION_STEPS",
                String.format("手机验证失败率%.1f%%，建议增加验证步骤或切换到备用认证方式", failureRate * 100),
                failureRate
            );
            case "oauth" -> new StrategyAdjustment(
                method, "DISABLE_TEMPORARILY",
                String.format("OAuth认证失败率%.1f%%，建议临时禁用并检查Provider配置", failureRate * 100),
                failureRate
            );
            default -> new StrategyAdjustment(
                method, "ALERT_ADMIN",
                String.format("认证方法%s失败率%.1f%%，建议管理员检查", method, failureRate * 100),
                failureRate
            );
        };
    }

    /**
     * P38-C: 声纹质量闭环检查
     * 声纹注册→验证→失败率→建议重新注册
     */
    public VoicePrintQualityReport checkVoicePrintQuality() {
        AuthMetricsService.AuthMetricsSnapshot snapshot = metricsService.getSnapshot();
        AuthMetricsService.MethodMetricsSnapshot vpMetrics =
            snapshot.methodMetrics().get("voiceprint");

        if (vpMetrics == null) {
            return new VoicePrintQualityReport(false, 0, "无声纹验证记录");
        }

        double failureRate = vpMetrics.failureRate();
        String recommendation;
        if (failureRate > 0.50) {
            recommendation = "声纹验证失败率过高(>50%)，强烈建议用户重新注册声纹";
        } else if (failureRate > 0.30) {
            recommendation = "声纹验证失败率偏高(>30%)，建议用户重新注册声纹";
        } else {
            recommendation = "声纹验证正常";
        }

        return new VoicePrintQualityReport(
            failureRate > 0.30,
            failureRate,
            recommendation
        );
    }

    public Map<String, StrategyAdjustment> getCurrentAdjustments() {
        return Map.copyOf(adjustments);
    }

    public record StrategyAdjustment(
        String method, String action, String reason, double failureRate
    ) {}

    public record VoicePrintQualityReport(
        boolean needsReRegistration, double failureRate, String recommendation
    ) {}
}
