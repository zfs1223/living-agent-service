/**
 * 员工状态映射表 - 与后端 EmployeeStatus 枚举对齐
 *
 * 设计原则：
 * - 工作动作状态决定区域分配（workstation/lounge/collaboration）
 * - 连接状态仅影响可用性显示（头顶状态点）
 * - 离线/告警状态的员工归纳到休息区，状态通过头顶点显示
 *
 * 区域说明：
 * - workstation: 工位区（ACTIVE/WORKING/LEARNING/EVOLVING）
 * - lounge: 休息区（IDLE/OFFLINE/DORMANT/ARCHIVED/TERMINATED/DISABLED/ERROR）✅ 包含所有非工作状态
 * - collaboration: 协作区（BUSY）
 */
const STATUS_LABELS: Record<string, { zh: string; en: string; zone: string; accent: string }> = {
  // ========== 工作动作状态（决定区域分配） ==========
  active:   { zh: '在线待命', en: 'Active',     zone: 'workstation',  accent: 'work' },
  working:  { zh: '工作中',   en: 'Working',    zone: 'workstation',  accent: 'work' },
  idle:     { zh: '休息中',   en: 'Idle',       zone: 'lounge',       accent: 'idle' },
  busy:     { zh: '协作中',   en: 'Collaborating', zone: 'collaboration', accent: 'work' },

  // ========== 学习/进化状态 ==========
  learning: { zh: '学习中',   en: 'Learning',   zone: 'workstation',  accent: 'work' },
  evolving: { zh: '进化中',   en: 'Evolving',   zone: 'workstation',  accent: 'work' },

  // ========== 连接/生命周期状态（归纳到休息区，状态通过头顶点显示） ==========
  offline:  { zh: '离线',     en: 'Offline',    zone: 'lounge',       accent: 'idle' },
  dormant:  { zh: '休眠',     en: 'Dormant',    zone: 'lounge',       accent: 'idle' },
  disabled: { zh: '禁用',     en: 'Disabled',   zone: 'lounge',       accent: 'alert' },
  archived: { zh: '归档',     en: 'Archived',   zone: 'lounge',       accent: 'idle' },
  terminated:{ zh: '已离职',  en: 'Terminated', zone: 'lounge',       accent: 'idle' },

  // ========== 兼容旧状态（已废弃但保留兼容） ==========
  online:   { zh: '在线',     en: 'Online',     zone: 'workstation',  accent: 'work' },
  away:     { zh: '离开',     en: 'Away',       zone: 'lounge',       accent: 'idle' },
  stopped:  { zh: '离线',     en: 'Stopped',    zone: 'lounge',       accent: 'idle' },
  inactive: { zh: '离线',     en: 'Inactive',   zone: 'lounge',       accent: 'idle' },
  error:    { zh: '异常',     en: 'Error',      zone: 'lounge',       accent: 'alert' },
};

export function normalizeStatus(rawStatus?: string) {
  return (rawStatus || 'idle').toLowerCase();
}

export function getStatusMeta(status?: string) {
  return STATUS_LABELS[normalizeStatus(status)] ?? STATUS_LABELS.idle;
}

export function getStatusLabel(status: string, isChinese: boolean) {
  const item = getStatusMeta(status);
  return isChinese ? item.zh : item.en;
}

export function getZoneLabel(zone: string, isChinese: boolean) {
  const labels: Record<string, { zh: string; en: string }> = {
    workstation: { zh: '工位区', en: 'Workstations' },
    lounge: { zh: '休息室', en: 'Lounge' },
    collaboration: { zh: '协作区', en: 'Collaboration' },
  };
  const item = labels[zone] ?? labels.workstation;
  return isChinese ? item.zh : item.en;
}

export function getZoneByStatus(status?: string) {
  return getStatusMeta(status).zone;
}
