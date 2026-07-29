/**
 * IM 会话列表项组件
 *
 * 显示：联系人名称 + 置顶/免打扰图标 + 最后消息摘要 + 时间 + 未读角标
 */
import './IMPage.css';

export interface Contact {
  id: string;
  name: string;
  avatar: string | null;
  lastMessageContent: string;
  lastMessageTime: string;
  unreadCount: number;
  pinned: boolean;
  muted: boolean;
  hidden: boolean;
  contactType: string;
}

interface ContactItemProps {
  contact: Contact;
  selected: boolean;
  onClick: () => void;
}

/** 格式化时间为 HH:mm 或 MM/DD */
function formatTime(timeStr: string): string {
  if (!timeStr) return '';
  const date = new Date(timeStr);
  const now = new Date();
  const isToday = date.toDateString() === now.toDateString();
  if (isToday) {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
  }
  return `${date.getMonth() + 1}/${date.getDate()}`;
}

export function ContactItem({ contact, selected, onClick }: ContactItemProps) {
  return (
    <div
      className={`contact-item${selected ? ' contact-item--selected' : ''}`}
      onClick={onClick}
    >
      <div className="contact-item__avatar">
        {contact.avatar ? (
          <img src={contact.avatar} alt={contact.name} className="contact-item__avatar-img" />
        ) : (
          <span className="contact-item__avatar-placeholder">
            {contact.name.charAt(0).toUpperCase()}
          </span>
        )}
      </div>
      <div className="contact-item__body">
        <div className="contact-item__header">
          <span className="contact-item__name">
            {contact.name}
            {contact.pinned && <span className="contact-item__icon contact-item__icon--pinned" title="置顶">📌</span>}
            {contact.muted && <span className="contact-item__icon contact-item__icon--muted" title="免打扰">🔇</span>}
          </span>
          <span className="contact-item__time">{formatTime(contact.lastMessageTime)}</span>
        </div>
        <div className="contact-item__footer">
          <span className="contact-item__last-msg">{contact.lastMessageContent}</span>
          {contact.unreadCount > 0 && (
            <span className="contact-item__badge">
              {contact.unreadCount > 99 ? '99+' : contact.unreadCount}
            </span>
          )}
        </div>
      </div>
    </div>
  );
}
