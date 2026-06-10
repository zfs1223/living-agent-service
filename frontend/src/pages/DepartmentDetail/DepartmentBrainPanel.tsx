import { IconMessage, IconPlus } from '@tabler/icons-react';
import { useTranslation } from 'react-i18next';
import { DeptInfo, BrainLike } from './types';
import DepartmentEmpty from './DepartmentEmpty';

export default function DepartmentBrainPanel({ brain, deptInfo, canAccessDepartmentBrain, onBrainChat, onCreateAgent, isEnterprise, emptyDesc }: {
  brain: BrainLike | null;
  deptInfo: DeptInfo;
  canAccessDepartmentBrain: boolean;
  onBrainChat: () => void;
  onCreateAgent: () => void;
  isEnterprise: boolean;
  emptyDesc: string;
}) {
  const { t } = useTranslation();
  return (
    <section className="card office-panel office-panel--compact office-panel--brain">
      <div className="office-panel__header">
        <div>
          <h2>{t('deptOffice.departmentBrain')}</h2>
          <p>{t('deptOffice.brainDesc')}</p>
        </div>
      </div>
      {brain ? (
        <div className="brain-card office-brain-card">
          <div className="brain-avatar">{deptInfo.icon}</div>
          <div className="brain-info">
            <h3>{brain.name || `${deptInfo.name} Brain`}</h3>
            <p>{brain.description || t('deptOffice.intelligentAssistant')}</p>
            <div className="brain-status">
              <span className={`status-dot ${brain.status === 'running' ? 'active' : ''}`} />
              <span>{brain.status === 'running' ? t('deptOffice.running') : t('deptOffice.stopped')}</span>
            </div>
          </div>
          {(isEnterprise || canAccessDepartmentBrain) && (
            <button className="btn btn-primary" onClick={onBrainChat} type="button">
              <IconMessage size={16} />
              {t('deptOffice.openBrainChat')}
            </button>
          )}
        </div>
      ) : (
        <DepartmentEmpty title={t('deptOffice.noBrainConfigured')} desc={emptyDesc} />
      )}
    </section>
  );
}
