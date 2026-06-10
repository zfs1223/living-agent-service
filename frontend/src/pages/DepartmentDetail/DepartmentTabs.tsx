import { IconBrain, IconClipboardList } from '@tabler/icons-react';
import { useTranslation } from 'react-i18next';

export default function DepartmentTabs({ activeTab, setActiveTab, navigate, code, canSeeDeptTasks }: any) {
  const { t } = useTranslation();
  return (
    <div className="dept-tabs office-tabs">
      <button className={`dept-tab ${activeTab === 'overview' ? 'active' : ''}`} onClick={() => { setActiveTab('overview'); navigate(`/departments/${encodeURIComponent(code || '')}/overview`); }} type="button">
        <IconBrain size={16} />
        {t('deptOffice.officeOverview')}
      </button>
      {canSeeDeptTasks && (
        <button className={`dept-tab ${activeTab === 'tasks' ? 'active' : ''}`} onClick={() => { setActiveTab('tasks'); navigate(`/departments/${encodeURIComponent(code || '')}/tasks`); }} type="button">
          <IconClipboardList size={16} />
          {t('deptOffice.publicTasks')}
        </button>
      )}
    </div>
  );
}
