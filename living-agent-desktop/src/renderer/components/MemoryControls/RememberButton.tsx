/**
 * P12: "记住这个" 按钮
 *
 * 在消息上提供一键记忆功能，持久化到 memory.db
 */
import { useState } from 'react';
import './MemoryControls.css';

interface RememberButtonProps {
  messageContent: string;
  messageId?: string;
  onRemember?: (content: string) => Promise<void>;
  onAlreadyRemembered?: () => void;
}

export default function RememberButton({
  messageContent,
  messageId,
  onRemember,
  onAlreadyRemembered
}: RememberButtonProps) {
  const [status, setStatus] = useState<'idle' | 'remembering' | 'success' | 'error'>('idle');
  const [showInput, setShowInput] = useState(false);
  const [note, setNote] = useState('');

  const handleRemember = async () => {
    if (!onRemember) return;

    setStatus('remembering');
    try {
      await onRemember(note ? `${messageContent}\n\n备注：${note}` : messageContent);
      setStatus('success');
      setShowInput(false);
      // 3s 后重置为 idle
      setTimeout(() => setStatus('idle'), 3000);
    } catch (err) {
      console.error('[RememberButton] 记忆失败:', err);
      setStatus('error');
      setTimeout(() => setStatus('idle'), 3000);
    }
  };

  if (status === 'success') {
    return (
      <span className="remember-button remember-button--success">
        ✅ 已记住
      </span>
    );
  }

  if (status === 'error') {
    return (
      <span className="remember-button remember-button--error">
        ❌ 记忆失败
      </span>
    );
  }

  return (
    <span className="remember-button-wrapper">
      {!showInput ? (
        <button
          className="remember-button"
          onClick={() => setShowInput(true)}
          title="将此消息内容保存到记忆库"
        >
          🧠 记住这个
        </button>
      ) : (
        <span className="remember-input-group">
          <input
            type="text"
            className="remember-input"
            placeholder="添加备注（可选）"
            value={note}
            onChange={(e) => setNote(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') handleRemember();
              if (e.key === 'Escape') setShowInput(false);
            }}
          />
          <button
            className="remember-button remember-button--confirm"
            onClick={handleRemember}
            disabled={status === 'remembering'}
          >
            {status === 'remembering' ? '保存中...' : '确认'}
          </button>
          <button
            className="remember-button remember-button--cancel"
            onClick={() => setShowInput(false)}
          >
            取消
          </button>
        </span>
      )}
    </span>
  );
}