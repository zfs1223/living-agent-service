import { IconArrowLeft } from '@tabler/icons-react';
import { useTranslation } from 'react-i18next';

export default function DepartmentTopbar({ onBack }: { onBack: () => void; }) {
  const { t } = useTranslation();
  return (
    <div className="office-page__topbar">
      <button className="btn btn-ghost" onClick={onBack} type="button">
        <IconArrowLeft size={16} />
        {t('deptOffice.backToDepartments')}
      </button>
      <div className="office-page__topbar-right">
        <span className="office-page__status-pill"><span className="office-page__status-dot" />{t('deptOffice.officeLive')}</span>
      </div>
    </div>
  );
}
