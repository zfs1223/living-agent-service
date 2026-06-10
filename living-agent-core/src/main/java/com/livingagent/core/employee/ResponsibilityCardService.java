package com.livingagent.core.employee;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 职责卡管理服务
 *
 * 管理人类员工的职责卡（ResponsibilityCard），
 * 提供创建、查询、更新和删除职责卡的功能。
 */
@Service
public class ResponsibilityCardService {

    private static final Logger log = LoggerFactory.getLogger(ResponsibilityCardService.class);

    private final Map<String, ResponsibilityCard> cardsByCardId = new ConcurrentHashMap<>();
    private final Map<String, ResponsibilityCard> cardsByEmployeeId = new ConcurrentHashMap<>();

    /**
     * 创建职责卡
     */
    public ResponsibilityCard createCard(ResponsibilityCard card) {
        Objects.requireNonNull(card, "card must not be null");
        Objects.requireNonNull(card.employeeId(), "employeeId must not be null");

        cardsByCardId.put(card.cardId(), card);
        cardsByEmployeeId.put(card.employeeId(), card);

        log.info("Created responsibility card {} for employee {}", card.cardId(), card.employeeId());
        return card;
    }

    /**
     * 为员工创建默认职责卡
     */
    public ResponsibilityCard createDefaultCard(String employeeId, String department, String position) {
        List<String> defaultRoles = getDefaultRolesForDepartment(department);
        List<String> defaultCapabilities = getDefaultCapabilitiesForDepartment(department);
        List<String> defaultTools = getDefaultToolsForDepartment(department);

        ResponsibilityCard card = ResponsibilityCard.create(
            employeeId, department, position,
            defaultRoles, defaultCapabilities, defaultTools
        );

        return createCard(card);
    }

    /**
     * 根据卡片ID查询
     */
    public Optional<ResponsibilityCard> getCard(String cardId) {
        return Optional.ofNullable(cardsByCardId.get(cardId));
    }

    /**
     * 根据员工ID查询
     */
    public Optional<ResponsibilityCard> getCardByEmployeeId(String employeeId) {
        return Optional.ofNullable(cardsByEmployeeId.get(employeeId));
    }

    /**
     * 更新职责卡
     */
    public ResponsibilityCard updateCard(String cardId, ResponsibilityCard updated) {
        ResponsibilityCard existing = cardsByCardId.get(cardId);
        if (existing == null) {
            throw new IllegalArgumentException("Card not found: " + cardId);
        }

        cardsByCardId.put(cardId, updated);
        cardsByEmployeeId.put(updated.employeeId(), updated);

        log.info("Updated responsibility card {} for employee {}", cardId, updated.employeeId());
        return updated;
    }

    /**
     * 删除职责卡
     */
    public void deleteCard(String cardId) {
        ResponsibilityCard card = cardsByCardId.remove(cardId);
        if (card != null) {
            cardsByEmployeeId.remove(card.employeeId());
            log.info("Deleted responsibility card {} for employee {}", cardId, card.employeeId());
        }
    }

    /**
     * 获取部门下所有职责卡
     */
    public List<ResponsibilityCard> getCardsByDepartment(String department) {
        return cardsByCardId.values().stream()
            .filter(c -> department.equals(c.department()))
            .toList();
    }

    private List<String> getDefaultRolesForDepartment(String department) {
        return switch (department) {
            case "tech" -> List.of("developer", "code-reviewer");
            case "hr" -> List.of("recruiter", "performance-manager");
            case "finance" -> List.of("accountant", "auditor");
            case "sales" -> List.of("sales-representative", "market-analyst");
            case "cs" -> List.of("support-agent", "ticket-handler");
            case "admin" -> List.of("admin-assistant", "document-manager");
            case "legal" -> List.of("contract-reviewer", "compliance-officer");
            case "ops" -> List.of("data-analyst", "operations-specialist");
            default -> List.of("employee");
        };
    }

    private List<String> getDefaultCapabilitiesForDepartment(String department) {
        return switch (department) {
            case "tech" -> List.of("code-review", "architecture-design", "ci-cd");
            case "hr" -> List.of("recruitment", "performance-evaluation");
            case "finance" -> List.of("expense-audit", "budget-management");
            case "sales" -> List.of("customer-engagement", "market-analysis");
            case "cs" -> List.of("ticket-processing", "issue-resolution");
            case "admin" -> List.of("document-processing", "content-creation");
            case "legal" -> List.of("contract-review", "compliance-check");
            case "ops" -> List.of("data-analysis", "operations-monitoring");
            default -> List.of("general-work");
        };
    }

    private List<String> getDefaultToolsForDepartment(String department) {
        return switch (department) {
            case "tech" -> List.of("gitlab", "jenkins", "sonarqube");
            case "hr" -> List.of("hr-system", "attendance-system");
            case "finance" -> List.of("erp", "invoice-system");
            case "sales" -> List.of("crm", "marketing-platform");
            case "cs" -> List.of("ticket-system", "knowledge-base");
            case "admin" -> List.of("document-system", "email");
            case "legal" -> List.of("contract-system", "compliance-database");
            case "ops" -> List.of("monitoring-dashboard", "analytics-platform");
            default -> List.of();
        };
    }
}
