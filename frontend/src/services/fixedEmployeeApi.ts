import { request } from './apiBase';

export type FixedEmployeeDefinition = {
  code: string;
  name: string;
  title: string;
  department: string;
  departmentName: string;
  neuronId: string;
  roles: string[];
  capabilities: string[];
  tools: string[];
  channel: string;
  personality?: {
    openness?: number;
    conscientiousness?: number;
    extroversion?: number;
    agreeableness?: number;
  } | null;
  icon: string;
  requiredSkills: string[];
};

export type FixedEmployeeProfile = {
  code: string;
  employeeId?: string;
  displayNameZh: string;
  displayNameEn?: string;
  summaryZh?: string;
  summaryEn?: string;
  traits: string[];
  toolTags: string[];
  currentTask?: string;
  status?: string;
  lastActiveAt?: string;
};

export type FixedEmployeePersona = {
  code: string;
  employeeId?: string;
  icon?: string;
  hair?: 'short' | 'side' | 'curly' | 'clean' | 'bun' | 'cap' | 'default';
  glasses?: boolean;
  badgeStyle?: 'classic' | 'compact' | 'shield' | 'round' | 'text';
  stance?: 'calm' | 'focused' | 'friendly' | 'strict' | 'busy';
  outfit?: string;
  accentColor?: string;
  face?: 'neutral' | 'smile' | 'serious' | 'alert';
  skinTone?: string;
  bodyShape?: string;
  clothingVariant?: string;
  accessoryVariant?: string;
  badgeLabel?: string;
  avatarStyle?: Record<string, unknown>;
};

export type FixedEmployeeSummary = {
  totalDefinitions: number;
  activeEmployees: number;
  departmentCount: number;
  countByDepartment: Record<string, number>;
};

export const fixedEmployeeApi = {
  getSummary: () => request<FixedEmployeeSummary>('/fixed-employees/summary'),
  getAllDefinitions: () => request<FixedEmployeeDefinition[]>('/fixed-employees/definitions'),
  getDefinition: (code: string) => request<FixedEmployeeDefinition>(`/fixed-employees/definitions/${encodeURIComponent(code)}`),
  getDefinitionsByDepartment: (department: string) => request<FixedEmployeeDefinition[]>(`/fixed-employees/definitions/by-department/${encodeURIComponent(department)}`),
  getGroupedDefinitions: () => request<Record<string, FixedEmployeeDefinition[]>>('/fixed-employees/grouped'),
  getProfiles: () => request<FixedEmployeeProfile[]>('/fixed-employees/profiles'),
  getProfile: (code: string) => request<FixedEmployeeProfile>(`/fixed-employees/profiles/${encodeURIComponent(code)}`),
  getPersonas: () => request<FixedEmployeePersona[]>('/fixed-employees/personas'),
  getPersona: (code: string) => request<FixedEmployeePersona>(`/fixed-employees/personas/${encodeURIComponent(code)}`),
};
