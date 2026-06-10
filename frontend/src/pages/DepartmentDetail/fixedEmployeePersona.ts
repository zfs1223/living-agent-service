import { AgentLike } from './types';
import { OfficeOutfit } from './officeMotion';
import type { FixedEmployeeDefinition } from '../../services/fixedEmployeeApi';

export type FixedEmployeePersona = {
  code: string;
  label: string;
  department: string;
  icon: string;
  hair: 'short' | 'side' | 'curly' | 'clean' | 'bun' | 'cap' | 'default';
  glasses: boolean;
  badgeStyle: 'classic' | 'compact' | 'shield' | 'round' | 'text';
  stance: 'calm' | 'focused' | 'friendly' | 'strict' | 'busy';
  outfit: OfficeOutfit;
  accent: string;
  face: 'neutral' | 'smile' | 'serious' | 'alert';
  summary: string;
  badgeLabel: string;
  traits: string[];
  tools: string[];
};

const DEPT_ACCENT: Record<string, string> = {
  tech: '#22d3ee', finance: '#60a5fa', ops: '#f59e0b', sales: '#fb7185', hr: '#f472b6', cs: '#a78bfa', admin: '#c084fc', legal: '#f87171', main: '#38bdf8',
};

function fallbackPersonaFromCode(code: string, definition?: FixedEmployeeDefinition | null, agentPersona?: AgentLike['persona']): FixedEmployeePersona {
  const department = definition?.department || codeDepartment(code);
  const title = definition?.title || definition?.name || code || '固定数字员工';
  const roles = Array.isArray(definition?.roles) ? definition.roles : [];
  const tools = Array.isArray(definition?.tools) ? definition.tools : [];
  const accent = DEPT_ACCENT[department] || '#94a3b8';
  const isStrict = department === 'finance' || department === 'legal' || code.endsWith('01');
  const isFriendly = department === 'sales' || department === 'hr' || department === 'cs';
  
  // Extract instance number for individual variation: 'TECH-028' -> '028' -> 28
  const instanceNum = code.includes('-') ? parseInt(code.split('-')[1] || '0', 10) : 0;
  const instanceIdx = instanceNum % 10; // 0-9 for variety

  if (agentPersona) {
    return {
      code: agentPersona.code || code,
      label: agentPersona.label || title,
      department: agentPersona.department || department,
      icon: agentPersona.icon || definition?.icon || departmentIcon(department),
      hair: (agentPersona.hair as FixedEmployeePersona['hair']) || (instanceIdx % 4 === 0 ? 'side' : instanceIdx % 4 === 1 ? 'cap' : instanceIdx % 4 === 2 ? 'curly' : 'short'),
      glasses: agentPersona.glasses !== undefined ? agentPersona.glasses : (isStrict || department === 'tech' || instanceIdx % 3 === 0),
      badgeStyle: (agentPersona.badgeStyle as FixedEmployeePersona['badgeStyle']) || (isStrict ? 'shield' : isFriendly ? 'round' : 'classic'),
      stance: (agentPersona.stance as FixedEmployeePersona['stance']) || (isStrict ? 'strict' : isFriendly ? 'friendly' : 'focused'),
      outfit: (agentPersona.outfit as OfficeOutfit) || departmentOutfit(department),
      accent: agentPersona.accent || accent,
      face: (agentPersona.face as FixedEmployeePersona['face']) || (isStrict ? 'serious' : isFriendly ? 'smile' : instanceIdx % 3 === 0 ? 'smile' : 'neutral'),
      summary: agentPersona.summary || (definition ? `固定数字员工：${title}` : ''),
      badgeLabel: agentPersona.badgeLabel || title,
      traits: agentPersona.traits?.length ? agentPersona.traits : roles.slice(0, 3),
      tools: agentPersona.tools?.length ? agentPersona.tools : tools.slice(0, 4),
    };
  }

  return {
    code,
    label: title,
    department,
    icon: definition?.icon || departmentIcon(department),
    hair: instanceIdx % 4 === 0 ? 'side' : instanceIdx % 4 === 1 ? 'cap' : instanceIdx % 4 === 2 ? 'curly' : 'short',
    glasses: isStrict || department === 'tech' || instanceIdx % 3 === 0,
    badgeStyle: isStrict ? 'shield' : isFriendly ? 'round' : 'classic',
    stance: isStrict ? 'strict' : isFriendly ? 'friendly' : 'focused',
    outfit: departmentOutfit(department),
    accent,
    face: isStrict ? 'serious' : isFriendly ? 'smile' : instanceIdx % 3 === 0 ? 'smile' : 'neutral',
    summary: definition ? `固定数字员工：${title}` : '',
    badgeLabel: title,
    traits: roles.slice(0, 3),
    tools: tools.slice(0, 4),
  };
}

function codeDepartment(code: string) {
  const prefix = code.split('-')[0]; // 'TECH' from 'TECH-028'
  const normalized = prefix.charAt(0).toUpperCase();
  if (normalized === 'T') return 'tech';
  if (normalized === 'F') return 'finance';
  if (normalized === 'O') return 'ops';
  if (normalized === 'S') return 'sales';
  if (normalized === 'H') return 'hr';
  if (normalized === 'C') return 'cs';
  if (normalized === 'A') return 'admin';
  if (normalized === 'L') return 'legal';
  return 'main';
}

function departmentIcon(department: string) {
  return ({ tech: '💻', finance: '💰', ops: '📈', sales: '📣', hr: '👥', cs: '🎧', admin: '📋', legal: '⚖️', main: '🎯' } as Record<string, string>)[department] || '🤖';
}

function departmentOutfit(department: string): OfficeOutfit {
  if (department === 'cs') return 'support';
  return (department === 'tech' || department === 'finance' || department === 'ops' || department === 'sales' || department === 'hr' || department === 'admin' || department === 'legal') ? department as OfficeOutfit : 'default';
}

function extractCode(agent?: AgentLike) {
  if (agent?.code) return agent.code.toUpperCase();
  // Handle URI format: employee://digital/tech/真盾/028
  if (agent?.id && agent.id.includes('://')) {
    const parts = agent.id.split('/');
    const dept = parts[3]; // 'tech', 'finance', etc. (after digital/human)
    const instance = parts[5]; // '028' - the instance number
    if (dept && dept !== 'digital' && dept !== 'human') {
      return `${dept.toUpperCase()}-${instance}`;
    }
  }
  const candidates = [agent?.id, agent?.title, agent?.name, agent?.current_task].filter(Boolean).join(' ').toUpperCase();
  const match = candidates.match(/([A-Z][0-9]{2})/);
  return match?.[1] || '';
}

function extractDefinitionCode(definition: FixedEmployeeDefinition) {
  return (definition.code || '').toUpperCase() || 'default';
}

function personaFromDefinition(definition: FixedEmployeeDefinition): FixedEmployeePersona {
  const code = extractDefinitionCode(definition);
  const personality = definition.personality || {};
  const roles = Array.isArray(definition.roles) ? definition.roles : [];
  const tools = Array.isArray(definition.tools) ? definition.tools : [];
  const departmentName = definition.departmentName || definition.department || 'unknown';
  const title = definition.title || definition.name || code || 'Employee';
  const icon = definition.icon || '🤖';
  const opener = personality.openness ?? 0.5;
  const conscientiousness = personality.conscientiousness ?? 0.5;
  const extroversion = personality.extroversion ?? 0.5;
  const agreeableness = personality.agreeableness ?? 0.5;
  const hair = conscientiousness > 0.85 ? 'clean' : extroversion > 0.75 ? 'side' : agreeableness > 0.75 ? 'bun' : opener > 0.7 ? 'curly' : 'short';
  const glasses = conscientiousness > 0.7 || title.includes('审查') || title.includes('管理员');
  const badgeStyle = tools.length > 2 ? 'shield' : roles.length > 2 ? 'classic' : 'compact';
  const face = conscientiousness > 0.85 ? 'serious' : extroversion > 0.7 ? 'smile' : agreeableness > 0.7 ? 'smile' : 'neutral';
  const stance = conscientiousness > 0.85 ? 'strict' : extroversion > 0.7 ? 'friendly' : opener > 0.7 ? 'busy' : 'focused';
  return {
    code,
    label: title,
    department: definition.department || 'main',
    icon,
    hair,
    glasses,
    badgeStyle,
    stance,
    outfit: (definition.department || 'default') as OfficeOutfit,
    accent: DEPT_ACCENT[definition.department] || '#94a3b8',
    face,
    summary: `后端定义：${departmentName} · ${roles.slice(0, 2).join(' / ')}`,
    badgeLabel: title,
    traits: roles.slice(0, 3),
    tools: tools.slice(0, 4),
  };
}

export function getFixedEmployeePersona(agent?: AgentLike): FixedEmployeePersona | null {
  if (agent?.persona) {
    const extractedCode = extractCode(agent);
    const codeDept = codeDepartment(extractedCode);
    // Extract instance number for individual variation: 'TECH-028' -> '028' -> 28
    const instanceNum = extractedCode.includes('-') ? parseInt(extractedCode.split('-')[1] || '0', 10) : 0;
    const instanceIdx = instanceNum % 10; // 0-9 for unique appearance

    // Skip empty, 'main' and 'default' departments; fall back to code prefix
    const resolvedDept = (agent.persona.department && agent.persona.department !== 'main' && agent.persona.department !== 'default')
      ? agent.persona.department
      : (agent.department && agent.department !== 'main' && agent.department !== 'default')
        ? agent.department
        : codeDept;

    const isStrict = resolvedDept === 'finance' || resolvedDept === 'legal' || instanceNum % 100 < 10;
    const isFriendly = resolvedDept === 'sales' || resolvedDept === 'hr' || resolvedDept === 'cs';

    const resolvedOutfit = (agent.persona.outfit && agent.persona.outfit !== 'default' && agent.persona.outfit !== resolvedDept)
      ? agent.persona.outfit
      : departmentOutfit(resolvedDept);

    // Individual hair style from instance number
    const hairStyles: FixedEmployeePersona['hair'][] = ['short', 'side', 'curly', 'cap', 'bun', 'clean'];
    const resolvedHair = (agent.persona.hair && agent.persona.hair !== 'default')
      ? agent.persona.hair
      : hairStyles[instanceIdx % hairStyles.length];

    // Individual glasses from instance number
    const hasGlasses = agent.persona.glasses !== undefined
      ? agent.persona.glasses
      : (isStrict || resolvedDept === 'tech' || instanceIdx % 3 === 0);

    // Individual face expression
    const faces: FixedEmployeePersona['face'][] = ['neutral', 'smile', 'serious'];
    const resolvedFace = agent.persona.face
      ? agent.persona.face
      : (isStrict ? 'serious' : isFriendly ? 'smile' : faces[instanceIdx % faces.length]);

    return {
      code: agent.persona.code || agent.code || extractedCode,
      label: agent.persona.label || agent.title || agent.name,
      department: resolvedDept,
      icon: agent.persona.icon || agent.avatar || '🤖',
      hair: resolvedHair,
      glasses: hasGlasses,
      badgeStyle: agent.persona.badgeStyle || 'classic',
      stance: agent.persona.stance || 'focused',
      outfit: resolvedOutfit as OfficeOutfit,
      accent: agent.persona.accent || DEPT_ACCENT[resolvedDept] || '#94a3b8',
      face: resolvedFace,
      summary: agent.persona.summary || '',
      badgeLabel: agent.persona.badgeLabel || agent.title || agent.name,
      traits: agent.persona.traits || [],
      tools: agent.persona.tools || [],
    };
  }
  const code = extractCode(agent);
  return code ? fallbackPersonaFromCode(code) : null;
}

export function getFixedEmployeePersonaFromDefinition(definition?: FixedEmployeeDefinition | null): FixedEmployeePersona | null {
  if (!definition) return null;
  return fallbackPersonaFromCode(extractDefinitionCode(definition), definition);
}

export function getFixedEmployeePersonaFromAgent(agent: AgentLike): FixedEmployeePersona {
  const code = extractCode(agent);
  const department = agent.department || codeDepartment(code);
  const title = agent.title || agent.name || code || '固定数字员工';
  return fallbackPersonaFromCode(code || 'default', {
    code,
    name: agent.name,
    title,
    department,
    departmentName: agent.departmentName || '',
    neuronId: '',
    roles: agent.persona?.traits || [],
    capabilities: [],
    tools: agent.persona?.tools || [],
    channel: '',
    personality: null,
    icon: agent.avatar || agent.persona?.icon || '',
    requiredSkills: [],
  }, agent.persona);
}

export function getPersonaTone(persona?: FixedEmployeePersona | null) {
  if (!persona) return { code: '', label: '', department: '', icon: '🤖', hair: 'default', glasses: false, badgeStyle: 'classic', stance: 'calm', outfit: 'default' as OfficeOutfit, accent: '#94a3b8', face: 'neutral' as const, summary: '', badgeLabel: '', traits: [], tools: [] };
  return persona;
}
