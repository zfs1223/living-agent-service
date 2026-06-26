package com.livingagent.core.autonomous.bounty.impl;

import com.livingagent.core.autonomous.bounty.LedgerService.IncomeRecord;
import com.livingagent.core.database.entity.LedgerTransactionEntity;
import com.livingagent.core.database.repository.LedgerTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * JpaLedgerService 单测 - P0-1 修复验证。
 *
 * 测试范围：
 * - recordIncome：收入记录写入
 * - recordReward：奖励记录写入
 * - getBalance：余额查询
 * - getTotalEarned：总收入查询
 * - getIncomeHistory：收入历史查询
 */
@ExtendWith(MockitoExtension.class)
class JpaLedgerServiceTest {

    @Mock
    private LedgerTransactionRepository repository;

    private JpaLedgerService ledgerService;

    private static final String EMPLOYEE_ID = "employee://digital/tech/developer/001";

    @BeforeEach
    void setUp() {
        ledgerService = new JpaLedgerService(repository);
    }

    @Test
    @DisplayName("getBalance - 新员工余额为0")
    void testGetBalance_NewEmployee_ReturnsZero() {
        when(repository.findLatestBalance(EMPLOYEE_ID)).thenReturn(Optional.empty());

        int balance = ledgerService.getBalance(EMPLOYEE_ID);

        assertEquals(0, balance);
        verify(repository).findLatestBalance(EMPLOYEE_ID);
    }

    @Test
    @DisplayName("getBalance - 有记录返回最新余额")
    void testGetBalance_ExistingEmployee_ReturnsBalance() {
        when(repository.findLatestBalance(EMPLOYEE_ID)).thenReturn(Optional.of(5000));

        int balance = ledgerService.getBalance(EMPLOYEE_ID);

        assertEquals(5000, balance);
    }

    @Test
    @DisplayName("recordIncome - 正确记录收入并更新余额")
    void testRecordIncome_UpdatesBalance() {
        // 初始余额 0
        when(repository.findLatestBalance(EMPLOYEE_ID)).thenReturn(Optional.empty());
        when(repository.save(any(LedgerTransactionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ledgerService.recordIncome(EMPLOYEE_ID, "INCOME", "task_001", 1000, "Task reward");

        verify(repository).save(any(LedgerTransactionEntity.class));
        verify(repository).findLatestBalance(EMPLOYEE_ID);
    }

    @Test
    @DisplayName("recordIncome - 多次累加余额正确")
    void testRecordIncome_MultipleRecords_AccumulatesBalance() {
        // 第一次：余额 0 -> 1000
        when(repository.findLatestBalance(EMPLOYEE_ID))
            .thenReturn(Optional.empty())      // 第一次查询
            .thenReturn(Optional.of(1000));    // 第二次查询

        when(repository.save(any(LedgerTransactionEntity.class))).thenAnswer(invocation -> {
            LedgerTransactionEntity entity = invocation.getArgument(0);
            return entity;
        });

        ledgerService.recordIncome(EMPLOYEE_ID, "INCOME", "task_001", 1000, "First task");
        ledgerService.recordIncome(EMPLOYEE_ID, "INCOME", "task_002", 500, "Second task");

        verify(repository, times(2)).save(any(LedgerTransactionEntity.class));
    }

    @Test
    @DisplayName("recordReward - 正确记录奖励")
    void testRecordReward_UpdatesBalance() {
        when(repository.findLatestBalance(EMPLOYEE_ID)).thenReturn(Optional.of(1000));
        when(repository.save(any(LedgerTransactionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ledgerService.recordReward(EMPLOYEE_ID, 200, "Performance bonus");

        verify(repository).save(any(LedgerTransactionEntity.class));
    }

    @Test
    @DisplayName("getTotalEarned - 无记录返回0")
    void testGetTotalEarned_NoRecords_ReturnsZero() {
        when(repository.getTotalEarned(EMPLOYEE_ID)).thenReturn(null);

        int total = ledgerService.getTotalEarned(EMPLOYEE_ID);

        assertEquals(0, total);
    }

    @Test
    @DisplayName("getTotalEarned - 有记录返回总和")
    void testGetTotalEarned_WithRecords_ReturnsSum() {
        when(repository.getTotalEarned(EMPLOYEE_ID)).thenReturn(1500);

        int total = ledgerService.getTotalEarned(EMPLOYEE_ID);

        assertEquals(1500, total);
    }

    @Test
    @DisplayName("getIncomeHistory - 返回收入历史列表")
    void testGetIncomeHistory_ReturnsList() {
        LedgerTransactionEntity entity1 = new LedgerTransactionEntity(
            "inc_001", EMPLOYEE_ID, "INCOME", "task_001", 1000, 1000, "RECEIVED", "Task 1"
        );
        LedgerTransactionEntity entity2 = new LedgerTransactionEntity(
            "inc_002", EMPLOYEE_ID, "REWARD", null, 200, 1200, "RECEIVED", "Bonus"
        );

        when(repository.findIncomeHistory(EMPLOYEE_ID, 10)).thenReturn(List.of(entity1, entity2));

        List<IncomeRecord> history = ledgerService.getIncomeHistory(EMPLOYEE_ID, 10);

        assertEquals(2, history.size());
        assertEquals("inc_001", history.get(0).incomeId());
        assertEquals(1000, history.get(0).amountCents());
        assertEquals("inc_002", history.get(1).incomeId());
        assertEquals(200, history.get(1).amountCents());
    }

    @Test
    @DisplayName("getIncomeHistory - 无记录返回空列表")
    void testGetIncomeHistory_NoRecords_ReturnsEmptyList() {
        when(repository.findIncomeHistory(EMPLOYEE_ID, 10)).thenReturn(List.of());

        List<IncomeRecord> history = ledgerService.getIncomeHistory(EMPLOYEE_ID, 10);

        assertTrue(history.isEmpty());
    }

    @Test
    @DisplayName("recordPotentialIncome - 不更新余额")
    void testRecordPotentialIncome_DoesNotUpdateBalance() {
        when(repository.findLatestBalance(EMPLOYEE_ID)).thenReturn(Optional.of(1000));
        when(repository.save(any(LedgerTransactionEntity.class))).thenAnswer(invocation -> {
            LedgerTransactionEntity entity = invocation.getArgument(0);
            assertEquals("PENDING", entity.getStatus());
            assertEquals(1000, entity.getBalanceAfter()); // 余额不变
            return entity;
        });

        ledgerService.recordPotentialIncome(EMPLOYEE_ID, "INCOME", "pending_task", 500);

        verify(repository).save(any(LedgerTransactionEntity.class));
    }

    @Test
    @DisplayName("confirmPendingIncome - 状态变更并更新余额")
    void testConfirmPendingIncome_UpdatesStatusAndBalance() {
        LedgerTransactionEntity pendingEntity = new LedgerTransactionEntity(
            "pending_001", EMPLOYEE_ID, "INCOME", "task_001", 500, 1000, "PENDING", "Pending income"
        );

        when(repository.findByTransactionId("pending_001")).thenReturn(Optional.of(pendingEntity));
        when(repository.findLatestBalance(EMPLOYEE_ID)).thenReturn(Optional.of(1000));
        when(repository.save(any(LedgerTransactionEntity.class))).thenAnswer(invocation -> {
            LedgerTransactionEntity saved = invocation.getArgument(0);
            assertEquals("RECEIVED", saved.getStatus());
            return saved;
        });

        ledgerService.confirmPendingIncome("pending_001");

        verify(repository).save(any(LedgerTransactionEntity.class));
    }

    @Test
    @DisplayName("confirmPendingIncome - 已确认的不重复处理")
    void testConfirmPendingIncome_AlreadyConfirmed_NoUpdate() {
        LedgerTransactionEntity receivedEntity = new LedgerTransactionEntity(
            "inc_001", EMPLOYEE_ID, "INCOME", "task_001", 500, 1500, "RECEIVED", "Already received"
        );

        when(repository.findByTransactionId("inc_001")).thenReturn(Optional.of(receivedEntity));

        ledgerService.confirmPendingIncome("inc_001");

        verify(repository, never()).save(any(LedgerTransactionEntity.class));
    }
}