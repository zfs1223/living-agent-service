/**
 * P13: 敏感信息显示组件
 *
 * 显示脱敏后的文本，支持悬停查看完整信息（需权限）
 */
import { useState } from 'react';
import { highlightSensitives, canViewSensitive, type SensitiveType } from '../../utils/sensitive-mask';
import './SensitiveText.css';

interface SensitiveTextProps {
  text: string;
  accessLevel?: string;
  showOriginal?: boolean; // 强制显示原文（用于管理员）
}

const TYPE_LABELS: Record<SensitiveType, string> = {
  phone: '手机号',
  idcard: '身份证',
  bankcard: '银行卡',
  email: '邮箱',
  amount: '金额',
  name: '姓名'
};

const TYPE_COLORS: Record<SensitiveType, string> = {
  phone: '#1890ff',
  idcard: '#722ed1',
  bankcard: '#fa8c16',
  email: '#52c41a',
  amount: '#eb2f96',
  name: '#13c2c2'
};

export default function SensitiveText({ text, accessLevel, showOriginal = false }: SensitiveTextProps) {
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null);
  const segments = highlightSensitives(text);
  const canView = canViewSensitive(accessLevel) || showOriginal;

  // 无敏感信息，直接返回原文
  if (segments.length === 1 && !segments[0].isSensitive) {
    return <span>{text}</span>;
  }

  return (
    <span className="sensitive-text">
      {segments.map((seg, index) => {
        if (!seg.isSensitive) {
          return <span key={index}>{seg.text}</span>;
        }

        const typeLabel = TYPE_LABELS[seg.type!] || '敏感';
        const typeColor = TYPE_COLORS[seg.type!] || '#999';

        return (
          <span
            key={index}
            className="sensitive-text__mask"
            style={{ color: typeColor, borderBottomColor: typeColor }}
            onMouseEnter={() => setHoveredIndex(index)}
            onMouseLeave={() => setHoveredIndex(null)}
            title={canView ? `${typeLabel}: ${seg.original}` : `${typeLabel}（需权限查看）`}
          >
            {seg.text}
            {hoveredIndex === index && canView && seg.original && (
              <span className="sensitive-text__tooltip">
                <span className="sensitive-text__tooltip-label">{typeLabel}:</span>
                <span className="sensitive-text__tooltip-value">{seg.original}</span>
              </span>
            )}
          </span>
        );
      })}
    </span>
  );
}