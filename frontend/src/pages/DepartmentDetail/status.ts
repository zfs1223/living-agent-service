/**
 * 员工状态映射表 - 与后端 EmployeeStatus 枚举对齐
 *
 * 设计原则：
 * - 工作动作状态决定区域分配（workstation/lounge/collaboration）
 * - 连接状态仅影响可用性显示（绿点/灰点）
 *
 * 区域说明：
 * - workstation: 工位区（ACTIVE/WORKING/LEARNING/EVOLVING）
 * - lounge: 休息区（IDLE）✅ 关键！空闲员工在这里
 * - collaboration: 协作区（BUSY）
 * - offline: 离线区（OFFLINE/DORMANT/ARCHIVED/TERMINATED）
 * - alert: 告警区（DISABLED）
 */
const STATUS_LABELS: Record<string, { zh: string; en: string; zone: string; accent: string }> = {
  // ========== 工作动作状态（决定区域分配） ==========
  active:   { zh: '在线待命', en: 'Active',     zone: 'workstation',  accent: 'work' },
  working:  { zh: '工作中',   en: 'Working',    zone: 'workstation',  accent: 'work' },
  idle:     { zh: '休息中',   en: 'Idle',       zone: 'lounge',       accent: 'idle' },  // ✅ 空闲员工去休息区
  busy:     { zh: '协作中',   en: 'Collaborating', zone: 'collaboration', accent: 'work' },

  // ========== 学习/进化状态 ==========
  learning: { zh: '学习中',   en: 'Learning',   zone: 'workstation',  accent: 'work' },
  evolving: { zh: '进化中',   en: 'Evolving',   zone: 'workstation',  accent: 'work' },

  // ========== 连接/生命周期状态（不影响工作区域） ==========
  offline:  { zh: '离线',     en: 'Offline',    zone: 'offline',      accent: 'idle' },
  dormant:  { zh: '休眠',     en: 'Dormant',    zone: 'offline',      accent: 'idle' },
  disabled: { zh: '禁用',     en: 'Disabled',   zone: 'alert',        accent: 'alert' },
  archived: { zh: '归档',     en: 'Archived',   zone: 'offline',      accent: 'idle' },
  terminated:{ zh: '已离职',  en: 'Terminated', zone: 'offline',      accent: 'idle' },

  // ========== 兼容旧状态（已废弃但保留兼容） ==========
  online:   { zh: '在线',     en: 'Online',     zone: 'workstation',  accent: 'work' },   // → 映射到 active
  away:     { zh: '离开',     en: 'Away',       zone: 'lounge',       accent: 'idle' },   // → 映射到 idle
  stopped:  { zh: '离线',     en: 'Stopped',    zone: 'offline',      accent: 'idle' },   // → 映射到 offline
  inactive: { zh: '离线',     en: 'Inactive',   zone: 'offline',      accent: 'idle' },   // → 映射到 offline
  error:    { zh: '异常',     en: 'Error',      zone: 'alert',        accent: 'alert' },   // → 映射到 disabled
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
    alert: { zh: '告警区', en: 'Alert Zone' },
    offline: { zh: '离线区', en: 'Offline' },
  };
  const item = labels[zone] ?? labels.workstation;
  return isChinese ? item.zh : item.en;
}

export function getZoneByStatus(status?: string) {
  return getStatusMeta(status).zone;
}
