-- Fix Fixed Employee Identifiers
-- Align neuron_id and channel with code definitions in FixedEmployeeRegistry.java
-- Format: neuron://{department}/{role}/001 and channel://{department}/{role}

-- Update neuron_id and channel to match code definitions
UPDATE fixed_employee_definition SET
    neuron_id = 'neuron://tech/code-reviewer/001',
    channel = 'channel://tech/code-review'
WHERE code = 'T01';

UPDATE fixed_employee_definition SET
    neuron_id = 'neuron://tech/architect/001',
    channel = 'channel://tech/architecture'
WHERE code = 'T02';

UPDATE fixed_employee_definition SET
    neuron_id = 'neuron://tech/devops/001',
    channel = 'channel://tech/devops'
WHERE code = 'T03';

UPDATE fixed_employee_definition SET
    neuron_id = 'neuron://tech/ops/001',
    channel = 'channel://tech/ops'
WHERE code = 'T04';

UPDATE fixed_employee_definition SET
    neuron_id = 'neuron://tech/model-admin/001',
    channel = 'channel://tech/model'
WHERE code = 'T05';

UPDATE fixed_employee_definition SET
    neuron_id = 'neuron://tech/state-admin/001',
    channel = 'channel://tech/state'
WHERE code = 'T06';

UPDATE fixed_employee_definition SET
    neuron_id = 'neuron://tech/security/001',
    channel = 'channel://tech/security'
WHERE code = 'T07';

UPDATE fixed_employee_definition SET
    neuron_id = 'neuron://tech/config-admin/001',
    channel = 'channel://tech/config'
WHERE code = 'T08';

UPDATE fixed_employee_definition SET
    neuron_id = 'neuron://tech/frontend/001',
    channel = 'channel://tech/frontend'
WHERE code = 'T09';

UPDATE fixed_employee_definition SET
    neuron_id = 'neuron://tech/backend/001',
    channel = 'channel://tech/backend'
WHERE code = 'T10';

UPDATE fixed_employee_definition SET
    neuron_id = 'neuron://finance/accountant/001',
    channel = 'channel://finance/accounting'
WHERE code = 'F01';

UPDATE fixed_employee_definition SET
    neuron_id = 'neuron://finance/auditor/001',
    channel = 'channel://finance/audit'
WHERE code = 'F02';

UPDATE fixed_employee_definition SET
    neuron_id = 'neuron://finance/cost-accountant/001',
    channel = 'channel://finance/cost'
WHERE code = 'F03';

UPDATE fixed_employee_definition SET
    neuron_id = 'neuron://finance/budget-admin/001',
    channel = 'channel://finance/budget'
WHERE code = 'F04';

UPDATE fixed_employee_definition SET
    neuron_id = 'neuron://ops/analyst/001',
    channel = 'channel://ops/analysis'
WHERE code = 'O01';

UPDATE fixed_employee_definition SET
    neuron_id = 'neuron://ops/operator/001',
    channel = 'channel://ops/daily'
WHERE code = 'O02';

UPDATE fixed_employee_definition SET
    neuron_id = 'neuron://ops/scheduler/001',
    channel = 'channel://ops/schedule'
WHERE code = 'O03';

UPDATE fixed_employee_definition SET
    neuron_id = 'neuron://ops/process-admin/001',
    channel = 'channel://ops/process'
WHERE code = 'O04';

UPDATE fixed_employee_definition SET
    neuron_id = 'neuron://sales/representative/001',
    channel = 'channel://sales/reps'
WHERE code = 'S01';

UPDATE fixed_employee_definition SET
    neuron_id = 'neuron://sales/marketer/001',
    channel = 'channel://sales/market'
WHERE code = 'S02';

UPDATE fixed_employee_definition SET
    neuron_id = 'neuron://sales/channel-manager/001',
    channel = 'channel://sales/channel'
WHERE code = 'S03';

UPDATE fixed_employee_definition SET
    neuron_id = 'neuron://hr/recruiter/001',
    channel = 'channel://hr/recruit'
WHERE code = 'H01';

UPDATE fixed_employee_definition SET
    neuron_id = 'neuron://hr/performance/001',
    channel = 'channel://hr/performance'
WHERE code = 'H02';

UPDATE fixed_employee_definition SET
    neuron_id = 'neuron://cs/agent/001',
    channel = 'channel://cs/support'
WHERE code = 'C01';

UPDATE fixed_employee_definition SET
    neuron_id = 'neuron://cs/ticket-handler/001',
    channel = 'channel://cs/ticket'
WHERE code = 'C02';

UPDATE fixed_employee_definition SET
    neuron_id = 'neuron://admin/assistant/001',
    channel = 'channel://admin/affairs'
WHERE code = 'A01';

UPDATE fixed_employee_definition SET
    neuron_id = 'neuron://admin/doc-manager/001',
    channel = 'channel://admin/docs'
WHERE code = 'A02';

UPDATE fixed_employee_definition SET
    neuron_id = 'neuron://admin/copywriter/001',
    channel = 'channel://admin/content'
WHERE code = 'A03';

UPDATE fixed_employee_definition SET
    neuron_id = 'neuron://legal/contract-reviewer/001',
    channel = 'channel://legal/contract'
WHERE code = 'L01';

UPDATE fixed_employee_definition SET
    neuron_id = 'neuron://legal/compliance/001',
    channel = 'channel://legal/compliance'
WHERE code = 'L02';

UPDATE fixed_employee_definition SET
    neuron_id = 'neuron://main/coordinator/001',
    channel = 'channel://main/coord'
WHERE code = 'M01';

UPDATE fixed_employee_definition SET
    neuron_id = 'neuron://main/strategist/001',
    channel = 'channel://main/strategy'
WHERE code = 'M02';

-- Update enterprise_employees table to fix employee_id format
-- Old format: fixed_T01 -> New format: employee://digital/tech/code-reviewer/001
UPDATE enterprise_employees SET
    employee_id = 'employee://digital/tech/code-reviewer/001'
WHERE employee_id = 'fixed_T01';

UPDATE enterprise_employees SET
    employee_id = 'employee://digital/tech/architect/001'
WHERE employee_id = 'fixed_T02';

UPDATE enterprise_employees SET
    employee_id = 'employee://digital/tech/devops/001'
WHERE employee_id = 'fixed_T03';

UPDATE enterprise_employees SET
    employee_id = 'employee://digital/tech/ops/001'
WHERE employee_id = 'fixed_T04';

UPDATE enterprise_employees SET
    employee_id = 'employee://digital/tech/model-admin/001'
WHERE employee_id = 'fixed_T05';

UPDATE enterprise_employees SET
    employee_id = 'employee://digital/tech/state-admin/001'
WHERE employee_id = 'fixed_T06';

UPDATE enterprise_employees SET
    employee_id = 'employee://digital/tech/security/001'
WHERE employee_id = 'fixed_T07';

UPDATE enterprise_employees SET
    employee_id = 'employee://digital/tech/config-admin/001'
WHERE employee_id = 'fixed_T08';

UPDATE enterprise_employees SET
    employee_id = 'employee://digital/tech/frontend/001'
WHERE employee_id = 'fixed_T09';

UPDATE enterprise_employees SET
    employee_id = 'employee://digital/tech/backend/001'
WHERE employee_id = 'fixed_T10';

UPDATE enterprise_employees SET
    employee_id = 'employee://digital/finance/accountant/001'
WHERE employee_id = 'fixed_F01';

UPDATE enterprise_employees SET
    employee_id = 'employee://digital/finance/auditor/001'
WHERE employee_id = 'fixed_F02';

UPDATE enterprise_employees SET
    employee_id = 'employee://digital/finance/cost-accountant/001'
WHERE employee_id = 'fixed_F03';

UPDATE enterprise_employees SET
    employee_id = 'employee://digital/finance/budget-admin/001'
WHERE employee_id = 'fixed_F04';

UPDATE enterprise_employees SET
    employee_id = 'employee://digital/ops/analyst/001'
WHERE employee_id = 'fixed_O01';

UPDATE enterprise_employees SET
    employee_id = 'employee://digital/ops/operator/001'
WHERE employee_id = 'fixed_O02';

UPDATE enterprise_employees SET
    employee_id = 'employee://digital/ops/scheduler/001'
WHERE employee_id = 'fixed_O03';

UPDATE enterprise_employees SET
    employee_id = 'employee://digital/ops/process-admin/001'
WHERE employee_id = 'fixed_O04';

UPDATE enterprise_employees SET
    employee_id = 'employee://digital/sales/representative/001'
WHERE employee_id = 'fixed_S01';

UPDATE enterprise_employees SET
    employee_id = 'employee://digital/sales/marketer/001'
WHERE employee_id = 'fixed_S02';

UPDATE enterprise_employees SET
    employee_id = 'employee://digital/sales/channel-manager/001'
WHERE employee_id = 'fixed_S03';

UPDATE enterprise_employees SET
    employee_id = 'employee://digital/hr/recruiter/001'
WHERE employee_id = 'fixed_H01';

UPDATE enterprise_employees SET
    employee_id = 'employee://digital/hr/performance/001'
WHERE employee_id = 'fixed_H02';

UPDATE enterprise_employees SET
    employee_id = 'employee://digital/cs/agent/001'
WHERE employee_id = 'fixed_C01';

UPDATE enterprise_employees SET
    employee_id = 'employee://digital/cs/ticket-handler/001'
WHERE employee_id = 'fixed_C02';

UPDATE enterprise_employees SET
    employee_id = 'employee://digital/admin/assistant/001'
WHERE employee_id = 'fixed_A01';

UPDATE enterprise_employees SET
    employee_id = 'employee://digital/admin/doc-manager/001'
WHERE employee_id = 'fixed_A02';

UPDATE enterprise_employees SET
    employee_id = 'employee://digital/admin/copywriter/001'
WHERE employee_id = 'fixed_A03';

UPDATE enterprise_employees SET
    employee_id = 'employee://digital/legal/contract-reviewer/001'
WHERE employee_id = 'fixed_L01';

UPDATE enterprise_employees SET
    employee_id = 'employee://digital/legal/compliance/001'
WHERE employee_id = 'fixed_L02';

UPDATE enterprise_employees SET
    employee_id = 'employee://digital/main/coordinator/001'
WHERE employee_id = 'fixed_M01';

UPDATE enterprise_employees SET
    employee_id = 'employee://digital/main/strategist/001'
WHERE employee_id = 'fixed_M02';

-- Update fixed_employee_definition employee_id references
UPDATE fixed_employee_definition SET
    employee_id = 'employee://digital/tech/code-reviewer/001'
WHERE code = 'T01';

UPDATE fixed_employee_definition SET
    employee_id = 'employee://digital/tech/architect/001'
WHERE code = 'T02';

UPDATE fixed_employee_definition SET
    employee_id = 'employee://digital/tech/devops/001'
WHERE code = 'T03';

UPDATE fixed_employee_definition SET
    employee_id = 'employee://digital/tech/ops/001'
WHERE code = 'T04';

UPDATE fixed_employee_definition SET
    employee_id = 'employee://digital/tech/model-admin/001'
WHERE code = 'T05';

UPDATE fixed_employee_definition SET
    employee_id = 'employee://digital/tech/state-admin/001'
WHERE code = 'T06';

UPDATE fixed_employee_definition SET
    employee_id = 'employee://digital/tech/security/001'
WHERE code = 'T07';

UPDATE fixed_employee_definition SET
    employee_id = 'employee://digital/tech/config-admin/001'
WHERE code = 'T08';

UPDATE fixed_employee_definition SET
    employee_id = 'employee://digital/tech/frontend/001'
WHERE code = 'T09';

UPDATE fixed_employee_definition SET
    employee_id = 'employee://digital/tech/backend/001'
WHERE code = 'T10';

UPDATE fixed_employee_definition SET
    employee_id = 'employee://digital/finance/accountant/001'
WHERE code = 'F01';

UPDATE fixed_employee_definition SET
    employee_id = 'employee://digital/finance/auditor/001'
WHERE code = 'F02';

UPDATE fixed_employee_definition SET
    employee_id = 'employee://digital/finance/cost-accountant/001'
WHERE code = 'F03';

UPDATE fixed_employee_definition SET
    employee_id = 'employee://digital/finance/budget-admin/001'
WHERE code = 'F04';

UPDATE fixed_employee_definition SET
    employee_id = 'employee://digital/ops/analyst/001'
WHERE code = 'O01';

UPDATE fixed_employee_definition SET
    employee_id = 'employee://digital/ops/operator/001'
WHERE code = 'O02';

UPDATE fixed_employee_definition SET
    employee_id = 'employee://digital/ops/scheduler/001'
WHERE code = 'O03';

UPDATE fixed_employee_definition SET
    employee_id = 'employee://digital/ops/process-admin/001'
WHERE code = 'O04';

UPDATE fixed_employee_definition SET
    employee_id = 'employee://digital/sales/representative/001'
WHERE code = 'S01';

UPDATE fixed_employee_definition SET
    employee_id = 'employee://digital/sales/marketer/001'
WHERE code = 'S02';

UPDATE fixed_employee_definition SET
    employee_id = 'employee://digital/sales/channel-manager/001'
WHERE code = 'S03';

UPDATE fixed_employee_definition SET
    employee_id = 'employee://digital/hr/recruiter/001'
WHERE code = 'H01';

UPDATE fixed_employee_definition SET
    employee_id = 'employee://digital/hr/performance/001'
WHERE code = 'H02';

UPDATE fixed_employee_definition SET
    employee_id = 'employee://digital/cs/agent/001'
WHERE code = 'C01';

UPDATE fixed_employee_definition SET
    employee_id = 'employee://digital/cs/ticket-handler/001'
WHERE code = 'C02';

UPDATE fixed_employee_definition SET
    employee_id = 'employee://digital/admin/assistant/001'
WHERE code = 'A01';

UPDATE fixed_employee_definition SET
    employee_id = 'employee://digital/admin/doc-manager/001'
WHERE code = 'A02';

UPDATE fixed_employee_definition SET
    employee_id = 'employee://digital/admin/copywriter/001'
WHERE code = 'A03';

UPDATE fixed_employee_definition SET
    employee_id = 'employee://digital/legal/contract-reviewer/001'
WHERE code = 'L01';

UPDATE fixed_employee_definition SET
    employee_id = 'employee://digital/legal/compliance/001'
WHERE code = 'L02';

UPDATE fixed_employee_definition SET
    employee_id = 'employee://digital/main/coordinator/001'
WHERE code = 'M01';

UPDATE fixed_employee_definition SET
    employee_id = 'employee://digital/main/strategist/001'
WHERE code = 'M02';

-- Update fixed_employee_profile employee_id references
UPDATE fixed_employee_profile SET
    employee_id = 'employee://digital/tech/code-reviewer/001'
WHERE code = 'T01';

UPDATE fixed_employee_profile SET
    employee_id = 'employee://digital/tech/architect/001'
WHERE code = 'T02';

UPDATE fixed_employee_profile SET
    employee_id = 'employee://digital/tech/devops/001'
WHERE code = 'T03';

UPDATE fixed_employee_profile SET
    employee_id = 'employee://digital/tech/ops/001'
WHERE code = 'T04';

UPDATE fixed_employee_profile SET
    employee_id = 'employee://digital/tech/model-admin/001'
WHERE code = 'T05';

UPDATE fixed_employee_profile SET
    employee_id = 'employee://digital/tech/state-admin/001'
WHERE code = 'T06';

UPDATE fixed_employee_profile SET
    employee_id = 'employee://digital/tech/security/001'
WHERE code = 'T07';

UPDATE fixed_employee_profile SET
    employee_id = 'employee://digital/tech/config-admin/001'
WHERE code = 'T08';

UPDATE fixed_employee_profile SET
    employee_id = 'employee://digital/tech/frontend/001'
WHERE code = 'T09';

UPDATE fixed_employee_profile SET
    employee_id = 'employee://digital/tech/backend/001'
WHERE code = 'T10';

UPDATE fixed_employee_profile SET
    employee_id = 'employee://digital/finance/accountant/001'
WHERE code = 'F01';

UPDATE fixed_employee_profile SET
    employee_id = 'employee://digital/finance/auditor/001'
WHERE code = 'F02';

UPDATE fixed_employee_profile SET
    employee_id = 'employee://digital/finance/cost-accountant/001'
WHERE code = 'F03';

UPDATE fixed_employee_profile SET
    employee_id = 'employee://digital/finance/budget-admin/001'
WHERE code = 'F04';

UPDATE fixed_employee_profile SET
    employee_id = 'employee://digital/ops/analyst/001'
WHERE code = 'O01';

UPDATE fixed_employee_profile SET
    employee_id = 'employee://digital/ops/operator/001'
WHERE code = 'O02';

UPDATE fixed_employee_profile SET
    employee_id = 'employee://digital/ops/scheduler/001'
WHERE code = 'O03';

UPDATE fixed_employee_profile SET
    employee_id = 'employee://digital/ops/process-admin/001'
WHERE code = 'O04';

UPDATE fixed_employee_profile SET
    employee_id = 'employee://digital/sales/representative/001'
WHERE code = 'S01';

UPDATE fixed_employee_profile SET
    employee_id = 'employee://digital/sales/marketer/001'
WHERE code = 'S02';

UPDATE fixed_employee_profile SET
    employee_id = 'employee://digital/sales/channel-manager/001'
WHERE code = 'S03';

UPDATE fixed_employee_profile SET
    employee_id = 'employee://digital/hr/recruiter/001'
WHERE code = 'H01';

UPDATE fixed_employee_profile SET
    employee_id = 'employee://digital/hr/performance/001'
WHERE code = 'H02';

UPDATE fixed_employee_profile SET
    employee_id = 'employee://digital/cs/agent/001'
WHERE code = 'C01';

UPDATE fixed_employee_profile SET
    employee_id = 'employee://digital/cs/ticket-handler/001'
WHERE code = 'C02';

UPDATE fixed_employee_profile SET
    employee_id = 'employee://digital/admin/assistant/001'
WHERE code = 'A01';

UPDATE fixed_employee_profile SET
    employee_id = 'employee://digital/admin/doc-manager/001'
WHERE code = 'A02';

UPDATE fixed_employee_profile SET
    employee_id = 'employee://digital/admin/copywriter/001'
WHERE code = 'A03';

UPDATE fixed_employee_profile SET
    employee_id = 'employee://digital/legal/contract-reviewer/001'
WHERE code = 'L01';

UPDATE fixed_employee_profile SET
    employee_id = 'employee://digital/legal/compliance/001'
WHERE code = 'L02';

UPDATE fixed_employee_profile SET
    employee_id = 'employee://digital/main/coordinator/001'
WHERE code = 'M01';

UPDATE fixed_employee_profile SET
    employee_id = 'employee://digital/main/strategist/001'
WHERE code = 'M02';

-- Update fixed_employee_persona employee_id references
UPDATE fixed_employee_persona SET
    employee_id = 'employee://digital/tech/code-reviewer/001'
WHERE code = 'T01';

UPDATE fixed_employee_persona SET
    employee_id = 'employee://digital/tech/architect/001'
WHERE code = 'T02';

UPDATE fixed_employee_persona SET
    employee_id = 'employee://digital/tech/devops/001'
WHERE code = 'T03';

UPDATE fixed_employee_persona SET
    employee_id = 'employee://digital/tech/ops/001'
WHERE code = 'T04';

UPDATE fixed_employee_persona SET
    employee_id = 'employee://digital/tech/model-admin/001'
WHERE code = 'T05';

UPDATE fixed_employee_persona SET
    employee_id = 'employee://digital/tech/state-admin/001'
WHERE code = 'T06';

UPDATE fixed_employee_persona SET
    employee_id = 'employee://digital/tech/security/001'
WHERE code = 'T07';

UPDATE fixed_employee_persona SET
    employee_id = 'employee://digital/tech/config-admin/001'
WHERE code = 'T08';

UPDATE fixed_employee_persona SET
    employee_id = 'employee://digital/tech/frontend/001'
WHERE code = 'T09';

UPDATE fixed_employee_persona SET
    employee_id = 'employee://digital/tech/backend/001'
WHERE code = 'T10';

UPDATE fixed_employee_persona SET
    employee_id = 'employee://digital/finance/accountant/001'
WHERE code = 'F01';

UPDATE fixed_employee_persona SET
    employee_id = 'employee://digital/finance/auditor/001'
WHERE code = 'F02';

UPDATE fixed_employee_persona SET
    employee_id = 'employee://digital/finance/cost-accountant/001'
WHERE code = 'F03';

UPDATE fixed_employee_persona SET
    employee_id = 'employee://digital/finance/budget-admin/001'
WHERE code = 'F04';

UPDATE fixed_employee_persona SET
    employee_id = 'employee://digital/ops/analyst/001'
WHERE code = 'O01';

UPDATE fixed_employee_persona SET
    employee_id = 'employee://digital/ops/operator/001'
WHERE code = 'O02';

UPDATE fixed_employee_persona SET
    employee_id = 'employee://digital/ops/scheduler/001'
WHERE code = 'O03';

UPDATE fixed_employee_persona SET
    employee_id = 'employee://digital/ops/process-admin/001'
WHERE code = 'O04';

UPDATE fixed_employee_persona SET
    employee_id = 'employee://digital/sales/representative/001'
WHERE code = 'S01';

UPDATE fixed_employee_persona SET
    employee_id = 'employee://digital/sales/marketer/001'
WHERE code = 'S02';

UPDATE fixed_employee_persona SET
    employee_id = 'employee://digital/sales/channel-manager/001'
WHERE code = 'S03';

UPDATE fixed_employee_persona SET
    employee_id = 'employee://digital/hr/recruiter/001'
WHERE code = 'H01';

UPDATE fixed_employee_persona SET
    employee_id = 'employee://digital/hr/performance/001'
WHERE code = 'H02';

UPDATE fixed_employee_persona SET
    employee_id = 'employee://digital/cs/agent/001'
WHERE code = 'C01';

UPDATE fixed_employee_persona SET
    employee_id = 'employee://digital/cs/ticket-handler/001'
WHERE code = 'C02';

UPDATE fixed_employee_persona SET
    employee_id = 'employee://digital/admin/assistant/001'
WHERE code = 'A01';

UPDATE fixed_employee_persona SET
    employee_id = 'employee://digital/admin/doc-manager/001'
WHERE code = 'A02';

UPDATE fixed_employee_persona SET
    employee_id = 'employee://digital/admin/copywriter/001'
WHERE code = 'A03';

UPDATE fixed_employee_persona SET
    employee_id = 'employee://digital/legal/contract-reviewer/001'
WHERE code = 'L01';

UPDATE fixed_employee_persona SET
    employee_id = 'employee://digital/legal/compliance/001'
WHERE code = 'L02';

UPDATE fixed_employee_persona SET
    employee_id = 'employee://digital/main/coordinator/001'
WHERE code = 'M01';

UPDATE fixed_employee_persona SET
    employee_id = 'employee://digital/main/strategist/001'
WHERE code = 'M02';

-- Drop old helper functions that generate incorrect format
DROP FUNCTION IF EXISTS fixed_employee_neuron_uri(text, text);
DROP FUNCTION IF EXISTS fixed_employee_channel_uri(text, text);
