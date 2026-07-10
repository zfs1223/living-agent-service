import { useTranslation } from 'react-i18next';
import { IconMessage, IconBuilding, IconWorld, IconRobot } from '@tabler/icons-react';

export type ChannelType = 'dept' | 'enterprise' | 'public' | 'agent';

interface ChannelIndicatorProps {
  channelType: ChannelType;
  channelName: string;
  connected?: boolean;
}

const CHANNEL_CONFIG: Record<ChannelType, { icon: typeof IconMessage; colorVar: string }> = {
  dept: { icon: IconBuilding, colorVar: 'var(--accent-text)' },
  enterprise: { icon: IconWorld, colorVar: 'var(--warning)' },
  public: { icon: IconMessage, colorVar: 'var(--text-tertiary)' },
  agent: { icon: IconRobot, colorVar: 'var(--success)' },
};

export default function ChannelIndicator({ channelType, channelName, connected }: ChannelIndicatorProps) {
  const { t } = useTranslation();
  const config = CHANNEL_CONFIG[channelType];
  const Icon = config.icon;

  const label = channelType === 'public'
    ? t('channel.visitorMode', '访客模式')
    : channelName;

  return (
    <span style={{
      display: 'inline-flex',
      alignItems: 'center',
      gap: '4px',
      fontSize: '11px',
      color: config.colorVar,
      background: 'var(--bg-secondary)',
      padding: '2px 8px',
      borderRadius: '10px',
      border: '1px solid var(--border-subtle)',
    }}>
      <Icon size={12} stroke={1.5} />
      <span>{label}</span>
      {connected !== undefined && (
        <span className={`status-dot ${connected ? 'running' : 'stopped'}`} style={{ marginLeft: '2px' }} />
      )}
    </span>
  );
}
