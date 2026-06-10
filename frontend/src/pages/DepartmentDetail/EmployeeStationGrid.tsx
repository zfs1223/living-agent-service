import { useTranslation } from 'react-i18next';
import EmployeeStationCard from './EmployeeStationCard';
import { AgentLike } from './types';

export default function EmployeeStationGrid({ agents, onAgentClick }: { agents: AgentLike[]; onAgentClick: (id: string) => void; }) {
  const { t } = useTranslation();
  if (!agents.length) return null;
  return (
    <section className="card office-panel office-panel--compact office-panel--stations">
      <div className="office-panel__header">
        <div>
          <h2>{t('deptOffice.fixedEmployeeStations')}</h2>
          <p>{t('deptOffice.stationDesc')}</p>
        </div>
        <div className="office-panel__summary">{agents.length}</div>
      </div>
      <div className="station-grid">
        {agents.map((agent) => <EmployeeStationCard key={agent.id} agent={agent} onAgentClick={onAgentClick} />)}
      </div>
    </section>
  );
}
