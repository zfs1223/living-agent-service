package com.livingagent.core.notification.config;

import com.livingagent.core.database.repository.NotificationRepository;
import com.livingagent.core.notification.EventDrivenNotifier;
import com.livingagent.core.notification.feedback.NotificationMetricsService;
import com.livingagent.core.notification.feedback.NotificationStrategyOptimizer;
import com.livingagent.core.proactive.alert.impl.DingTalkNotifier;
import com.livingagent.core.proactive.alert.impl.FeishuNotifier;
import com.livingagent.core.proactive.alert.impl.WebhookAlertNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;

@Configuration
public class NotificationConfig {

    private static final Logger log = LoggerFactory.getLogger(NotificationConfig.class);

    @Bean
    public NotificationMetricsService notificationMetricsService() {
        log.info("Initializing NotificationMetricsService");
        return new NotificationMetricsService();
    }

    @Bean
    public NotificationStrategyOptimizer notificationStrategyOptimizer(NotificationMetricsService metricsService) {
        log.info("Initializing NotificationStrategyOptimizer");
        return new NotificationStrategyOptimizer(metricsService);
    }

    @Bean
    public EventDrivenNotifier eventDrivenNotifier(
            NotificationMetricsService metricsService,
            NotificationStrategyOptimizer strategyOptimizer,
            NotificationRepository notificationRepository,
            @Value("${notification.dingtalk.access-token:}") String dingtalkAccessToken,
            @Value("${notification.dingtalk.secret:}") String dingtalkSecret,
            @Value("${notification.feishu.webhook-key:}") String feishuWebhookKey,
            @Value("${notification.webhook.url:}") String webhookUrl) {

        log.info("Initializing EventDrivenNotifier");
        EventDrivenNotifier notifier = new EventDrivenNotifier(metricsService, strategyOptimizer, notificationRepository);

        // 注册钉钉渠道
        if (dingtalkAccessToken != null && !dingtalkAccessToken.isEmpty()) {
            DingTalkNotifier dingTalk = dingtalkSecret != null && !dingtalkSecret.isEmpty()
                ? new DingTalkNotifier(dingtalkAccessToken, dingtalkSecret)
                : new DingTalkNotifier(dingtalkAccessToken);
            notifier.registerChannel(dingTalk);
            log.info("Registered DingTalk notification channel");
        } else {
            log.info("DingTalk channel not configured (missing notification.dingtalk.access-token)");
        }

        // 注册飞书渠道
        if (feishuWebhookKey != null && !feishuWebhookKey.isEmpty()) {
            notifier.registerChannel(new FeishuNotifier(feishuWebhookKey));
            log.info("Registered Feishu notification channel");
        } else {
            log.info("Feishu channel not configured (missing notification.feishu.webhook-key)");
        }

        // 注册通用 Webhook 渠道
        if (webhookUrl != null && !webhookUrl.isEmpty()) {
            notifier.registerChannel(new WebhookAlertNotifier("webhook", webhookUrl));
            log.info("Registered Webhook notification channel");
        }

        // 注册默认事件订阅
        notifier.subscribe("RISK_CRITICAL", List.of("dingtalk", "feishu"), EventDrivenNotifier.NotificationPriority.CRITICAL);
        notifier.subscribe("RISK_HIGH", List.of("dingtalk"), EventDrivenNotifier.NotificationPriority.HIGH);
        notifier.subscribe("TASK_REMINDER", List.of("websocket"), EventDrivenNotifier.NotificationPriority.MEDIUM);
        notifier.subscribe("SYSTEM_REPORT", List.of("system_message"), EventDrivenNotifier.NotificationPriority.LOW);

        log.info("EventDrivenNotifier initialized with {} channels", notifier.getChannelAvailability().size());
        return notifier;
    }
}
