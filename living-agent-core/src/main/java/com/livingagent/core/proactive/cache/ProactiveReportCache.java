package com.livingagent.core.proactive.cache;

import com.livingagent.core.proactive.duty.DutyCardParser;
import com.livingagent.core.proactive.duty.DutyCardParser.DutyCard;
import com.livingagent.core.proactive.duty.DutyCardParser.ChairmanReportSummary;
import com.livingagent.core.proactive.predictor.RiskPredictor;
import com.livingagent.core.ops.scheduler.TaskCheckout;
import com.livingagent.core.ops.scheduler.TaskCheckout.TaskStatistics;
import com.livingagent.core.skill.bounty.BountyHunterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PR-8: 汇报内容缓存服务
 * 汇报内容长期准备，定期更新（每5分钟刷新一次）
 * 用于减少登录时的汇报生成延迟
 */
@Component
public class ProactiveReportCache {

    private static final Logger log = LoggerFactory.getLogger(ProactiveReportCache.class);

    // 缓存刷新间隔（5分钟）
    private static final long CACHE_REFRESH_INTERVAL_MS = 300_000;

    // 全局汇报缓存（董事长视角）
    private volatile CachedReport globalReportCache = null;
    private volatile Instant globalCacheLastUpdate = null;

    // 用户汇报缓存
    private final Map<String, CachedReport> userReportCache = new ConcurrentHashMap<>();
    private final Map<String, Instant> userCacheLastUpdate = new ConcurrentHashMap<>();

    // 组件注入
    private final DutyCardParser dutyCardParser;
    private final TaskCheckout taskCheckout;
    private BountyHunterService bountyHunterService; // 可选依赖
    private final RiskPredictor riskPredictor;

    public ProactiveReportCache(
            DutyCardParser dutyCardParser,
            TaskCheckout taskCheckout,
            RiskPredictor riskPredictor
    ) {
        this.dutyCardParser = dutyCardParser;
        this.taskCheckout = taskCheckout;
        this.riskPredictor = riskPredictor;
        // 启动定时刷新任务
        startRefreshTask();
    }

    /**
     * 可选注入 BountyHunterService（赏金猎人服务）
     * 如果没有实现类，将使用 null，相关功能将被跳过
     */
    @Autowired(required = false)
    public void setBountyHunterService(BountyHunterService bountyHunterService) {
        this.bountyHunterService = bountyHunterService;
        if (bountyHunterService != null) {
            log.info("BountyHunterService injected into ProactiveReportCache");
        } else {
            log.info("BountyHunterService not available, bounty-related features will be disabled");
        }
    }

    /**
     * 获取全局汇报缓存（董事长视角）
     */
    public CachedReport getGlobalReport() {
        refreshGlobalCacheIfNeeded();
        return globalReportCache;
    }

    /**
     * 获取用户汇报缓存
     */
    public CachedReport getUserReport(String userId) {
        refreshUserCacheIfNeeded(userId);
        return userReportCache.get(userId);
    }

    /**
     * 强制刷新缓存（用于系统状态变化时）
     */
    public void forceRefresh() {
        globalCacheLastUpdate = null;
        userCacheLastUpdate.clear();
        log.info("Proactive report cache force refresh triggered");
    }

    /**
     * 刷新全局缓存（如果需要）
     */
    private void refreshGlobalCacheIfNeeded() {
        Instant now = Instant.now();
        if (globalCacheLastUpdate == null ||
            now.toEpochMilli() - globalCacheLastUpdate.toEpochMilli() > CACHE_REFRESH_INTERVAL_MS) {
            refreshGlobalCache();
            globalCacheLastUpdate = now;
        }
    }

    /**
     * 刷新用户缓存（如果需要）
     */
    private void refreshUserCacheIfNeeded(String userId) {
        Instant now = Instant.now();
        Instant lastUpdate = userCacheLastUpdate.get(userId);
        if (lastUpdate == null ||
            now.toEpochMilli() - lastUpdate.toEpochMilli() > CACHE_REFRESH_INTERVAL_MS) {
            refreshUserCache(userId);
            userCacheLastUpdate.put(userId, now);
        }
    }

    /**
     * 刷新全局缓存
     */
    private void refreshGlobalCache() {
        try {
            List<String> suggestions = new ArrayList<>();
            List<String> alerts = new ArrayList<>();
            Map<String, Object> metadata = new LinkedHashMap<>();

            // 1. 获取职责卡汇总
            if (dutyCardParser != null) {
                ChairmanReportSummary summary = dutyCardParser.getChairmanReportSummary();
                suggestions.add(String.format("数字员工体系已部署 %d 个部门", summary.getTotalDepartmentCount()));
                metadata.put("deptCount", summary.getTotalDepartmentCount());
            }

            // 2. 获取任务统计
            if (taskCheckout != null) {
                TaskStatistics stats = taskCheckout.getStatistics();
                suggestions.add(String.format("当前待处理任务 %d 个，已完成 %d 个", stats.pendingCount(), stats.completedCount()));
                metadata.put("pendingTasks", stats.pendingCount());
                metadata.put("completedTasks", stats.completedCount());
                if (stats.pendingCount() > 20) {
                    alerts.add("待处理任务数量较高，建议分配执行");
                }
            }

            // 3. 获取风险预警
            if (riskPredictor != null) {
                var alertsList = riskPredictor.getActiveAlerts();
                if (alertsList != null && !alertsList.isEmpty()) {
                    for (var alert : alertsList) {
                        alerts.add(String.format("%s: %s", alert.indicatorName(), alert.recommendation()));
                    }
                }
            }

            globalReportCache = new CachedReport(
                "global",
                suggestions,
                alerts,
                metadata,
                Instant.now()
            );

            // 缓存刷新统计，改为TRACE级别减少日志噪音
            log.trace("Global proactive report cache refreshed: {} suggestions, {} alerts",
                suggestions.size(), alerts.size());
        } catch (Exception e) {
            log.warn("Failed to refresh global proactive report cache: {}", e.getMessage());
        }
    }

    /**
     * 刷新用户缓存
     */
    private void refreshUserCache(String userId) {
        try {
            List<String> suggestions = new ArrayList<>();
            List<String> alerts = new ArrayList<>();
            Map<String, Object> metadata = new LinkedHashMap<>();

            // 1. 获取用户收益
            if (bountyHunterService != null) {
                BountyHunterService.WorkerEarnings earnings = bountyHunterService.getWorkerEarnings(userId);
                if (earnings != null) {
                    suggestions.add(String.format("已完成 %d 个任务，收益 %.2f 元", earnings.tasksCompleted(), earnings.totalEarned()));
                    metadata.put("tasksCompleted", earnings.tasksCompleted());
                    metadata.put("totalEarned", earnings.totalEarned());
                    if (earnings.pendingEarnings() > 0) {
                        suggestions.add(String.format("待结算收益 %.2f 元", earnings.pendingEarnings()));
                    }
                    if (earnings.successRate() < 0.8) {
                        alerts.add(String.format("任务成功率 %.0f%%，建议改进", earnings.successRate() * 100));
                    }
                }
                int availableTasks = bountyHunterService.findAvailableTasks(userId).size();
                if (availableTasks > 0) {
                    suggestions.add(String.format("有 %d 个任务可接取", availableTasks));
                }
            }

            userReportCache.put(userId, new CachedReport(
                userId,
                suggestions,
                alerts,
                metadata,
                Instant.now()
            ));

            log.debug("User {} proactive report cache refreshed: {} suggestions, {} alerts",
                userId, suggestions.size(), alerts.size());
        } catch (Exception e) {
            log.warn("Failed to refresh user {} proactive report cache: {}", userId, e.getMessage());
        }
    }

    /**
     * 启动定时刷新任务
     */
    private void startRefreshTask() {
        Thread refreshThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(CACHE_REFRESH_INTERVAL_MS);
                    // 刷新全局缓存
                    refreshGlobalCache();
                    globalCacheLastUpdate = Instant.now();
                    log.debug("Proactive report cache auto-refresh completed");
                } catch (InterruptedException e) {
                    log.info("Proactive report cache refresh thread interrupted");
                    break;
                } catch (Exception e) {
                    log.warn("Proactive report cache auto-refresh failed: {}", e.getMessage());
                }
            }
        }, "proactive-report-cache-refresh");
        refreshThread.setDaemon(true);
        refreshThread.start();
        log.info("Proactive report cache refresh thread started (interval: {}ms)", CACHE_REFRESH_INTERVAL_MS);
    }

    /**
     * 缓存的汇报内容
     */
    public record CachedReport(
        String targetId,           // "global" 或 userId
        List<String> suggestions,  // 建议列表
        List<String> alerts,       // 警告列表
        Map<String, Object> metadata, // 元数据
        Instant cachedAt           // 缓存时间
    ) {
        public boolean isExpired(long maxAgeMs) {
            return Instant.now().toEpochMilli() - cachedAt.toEpochMilli() > maxAgeMs;
        }

        public int getSuggestionCount() {
            return suggestions != null ? suggestions.size() : 0;
        }

        public int getAlertCount() {
            return alerts != null ? alerts.size() : 0;
        }

        public String formatForUser() {
            StringBuilder sb = new StringBuilder();
            sb.append("📋 系统状态汇报\n\n");
            if (suggestions != null && !suggestions.isEmpty()) {
                sb.append("💡 建议：\n");
                for (String s : suggestions) {
                    sb.append("- ").append(s).append("\n");
                }
                sb.append("\n");
            }
            if (alerts != null && !alerts.isEmpty()) {
                sb.append("⚠️ 警告：\n");
                for (String a : alerts) {
                    sb.append("- ").append(a).append("\n");
                }
                sb.append("\n");
            }
            sb.append("📅 时间：").append(cachedAt.toString()).append("\n");
            return sb.toString();
        }
    }
}