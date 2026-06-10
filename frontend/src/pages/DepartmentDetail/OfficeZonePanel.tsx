import type { CSSProperties } from 'react';
import { useTranslation } from 'react-i18next';
import { AgentLike } from './types';
import PixelAgent from './PixelAgent';
import { getOfficeMotion } from './officeMotion';

export default function OfficeZonePanel({ zoneId, title, hint, agents, onAgentClick, transitionById, motionById }: {
  zoneId: string;
  title: string;
  hint: string;
  agents: AgentLike[];
  onAgentClick: (id: string) => void;
  transitionById?: Record<string, { zone: string; prevZone: string; at: number; holdMs: number; phase?: string; motionKind?: string; direction?: string; progress?: number }>;
  motionById?: Record<string, ReturnType<typeof getOfficeMotion>>;
}) {
  const { t } = useTranslation();
  const workstationSeats = [
    { id: 1, row: 0, col: 0, dir: 'south' },
    { id: 2, row: 0, col: 1, dir: 'south' },
    { id: 3, row: 0, col: 2, dir: 'south' },
    { id: 4, row: 0, col: 3, dir: 'south' },
    { id: 5, row: 0, col: 4, dir: 'south' },
    { id: 6, row: 0, col: 5, dir: 'south' },
    { id: 7, row: 1, col: 0, dir: 'north' },
    { id: 8, row: 1, col: 1, dir: 'north' },
    { id: 9, row: 1, col: 2, dir: 'north' },
    { id: 10, row: 1, col: 3, dir: 'north' },
    { id: 11, row: 1, col: 4, dir: 'north' },
    { id: 12, row: 1, col: 5, dir: 'north' },
  ] as const;

  const layoutClassByIndex = (index: number) => {
    if (zoneId === 'workstation') return `seat-${(index % workstationSeats.length) + 1}`;
    if (zoneId === 'collaboration') return ['table-a', 'table-b', 'aisle'][index % 3];
    if (zoneId === 'lounge') return ['sofa-a', 'sofa-b', 'aisle'][index % 3];
    if (zoneId === 'alert') return ['desk-a', 'desk-b', 'desk-a'][index % 3];
    return ['corner', 'back', 'aisle'][index % 3];
  };

  const ringClassByIndex = (index: number) => (zoneId === 'workstation' && index % 2 === 0 ? 'ring-a' : 'ring-b');
  const workstationSeatCount = workstationSeats.length;

  return (
    <section className={`office-zone office-zone--${zoneId}`}>
      <div className="office-zone__header">
        <div>
          <h3>{title}</h3>
          <p>{hint}</p>
        </div>
        <span className="office-zone__count">{agents.length}</span>
      </div>
      <div className={`office-zone__avatars office-zone__avatars--pixel office-zone__avatars--${zoneId}`}>
        <div className="office-zone__fixtures" aria-hidden="true">
          {zoneId === 'workstation' && (
            <>
              <span className="office-fixture office-fixture--desk office-fixture--row-1" />
              <span className="office-fixture office-fixture--desk office-fixture--row-2" />
              <span className="office-fixture office-fixture--aisle office-fixture--aisle-1" />
              <span className="office-fixture office-fixture--aisle office-fixture--aisle-1" />
            </>
          )}
          {zoneId === 'collaboration' && (
            <>
              <span className="office-fixture office-fixture--table office-fixture--meeting" />
              <span className="office-fixture office-fixture--chairs office-fixture--meeting-chairs" />
            </>
          )}
          {zoneId === 'lounge' && (
            <>
              <span className="office-fixture office-fixture--sofa office-fixture--sofa-left" />
              <span className="office-fixture office-fixture--sofa office-fixture--sofa-right" />
              <span className="office-fixture office-fixture--pantry" />
            </>
          )}
          {zoneId === 'alert' && (
            <>
              <span className="office-fixture office-fixture--desk office-fixture--control" />
              <span className="office-fixture office-fixture--screen office-fixture--control-screen" />
            </>
          )}
        </div>
        {agents.map((agent, index) => {
          const transition = transitionById?.[agent.id];
          const motion = motionById?.[agent.id];
          const seatIndex = zoneId === 'workstation' ? index % workstationSeatCount : index % 4;
          const seat = zoneId === 'workstation' ? `seat-${seatIndex + 1}` : motion?.seat || layoutClassByIndex(index);
          const seatPosition = zoneId === 'workstation' ? workstationSeats[seatIndex] : null;
          const seatLabel = zoneId === 'workstation' ? `#${seatIndex + 1}` : '';
          const employeeLabel = zoneId === 'workstation' ? agent.name : agent.name;
          return (
            <div
              key={agent.id}
              className={`office-zone__slot office-zone__slot--${seat} office-zone__slot--${ringClassByIndex(index)}`}
              style={{
                animationDelay: `${index * 90}ms`,
                '--slot-x': zoneId === 'workstation' && seatPosition ? `${seatPosition.col * 144}px` : `${(index % 4) * 8}px`,
                '--slot-y': zoneId === 'workstation' && seatPosition ? `${seatPosition.row * 205}px` : `${Math.floor(index / 4) * 10}px`,
              } as CSSProperties}
            >
              <div className="office-seat-card">
                <div className="office-seat-card__badge">{seatLabel || ' '}</div>
                <div className={`office-seat-card__desk office-seat-card__desk--${seatPosition?.row === 0 ? 'front' : 'back'}`}>
                  <span className="office-seat-card__keyboard" />
                  <span className="office-seat-card__chair" />
                </div>
                <PixelAgent
                  agent={agent}
                  onAgentClick={onAgentClick}
                  lane={index % 4}
                  transition={transition as any}
                  motion={motion}
                />
                <div className="office-seat-card__name">{employeeLabel}</div>
              </div>
            </div>
          );
        })}
        {agents.length === 0 && <span className="office-zone__empty">{t('deptOffice.noEmployees')}</span>}
      </div>
    </section>
  );
}
