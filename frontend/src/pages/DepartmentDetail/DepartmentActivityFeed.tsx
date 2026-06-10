import { useTranslation } from 'react-i18next';

export default function DepartmentActivityFeed({ timelineCount = 0 }: { timelineCount?: number; }) {
  const { t } = useTranslation();
  return (
    <section className="card office-panel office-panel--compact office-panel--activity">
      <div className="office-panel__header">
        <div>
          <h2>{t('deptOffice.recentActivity')}</h2>
          <p>{t('deptOffice.activityDesc')}</p>
        </div>
        <div className="office-panel__summary">{timelineCount}</div>
      </div>
      <div className="timeline timeline--compact">
        <div className="timeline__item">
          <span className="timeline__dot" />
          <div>
            <strong>{t('deptOffice.backendConnected')}</strong>
            <p>{t('deptOffice.backendConnectedDesc')}</p>
          </div>
        </div>
      </div>
    </section>
  );
}
