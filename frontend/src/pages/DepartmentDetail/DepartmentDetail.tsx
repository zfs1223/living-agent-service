import { useEffect, useMemo, useRef, useState, useCallback } from 'react';
import { useNavigate, useLocation, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import PublicTaskBoard from '../../components/PublicTaskBoard';
import { agentApi } from '../../services/api';
import { fixedEmployeeApi, type FixedEmployeeDefinition, type FixedEmployeePersona, type FixedEmployeeProfile } from '../../services/fixedEmployeeApi';
import { DepartmentActivityFeed, DepartmentBrainPanel, DepartmentHero, DepartmentTabs, LoungeStrip, OfficeFloor } from './components';
import DepartmentChatInline from './DepartmentChatInline';
import type { ExecutionEventData, EmployeeStatusChangedData } from './DepartmentChatInline';
import type { DeptInfo, AgentLike } from './types';
import { getFixedEmployeePersonaFromDefinition } from './fixedEmployeePersona';
import { useAuthStore } from '../../stores';

type DepartmentCode = 'main' | 'tech' | 'sales' | 'finance' | 'hr' | 'admin' | 'cs' | 'legal' | 'ops';

const DEPARTMENTS: Record<DepartmentCode, DeptInfo> = {
  main: { icon: '🏢', name: '主部门', name_en: 'Main' },
  tech: { icon: '🧠', name: '技术部', name_en: 'Tech' },
  sales: { icon: '📈', name: '销售部', name_en: 'Sales' },
  finance: { icon: '💰', name: '财务部', name_en: 'Finance' },
  hr: { icon: '👥', name: '人事部', name_en: 'HR' },
  admin: { icon: '🛠️', name: '行政部', name_en: 'Admin' },
  cs: { icon: '🎧', name: '客服部', name_en: 'Customer Support' },
  legal: { icon: '⚖️', name: '法务部', name_en: 'Legal' },
  ops: { icon: '🚚', name: '运营部', name_en: 'Operations' },
};

type UserLike = {
  identity?: string;
  access_level?: string;
  department_code?: string;
  title?: string;
};

function extractCode(...values: Array<string | undefined>) {
  const candidates = values.filter(Boolean).join(' ').toUpperCase();
  const match = candidates.match(/\b([A-Z][0-9]{2})\b/);
  return match?.[1] || '';
}

export default function DepartmentDetail() {
  const { code } = useParams<{ code: string }>();
  const location = useLocation();
  const navigate = useNavigate();
  const { t, i18n } = useTranslation();
  const isChinese = i18n.language?.startsWith('zh');
  const [activeTab, setActiveTab] = useState<'overview' | 'tasks'>(location.pathname.endsWith('/tasks') ? 'tasks' : 'overview');
  const [selectedAgentId, setSelectedAgentId] = useState<string | null>(null);
  const agentInfoRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    setActiveTab(location.pathname.endsWith('/tasks') ? 'tasks' : 'overview');
  }, [location.pathname]);

  const user = useAuthStore((s) => s.user) as unknown as UserLike | undefined;
  const isEnterprise = Boolean(user?.identity === 'INTERNAL_ENTERPRISE' || user?.access_level === 'FULL');
  const isDepartmentHead = Boolean(user?.department_code === code && user?.title?.includes('负责人'));
  const canAccessDepartmentBrain = Boolean(isEnterprise || isDepartmentHead);
  const canSeeDeptTasks = isEnterprise || !!user?.department_code;

  const deptCode = (code || 'main') as DepartmentCode;
  const deptInfo = DEPARTMENTS[deptCode] || DEPARTMENTS.main;

  const [department] = useState<any>(null);
  const [baseAgents, setBaseAgents] = useState<AgentLike[]>([]);
  const [members] = useState<any[]>([]);
  const [fixedDefs, setFixedDefs] = useState<FixedEmployeeDefinition[]>([]);
  const [fixedProfiles, setFixedProfiles] = useState<FixedEmployeeProfile[]>([]);
  const [fixedPersonas, setFixedPersonas] = useState<FixedEmployeePersona[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  /** 员工实时状态覆盖：code -> { status, currentTask, lastActiveAt } */
  const [employeeOverrides, setEmployeeOverrides] = useState<Record<string, { status: string; currentTask?: string; lastActiveAt?: string }>>({});

  const onExecutionEvent = useCallback((event: ExecutionEventData) => {
    const employeeCode = event.employeeCode;
    if (!employeeCode) return;

    setEmployeeOverrides((prev) => {
      const existing = prev[employeeCode] || {};
      const updated = { ...existing };

      switch (event.eventType) {
        case 'receipt_received': {
          const receiptStatus = event.receiptStatus;
          if (receiptStatus === 'COMPLETED') {
            updated.status = 'idle';          // ✅ 任务完成 → 休息区
            updated.currentTask = '';
            updated.lastActiveAt = event.timestamp;
          } else if (receiptStatus === 'FAILED') {
            updated.status = 'error';
            updated.lastActiveAt = event.timestamp;
          } else if (receiptStatus === 'DEGRADED') {
            updated.status = 'idle';          // ✅ 降级完成也休息
            updated.lastActiveAt = event.timestamp;
          } else {
            updated.status = 'working';       // ✅ 执行中 → working
            updated.lastActiveAt = event.timestamp;
          }
          break;
        }
        case 'execution_started':
        case 'employee_assigned':
          updated.status = 'working';         // ✅ 开始执行 → working
          updated.lastActiveAt = event.timestamp;
          break;
        case 'async_final_response':
          // 所有员工完成，进入空闲状态（休息区）
          updated.status = 'idle';            // ✅ 全部完成 → 休息区
          updated.currentTask = '';
          updated.lastActiveAt = event.timestamp;
          break;
        default:
          break;
      }

      if (updated.status === existing.status && updated.currentTask === existing.currentTask) {
        return prev;
      }
      return { ...prev, [employeeCode]: updated };
    });
  }, []);

  /** ✅ 处理后端推送的员工状态变化事件 */
  const onEmployeeStatusChanged = useCallback((event: EmployeeStatusChangedData) => {
    const employeeId = event.employeeId;
    if (!employeeId) return;

    setEmployeeOverrides((prev) => {
      const existing = prev[employeeId] || {};
      // 后端推送的新状态直接使用（已转为小写）
      const newStatus = event.newStatus.toLowerCase();
      if (newStatus === existing.status) return prev;

      return {
        ...prev,
        [employeeId]: {
          ...existing,
          status: newStatus,
          lastActiveAt: event.timestamp,
        },
      };
    });
  }, []);

  useEffect(() => {
    let cancelled = false;

    async function loadDepartmentEmployees() {
      setIsLoading(true);
      setLoadError(null);
      try {
        const [definitionsResult, profilesResult, personasResult, agentsResult] = await Promise.allSettled([
          fixedEmployeeApi.getDefinitionsByDepartment(deptCode),
          fixedEmployeeApi.getProfiles(),
          fixedEmployeeApi.getPersonas(),
          agentApi.list(),
        ]);

        if (cancelled) return;

        if (definitionsResult.status === 'fulfilled') {
          setFixedDefs(definitionsResult.value);
        } else {
          setFixedDefs([]);
          console.warn('Failed to load fixed employee definitions', definitionsResult.reason);
        }

        if (profilesResult.status === 'fulfilled') {
          setFixedProfiles(profilesResult.value);
        } else {
          setFixedProfiles([]);
          console.warn('Failed to load fixed employee profiles', profilesResult.reason);
        }

        if (personasResult.status === 'fulfilled') {
          setFixedPersonas(personasResult.value);
        } else {
          setFixedPersonas([]);
          console.warn('Failed to load fixed employee personas', personasResult.reason);
        }

        if (agentsResult.status === 'fulfilled') {
          setBaseAgents((agentsResult.value || []).map((agent: any) => ({
            id: agent.id || agent.agent_id || agent.employeeId || agent.code || agent.name,
            name: agent.name || agent.display_name || agent.title || agent.id,
            avatar: agent.avatar || agent.avatar_url || agent.icon,
            title: agent.title || agent.role_description || agent.position,
            status: agent.status,
            current_task: agent.current_task || agent.currentTask || agent.description,
            updated_at: agent.updated_at || agent.updatedAt,
            last_active_at: agent.last_active_at || agent.lastActiveAt,
            code: agent.code,
            department: agent.department || agent.department_code,
            departmentName: agent.departmentName || agent.department_name,
          })).filter((agent: AgentLike) => Boolean(agent.id)));
        } else {
          setBaseAgents([]);
          console.warn('Failed to load base agents', agentsResult.reason);
        }

        const failures = [definitionsResult, profilesResult, personasResult, agentsResult].filter((result) => result.status === 'rejected').length;
        setLoadError(failures > 0 ? t('deptOffice.partialLoadError', { count: failures }) : null);
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    }

    loadDepartmentEmployees();
    return () => {
      cancelled = true;
    };
  }, [deptCode, isChinese]);

  const fixedEmployees = useMemo(() => {
    const profileByCode = new Map(fixedProfiles.map((profile) => [profile.code, profile]));
    const personaByCode = new Map(fixedPersonas.map((persona) => [persona.code, persona]));

    return fixedDefs.map((def) => {
      const matched = baseAgents.find((agent) => {
        const codeMatch = extractCode(agent.id, agent.name, agent.title, agent.current_task, agent.code);
        return codeMatch === def.code || agent.id === def.code;
      });
      const profile = profileByCode.get(def.code);
      const dbPersona = personaByCode.get(def.code);
      const fallbackPersona = getFixedEmployeePersonaFromDefinition(def);
      const localizedName = isChinese
        ? (profile?.displayNameZh || def.name || def.title || fallbackPersona?.label || def.code)
        : (profile?.displayNameEn || matched?.name || def.name || def.title || def.code);
      const localizedTitle = isChinese
        ? (def.title || profile?.displayNameZh || fallbackPersona?.badgeLabel || def.departmentName)
        : (def.title || matched?.title || def.name);
      const localizedTask = isChinese
        ? (profile?.currentTask || profile?.summaryZh || fallbackPersona?.summary || def.roles?.[0] || def.title)
        : (profile?.currentTask || profile?.summaryEn || matched?.current_task || def.roles?.[0] || def.title);
      const traits = profile?.traits?.length ? profile.traits : fallbackPersona?.traits || def.roles || [];
      const tools = profile?.toolTags?.length ? profile.toolTags : fallbackPersona?.tools || def.tools || [];

      const resolvedDepartment = def.department || (def.name && /真[构捷稳模续盾策绘栈]/.test(def.name) ? 'tech' : def.name && /真[账审算预]/.test(def.name) ? 'finance' : def.name && /真[析营度流]/.test(def.name) ? 'ops' : def.name && /真[拓宣联]/.test(def.name) ? 'sales' : def.name && /真[才绩]/.test(def.name) ? 'hr' : def.name && /真[晴修]/.test(def.name) ? 'cs' : def.name && /真[序典笔]/.test(def.name) ? 'admin' : def.name && /真[律规]/.test(def.name) ? 'legal' : 'main');
      // Extract instance number from backendId URI (employee://digital/tech/真盾/028 -> 028)
      const backendUri = matched?.id || profile?.employeeId || dbPersona?.employeeId || '';
      const instanceMatch = backendUri.match(/\/(\d{3})$/);
      const instanceNum = instanceMatch ? instanceMatch[1] : '0';
      const resolvedCode = `${resolvedDepartment.toUpperCase()}-${instanceNum}`;

      return {
        id: def.code, // Use definition code as primary ID (e.g., "T01")
        backendId: matched?.id || profile?.employeeId || dbPersona?.employeeId,
        name: localizedName,
        avatar: dbPersona?.icon || fallbackPersona?.icon || def.icon,
        title: localizedTitle,
        status: employeeOverrides[def.code]?.status || profile?.status || matched?.status || 'active',
        current_task: employeeOverrides[def.code]?.currentTask !== undefined ? employeeOverrides[def.code].currentTask : localizedTask,
        updated_at: matched?.updated_at,
        last_active_at: employeeOverrides[def.code]?.lastActiveAt || profile?.lastActiveAt || matched?.last_active_at,
        code: resolvedCode, // Full code with instance number (e.g., "TECH-028")
        department: resolvedDepartment,
        departmentName: def.departmentName,
        persona: {
          code: def.code,
          label: localizedName,
          department: resolvedDepartment,
          icon: dbPersona?.icon && dbPersona.icon !== '🤖' ? dbPersona.icon : (fallbackPersona?.icon || def.icon),
          hair: (dbPersona?.hair && dbPersona.hair !== 'default') ? dbPersona.hair : (fallbackPersona?.hair || 'short'),
          glasses: dbPersona?.glasses !== undefined ? dbPersona.glasses : (fallbackPersona?.glasses ?? false),
          badgeStyle: (dbPersona?.badgeStyle && dbPersona.badgeStyle !== 'classic') ? dbPersona.badgeStyle : (fallbackPersona?.badgeStyle || 'classic'),
          stance: (dbPersona?.stance && dbPersona.stance !== 'focused') ? dbPersona.stance : (fallbackPersona?.stance || 'focused'),
          outfit: (dbPersona?.outfit && dbPersona.outfit !== 'default') ? dbPersona.outfit : (fallbackPersona?.outfit || resolvedDepartment || 'default'),
          accent: (dbPersona?.accentColor && dbPersona.accentColor !== '#94a3b8') ? dbPersona.accentColor : (fallbackPersona?.accent || '#94a3b8'),
          face: (dbPersona?.face && dbPersona.face !== 'neutral') ? dbPersona.face : (fallbackPersona?.face || 'neutral'),
          summary: isChinese ? (profile?.summaryZh || fallbackPersona?.summary || '') : (profile?.summaryEn || fallbackPersona?.summary || ''),
          badgeLabel: dbPersona?.badgeLabel || fallbackPersona?.badgeLabel || def.title,
          traits,
          tools,
        },
      } satisfies AgentLike & { backendId?: string };
    });
  }, [baseAgents, fixedDefs, fixedPersonas, fixedProfiles, isChinese, employeeOverrides]);

  const officeAgents = fixedEmployees.length > 0 ? fixedEmployees : baseAgents;

  const selectedAgent = selectedAgentId ? officeAgents.find((agent) => {
    const agentWithExtraIds = agent as AgentLike & { backendId?: string; definitionId?: string };
    return agentWithExtraIds.id === selectedAgentId ||
      agentWithExtraIds.name === selectedAgentId ||
      agentWithExtraIds.code === selectedAgentId ||
      agentWithExtraIds.backendId === selectedAgentId ||
      agentWithExtraIds.definitionId === selectedAgentId;
  }) || officeAgents.find((agent) => selectedAgentId.startsWith(`${agent.id}::`)) || null : null;

  useEffect(() => {
    if (!selectedAgentId) return;

    const handlePointerDown = (event: PointerEvent) => {
      const target = event.target as HTMLElement | null;
      if (target && agentInfoRef.current?.contains(target)) return;
      if (target?.closest('.pixel-agent')) return;
      if (target?.closest('.office-seat-card')) return;
      setSelectedAgentId(null);
    };

    const timer = window.setTimeout(() => {
      document.addEventListener('pointerdown', handlePointerDown);
    }, 0);

    return () => {
      window.clearTimeout(timer);
      document.removeEventListener('pointerdown', handlePointerDown);
    };
  }, [selectedAgentId]);

  const handleAgentClick = (agentId: string) => {
    setSelectedAgentId(agentId);
  };

  return (
    <div className="page-container office-page">
      {selectedAgent ? (
        <section ref={agentInfoRef} className="card office-panel office-panel--compact office-panel--agent-drawer office-panel--agent-drawer-fixed">
          <div className="office-panel__header">
            <div>
              <h2>{t('deptOffice.digitalEmployeeInfo')}</h2>
              <p>{t('deptOffice.digitalEmployeeInfoDesc')}</p>
            </div>
            <button className="btn btn-ghost" onClick={() => setSelectedAgentId(null)} type="button">
              {t('common.close')}
            </button>
          </div>
          <div className="agent-drawer">
            <div className="agent-drawer__avatar">{selectedAgent.avatar || selectedAgent.persona?.icon || '🤖'}</div>
            <div className="agent-drawer__meta">
              <h3>{selectedAgent.name}</h3>
              <p>{selectedAgent.title || selectedAgent.persona?.badgeLabel || t('deptOffice.digitalEmployee')}</p>
              <div className="agent-drawer__row"><span>{t('deptOffice.code')}</span><strong>{selectedAgent.persona?.code || selectedAgent.code || selectedAgent.id}</strong></div>
              <div className="agent-drawer__row"><span>{t('deptOffice.department')}</span><strong>{selectedAgent.departmentName || selectedAgent.department || '-'}</strong></div>
              <div className="agent-drawer__row"><span>{t('deptOffice.status')}</span><strong>{selectedAgent.status || '-'}</strong></div>
              <div className="agent-drawer__row"><span>{t('deptOffice.currentTask')}</span><strong>{selectedAgent.current_task || '-'}</strong></div>
              <div className="agent-drawer__row"><span>{t('deptOffice.updated')}</span><strong>{selectedAgent.updated_at || selectedAgent.last_active_at || '-'}</strong></div>
            </div>
          </div>
          {selectedAgent.persona?.summary ? <p className="agent-drawer__summary">{selectedAgent.persona.summary}</p> : null}
          {selectedAgent.persona?.traits?.length ? (
            <div className="agent-drawer__tags">
              {selectedAgent.persona.traits.map((trait) => <span key={trait}>{trait}</span>)}
            </div>
          ) : null}
          {selectedAgent.persona?.tools?.length ? (
            <div className="agent-drawer__tools">
              <strong>{t('deptOffice.tools')}</strong>
              <div>{selectedAgent.persona.tools.map((tool) => <span key={tool}>{tool}</span>)}</div>
            </div>
          ) : null}
        </section>
      ) : null}
      <div className="office-page__topbar">
        <button className="btn btn-ghost" onClick={() => navigate('/departments')} type="button">
          {t('common.back')}
        </button>
        <div className="office-page__topbar-right">
          <span className="office-page__status-pill"><span className="office-page__status-dot" />{t('deptOffice.officeLive')}</span>
        </div>
      </div>

      <DepartmentHero
        deptInfo={deptInfo}
        department={department}
        membersCount={members.length}
        agentsCount={officeAgents.length}
        canSeeDeptTasks={canSeeDeptTasks}
        canAccessDepartmentBrain={canAccessDepartmentBrain}
      />

      <DepartmentChatInline
        departmentCode={code || 'main'}
        deptName={deptInfo.name}
        onExecutionEvent={onExecutionEvent}
        onEmployeeStatusChanged={onEmployeeStatusChanged}
      />

      <DepartmentTabs
        activeTab={activeTab}
        setActiveTab={setActiveTab}
        navigate={navigate}
        code={code}
        canSeeDeptTasks={canSeeDeptTasks}
      />

      {isLoading ? (
        <div className="loading-state">{t('common.loading')}</div>
      ) : activeTab === 'tasks' && canSeeDeptTasks ? (
        <PublicTaskBoard department={code} />
      ) : (
        <div className="office-layout office-layout--single">
          {loadError ? <div className="alert alert-warning">{loadError}</div> : null}
          <div className="office-layout__left">
            <div className="office-room-shell">
              <OfficeFloor agents={officeAgents} onAgentClick={handleAgentClick} />
            </div>
            <DepartmentBrainPanel
              brain={department?.brain || null}
              deptInfo={deptInfo}
              canAccessDepartmentBrain={canAccessDepartmentBrain}
              onBrainChat={() => navigate(`/chat?brain=${encodeURIComponent(code || '')}&dept=${encodeURIComponent(deptInfo.name)}`)}
              onCreateAgent={() => navigate('/agents/new')}
              isEnterprise={isEnterprise}
              emptyDesc={t('deptOffice.noBrainConfiguredDesc')}
            />
            <DepartmentActivityFeed timelineCount={0} />
            <LoungeStrip agents={officeAgents} onAgentClick={handleAgentClick} />
            {members.length > 0 ? (
              <section className="card office-panel office-panel--compact office-panel--members">
                <div className="office-panel__header">
                  <div>
                    <h2>{t('deptOffice.teamMembers')}</h2>
                    <p>{t('deptOffice.teamMembersDesc')}</p>
                  </div>
                </div>
                <div className="member-list member-list--compact">
                  {members.map((member: any) => (
                    <div key={member.id} className="member-item">
                      <div className="member-avatar">{member.display_name?.charAt(0)?.toUpperCase() || member.username?.charAt(0)?.toUpperCase() || member.name?.charAt(0)?.toUpperCase() || '?'}</div>
                      <div className="member-info">
                        <span className="member-name">{member.display_name || member.username || member.name}</span>
                        <span className="member-title">{member.title || t('deptOffice.member')}</span>
                      </div>
                    </div>
                  ))}
                </div>
              </section>
            ) : null}
          </div>
        </div>
      )}
    </div>
  );
}
