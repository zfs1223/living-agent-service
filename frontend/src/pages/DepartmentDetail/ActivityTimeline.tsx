import { useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { OfficeEvent } from './officeMotion';

export default function ActivityTimeline({ timeline }: { timeline: OfficeEvent[]; }) {
  const { t } = useTranslation();
  const listRef = useRef<HTMLDivElement | null>(null);
  const latestKeyRef = useRef<string>('');

  useEffect(() => {
    const latest = timeline[0];
    if (!latest) return;
    const latestKey = latest.id;
    if (latestKey === latestKeyRef.current) return;
    latestKeyRef.current = latestKey;
    const el = listRef.current;
    if (el) {
      el.scrollTo({ top: 0, behavior: 'smooth' });
    }
  }, [timeline]);

  return (
    <section className="card office-panel">
      <div className="office-panel__header">
        <div>
          <h2>{t('deptOffice.statusTimeline')}</h2>
          <p>{t('deptOffice.timelineDesc')}</p>
        </div>
      </div>
      <div ref={listRef} className="timeline timeline--live">
        {timeline.slice(0, 8).map((entry) => (
          <div key={entry.id} className={`timeline__item timeline__item--live timeline__item--${entry.category}`}>
            <span className="timeline__dot" />
            <div>
              <strong>{entry.agentName}</strong>
              <p>{entry.message}</p>
              <span className="timeline__meta">{entry.fromZone} → {entry.toZone}</span>
            </div>
          </div>
        ))}
        {timeline.length === 0 && (
          <div className="timeline__item">
            <span className="timeline__dot" />
            <div>
              <strong>{t('deptOffice.waitingForEvents')}</strong>
              <p>{t('deptOffice.waitingDesc')}</p>
            </div>
          </div>
        )}
      </div>
    </section>
  );
}
