package com.livingagent.core.autonomy;

import java.util.List;

public interface EmployeeExecutionReceiptService {

    void registerExecution(DepartmentExecutionResult executionResult);

    EmployeeExecutionReceipt recordReceipt(EmployeeExecutionReceipt receipt);

    List<EmployeeExecutionReceipt> getReceipts(String executionId);

    boolean isExecutionComplete(String executionId);

    void addReceiptListener(ReceiptListener listener);

    void removeReceiptListener(ReceiptListener listener);

    interface ReceiptListener {
        void onReceiptRecorded(EmployeeExecutionReceipt receipt, DepartmentExecutionResult executionResult);
    }
}
