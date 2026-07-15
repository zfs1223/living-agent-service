package com.livingagent.core.autonomy;

import java.util.List;

public interface EmployeeExecutionReceiptService {

    void registerExecution(DepartmentExecutionResult executionResult);

    EmployeeExecutionReceipt recordReceipt(EmployeeExecutionReceipt receipt);

    List<EmployeeExecutionReceipt> getReceipts(String executionId);

    /**
     * 获取指定部门的所有回执。
     */
    List<EmployeeExecutionReceipt> getReceiptsByDepartment(String department);

    /**
     * 获取指定员工的最近 N 条回执。
     * 64-G-2: 供 SkillRefineService 分析员工执行效果使用。
     */
    default List<EmployeeExecutionReceipt> getReceiptsByEmployee(String employeeCode, int limit) {
        return List.of();
    }

    boolean isExecutionComplete(String executionId);

    void addReceiptListener(ReceiptListener listener);

    void removeReceiptListener(ReceiptListener listener);

    interface ReceiptListener {
        void onReceiptRecorded(EmployeeExecutionReceipt receipt, DepartmentExecutionResult executionResult);
    }
}
