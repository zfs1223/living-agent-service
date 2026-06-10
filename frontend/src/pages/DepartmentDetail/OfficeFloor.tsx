import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { AgentLike } from './types';
import { OfficeZoneId, getOfficeMotion } from './officeMotion';
import { getZoneByStatus } from './status';
import OfficeFloorGrid from './OfficeFloorGrid';
import OfficeCoreNode from './OfficeCoreNode';
import OfficeZonePanel from './OfficeZonePanel';
import useOfficePresence from './useOfficePresence';

export default function OfficeFloor({ agents, onAgentClick, presence }: { agents: AgentLike[]; onAgentClick: (id: string) => void; presence?: ReturnType<typeof useOfficePresence>; }) {
  const { t, i18n } = useTranslation();
  const isChinese = i18n.language?.startsWith('zh');
  const officePresence = presence ?? useOfficePresence(agents, isChinese);
  const zones = officePresence.zones;

  const zoneCards = [
    { id: 'workstation', title: t('deptOffice.workstationHall'), hint: t('deptOffice.workstationHint') },
    { id: 'collaboration', title: t('deptOffice.meetingHub'), hint: t('deptOffice.meetingHint') },
    { id: 'lounge', title: t('deptOffice.pantryLounge'), hint: t('deptOffice.pantryHint') },
    { id: 'alert', title: t('deptOffice.controlDesk'), hint: t('deptOffice.controlDeskHint') },
  ];

  const movingCount = useMemo(() => agents.filter((agent) => {
    const motion = getOfficeMotion(agent);
    return motion.holdMs <= 1800;
  }).length, [agents]);

  const roomCount = Object.values(zones).reduce((sum, list) => sum + list.length, 0);

  return (
    <section className="office-scene card office-scene--roomy">
      <div className="office-scene__header">
        <div>
          <h2>{t('deptOffice.officeRoom')}</h2>
          <p>{t('deptOffice.officeRoomDesc')}</p>
        </div>
        <div className="office-scene__legend">
          <span><i className="office-dot office-dot--work" />{t('deptOffice.work')}</span>
          <span><i className="office-dot office-dot--idle" />{t('deptOffice.standby')}</span>
          <span><i className="office-dot office-dot--alert" />{t('deptOffice.alert')}</span>
        </div>
      </div>

      <div className="office-floor office-floor--room">
        <div className="office-floor__ambient office-floor__ambient--1" />
        <div className="office-floor__ambient office-floor__ambient--2" />
        <OfficeFloorGrid />
        <div className="office-floor__road office-floor__road--1" />
        <div className="office-floor__road office-floor__road--2" />

        <OfficeCoreNode className="office-floor__core--meeting" label={t('deptOffice.meetingTable')} />
        <OfficeCoreNode className="office-floor__core--server" label={t('deptOffice.modelCore')} />
        <OfficeCoreNode className="office-floor__core--lounge" label={t('deptOffice.lounge')} />

        {zoneCards.map((zone) => (
          <OfficeZonePanel
            key={zone.id}
            zoneId={zone.id}
            title={zone.title}
            hint={zone.hint}
            agents={zones[zone.id as OfficeZoneId]}
            onAgentClick={onAgentClick}
            transitionById={officePresence.transitionById}
            motionById={officePresence.motionById}
          />
        ))}
      </div>

      <div className="office-floor__footer">
        {t('deptOffice.roomFooter', { moving: movingCount, total: roomCount })}
      </div>
    </section>
  );
}
