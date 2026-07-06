import { request } from './apiBase';

export interface SystemHealth {
  healthScore: number;
  status: string;
  activeComponents: number;
  totalComponents: number;
  components: ComponentStatus[];
}

export interface ComponentStatus {
  name: string;
  status: string;
  healthScore: number;
}

export interface EmployeeMetrics {
  totalEmployees: number;
  activeEmployees: number;
  riskEmployees: number;
  activationRate: number;
  digitalEmployees: number;
  humanEmployees: number;
}

export interface TaskMetrics {
  totalTasks: number;
  pendingTasks: number;
  completedToday: number;
  failedTasks: number;
  completionRate: number;
  totalTokensToday: number;
}

export interface CostBreakdown {
  category: string;
  amount: number;
  percentage: number;
  trend: string;
}

export interface CostAnalysis {
  totalCosts: number;
  internalCosts: number;
  externalBounties: number;
  pendingBounties: number;
  costPerTask: number;
  outsourcingRate: number;
  breakdowns: CostBreakdown[];
}

export interface DepartmentHealth {
  code: string;
  name: string;
  memberCount: number;
  activeMembers: number;
  todayTasks: number;
  todayTokens: number;
  healthScore: number;
  status: string;
  riskCount: number;
}

export interface RiskAlert {
  alertId: string;
  level: string;
  title: string;
  message: string;
  department: string;
  employeeId: string | null;
  impact: string;
  detectedAt: string;
}

export interface StrategicSuggestion {
  suggestionId: string;
  category: string;
  title: string;
  description: string;
  priority: number;
  action: string;
  context: Record<string, any>;
}

export interface EnterpriseSummary {
  generatedAt: string;
  systemHealth: SystemHealth;
  employeeMetrics: EmployeeMetrics;
  taskMetrics: TaskMetrics;
  costAnalysis: CostAnalysis;
  departmentHealth: DepartmentHealth[];
  riskAlerts: RiskAlert[];
  strategicSuggestions: StrategicSuggestion[];
}

export interface DepartmentSummary {
  code: string;
  name: string;
  memberCount: number;
  activeMembers: number;
  todayTasks: number;
  healthScore: number;
  status: string;
  brainCode: string | null;
}

export interface WorkspaceSummary {
  employeeId: string;
  name: string;
  pendingTasks: number;
  completedTasks: number;
  recentTasks: MyTask[];
  accessibleAgents: AccessibleAgent[];
}

export interface MyTask {
  taskId: string;
  title: string;
  status: string;
  priority: string;
  createdAt: string;
}

export interface AccessibleAgent {
  id: string;
  name: string;
  status: string;
  roleDescription: string;
}

export const dashboardApi = {
  getEnterpriseSummary: () =>
    request<EnterpriseSummary>('/dashboard/enterprise/summary'),

  getDepartmentHealth: () =>
    request<DepartmentHealth[]>('/dashboard/enterprise/departments'),

  getRiskAlerts: () =>
    request<RiskAlert[]>('/dashboard/enterprise/risks'),

  getCostAnalysis: () =>
    request<CostAnalysis>('/dashboard/enterprise/costs'),

  getDepartmentSummary: (code: string) =>
    request<DepartmentSummary>(`/dashboard/department/${code}/summary`),

  getWorkspaceSummary: () =>
    request<WorkspaceSummary>('/dashboard/employee/workspace'),
};
