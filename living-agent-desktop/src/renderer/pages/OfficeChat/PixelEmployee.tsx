/**
 * 像素风格员工组件（简化版）
 * 基于 frontend/src/pages/DepartmentDetail/PixelAgent.tsx 简化
 */
import { useRef, useEffect, useMemo } from 'react';

const DK = '#3a4050';
const SKIN = '#f5d5b8';
const EYE = '#0a0a14';

// 角色样式
const CHARACTER_STYLES = {
  striped: {
    colors: { hair: '#6b4423', skin: SKIN, shirt: '#e04040', stripe: '#7d4a27', pants: '#5a5a6a', shoes: DK },
    pattern: [
      '000002220000',
      '000022222000',
      '000222222200',
      '000211111200',
      '000217117120',
      '000211111200',
      '000021112000',
      '000001111000',
      '088333333388',
      '000333333300',
      '000343434300',
      '000333333300',
      '000343434300',
      '000333333300',
      '000055555500',
      '000055555500',
      '000055005500',
      '000055005500',
      '000055005500',
      '000055005500',
      '000066006600',
      '000666066600',
    ],
  },
  suit: {
    colors: { hair: '#5a2a2a', skin: SKIN, suit: '#3a3a50', shirt: '#f0f0f0', tie: '#5a40a0', pants: '#3a3a50', shoes: DK, glasses: '#70d8f0' },
    pattern: [
      '000002220000',
      '000022222000',
      '000222222200',
      '000211111200',
      '000217117120',
      '000211111200',
      '000021112000',
      '000001111000',
      '088333333388',
      '000333433300',
      '000333433300',
      '000333433300',
      '000333433300',
      '000333333300',
      '000055555500',
      '000055555500',
      '000055005500',
      '000055005500',
      '000055005500',
      '000055005500',
      '000066006600',
      '000666066600',
    ],
  },
  red_female: {
    colors: { hair: '#9a3a3a', skin: SKIN, shirt: '#f04040', skirt: '#5a2040', shoes: DK, lips: '#f04040', longHair: '#8a2a2a' },
    pattern: [
      '000022222000',
      '000222222200',
      '002222222220',
      '002211111120',
      '002217117120',
      '002211111220',
      '002221112220',
      '000221112200',
      '088333333388',
      '000333333300',
      '000333333300',
      '000333333300',
      '000333333300',
      '000044444400',
      '000044444400',
      '000044444400',
      '000044444400',
      '000044444400',
      '000044444400',
      '000044444400',
      '000066006600',
      '000666066600',
    ],
  },
  purple_female: {
    colors: { hair: '#d8b060', skin: SKIN, shirt: '#a04080', emblem: '#40a040', skirt: '#8040a0', shoes: DK, lips: '#f04040' },
    pattern: [
      '000022222000',
      '000222222200',
      '002222222220',
      '002211111120',
      '002217117120',
      '002211111220',
      '002221112220',
      '000221112200',
      '088333333388',
      '000334343300',
      '000333333300',
      '000333333300',
      '000333333300',
      '000044444400',
      '000044444400',
      '000044444400',
      '000044444400',
      '000044444400',
      '000044444400',
      '000044444400',
      '000066006600',
      '000666066600',
    ],
  },
  sunglasses: {
    colors: { hair: '#9a6a3a', skin: SKIN, shirt: '#6a6a6a', shirtAlt: '#4a4a4a', pants: '#7a5a3a', shoes: DK, glasses: '#2a2a3a' },
    pattern: [
      '000002220000',
      '000022222000',
      '000222222200',
      '000211111200',
      '000217117120',
      '000211111200',
      '000021112000',
      '000001111000',
      '088333333388',
      '000343434300',
      '000434343400',
      '000333333300',
      '000343434300',
      '000333333300',
      '000055555500',
      '000055555500',
      '000055005500',
      '000055005500',
      '000055005500',
      '000055005500',
      '000066006600',
      '000666066600',
    ],
  },
  beard: {
    colors: { hair: '#5a3a2a', skin: SKIN, shirt: '#4a4a4a', pants: '#5a5a5a', shoes: DK, beard: '#5a3a2a', belt: '#7a5a3a', buckle: '#a0a080' },
    pattern: [
      '000002220000',
      '000022222000',
      '000222222200',
      '000211111200',
      '000217117120',
      '000211111200',
      '000021112000',
      '000001111000',
      '088333333388',
      '000333333300',
      '000333333300',
      '000333333300',
      '000339999300',
      '000333333300',
      '000055555500',
      '000055555500',
      '000055005500',
      '000055005500',
      '000055005500',
      '000055005500',
      '000066006600',
      '000666066600',
    ],
  },
} as const;

type CharacterStyleKey = keyof typeof CHARACTER_STYLES;
const STYLE_KEYS: CharacterStyleKey[] = ['striped', 'suit', 'red_female', 'purple_female', 'sunglasses', 'beard'];

function getCharacterStyle(department: string, instanceNum: number, agentId: string): CharacterStyleKey {
  let idHash = 0;
  for (let i = 0; i < agentId.length; i++) {
    idHash = ((idHash << 5) - idHash) + agentId.charCodeAt(i);
    idHash = idHash & idHash;
  }
  const hash = (
    Math.abs(idHash) * 31 +
    (instanceNum % 100) * 17 +
    (department.length || 1) * 13
  ) % STYLE_KEYS.length;
  return STYLE_KEYS[hash];
}

const COLOR_MAP_CODES: Record<number, string> = {
  0: 'transparent',
  1: 'skin',
  2: 'hair',
  3: 'shirt',
  4: 'pattern',
  5: 'pants',
  6: 'shoes',
  7: 'eyes',
  8: 'arms',
  9: 'details',
};

function renderCharacter(style: CharacterStyleKey) {
  const char = CHARACTER_STYLES[style];
  const c = char.colors as Record<string, string | undefined>;
  const colorValues: Record<string, string> = {
    transparent: 'transparent',
    skin: c.skin || SKIN,
    hair: c.hair || '#6b4423',
    shirt: c.shirt || c.suit || '#4a4a4a',
    pattern: c.stripe || c.emblem || c.shirtAlt || 'transparent',
    pants: c.pants || c.skirt || '#4a4a7a',
    shoes: c.shoes || DK,
    eyes: EYE,
    arms: c.skin || SKIN,
    details: c.lips || c.beard || c.buckle || c.belt || 'transparent',
  };

  return char.pattern.map(row =>
    row.split('').map(cell => {
      const code = parseInt(cell, 10);
      const colorKey = COLOR_MAP_CODES[code];
      return colorValues[colorKey] || 'transparent';
    })
  );
}

function PixelCanvas({ pattern, pose }: { pattern: string[][]; pose: 'stand' | 'walk' | 'sit' | 'alert' }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const pixelSize = 3;
  const canvasWidth = pattern[0].length * pixelSize;
  const canvasHeight = pattern.length * pixelSize;

  const transformStyle = useMemo(() => {
    if (pose === 'sit') return 'scaleY(0.88) translateY(4px)';
    if (pose === 'walk') return 'translateY(-2px)';
    if (pose === 'alert') return 'scale(1.04)';
    return 'none';
  }, [pose]);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    ctx.clearRect(0, 0, canvasWidth, canvasHeight);

    for (let y = 0; y < pattern.length; y++) {
      for (let x = 0; x < pattern[y].length; x++) {
        const color = pattern[y][x];
        if (color !== 'transparent') {
          const alpha = pose === 'alert' && y <= 3 ? 0.82 : 1;
          ctx.globalAlpha = alpha;
          ctx.fillStyle = color;
          ctx.fillRect(x * pixelSize, y * pixelSize, pixelSize, pixelSize);
        }
      }
    }
    ctx.globalAlpha = 1;
  }, [pattern, pose, canvasWidth, canvasHeight]);

  return (
    <canvas
      ref={canvasRef}
      width={canvasWidth}
      height={canvasHeight}
      style={{
        imageRendering: 'pixelated',
        background: 'transparent',
        transform: transformStyle,
        transformOrigin: 'center center',
      }}
    />
  );
}

export type EmployeeOrigin = 'fixed' | 'personal' | 'human';

export interface PixelEmployeeProps {
  id: string;
  name: string;
  title?: string;
  status: string;
  currentTask?: string;
  department?: string;
  instanceNum?: number;
  /**
   * P28: 员工来源（AGENTS.md §5.3 / §7.3）
   * - fixed: 固定数字员工，禁止 /ws/agent 直连，仅可走部门大脑
   * - personal: 个人助理，允许 /ws/agent 直连
   * - human: 真实人类，允许 /ws/agent 直连
   * 未传值时按 fixed 处理（最严格兜底）
   */
  origin?: EmployeeOrigin;
  onClick?: (id: string, origin: EmployeeOrigin) => void;
  /**
   * P23: 员工状态自动恢复可见性
   * 当 status=error/offline 时，显示恢复进度
   */
  recoveryStatus?: {
    isRecovering: boolean;
    progress?: number; // 0-100
    step?: string;
    attemptCount?: number;
    estimatedCompleteAt?: string;
  };
}

// 状态颜色
const STATUS_COLORS: Record<string, string> = {
  working: '#52c41a',
  active: '#52c41a',
  learning: '#1677ff',
  evolving: '#722ed1',
  idle: '#faad14',
  busy: '#1677ff',
  offline: '#d9d9d9',
  dormant: '#d9d9d9',
  disabled: '#ff4d4f',
  error: '#ff4d4f',
  archived: '#d9d9d9',
  terminated: '#d9d9d9',
};

// 状态标签
const STATUS_LABELS: Record<string, string> = {
  working: '工作中',
  active: '待命',
  learning: '学习中',
  evolving: '进化中',
  idle: '休息中',
  busy: '协作中',
  offline: '离线',
  dormant: '休眠',
  disabled: '禁用',
  error: '异常',
  archived: '归档',
  terminated: '已离职',
};

// 状态点：只区分在线/离线（故障）
// 在线状态（绿色）：working/active/learning/evolving/idle/busy
// 离线/故障状态（灰色/红色）：offline/dormant/disabled/error/archived/terminated
function isOnline(status: string): boolean {
  const s = status.toLowerCase();
  return ['working', 'active', 'learning', 'evolving', 'idle', 'busy'].includes(s);
}

function isFaulted(status: string): boolean {
  const s = status.toLowerCase();
  return ['error', 'disabled'].includes(s);
}

// 状态说话气泡文本：所有非默认工作状态都显示
function getStatusBubbleText(status: string, currentTask?: string): string | null {
  const s = status.toLowerCase();
  // 有当前任务的工作状态显示任务摘要
  if (['working', 'busy'].includes(s)) {
    return currentTask ? `处理: ${currentTask}` : '处理中...';
  }
  if (s === 'active') return '待命中';
  if (s === 'learning') return '学习中...';
  if (s === 'evolving') return '进化中...';
  if (s === 'idle') return '休息中';
  if (s === 'error') return '出错了!';
  if (s === 'disabled') return '已禁用';
  if (s === 'offline') return null; // 离线不显示气泡，只显示灰点
  if (s === 'dormant') return null;
  if (s === 'archived') return null;
  if (s === 'terminated') return null;
  return null;
}

export default function PixelEmployee({ id, name, title, status, currentTask, department = 'tech', instanceNum = 0, origin = 'fixed', recoveryStatus, onClick }: PixelEmployeeProps) {
  const normalizedStatus = (status || 'idle').toLowerCase();
  const styleKey = getCharacterStyle(department, instanceNum, id);
  const characterPattern = renderCharacter(styleKey);

  // 根据状态决定姿势
  const pose: 'stand' | 'walk' | 'sit' | 'alert' =
    normalizedStatus === 'working' || normalizedStatus === 'learning' || normalizedStatus === 'evolving' ? 'sit' :
    normalizedStatus === 'busy' ? 'walk' :
    normalizedStatus === 'error' || normalizedStatus === 'disabled' ? 'alert' :
    'stand';

  // 状态点：在线=绿色，故障=红色，离线=灰色
  const online = isOnline(normalizedStatus);
  const faulted = isFaulted(normalizedStatus);
  const dotColor = faulted ? '#ff4d4f' : (online ? '#52c41a' : '#d9d9d9');

  // 说话气泡文本
  const bubbleText = getStatusBubbleText(normalizedStatus, currentTask);

  // P23: 是否显示恢复进度环（error/offline 且正在恢复）
  const showRecoveryRing = recoveryStatus?.isRecovering && (faulted || normalizedStatus === 'offline');
  const recoveryProgress = recoveryStatus?.progress ?? 0;

  // P28: origin 角标——fixed=🔒(锁)，personal=⭐(星)，human=👤(人)
  const originBadge: { icon: string; label: string; className: string } | null =
    origin === 'fixed'   ? { icon: '🔒', label: '固定员工 · 仅走部门大脑',           className: 'pixel-employee__origin-badge pixel-employee__origin-badge--fixed' } :
    origin === 'personal' ? { icon: '⭐', label: '个人助理 · 可直连',                className: 'pixel-employee__origin-badge pixel-employee__origin-badge--personal' } :
    origin === 'human'    ? { icon: '👤', label: '人类员工 · 可直连',                className: 'pixel-employee__origin-badge pixel-employee__origin-badge--human' } :
    null;

  return (
    <div
      className={`pixel-employee pixel-employee--${normalizedStatus}`}
      onClick={() => onClick?.(id, origin)}
      role="button"
      tabIndex={0}
    >
      {/* 漫画说话气泡（头部上方） */}
      {bubbleText && (
        <div className={`pixel-employee__speech-bubble ${faulted ? 'pixel-employee__speech-bubble--fault' : ''}`}>
          <span className="pixel-employee__speech-text">{bubbleText}</span>
          <span className="pixel-employee__speech-tail" />
        </div>
      )}

      {/* 员工名称 */}
      <div className="pixel-employee__name">
        <span className="pixel-employee__name-text">{name}</span>
        {title && <span className="pixel-employee__title">{title}</span>}
      </div>

      {/* 像素角色 */}
      <div className="pixel-employee__sprite">
        <PixelCanvas pattern={characterPattern} pose={pose} />
      </div>

      {/* 状态点：只区分在线/离线（故障） */}
      <span
        className={`pixel-employee__status-dot ${online ? 'online' : 'offline'} ${faulted ? 'faulted' : ''}`}
        style={{ background: dotColor }}
        title={online ? '在线' : (faulted ? '故障' : '离线')}
      />

      {/* P28: origin 角标（右下角） */}
      {originBadge && (
        <span className={originBadge.className} title={originBadge.label} aria-label={originBadge.label}>
          {originBadge.icon}
        </span>
      )}

      {/* P23: 恢复进度环（error/offline 且正在恢复时显示） */}
      {showRecoveryRing && (
        <div
          className="pixel-employee__recovery-ring"
          title={recoveryStatus?.step || '正在恢复...'}
        >
          <svg viewBox="0 0 36 36" className="recovery-ring-svg">
            <circle
              cx="18"
              cy="18"
              r="16"
              fill="none"
              stroke="#333"
              strokeWidth="3"
            />
            <circle
              cx="18"
              cy="18"
              r="16"
              fill="none"
              stroke="#52c41a"
              strokeWidth="3"
              strokeDasharray={`${recoveryProgress} 100`}
              strokeLinecap="round"
              transform="rotate(-90 18 18)"
            />
          </svg>
          <span className="recovery-ring-text">{recoveryProgress}%</span>
        </div>
      )}

      {/* P23: 恢复详情悬浮提示 */}
      {showRecoveryRing && (
        <div className="pixel-employee__recovery-tooltip">
          <div className="recovery-tooltip-title">🔄 自动恢复中</div>
          {recoveryStatus?.step && <div className="recovery-tooltip-step">步骤: {recoveryStatus.step}</div>}
          {recoveryStatus?.attemptCount !== undefined && <div className="recovery-tooltip-attempt">已尝试: {recoveryStatus.attemptCount} 次</div>}
          {recoveryStatus?.estimatedCompleteAt && <div className="recovery-tooltip-eta">预计完成: {recoveryStatus.estimatedCompleteAt}</div>}
        </div>
      )}
    </div>
  );
}