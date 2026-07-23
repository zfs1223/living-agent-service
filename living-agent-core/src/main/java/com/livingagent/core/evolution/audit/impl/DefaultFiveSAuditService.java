package com.livingagent.core.evolution.audit.impl;

import com.livingagent.core.evolution.audit.FiveSAuditService;
import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import com.livingagent.core.knowledge.LayeredKnowledgeBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * P10: 5S 审计服务默认实现。
 *
 * 审计流程：
 * 1. 整理(Seiri) → 扫描知识库/技能库，标记过期/无效/低质项
 * 2. 整顿(Seiton) → 检查分类标准，标记需要重新分类的项
 * 3. 清扫(Seiso) → 标记冗余/错误项进入7天观察期
 * 4. 标准化(Seiketsu) → 建立质量标准
 * 5. 维持(Shitsuke) → 计算合规率，发布审计事件
 */
@Service
public class DefaultFiveSAuditService implements FiveSAuditService {

    private static final Logger log = LoggerFactory.getLogger(DefaultFiveSAuditService.class);
    private static final int OBSERVATION_PERIOD_DAYS = 7;
    private static final int MAX_ITEMS_PER_AUDIT = 50;
    private static final int MAX_SKILL_ITEMS_PER_AUDIT = 10;

    private final LayeredKnowledgeBase knowledgeBase;
    private final CrossLoopEventBus eventBus;

    /** 审计标记项存储 */
    private final Map<String, AuditItem> markedItems = new ConcurrentHashMap<>();
    /** 审计历史 */
    private final List<FiveSAuditResult> auditHistory = new ArrayList<>();

    public DefaultFiveSAuditService(LayeredKnowledgeBase knowledgeBase, CrossLoopEventBus eventBus) {
        this.knowledgeBase = knowledgeBase;
        this.eventBus = eventBus;
    }

    @Override
    public FiveSAuditResult performAudit(AuditType auditType) {
        log.info("[P10/5S] 开始5S审计: type={}", auditType);
        List<AuditItem> items = new ArrayList<>();

        if (auditType == AuditType.KNOWLEDGE || auditType == AuditType.ALL) {
            items.addAll(auditKnowledge());
        }
        if (auditType == AuditType.SKILL || auditType == AuditType.ALL) {
            items.addAll(auditSkills());
        }

        // 计算审计指标
        int markedCount = items.size();
        int reclassifiedCount = (int) items.stream().filter(i -> i.suggestedAction().contains("reclassify")).count();
        int cleanupCount = (int) items.stream().filter(i -> i.suggestedAction().contains("cleanup") || i.suggestedAction().contains("delete")).count();

        FiveSAuditResult result = new FiveSAuditResult(
            UUID.randomUUID().toString(),
            auditType,
            Instant.now(),
            markedCount,
            reclassifiedCount,
            cleanupCount,
            0, // newStandards - 后续迭代实现
            markedItems.isEmpty() ? 1.0 : 1.0 - (double) markedCount / Math.max(1, markedCount + markedItems.size()),
            items
        );

        // 记录标记项
        for (AuditItem item : items) {
            markedItems.put(item.itemId(), item);
        }

        // 记录审计历史
        synchronized (auditHistory) {
            auditHistory.add(result);
            while (auditHistory.size() > 100) {
                auditHistory.remove(0);
            }
        }

        // 发布跨闭环事件
        if (eventBus != null && markedCount > 0) {
            eventBus.publish(10, "5s_audit_completed",
                CrossLoopEvent.EventPriority.KNOWLEDGE,
                Map.of("auditId", result.auditId(),
                    "auditType", auditType.name(),
                    "markedItems", markedCount,
                    "cleanupItems", cleanupCount));
        }

        log.info("[P10/5S] 5S审计完成: marked={}, cleanup={}, compliance={:.1f}%",
            markedCount, cleanupCount, result.complianceRate() * 100);

        return result;
    }

    @Override
    public List<AuditItem> getPendingItems() {
        return markedItems.values().stream()
            .filter(i -> "PENDING".equals(i.status()))
            .collect(Collectors.toList());
    }

    @Override
    public boolean confirmCleanup(String itemId) {
        AuditItem item = markedItems.get(itemId);
        if (item == null) return false;
        if (!item.isObservationPeriodOver()) {
            log.warn("[P10/5S] 审计项仍在观察期内，不能确认清理: id={}, endsAt={}", itemId, item.observationPeriodEnd());
            return false;
        }

        // 执行清理（更新状态为 CONFIRMED）
        AuditItem confirmed = new AuditItem(
            item.itemId(), item.itemType(), item.itemName(),
            item.issue(), item.severity(), item.suggestedAction(),
            item.markedAt(), item.observationPeriodEnd(), "CONFIRMED");
        markedItems.put(itemId, confirmed);

        log.info("[P10/5S] 审计项清理确认: id={}, type={}, action={}", itemId, item.itemType(), item.suggestedAction());
        return true;
    }

    @Override
    public boolean rollbackItem(String itemId) {
        AuditItem item = markedItems.get(itemId);
        if (item == null) return false;

        AuditItem rolled = new AuditItem(
            item.itemId(), item.itemType(), item.itemName(),
            item.issue(), item.severity(), item.suggestedAction(),
            item.markedAt(), item.observationPeriodEnd(), "ROLLED_BACK");
        markedItems.put(itemId, rolled);
        markedItems.remove(itemId); // 回滚项移出标记

        log.info("[P10/5S] 审计项回滚: id={}, type={}", itemId, item.itemType());
        return true;
    }

    @Override
    public List<FiveSAuditResult> getAuditHistory(int limit) {
        synchronized (auditHistory) {
            int size = auditHistory.size();
            return new ArrayList<>(auditHistory.subList(Math.max(0, size - limit), size));
        }
    }

    /**
     * 每月执行一次5S审计。
     */
    @Scheduled(fixedRate = 30L * 24 * 60 * 60 * 1000, initialDelay = 7 * 24 * 60 * 60 * 1000)
    public void scheduledAudit() {
        performAudit(AuditType.ALL);
    }

    // ========== 内部方法 ==========

    private List<AuditItem> auditKnowledge() {
        List<AuditItem> items = new ArrayList<>();
        // 基于知识库的5S审计
        // 当前实现为模拟数据，后续接入真实知识库统计
        int count = 0;
        // TODO: 接入 LayeredKnowledgeBase 的实际统计数据
        // - 过期知识：超过90天未被引用
        // - 冲突知识：多条同主题且矛盾
        // - 低质知识：置信度<0.3
        // - 冗余知识：内容重复度>80%

        if (count == 0) {
            log.debug("[P10/5S] 知识库5S审计：无需标记项");
        }
        return items;
    }

    private List<AuditItem> auditSkills() {
        List<AuditItem> items = new ArrayList<>();
        // 基于技能库的5S审计
        // 当前实现为模拟数据，后续接入 SKILL_INDEX.json 统计
        // - 废弃技能：超过60天未被触发
        // - 低效技能：成功率<50%
        // - 过时触发词：触发词与功能不匹配
        // - 缺失依赖：依赖的技能不存在

        log.debug("[P10/5S] 技能库5S审计：无需标记项");
        return items;
    }

    private AuditItem createAuditItem(String itemType, String itemName, String issue, String severity, String action) {
        Instant now = Instant.now();
        return new AuditItem(
            UUID.randomUUID().toString(),
            itemType, itemName, issue, severity, action,
            now, now.plusSeconds(OBSERVATION_PERIOD_DAYS * 24 * 3600L), "PENDING");
    }
}
