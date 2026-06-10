import { useTranslation } from 'react-i18next';
import { AgentLike } from './types';
import { normalizeStatus } from './status';
import { getFixedEmployeePersonaFromAgent, getPersonaTone } from './fixedEmployeePersona';

export default function LoungeStrip({ agents, onAgentClick }: { agents: AgentLike[]; onAgentClick: (id: string) => void; }) {
  const { t, i18n } = useTranslation();
  const isChinese = i18n.language?.startsWith('zh');
  const idleAgents = agents.filter((agent) => normalizeStatus(agent.status) === 'idle');
  return (
    <section className="card office-panel office-panel--compact office-panel--lounge">
      <div className="office-panel__header">
        <div>
          <h2>{t('deptOffice.loungeStandby')}</h2>
          <p>{t('deptOffice.loungeDesc')}</p>
        </div>
      </div>
      <div className="lounge-strip">
        {idleAgents.slice(0, 3).map((agent) => {
          const persona = getPersonaTone(getFixedEmployeePersonaFromAgent(agent));
          return (
            <button key={agent.id} className="lounge-card" onClick={() => onAgentClick(agent.id || agent.code || agent.name)} type="button">
              <span className={`station-avatar__dot status-idle station-avatar__dot--${persona?.badgeStyle || 'classic'}`} />
              <strong>{agent.name}{persona?.code ? ` · ${persona.code}` : ''}</strong>
              <span>{persona?.summary || agent.title || t('deptOffice.onStandby')}</span>
            </button>
          );
        })}
        {idleAgents.length === 0 && <div className="empty-state-small">{t('deptOffice.noIdleEmployees')}</div>}
      </div>
    </section>
  );
}
