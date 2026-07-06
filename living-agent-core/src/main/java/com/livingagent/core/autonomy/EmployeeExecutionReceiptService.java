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

    boolean isExecutionComplete(String executionId);

    void addReceiptListener(ReceiptListener listener);

    void removeReceiptListener(ReceiptListener listener);

    interface ReceiptListener {
        void onReceiptRecorded(EmployeeExecutionReceipt receipt, DepartmentExecutionResult executionResult);
    }
}
