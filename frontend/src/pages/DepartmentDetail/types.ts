export type DeptInfo = {
  icon: string;
  name: string;
  name_en: string;
};

export type DepartmentLike = {
  id?: string;
  name?: string;
  description?: string;
};

export type AgentLike = {
  id: string;
  name: string;
  avatar?: string;
  title?: string;
  status?: string;
  current_task?: string;
  updated_at?: string;
  last_active_at?: string;
  code?: string;
  department?: string;
  departmentName?: string;
  backendId?: string;
  definitionId?: string;
  persona?: {
    code?: string;
    label?: string;
    department?: string;
    icon?: string;
    hair?: 'short' | 'side' | 'curly' | 'clean' | 'bun' | 'cap' | 'default';
    glasses?: boolean;
    badgeStyle?: 'classic' | 'compact' | 'shield' | 'round' | 'text';
    stance?: 'calm' | 'focused' | 'friendly' | 'strict' | 'busy';
    outfit?: string;
    accent?: string;
    face?: 'neutral' | 'smile' | 'serious' | 'alert';
    summary?: string;
    badgeLabel?: string;
    traits?: string[];
    tools?: string[];
  };
};

export type BrainLike = {
  id?: string;
  name?: string;
  description?: string;
  status?: string;
};

export type OverviewKpi = {
  icon: React.ReactNode;
  label: string;
  value: React.ReactNode;
  hint: string;
};
