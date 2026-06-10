import { useTranslation } from 'react-i18next';
import { AgentLike } from './types';
import { getStatusLabel, getZoneByStatus, getZoneLabel, normalizeStatus, getStatusMeta } from './status';
import PixelAgent from './PixelAgent';
import { getFixedEmployeePersonaFromAgent, getPersonaTone } from './fixedEmployeePersona';

export default function EmployeeStationCard({ agent, onAgentClick }: { agent: AgentLike; onAgentClick: (id: string) => void; }) {
  const { t, i18n } = useTranslation();
  const isChinese = i18n.language?.startsWith('zh');
  const status = normalizeStatus(agent.status);
  const zone = getZoneByStatus(status);
  const meta = getStatusMeta(status);
  const persona = getPersonaTone(getFixedEmployeePersonaFromAgent(agent));

  return (
    <button type="button" className={`station-card station-card--${zone}`} onClick={() => onAgentClick(agent.id)}>
      <div className="station-card__top">
        <div className={`station-card__avatar station-card__avatar--${meta.accent}`}>
          <PixelAgent agent={agent} onAgentClick={onAgentClick} lane={0} />
        </div>
        <div className="station-card__meta">
          <strong>{agent.name}</strong>
          <span>{agent.title || t('deptOffice.digitalEmployee')}</span>
          {persona.badgeLabel ? <span className="station-card__badge-label">{persona.badgeLabel}</span> : null}
        </div>
      </div>
      <div className="station-card__body">
        <span className={`station-card__badge status-${status}`}>{getStatusLabel(status, isChinese)}</span>
        <span className="station-card__zone">{getZoneLabel(zone, isChinese)}</span>
      </div>
      <div className="station-card__traits">
        {persona.traits.slice(0, 3).map((trait) => (
          <span key={trait} className="station-card__trait">{trait}</span>
        ))}
      </div>
      <div className="station-card__tool-row">
        {persona.tools.slice(0, 3).map((tool) => (
          <span key={tool} className="station-card__tool">{tool}</span>
        ))}
      </div>
      <div className="station-card__persona-desc">{persona.summary || t('deptOffice.noPersonaSummary')}</div>
      <div className="station-card__footer">
        <span>{t('deptOffice.lastActive')}</span>
        <span>{agent.updated_at || agent.last_active_at || '--'}</span>
      </div>
      <p className="station-card__task">{agent.current_task || t('deptOffice.noCurrentTaskSummary')}</p>
    </button>
  );
}
