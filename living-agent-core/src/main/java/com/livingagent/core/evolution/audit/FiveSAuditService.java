package com.livingagent.core.evolution.audit;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * P10: DBS 5S 审计服务。
 *
 * 5S 审计映射：
 * - 整理(Seiri) → 标记过期/无效的知识和技能
 * - 整顿(Seiton) → 知识分类标准化、技能触发词优化
 * - 清扫(Seiso) → 删除冗余/错误知识和废弃技能
 * - 标准化(Seiketsu) → 建立知识和技能质量标准
 * - 维持(Shitsuke) → 定期审计制度
 *
 * 关联闭环：
 * - 闭环26（知识自进化）→ 知识库5S审计
 * - 闭环42（技能进化）→ 技能库5S审计
 * - 闭环24（自愈）→ 审计发现的异常触发自愈
 *
 * 安全约束：
 * - 审计结果先标记后清理，不直接删除
 * - 标记后7天观察期，期间可回滚
 * - 每次审计最多标记50条知识/10个技能
 */
public interface FiveSAuditService {

    /**
     * 执行5S审计。
     *
     * @param auditType 审计类型（KNOWLEDGE / SKILL / ALL）
     * @return 审计结果
     */
    FiveSAuditResult performAudit(AuditType auditType);

    /**
     * 获取待处理的审计标记项（观察期内）。
     */
    List<AuditItem> getPendingItems();

    /**
     * 确认清理标记项（观察期后）。
     */
    boolean confirmCleanup(String itemId);

    /**
     * 回滚标记项（观察期内）。
     */
    boolean rollbackItem(String itemId);

    /**
     * 获取审计历史。
     */
    List<FiveSAuditResult> getAuditHistory(int limit);

    enum AuditType {
        KNOWLEDGE,   // 仅知识库审计
        SKILL,       // 仅技能库审计
        ALL          // 全部审计
    }

    /**
     * 5S 审计结果。
     */
    record FiveSAuditResult(
        String auditId,
        AuditType auditType,
        Instant auditTime,
        // 整理(Seiri): 标记项
        int markedItems,
        // 整顿(Seiton): 需要重新分类的项
        int reclassifiedItems,
        // 清扫(Seiso): 需要清理的项
        int cleanupItems,
        // 标准化(Seiketsu): 新建立的标准
        int newStandards,
        // 维持(Shitsuke): 审计合规率
        double complianceRate,
        // 标记的审计项列表
        List<AuditItem> items
    ) {}

    /**
     * 审计项。
     */
    record AuditItem(
        String itemId,
        String itemType,      // "knowledge" 或 "skill"
        String itemName,
        String issue,         // 问题描述
        String severity,      // LOW / MEDIUM / HIGH
        String suggestedAction, // 建议操作
        Instant markedAt,
        Instant observationPeriodEnd, // 观察期结束时间（markedAt + 7天）
        String status         // PENDING / CONFIRMED / ROLLED_BACK
    ) {
        public boolean isObservationPeriodOver() {
            return Instant.now().isAfter(observationPeriodEnd);
        }
    }
}
