import React from 'react';
import { copyToClipboard } from '../../utils/clipboard';

interface CopyMessageButtonProps {
    text: string;
}

/** Tiny copy button shown on hover at the bottom of message bubbles */
function CopyMessageButton({ text }: CopyMessageButtonProps) {
    const [copied, setCopied] = React.useState(false);
    const handleCopy = (e: React.MouseEvent) => {
        e.stopPropagation();
        const copySuccess = () => {
            setCopied(true);
            setTimeout(() => setCopied(false), 1500);
        };

        if (navigator.clipboard && window.isSecureContext) {
            copyToClipboard(text).then(copySuccess).catch(err => console.error('Clipboard API failed', err));
        } else {
            // Fallback for non-HTTPS dev environments
            const textArea = document.createElement("textarea");
            textArea.value = text;
            textArea.style.position = "fixed";  // Avoid scrolling to bottom
            document.body.appendChild(textArea);
            textArea.focus();
            textArea.select();
            try {
                if (document.execCommand('copy')) {
                    copySuccess();
                }
            } catch (err) {
                console.error('Fallback copy failed', err);
            }
            document.body.removeChild(textArea);
        }
    };
    return (
        <button
            onClick={handleCopy}
            title="复制"
            style={{
                background: 'none', border: 'none', cursor: 'pointer', padding: '2px',
                color: copied ? 'var(--accent-text)' : 'var(--text-tertiary)',
                opacity: copied ? 1 : 0.5, transition: 'opacity .15s, color .15s',
                display: 'inline-flex', alignItems: 'center', verticalAlign: 'middle',
                marginLeft: '6px', flexShrink: 0,
            }}
            onMouseEnter={e => (e.currentTarget.style.opacity = '1')}
            onMouseLeave={e => (e.currentTarget.style.opacity = copied ? '1' : '0.5')}
        >
            {copied ? (
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><polyline points="20 6 9 17 4 12" /></svg>
            ) : (
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2" /><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" /></svg>
            )}
        </button>
    );
}

export default CopyMessageButton;
