import { AgentLike } from './types';
import { OfficeTransition, getOfficeMotion } from './officeMotion';
import { getStatusMeta, normalizeStatus } from './status';
import { getFixedEmployeePersona, getPersonaTone } from './fixedEmployeePersona';
import { useRef, useEffect, useMemo } from 'react';

const DK = '#3a4050';
const DK2 = '#4a5060';
const SKIN = '#f5d5b8';
const EYE = '#0a0a14';

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
  skull: {
    colors: { hair: '#5a3a28', skin: SKIN, shirt: '#4a4a5a', skull: '#f0f0f0', pants: '#4a4a7a', shoes: DK },
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
      '000344444300',
      '000340004300',
      '000340904300',
      '000334443300',
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
  dress: {
    colors: { hair: '#7a3a3a', skin: SKIN, dress: '#a06a3a', sleeves: DK, shoes: DK, lips: '#f04040' },
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
} as const;

type CharacterStyleKey = keyof typeof CHARACTER_STYLES;

const STYLE_BY_DEPT: Record<string, CharacterStyleKey> = {
  tech: 'striped',
  finance: 'suit',
  ops: 'skull',
  sales: 'red_female',
  hr: 'purple_female',
  legal: 'sunglasses',
  admin: 'beard',
  support: 'dress',
  cs: 'dress',
};

const STYLE_KEYS: CharacterStyleKey[] = ['striped', 'suit', 'skull', 'red_female', 'purple_female', 'sunglasses', 'beard', 'dress'];

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
    shirt: c.shirt || c.suit || c.dress || '#4a4a4a',
    pattern: c.stripe || c.skull || c.emblem || c.shirtAlt || c.shirtPattern || 'transparent',
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
  }, [pattern, pose]);

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

export default function PixelAgent({ agent, onAgentClick, lane = 0, transition, motion }: { agent: AgentLike; onAgentClick: (id: string) => void; lane?: number; transition?: OfficeTransition; motion?: ReturnType<typeof getOfficeMotion>; }) {
  const status = normalizeStatus(agent.status);
  const meta = getStatusMeta(status);
  const officeMotion = motion ?? getOfficeMotion(agent);
  const persona = getPersonaTone(getFixedEmployeePersona(agent));
  const moving = transition?.phase === 'entering' || transition?.phase === 'moving';
  const exiting = transition?.direction === 'out';

  const extractedCode = extractCode(agent);
  const instanceNum = extractedCode.includes('-') ? parseInt(extractedCode.split('-')[1] || '0', 10) : 0;
  const styleKey = getCharacterStyle(persona.department || 'main', instanceNum, agent.id);
  const characterPattern = renderCharacter(styleKey);

  const pose = officeMotion.pose;
  const facing = pose === 'walk' ? (lane % 2 === 0 ? 'front' : 'side') : pose === 'sit' ? 'desk' : pose === 'alert' ? 'alert' : 'front';

  return (
    <button
      type="button"
      className={[
        `pixel-agent pixel-agent--${meta.accent}`,
        `pixel-agent--${officeMotion.mood}`,
        `pixel-agent--lane-${lane}`,
        moving ? 'pixel-agent--moving' : '',
        exiting ? 'pixel-agent--exiting' : '',
        `pixel-agent--zone-${officeMotion.zone}`,
        `pixel-agent--phase-${transition?.phase || 'idle'}`,
        `pixel-agent--motion-${officeMotion.motionKind}`,
        `pixel-agent--pose-${pose}`,
        `pixel-agent--facing-${facing}`,
        `pixel-agent--style-${styleKey}`,
        `pixel-agent--status-${status}`,
        persona ? `pixel-agent--persona-${persona.code}` : '',
      ].filter(Boolean).join(' ')}
      onClick={(e) => {
        e.stopPropagation();
        onAgentClick(agent.id || agent.code || agent.name);
      }}
      style={{ animationDelay: `${lane * 120 + officeMotion.jitterMs}ms` }}
      data-zone={officeMotion.zone}
      data-status={status}
      data-motion={officeMotion.motionKind}
      aria-label={`${agent.name} ${status}`}
    >
      <span className="pixel-agent__label pixel-agent__label--top">
        <span className={`pixel-agent__pulse status-${status}`} />
        {agent.name}
      </span>
      <span className="pixel-agent__sprite" aria-hidden="true">
        <PixelCanvas
          pattern={characterPattern}
          pose={pose}
        />
      </span>
    </button>
  );
}

function extractCode(agent?: AgentLike) {
  if (agent?.code) return agent.code.toUpperCase();
  if (agent?.id && agent.id.includes('://')) {
    const parts = agent.id.split('/');
    const dept = parts[3];
    const instance = parts[5];
    if (dept && dept !== 'digital' && dept !== 'human') {
      return instance ? `${dept.toUpperCase()}-${instance}` : dept.toUpperCase();
    }
  }
  const candidates = [agent?.id, agent?.title, agent?.name, agent?.current_task].filter(Boolean).join(' ').toUpperCase();
  const match = candidates.match(/([A-Z][0-9]{2})/);
  return match ? match[1] : 'M0';
}
