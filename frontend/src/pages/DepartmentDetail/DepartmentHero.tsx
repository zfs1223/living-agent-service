import { useTranslation } from 'react-i18next';
import { DeptInfo, DepartmentLike } from './types';

export default function DepartmentHero({ deptInfo, department, membersCount, agentsCount, canSeeDeptTasks, canAccessDepartmentBrain }: {
  deptInfo: DeptInfo;
  department: DepartmentLike;
  membersCount: number;
  agentsCount: number;
  canSeeDeptTasks: boolean;
  canAccessDepartmentBrain: boolean;
}) {
  const { t, i18n } = useTranslation();
  const isChinese = i18n.language?.startsWith('zh');
  return (
    <section className="office-hero card office-hero--compact">
      <div className="office-hero__ambient" />
      <div className="office-hero__content">
        <div className="office-hero__eyebrow">
          <span className="office-hero__dot" />
          {t('deptOffice.digitalOffice')}
        </div>
        <div className="office-hero__main">
          <div className="office-hero__icon">{deptInfo.icon}</div>
          <div className="office-hero__text">
            <h1 className="office-hero__title">{isChinese ? deptInfo.name : deptInfo.name_en}</h1>
            <p className="office-hero__desc">{department?.description || t('deptOffice.noDescription')}</p>
            <div className="office-hero__tags">
              <span className="badge badge-info">{t('deptOffice.departmentHome')}</span>
              <span className="badge badge-success">{t('deptOffice.visualStations')}</span>
              <span className="badge badge-warning">{canAccessDepartmentBrain ? t('deptOffice.brainEnabled') : t('deptOffice.brainRestricted')}</span>
            </div>
          </div>
        </div>
      </div>
      <div className="office-hero__metrics office-hero__metrics--compact">
        <div className="office-metric"><span className="office-metric__label">{t('deptOffice.members')}</span><strong>{membersCount}</strong></div>
        <div className="office-metric"><span className="office-metric__label">{t('deptOffice.agents')}</span><strong>{agentsCount}</strong></div>
        <div className="office-metric"><span className="office-metric__label">{t('deptOffice.taskBoard')}</span><strong>{canSeeDeptTasks ? t('deptOffice.open') : t('deptOffice.limited')}</strong></div>
      </div>
    </section>
  );
}
