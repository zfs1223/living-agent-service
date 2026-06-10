import React, { useState, useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import { useAuthStore, getToken } from '../../stores';
import { useToastStore } from '../../stores/toastStore';
import { useAppStore } from '../../stores';
import { agentApi, uploadFileWithProgress } from '../../services/api';
import MarkdownRenderer from '../../components/MarkdownRenderer';
import AgentBayLivePanel, { LivePreviewState } from '../../components/AgentBayLivePanel';
import { copyToClipboard } from '../../utils/clipboard';

/** Tiny copy button shown on hover at the bottom of message bubbles */
function CopyMessageButton({ text }: { text: string }) {
    const [copied, setCopied] = React.useState(false);
    const handleCopy = (e: React.MouseEvent) => {
        e.stopPropagation();
        const copySuccess = () => { setCopied(true); setTimeout(() => setCopied(false), 1500); };
        if (navigator.clipboard && window.isSecureContext) {
            copyToClipboard(text).then(copySuccess).catch(err => console.error('Clipboard API failed', err));
        } else {
            const textArea = document.createElement("textarea");
            textArea.value = text;
            textArea.style.position = "fixed";
            document.body.appendChild(textArea);
            textArea.focus();
            textArea.select();
            try { if (document.execCommand('copy')) copySuccess(); } catch (err) { console.error('Fallback copy failed', err); }
            document.body.removeChild(textArea);
        }
    };
    return (
        <button onClick={handleCopy} title="复制" style={{ background: 'none', border: 'none', cursor: 'pointer', padding: '2px', color: copied ? 'var(--accent-text)' : 'var(--text-tertiary)', opacity: copied ? 1 : 0.5, transition: 'opacity .15s, color .15s', display: 'inline-flex', alignItems: 'center', verticalAlign: 'middle', marginLeft: '6px', flexShrink: 0 }}
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

interface ChatMsg { role: 'user' | 'assistant' | 'tool_call'; content: string; fileName?: string; toolName?: string; toolArgs?: any; toolStatus?: 'running' | 'done'; toolResult?: string; thinking?: string; imageUrl?: string; timestamp?: string; }

interface AgentChatProps {
    agentId: string;
    agent: any;
    supportsVision: boolean;
    llmModels: any[];
}

export default function AgentChat({ agentId, agent, supportsVision, llmModels }: AgentChatProps) {
    const { t, i18n } = useTranslation();
    const queryClient = useQueryClient();
    const token = useAuthStore((s) => s.token);
    const currentUser = useAuthStore((s) => s.user);
    const isAdmin = currentUser?.role === 'platform_admin' || currentUser?.role === 'org_admin';

    // Session state
    const [sessions, setSessions] = useState<any[]>([]);
    const [allSessions, setAllSessions] = useState<any[]>([]);
    const [activeSession, setActiveSession] = useState<any | null>(null);
    const [chatScope, setChatScope] = useState<'mine' | 'all'>('mine');
    const [allUserFilter, setAllUserFilter] = useState<string>('');
    const [historyMsgs, setHistoryMsgs] = useState<any[]>([]);
    const [sessionsLoading, setSessionsLoading] = useState(false);
    const [allSessionsLoading, setAllSessionsLoading] = useState(false);
    const [agentExpired, setAgentExpired] = useState(false);

    // WebSocket state
    type SessionRuntimeKey = string;
    const wsMapRef = useRef<Record<SessionRuntimeKey, WebSocket>>({});
    const reconnectTimerRef = useRef<Record<SessionRuntimeKey, ReturnType<typeof setTimeout> | null>>({});
    const reconnectDisabledRef = useRef<Record<SessionRuntimeKey, boolean>>({});
    const sessionUiStateRef = useRef<Record<SessionRuntimeKey, { isWaiting: boolean; isStreaming: boolean }>>({});
    const activeSessionIdRef = useRef<string | null>(null);
    const currentAgentIdRef = useRef<string | undefined>(agentId);
    const sessionMsgAbortRef = useRef<AbortController | null>(null);
    const sessionLoadSeqRef = useRef(0);

    const buildSessionRuntimeKey = (agentId: string, sessionId: string) => `${agentId}:${sessionId}`;

    const clearReconnectTimer = (key: SessionRuntimeKey) => {
        const timer = reconnectTimerRef.current[key];
        if (timer) { clearTimeout(timer); reconnectTimerRef.current[key] = null; }
    };

    const closeSessionSocket = (key: SessionRuntimeKey, disableReconnect = true) => {
        if (disableReconnect) reconnectDisabledRef.current[key] = true;
        clearReconnectTimer(key);
        const ws = wsMapRef.current[key];
        if (ws && ws.readyState !== WebSocket.CLOSED) ws.close();
        delete wsMapRef.current[key];
        delete sessionUiStateRef.current[key];
    };

    const setSessionUiState = (key: SessionRuntimeKey, next: Partial<{ isWaiting: boolean; isStreaming: boolean }>) => {
        const prev = sessionUiStateRef.current[key] || { isWaiting: false, isStreaming: false };
        sessionUiStateRef.current[key] = { ...prev, ...next };
    };

    const isWritableSession = (sess: any) => {
        if (!sess) return false;
        const isAgentSession = sess.source_channel === 'agent' || sess.participant_type === 'agent';
        if (isAgentSession) return false;
        if (sess.user_id && currentUser && sess.user_id !== String(currentUser.id)) return false;
        return true;
    };

    const syncActiveSocketState = (sess: any | null = activeSession, aId: string | undefined = agentId) => {
        if (!sess || !aId) { wsRef.current = null; setWsConnected(false); return; }
        const key = buildSessionRuntimeKey(aId, sess.id);
        const ws = wsMapRef.current[key];
        wsRef.current = ws ?? null;
        setWsConnected(!!ws && ws.readyState === WebSocket.OPEN);
    };

    const fetchMySessions = async (silent = false, aId: string | undefined = agentId) => {
        if (!aId) return [];
        if (!silent && currentAgentIdRef.current === aId) setSessionsLoading(true);
        try {
            const tkn = getToken();
            const res = await fetch(`/api/agents/${encodeURIComponent(aId!)}/sessions?scope=mine`, { headers: { Authorization: `Bearer ${tkn}` } });
            if (res.ok) {
                const data = await res.json();
                if (currentAgentIdRef.current === aId) setSessions(data);
                if (!silent && currentAgentIdRef.current === aId) setSessionsLoading(false);
                return data;
            }
        } catch { }
        if (!silent && currentAgentIdRef.current === aId) setSessionsLoading(false);
        return [];
    };

    const fetchAllSessions = async () => {
        if (!agentId) return;
        setAllSessionsLoading(true);
        try {
            const tkn = getToken();
            const res = await fetch(`/api/agents/${encodeURIComponent(agentId!)}/sessions?scope=all`, { headers: { Authorization: `Bearer ${tkn}` } });
            if (res.ok) {
                const all = await res.json();
                if (currentAgentIdRef.current === agentId) { setAllSessions(all.filter((s: any) => s.source_channel !== 'trigger')); }
            }
        } catch { }
        setAllSessionsLoading(false);
    };

    const IMAGE_EXTS = ['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp'];
    const parseChatMsg = (msg: ChatMsg): ChatMsg => {
        if (msg.role !== 'user') return msg;
        let parsed = { ...msg };
        const newFmt = msg.content.match(/^\[file:([^\]]+)\]\n?/);
        if (newFmt) { parsed = { ...msg, fileName: newFmt[1], content: msg.content.slice(newFmt[0].length).trim() }; }
        const chanFmt = !newFmt && msg.content.match(/^\[\u6587\u4ef6\u5df2\u4e0a\u4f20: (?:workspace\/uploads\/)?([^\]\n]+)\]/);
        if (chanFmt) { const raw = chanFmt[1]; const fileName = raw.split('/').pop() || raw; parsed = { ...msg, fileName, content: msg.content.slice(chanFmt[0].length).trim() }; }
        const oldFmt = !newFmt && !chanFmt && msg.content.match(/^\[File: ([^\]]+)\]/);
        if (oldFmt) { const fileName = oldFmt[1]; const qMatch = msg.content.match(/\nQuestion: ([\s\S]+)$/); parsed = { ...msg, fileName, content: qMatch ? qMatch[1].trim() : '' }; }
        if (parsed.fileName && !parsed.imageUrl && agentId) {
            const ext = parsed.fileName.split('.').pop()?.toLowerCase() || '';
            if (IMAGE_EXTS.includes(ext)) { parsed.imageUrl = `/api/agents/${encodeURIComponent(agentId!)}/files/download?path=workspace/uploads/${encodeURIComponent(parsed.fileName)}&token=${token}`; }
        }
        return parsed;
    };

    const selectSession = async (sess: any) => {
        const targetAgentId = agentId;
        if (!targetAgentId) return;
        const runtimeKey = buildSessionRuntimeKey(targetAgentId, String(sess.id));
        const runtimeState = sessionUiStateRef.current[runtimeKey] || { isWaiting: false, isStreaming: false };
        activeSessionIdRef.current = sess.id;
        setChatMessages([]);
        setHistoryMsgs([]);
        setIsStreaming(runtimeState.isStreaming);
        setIsWaiting(runtimeState.isWaiting);
        setActiveSession(sess);
        setAgentExpired(false);
        syncActiveSocketState(sess, targetAgentId);
        sessionMsgAbortRef.current?.abort();
        const controller = new AbortController();
        sessionMsgAbortRef.current = controller;
        const loadSeq = ++sessionLoadSeqRef.current;
        try {
            const tkn = getToken();
            const res = await fetch(`/api/agents/${encodeURIComponent(targetAgentId!)}/sessions/${sess.id}/messages`, { headers: { Authorization: `Bearer ${tkn}` }, signal: controller.signal });
            if (!res.ok) return;
            const msgs = await res.json();
            if (controller.signal.aborted || loadSeq !== sessionLoadSeqRef.current) return;
            if (currentAgentIdRef.current !== targetAgentId) return;
            if (activeSessionIdRef.current !== sess.id) return;
            const isAgentSession = sess.source_channel === 'agent' || sess.participant_type === 'agent';
            const preParsed = msgs.map((m: any) => parseChatMsg({ role: m.role, content: m.content || '', ...(m.toolName && { toolName: m.toolName, toolArgs: m.toolArgs, toolStatus: m.toolStatus, toolResult: m.toolResult }), ...(m.thinking && { thinking: m.thinking }), ...(m.created_at && { timestamp: m.created_at }), ...(m.id && { id: m.id }) }));
            if (!isAgentSession && sess.user_id === String(currentUser?.id)) { setChatMessages(preParsed); } else { setHistoryMsgs(preParsed); }
        } catch (err: any) { if (err?.name === 'AbortError') return; console.error('Failed to load session messages:', err); }
    };

    const createNewSession = async () => {
        if (!agentId) return;
        try {
            const tkn = getToken();
            const res = await fetch(`/api/agents/${encodeURIComponent(agentId!)}/sessions`, { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${tkn}` }, body: JSON.stringify({}) });
            if (res.ok) { const newSess = await res.json(); setSessions(prev => [newSess, ...prev]); setIsStreaming(false); setIsWaiting(false); await selectSession(newSess); }
            else { const err = await res.json().catch(() => ({ detail: `HTTP ${res.status}` })); useToastStore.getState().showToast(t('chat.createSessionFailed', '创建会话失败') + `: ${err.detail || res.status}`, 'error'); }
        } catch (err: any) { useToastStore.getState().showToast(t('chat.createSessionFailed', '创建会话失败') + `: ${err.message || err}`, 'error'); }
    };

    const deleteSession = async (sessionId: string) => {
        if (!confirm(t('chat.deleteConfirm', 'Delete this session?'))) return;
        const tkn = getToken();
        try {
            await fetch(`/api/agents/${encodeURIComponent(agentId!)}/sessions/${sessionId}`, { method: 'DELETE', headers: { Authorization: `Bearer ${tkn}` } });
            if (agentId) closeSessionSocket(buildSessionRuntimeKey(agentId, sessionId), true);
            if (activeSession?.id === sessionId) { activeSessionIdRef.current = null; setActiveSession(null); setChatMessages([]); setHistoryMsgs([]); setWsConnected(false); setIsStreaming(false); setIsWaiting(false); }
            await fetchMySessions(false, agentId);
            await fetchAllSessions();
        } catch (e: any) { useToastStore.getState().showToast(e.message || t('chat.deleteFailed', '删除失败'), 'error'); }
    };

    const [chatMessages, setChatMessages] = useState<ChatMsg[]>([]);
    const [liveState, setLiveState] = useState<LivePreviewState>({});
    const [livePanelVisible, setLivePanelVisible] = useState(false);
    const [sessionListCollapsed, setSessionListCollapsed] = useState(false);
    const [chatInput, setChatInput] = useState('');
    const [wsConnected, setWsConnected] = useState(false);
    const [uploading, setUploading] = useState(false);
    const [isWaiting, setIsWaiting] = useState(false);
    const [isStreaming, setIsStreaming] = useState(false);
    const [uploadProgress, setUploadProgress] = useState(-1);
    const uploadAbortRef = useRef<(() => void) | null>(null);
    const [attachedFiles, setAttachedFiles] = useState<{ name: string; text: string; path?: string; imageUrl?: string }[]>([]);
    const wsRef = useRef<WebSocket | null>(null);
    const chatEndRef = useRef<HTMLDivElement>(null);
    const chatContainerRef = useRef<HTMLDivElement>(null);
    const chatInputRef = useRef<HTMLTextAreaElement>(null);
    const fileInputRef = useRef<HTMLInputElement>(null);

    const ensureSessionSocket = (sess: any, aId: string, authToken: string) => {
        const sessionId = String(sess.id);
        const key = buildSessionRuntimeKey(aId, sessionId);
        const existing = wsMapRef.current[key];
        if (existing && (existing.readyState === WebSocket.OPEN || existing.readyState === WebSocket.CONNECTING)) return;
        reconnectDisabledRef.current[key] = false;
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const sessionParam = `&session_id=${sessionId}`;
        const scheduleReconnect = () => {
            if (reconnectDisabledRef.current[key]) return;
            clearReconnectTimer(key);
            reconnectTimerRef.current[key] = setTimeout(() => { reconnectTimerRef.current[key] = null; if (!reconnectDisabledRef.current[key]) ensureSessionSocket(sess, aId, authToken); }, 2000);
        };
        const ws = new WebSocket(`${protocol}//${window.location.host}/ws/agent?token=${authToken}${sessionParam}&agentId=${encodeURIComponent(aId)}`);
        wsMapRef.current[key] = ws;
        ws.onopen = () => {
            if (reconnectDisabledRef.current[key]) { ws.close(); return; }
            if (currentAgentIdRef.current === aId && activeSessionIdRef.current === sessionId) { wsRef.current = ws; setWsConnected(true); }
        };
        ws.onclose = (e) => {
            if (wsMapRef.current[key] === ws) delete wsMapRef.current[key];
            setSessionUiState(key, { isWaiting: false, isStreaming: false });
            const isActiveRuntime = currentAgentIdRef.current === aId && activeSessionIdRef.current === sessionId;
            if (isActiveRuntime) { wsRef.current = null; setWsConnected(false); setIsWaiting(false); setIsStreaming(false); }
            if (e.code === 4003 || e.code === 4002) { reconnectDisabledRef.current[key] = true; clearReconnectTimer(key); if (isActiveRuntime && e.code === 4003) setAgentExpired(true); return; }
            scheduleReconnect();
        };
        ws.onerror = () => { const isActiveRuntime = currentAgentIdRef.current === aId && activeSessionIdRef.current === sessionId; if (isActiveRuntime) setWsConnected(false); };
        ws.onmessage = (e) => {
            const d = JSON.parse(e.data);
            const isActiveRuntime = currentAgentIdRef.current === aId && activeSessionIdRef.current === sessionId;
            if (['thinking', 'chunk', 'tool_call', 'done', 'error', 'quota_exceeded'].includes(d.type)) {
                const nextStreaming = ['thinking', 'chunk', 'tool_call'].includes(d.type);
                const endStreaming = ['done', 'error', 'quota_exceeded'].includes(d.type);
                setSessionUiState(key, { isWaiting: false, isStreaming: endStreaming ? false : nextStreaming });
            }
            if (!isActiveRuntime) {
                if (['done', 'error', 'quota_exceeded', 'trigger_notification'].includes(d.type)) fetchMySessions(true, aId);
                if (['done', 'error', 'quota_exceeded'].includes(d.type)) closeSessionSocket(key, true);
                return;
            }
            if (['thinking', 'chunk', 'tool_call', 'done', 'error', 'quota_exceeded'].includes(d.type)) {
                setIsWaiting(false);
                if (['thinking', 'chunk', 'tool_call'].includes(d.type)) setIsStreaming(true);
                if (['done', 'error', 'quota_exceeded'].includes(d.type)) setIsStreaming(false);
            }
            if (d.type === 'thinking') {
                setChatMessages(prev => { const last = prev[prev.length - 1]; if (last && last.role === 'assistant' && (last as any)._streaming) { return [...prev.slice(0, -1), { ...last, thinking: (last.thinking || '') + d.content } as any]; } return [...prev, { role: 'assistant', content: '', thinking: d.content, _streaming: true } as any]; });
            } else if (d.type === 'tool_call') {
                if (d.live_preview) {
                    const lp = d.live_preview;
                    setLiveState(prev => { const next = { ...prev }; if ((lp.env === 'desktop' || lp.env === 'browser') && lp.screenshot_url) { if (lp.env === 'desktop') next.desktop = { screenshotUrl: lp.screenshot_url }; else next.browser = { screenshotUrl: lp.screenshot_url }; } else if (lp.env === 'code' && lp.output) { const existing = prev.code?.output || ''; next.code = { output: existing + (existing ? '\n---\n' : '') + lp.output }; } return next; });
                    setLivePanelVisible(true); setSessionListCollapsed(true); useAppStore.setState({ sidebarCollapsed: true });
                }
                setChatMessages(prev => { const toolMsg: ChatMsg = { role: 'tool_call', content: '', toolName: d.name, toolArgs: d.args, toolStatus: d.status, toolResult: d.result }; if (d.status === 'done') { const lastIdx = prev.length - 1; const last = prev[lastIdx]; if (last && last.role === 'tool_call' && last.toolName === d.name && last.toolStatus === 'running') return [...prev.slice(0, lastIdx), toolMsg]; } return [...prev, toolMsg]; });
            } else if (d.type === 'chunk') {
                setChatMessages(prev => { const last = prev[prev.length - 1]; if (last && last.role === 'assistant' && (last as any)._streaming) return [...prev.slice(0, -1), { ...last, content: last.content + d.content } as any]; return [...prev, { role: 'assistant', content: d.content, _streaming: true } as any]; });
            } else if (d.type === 'done') {
                setChatMessages(prev => { const last = prev[prev.length - 1]; const thinking = (last && last.role === 'assistant' && (last as any)._streaming) ? last.thinking : undefined; if (last && last.role === 'assistant' && (last as any)._streaming) return [...prev.slice(0, -1), parseChatMsg({ role: 'assistant', content: d.content, thinking, timestamp: new Date().toISOString() })]; return [...prev, parseChatMsg({ role: d.role, content: d.content, timestamp: new Date().toISOString() })]; });
                fetchMySessions(true, aId);
            } else if (d.type === 'error' || d.type === 'quota_exceeded') {
                const msg = d.content || d.detail || d.message || 'Request denied';
                setChatMessages(prev => { const last = prev[prev.length - 1]; if (last && last.role === 'assistant' && last.content === `⚠️ ${msg}`) return prev; return [...prev, parseChatMsg({ role: 'assistant', content: `⚠️ ${msg}` })]; });
                if (msg.includes('expired') || msg.includes('Setup failed') || msg.includes('no LLM model') || msg.includes('No model')) { reconnectDisabledRef.current[key] = true; if (msg.includes('expired')) setAgentExpired(true); }
            } else if (d.type === 'trigger_notification') {
                setChatMessages(prev => [...prev, parseChatMsg({ role: 'assistant', content: d.content })]); fetchMySessions(true, aId);
            } else {
                setChatMessages(prev => [...prev, parseChatMsg({ role: d.role, content: d.content })]);
            }
        };
    };

    useEffect(() => { currentAgentIdRef.current = agentId; }, [agentId]);

    useEffect(() => {
        sessionMsgAbortRef.current?.abort(); activeSessionIdRef.current = null; setActiveSession(null); setChatMessages([]); setHistoryMsgs([]); setIsStreaming(false); setIsWaiting(false); setWsConnected(false); wsRef.current = null; setChatScope('mine'); setAgentExpired(false);
    }, [agentId]);

    useEffect(() => {
        if (!agentId || !token) return;
        fetchMySessions(false, agentId).then((data: any) => { if (currentAgentIdRef.current !== agentId) return; setSessionsLoading(false); if (data && data.length > 0) selectSession(data[0]); });
    }, [agentId, token]);

    useEffect(() => {
        if (!agentId || !token || !activeSession) { syncActiveSocketState(null, agentId); return; }
        activeSessionIdRef.current = String(activeSession.id);
        if (!isWritableSession(activeSession)) { syncActiveSocketState(activeSession, agentId); return; }
        ensureSessionSocket(activeSession, agentId, token);
        syncActiveSocketState(activeSession, agentId);
    }, [agentId, token, activeSession?.id]);

    useEffect(() => {
        return () => {
            sessionMsgAbortRef.current?.abort();
            Object.keys(reconnectDisabledRef.current).forEach((key) => { reconnectDisabledRef.current[key] = true; });
            Object.keys(reconnectTimerRef.current).forEach((key) => clearReconnectTimer(key));
            Object.values(wsMapRef.current).forEach((ws) => { if (ws.readyState !== WebSocket.CLOSED) ws.close(); });
            wsMapRef.current = {}; wsRef.current = null;
        };
    }, []);

    // Smart scroll
    const isNearBottom = useRef(true);
    const isFirstLoad = useRef(true);
    const [showScrollBtn, setShowScrollBtn] = useState(false);
    const historyContainerRef = useRef<HTMLDivElement>(null);
    const [showHistoryScrollBtn, setShowHistoryScrollBtn] = useState(false);
    const handleHistoryScroll = () => { const el = historyContainerRef.current; if (!el) return; const distFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight; setShowHistoryScrollBtn(distFromBottom > 200); };
    const scrollHistoryToBottom = () => { const el = historyContainerRef.current; if (el) el.scrollTop = el.scrollHeight; setShowHistoryScrollBtn(false); };
    useEffect(() => { const el = historyContainerRef.current; if (!el) return; const timer = setTimeout(() => { const distFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight; setShowHistoryScrollBtn(distFromBottom > 200); }, 100); return () => clearTimeout(timer); }, [historyMsgs, activeSession?.id]);

    const ChatMessageItem = React.useMemo(() => React.memo(({ msg, i, isLeft, t }: { msg: any, i: number, isLeft: boolean, t: any }) => {
        const fe = msg.fileName?.split('.').pop()?.toLowerCase() ?? '';
        const fi = fe === 'pdf' ? '📄' : (fe === 'csv' || fe === 'xlsx' || fe === 'xls') ? '📊' : (fe === 'docx' || fe === 'doc') ? '📝' : '📎';
        const isImage = msg.imageUrl && ['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp'].includes(fe);
        const timestampHtml = msg.timestamp ? (() => {
            const d = new Date(msg.timestamp); const now = new Date(); const diffMs = now.getTime() - d.getTime(); const isToday = d.toDateString() === now.toDateString();
            let timeStr = ''; if (isToday) timeStr = d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }); else if (diffMs < 7 * 86400000) timeStr = d.toLocaleDateString([], { weekday: 'short' }) + ' ' + d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }); else timeStr = d.toLocaleDateString([], { month: 'short', day: 'numeric' }) + ' ' + d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
            return (<div style={{ fontSize: '10px', color: 'var(--text-tertiary)', marginTop: '4px', opacity: 0.6, display: 'flex', alignItems: 'center', justifyContent: isLeft ? 'flex-start' : 'flex-end' }}>{timeStr}{msg.content && <CopyMessageButton text={msg.content} />}</div>);
        })() : null;
        return (
            <div key={i} style={{ display: 'flex', flexDirection: isLeft ? 'row' : 'row-reverse', gap: '8px', marginBottom: '8px' }}>
                <div style={{ width: '28px', height: '28px', borderRadius: '50%', background: isLeft ? 'var(--bg-elevated)' : 'rgba(16,185,129,0.15)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '11px', flexShrink: 0, color: 'var(--text-secondary)', fontWeight: 600 }}>{isLeft ? (msg.sender_name ? msg.sender_name[0] : 'A') : 'U'}</div>
                <div style={{ maxWidth: '75%', padding: '8px 12px', borderRadius: '12px', background: isLeft ? 'var(--bg-secondary)' : 'rgba(16,185,129,0.1)', fontSize: '13px', lineHeight: '1.5', wordBreak: 'break-word' }}>
                    {isLeft && msg.sender_name && <div style={{ fontSize: '10px', color: 'var(--text-tertiary)', marginBottom: '2px', fontWeight: 600 }}>🤖 {msg.sender_name}</div>}
                    {isImage ? (<div style={{ marginBottom: '4px' }}><img src={msg.imageUrl} alt={msg.fileName} style={{ maxWidth: '200px', maxHeight: '150px', borderRadius: '8px', border: '1px solid var(--border-subtle)' }} loading="lazy" /></div>) : (msg.fileName && (<div style={{ display: 'inline-flex', alignItems: 'center', gap: '5px', background: isLeft ? 'rgba(0,0,0,0.05)' : 'rgba(0,0,0,0.08)', borderRadius: '6px', padding: '4px 8px', marginBottom: msg.content ? '4px' : '0', fontSize: '11px', border: '1px solid var(--border-subtle)', color: 'var(--text-secondary)' }}><span>{fi}</span><span style={{ fontWeight: 500, color: 'var(--text-primary)', maxWidth: '200px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{msg.fileName}</span></div>))}
                    {msg.thinking && (<details style={{ marginBottom: '8px', fontSize: '12px', background: 'rgba(147, 130, 220, 0.08)', borderRadius: '6px', border: '1px solid rgba(147, 130, 220, 0.15)' }}><summary style={{ padding: '6px 10px', cursor: 'pointer', color: 'rgba(147, 130, 220, 0.9)', fontWeight: 500, userSelect: 'none', display: 'flex', alignItems: 'center', gap: '4px' }}>💭 {t('chat.thinking', '思考中')}</summary><div style={{ padding: '4px 10px 8px', fontSize: '12px', lineHeight: '1.6', color: 'var(--text-secondary)', whiteSpace: 'pre-wrap', wordBreak: 'break-word', maxHeight: '300px', overflow: 'auto' }}>{msg.thinking}</div></details>)}
                    {msg.role === 'assistant' ? ((msg as any)._streaming && !msg.content ? (<div className="thinking-indicator"><div className="thinking-dots"><span /><span /><span /></div><span style={{ color: 'var(--text-tertiary)', fontSize: '13px' }}>{t('agent.chat.thinking', 'Thinking...')}</span></div>) : <MarkdownRenderer content={msg.content} />) : <div style={{ whiteSpace: 'pre-wrap' }}>{msg.content}</div>}
                    {timestampHtml}
                </div>
            </div>
        );
    }), [t]);

    const handleChatScroll = () => { const el = chatContainerRef.current; if (!el) return; const distFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight; isNearBottom.current = distFromBottom < 5; setShowScrollBtn(distFromBottom > 200); };
    const scrollToBottom = () => { chatEndRef.current?.scrollIntoView({ behavior: 'instant' as ScrollBehavior }); setShowScrollBtn(false); };
    useEffect(() => { if (!chatEndRef.current) return; if (isFirstLoad.current && chatMessages.length > 0) { chatEndRef.current.scrollIntoView({ behavior: 'instant' as ScrollBehavior }); isFirstLoad.current = false; setTimeout(() => chatInputRef.current?.focus(), 100); return; } if (isNearBottom.current) chatEndRef.current.scrollIntoView({ behavior: 'instant' as ScrollBehavior }); }, [chatMessages]);
    useEffect(() => { if (activeSession && wsConnected) setTimeout(() => chatInputRef.current?.focus(), 50); }, [activeSession?.id, wsConnected]);

    const sendChatMsg = () => {
        if (!agentId || !activeSession?.id) return;
        const activeRuntimeKey = buildSessionRuntimeKey(agentId, String(activeSession.id));
        const activeSocket = wsMapRef.current[activeRuntimeKey];
        if (!activeSocket || activeSocket.readyState !== WebSocket.OPEN) return;
        if (!chatInput.trim() && attachedFiles.length === 0) return;
        let userMsg = chatInput.trim(); let contentForLLM = userMsg; let displayFiles = '';
        if (attachedFiles.length > 0) {
            let filesPrompt = ''; let filesDisplay = '';
            attachedFiles.forEach(file => { filesDisplay += `[📎 ${file.name}] `; if (file.imageUrl && supportsVision) { filesPrompt += `[image_data:${file.imageUrl}]\n`; } else if (file.imageUrl) { filesPrompt += `[图片文件已上传: ${file.name}，保存在 ${file.path || ''}]\n`; } else { const wsPath = file.path || ''; const codePath = wsPath.replace(/^workspace\//, ''); const fileLoc = wsPath ? `\nFile location: ${wsPath} (for read_file/read_document tools)\nIn execute_code, use relative path: "${codePath}" (working directory is workspace/)\n` : ''; filesPrompt += `[File: ${file.name}]${fileLoc}\n${file.text}\n\n`; } });
            if (supportsVision && attachedFiles.some(f => f.imageUrl)) { contentForLLM = userMsg ? `${filesPrompt}\n${userMsg}` : `${filesPrompt}\n请分析这些文件`; } else { contentForLLM = userMsg ? `${filesPrompt}\nQuestion: ${userMsg}` : `Please analyze these files:\n\n${filesPrompt}`; }
            displayFiles = filesDisplay.trim(); userMsg = userMsg ? `${displayFiles}\n${userMsg}` : displayFiles;
        }
        setIsWaiting(true); setIsStreaming(false); setSessionUiState(activeRuntimeKey, { isWaiting: true, isStreaming: false });
        setChatMessages(prev => [...prev, parseChatMsg({ role: 'user', content: userMsg, fileName: attachedFiles.map(f => f.name).join(', '), imageUrl: attachedFiles.length === 1 ? attachedFiles[0].imageUrl : undefined, timestamp: new Date().toISOString() })]);
        activeSocket.send(JSON.stringify({ content: contentForLLM, display_content: userMsg, file_name: attachedFiles.map(f => f.name).join(', ') }));
        setChatInput(''); requestAnimationFrame(() => { if (chatInputRef.current) chatInputRef.current.style.height = 'auto'; }); setAttachedFiles([]);
    };

    const handleChatFile = async (e: React.ChangeEvent<HTMLInputElement>) => {
        const files = Array.from(e.target.files || []); if (!files.length) return;
        const allowedFiles = files.slice(0, 10 - attachedFiles.length); if (!allowedFiles.length) { useToastStore.getState().showToast(t('chat.fileLimitReached', '附件数量已达上限（最多10个）'), 'info'); return; }
        setUploading(true); setUploadProgress(0);
        try {
            const uploadPromises = allowedFiles.map(file => { const { promise } = uploadFileWithProgress(`/api/agents/${encodeURIComponent(agentId!)}/files/upload?path=workspace/uploads`, file, () => { }, undefined); return promise; });
            const results = await Promise.all(uploadPromises);
            const newAttached = results.map(data => ({ name: data.filename, text: data.extracted_text, path: data.workspace_path, imageUrl: data.image_data_url || undefined }));
            setAttachedFiles(prev => [...prev, ...newAttached].slice(0, 10));
        } catch (err: any) { if (err?.message !== 'Upload cancelled') useToastStore.getState().showToast(t('agent.upload.failed'), 'error'); }
        finally { setUploading(false); setUploadProgress(-1); uploadAbortRef.current = null; if (fileInputRef.current) fileInputRef.current.value = ''; }
    };

    const handlePaste = async (e: React.ClipboardEvent) => {
        const items = e.clipboardData?.items; if (!items) return;
        const filesToUpload: File[] = [];
        for (let i = 0; i < items.length; i++) { if (items[i].type.startsWith('image/')) { const blob = items[i].getAsFile(); if (blob) { const ext = blob.type.split('/')[1] || 'png'; const fileName = `paste-${Date.now()}-${i}.${ext}`; filesToUpload.push(new File([blob], fileName, { type: blob.type })); } } }
        if (!filesToUpload.length) return; e.preventDefault();
        const allowedFiles = filesToUpload.slice(0, 10 - attachedFiles.length); if (!allowedFiles.length) { useToastStore.getState().showToast(t('chat.fileLimitReached', '附件数量已达上限（最多10个）'), 'info'); return; }
        setUploading(true); setUploadProgress(0);
        try {
            const uploadPromises = allowedFiles.map(file => { const { promise } = uploadFileWithProgress(`/chat/upload`, file, () => { }, agentId ? { agent_id: agentId } : undefined); return promise; });
            const results = await Promise.all(uploadPromises);
            const newAttached = results.map(data => ({ name: data.filename, text: data.extracted_text, path: data.workspace_path, imageUrl: data.image_data_url || undefined }));
            setAttachedFiles(prev => [...prev, ...newAttached].slice(0, 10));
        } catch (err: any) { if (err?.message !== 'Upload cancelled') useToastStore.getState().showToast(t('agent.upload.failed'), 'error'); }
        finally { setUploading(false); setUploadProgress(-1); uploadAbortRef.current = null; }
    };

    return (
        <div style={{ display: 'flex', gap: '0', flex: 1, minHeight: 0, height: 'calc(100vh - 206px)' }}>
            {/* Left: session sidebar */}
            <div className={`session-sidebar ${sessionListCollapsed ? 'collapsed' : ''}`} style={{ width: sessionListCollapsed ? '0px' : '220px', transition: 'width 0.2s ease', flexShrink: 0, borderRight: sessionListCollapsed ? 'none' : '1px solid var(--border-subtle)', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
                <div style={{ display: 'flex', alignItems: 'center', padding: '10px 12px 0', gap: '4px', borderBottom: '1px solid var(--border-subtle)', position: 'relative' }}>
                    {!sessionListCollapsed && (
                        <button onClick={() => setSessionListCollapsed(true)} className="session-sidebar-collapseBtn" style={{ position: 'absolute', top: '6px', right: '4px', zIndex: 10, background: 'none', border: 'none', color: 'var(--text-tertiary)', cursor: 'pointer', padding: '4px', borderRadius: '4px' }} title={t('chat.collapseSessions', '收起会话列表')} onMouseEnter={e => e.currentTarget.style.background='var(--bg-secondary)'} onMouseLeave={e => e.currentTarget.style.background='none'}>
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="M15 18l-6-6 6-6"/></svg>
                        </button>
                    )}
                    <button onClick={() => setChatScope('mine')} style={{ flex: 1, padding: '5px 0', background: 'none', border: 'none', cursor: 'pointer', fontSize: '12px', fontWeight: chatScope === 'mine' ? 600 : 400, color: chatScope === 'mine' ? 'var(--text-primary)' : 'var(--text-tertiary)', borderBottom: chatScope === 'mine' ? '2px solid var(--accent-primary)' : '2px solid transparent', paddingBottom: '8px' }}>{t('agent.chat.mySessions')}</button>
                    {isAdmin && (<button onClick={() => { setChatScope('all'); fetchAllSessions(); }} style={{ flex: 1, padding: '5px 0', background: 'none', border: 'none', cursor: 'pointer', fontSize: '12px', fontWeight: chatScope === 'all' ? 600 : 400, color: chatScope === 'all' ? 'var(--text-primary)' : 'var(--text-tertiary)', borderBottom: chatScope === 'all' ? '2px solid var(--accent-primary)' : '2px solid transparent', paddingBottom: '8px' }}>{t('agent.chat.allUsers')}</button>)}
                </div>
                {chatScope === 'mine' && (
                    <div style={{ padding: '8px 12px', borderBottom: '1px solid var(--border-subtle)' }}>
                        <button onClick={createNewSession} style={{ width: '100%', padding: '5px 8px', background: 'none', border: '1px solid var(--border-subtle)', borderRadius: '6px', cursor: 'pointer', fontSize: '12px', color: 'var(--text-secondary)', textAlign: 'left', display: 'flex', alignItems: 'center', gap: '6px' }} onMouseEnter={e => { e.currentTarget.style.background = 'var(--bg-secondary)'; e.currentTarget.style.color = 'var(--text-primary)'; }} onMouseLeave={e => { e.currentTarget.style.background = 'none'; e.currentTarget.style.color = 'var(--text-secondary)'; }}>+ {t('agent.chat.newSession')}</button>
                    </div>
                )}
                <div style={{ flex: 1, overflowY: 'auto', padding: '4px 0' }}>
                    {chatScope === 'mine' ? (
                        sessionsLoading ? (<div style={{ padding: '20px 12px', fontSize: '12px', color: 'var(--text-tertiary)' }}>{t('common.loading')}</div>) : sessions.length === 0 ? (<div style={{ padding: '20px 12px', fontSize: '12px', color: 'var(--text-tertiary)' }}>{t('agent.chat.noSessionsYet')}<br />{t('agent.chat.clickToStart')}</div>) : sessions.map((s: any) => {
                            const isActive = activeSession?.id === s.id; const isOwn = s.user_id === String(currentUser?.id);
                            const channelLabel: Record<string, string> = { feishu: t('common.channels.feishu'), discord: t('common.channels.discord'), slack: t('common.channels.slack'), dingtalk: t('common.channels.dingtalk'), wecom: t('common.channels.wecom') }; const chLabel = channelLabel[s.source_channel];
                            return (
                                <div key={s.id} onClick={() => selectSession(s)} className="session-item" style={{ padding: '8px 12px', cursor: 'pointer', borderLeft: isActive ? '2px solid var(--accent-primary)' : '2px solid transparent', background: isActive ? 'var(--bg-secondary)' : 'transparent', marginBottom: '1px', position: 'relative' }}
                                    onMouseEnter={e => { if (!isActive) e.currentTarget.style.background = 'var(--bg-secondary)'; const btn = e.currentTarget.querySelector('.del-btn') as HTMLElement; if (btn) btn.style.opacity = '0.5'; }}
                                    onMouseLeave={e => { if (!isActive) e.currentTarget.style.background = 'transparent'; const btn = e.currentTarget.querySelector('.del-btn') as HTMLElement; if (btn) btn.style.opacity = '0'; }}>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '5px', marginBottom: '2px' }}>
                                        <div style={{ fontSize: '12px', fontWeight: isActive ? 600 : 400, color: 'var(--text-primary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', flex: 1 }}>{s.title}</div>
                                        {chLabel && <span style={{ fontSize: '9px', padding: '1px 4px', borderRadius: '3px', background: 'var(--bg-tertiary)', color: 'var(--text-tertiary)', flexShrink: 0 }}>{chLabel}</span>}
                                    </div>
                                    <div style={{ fontSize: '10px', color: 'var(--text-tertiary)', display: 'flex', alignItems: 'center', gap: '6px' }}>
                                        {isOwn && isActive && wsConnected && <span className="status-dot running" style={{ width: '5px', height: '5px', flexShrink: 0 }} />}
                                        {s.last_message_at ? new Date(s.last_message_at).toLocaleString(i18n.language === 'zh' ? 'zh-CN' : 'en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) : new Date(s.created_at).toLocaleString(i18n.language === 'zh' ? 'zh-CN' : 'en-US', { month: 'short', day: 'numeric' })}
                                        {s.message_count > 0 && <span style={{ marginLeft: 'auto' }}>{s.message_count}</span>}
                                    </div>
                                    <button className="del-btn" onClick={(e) => { e.stopPropagation(); deleteSession(s.id); }} style={{ position: 'absolute', top: '4px', right: '4px', background: 'none', border: 'none', cursor: 'pointer', padding: '2px 4px', opacity: 0, fontSize: '14px', color: 'var(--text-tertiary)', lineHeight: 1, transition: 'opacity 0.15s' }} onMouseEnter={e => { e.currentTarget.style.opacity = '1'; e.currentTarget.style.color = 'var(--status-error)'; }} onMouseLeave={e => { e.currentTarget.style.opacity = '0.5'; e.currentTarget.style.color = 'var(--text-tertiary)'; }} title={t('chat.deleteSession', 'Delete session')}>×</button>
                                </div>
                            );
                        })
                    ) : (
                        <>
                            <div style={{ padding: '8px 10px', borderBottom: '1px solid var(--border-subtle)' }}>
                                <select value={allUserFilter} onChange={e => setAllUserFilter(e.target.value)} style={{ width: '100%', padding: '4px 6px', fontSize: '11px', background: 'var(--bg-secondary)', border: '1px solid var(--border-subtle)', borderRadius: '5px', color: 'var(--text-primary)', cursor: 'pointer' }}>
                                    <option value="">{t('chat.allUsers', '所有用户')}</option>
                                    {Array.from(new Set(allSessions.map((s: any) => s.username || s.user_id))).filter(Boolean).map((u: any) => (<option key={u} value={u}>{u}</option>))}
                                </select>
                            </div>
                            {allSessionsLoading ? (<div style={{ padding: '8px 12px', display: 'flex', flexDirection: 'column', gap: '4px' }}>{[...Array(6)].map((_, i) => (<div key={i} style={{ padding: '6px 0', animation: 'pulse 1.5s ease-in-out infinite', animationDelay: `${i * 0.1}s` }}><div style={{ height: '12px', width: `${70 + (i % 3) * 10}%`, background: 'var(--bg-tertiary)', borderRadius: '4px', marginBottom: '6px' }} /><div style={{ height: '10px', width: `${40 + (i % 4) * 8}%`, background: 'var(--bg-tertiary)', borderRadius: '3px', opacity: 0.6 }} /></div>))}</div>) : allSessions.length === 0 ? (<div style={{ padding: '20px 12px', fontSize: '12px', color: 'var(--text-tertiary)', textAlign: 'center' }}>{t('agent.chat.noSessionsYet')}</div>) : null}
                            {!allSessionsLoading && allSessions.filter((s: any) => !allUserFilter || (s.username || s.user_id) === allUserFilter).map((s: any) => {
                                const isActive = activeSession?.id === s.id;
                                return (
                                    <div key={s.id} onClick={() => selectSession(s)} className="session-item" style={{ padding: '6px 12px', cursor: 'pointer', borderLeft: isActive ? '2px solid var(--accent-primary)' : '2px solid transparent', background: isActive ? 'var(--bg-secondary)' : 'transparent', position: 'relative' }}
                                        onMouseEnter={e => { if (!isActive) e.currentTarget.style.background = 'var(--bg-secondary)'; const btn = e.currentTarget.querySelector('.del-btn') as HTMLElement; if (btn) btn.style.opacity = '0.5'; }}
                                        onMouseLeave={e => { if (!isActive) e.currentTarget.style.background = 'transparent'; const btn = e.currentTarget.querySelector('.del-btn') as HTMLElement; if (btn) btn.style.opacity = '0'; }}>
                                        <div style={{ display: 'flex', alignItems: 'center', gap: '5px', marginBottom: '1px' }}><div style={{ fontSize: '12px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', color: 'var(--text-primary)', flex: 1 }}>{s.title}</div></div>
                                        <div style={{ fontSize: '10px', color: 'var(--text-tertiary)', display: 'flex', gap: '4px' }}><span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', flex: 1 }}>{s.username || ''}</span><span style={{ flexShrink: 0 }}>{s.last_message_at ? new Date(s.last_message_at).toLocaleString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) : ''}{s.message_count > 0 ? ` · ${s.message_count}` : ''}</span></div>
                                        <button className="del-btn" onClick={(e) => { e.stopPropagation(); deleteSession(s.id); }} style={{ position: 'absolute', top: '4px', right: '4px', background: 'none', border: 'none', cursor: 'pointer', padding: '2px 4px', opacity: 0, fontSize: '14px', color: 'var(--text-tertiary)', lineHeight: 1, transition: 'opacity 0.15s' }} onMouseEnter={e => { e.currentTarget.style.opacity = '1'; e.currentTarget.style.color = 'var(--status-error)'; }} onMouseLeave={e => { e.currentTarget.style.opacity = '0.5'; e.currentTarget.style.color = 'var(--text-tertiary)'; }} title={t('chat.deleteSession', 'Delete session')}>×</button>
                                    </div>
                                );
                            })}
                        </>
                    )}
                </div>
            </div>

            {/* Right: chat/message area */}
            <div className={`agent-chat-area ${!!(liveState.desktop || liveState.browser || liveState.code) ? 'has-live-panel' : ''}`} style={{ flex: 1, display: 'flex', flexDirection: 'row', position: 'relative', minWidth: 0, overflow: 'hidden' }}>
                <div style={{ flex: 1, display: 'flex', flexDirection: 'column', position: 'relative', minWidth: 0, overflow: 'hidden' }}>
                    {sessionListCollapsed && (
                        <button onClick={() => setSessionListCollapsed(false)} style={{ position: 'absolute', top: '12px', left: '12px', zIndex: 10, width: '28px', height: '28px', borderRadius: '6px', background: 'var(--bg-elevated)', border: '1px solid var(--border-subtle)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--text-secondary)', cursor: 'pointer', boxShadow: '0 2px 4px rgba(0,0,0,0.05)' }} title={t('chat.showSessions', '显示会话列表')} onMouseEnter={e => e.currentTarget.style.background='var(--bg-secondary)'} onMouseLeave={e => e.currentTarget.style.background='var(--bg-elevated)'}>
                            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect><line x1="9" y1="3" x2="9" y2="21"></line></svg>
                        </button>
                    )}
                    {!activeSession ? (
                        <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--text-tertiary)', fontSize: '13px', flexDirection: 'column', gap: '8px' }}>
                            <div>{t('agent.chat.noSessionSelected')}</div>
                            <button className="btn btn-secondary" onClick={createNewSession} style={{ fontSize: '12px' }}>{t('agent.chat.startNewSession')}</button>
                        </div>
                    ) : (activeSession.user_id && currentUser && activeSession.user_id !== String(currentUser.id)) || activeSession.source_channel === 'agent' || activeSession.participant_type === 'agent' ? (
                        /* Read-only history view */
                        <>
                            <div ref={historyContainerRef} onScroll={handleHistoryScroll} style={{ flex: 1, overflowY: 'auto', padding: '12px 16px' }}>
                                <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginBottom: '12px', padding: '4px 8px', background: 'var(--bg-secondary)', borderRadius: '4px', display: 'inline-block' }}>
                                    {activeSession.source_channel === 'agent' ? `🤖 ${t('chat.agentConversation', 'Agent 对话')} · ${activeSession.username || t('chat.agents', 'Agents')}` : `${t('chat.readOnly', '只读')} · ${activeSession.username || t('chat.user', '用户')}`}
                                </div>
                                {(() => {
                                    const isA2A = activeSession.source_channel === 'agent' || activeSession.participant_type === 'agent';
                                    const thisAgentName = agent?.name;
                                    const thisAgentPid = isA2A && thisAgentName ? historyMsgs.find((m: any) => m.sender_name === thisAgentName)?.participant_id : null;
                                    return historyMsgs.map((m: any, i: number) => {
                                        const isLeft = isA2A && thisAgentPid ? m.participant_id !== thisAgentPid : m.role === 'assistant';
                                        if (m.role === 'tool_call') {
                                            const tName = m.toolName || (() => { try { return JSON.parse(m.content || '{}').name; } catch { return 'tool'; } })();
                                            const tArgs = m.toolArgs || (() => { try { return JSON.parse(m.content || '{}').args; } catch { return {}; } })();
                                            const tResult = m.toolResult ?? (() => { try { return JSON.parse(m.content || '{}').result; } catch { return ''; } })();
                                            return (
                                                <div key={i} style={{ display: 'flex', gap: '8px', marginBottom: '6px', paddingLeft: '36px', minWidth: 0 }}>
                                                    <details style={{ flex: 1, minWidth: 0, borderRadius: '8px', background: 'var(--accent-subtle)', border: '1px solid var(--accent-subtle)', fontSize: '12px', overflow: 'hidden' }}>
                                                        <summary style={{ padding: '6px 10px', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: '6px', userSelect: 'none', listStyle: 'none', overflow: 'hidden' }}>
                                                            <span style={{ fontSize: '13px' }}>⚡</span>
                                                            <span style={{ fontWeight: 600, color: 'var(--accent-text)' }}>{tName}</span>
                                                            {tArgs && typeof tArgs === 'object' && Object.keys(tArgs).length > 0 && (
                                                                <span style={{ color: 'var(--text-tertiary)', fontSize: '11px', fontFamily: 'var(--font-mono)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', flex: 1 }}>
                                                                    {`(${Object.entries(tArgs).map(([k, v]) => `${k}: ${typeof v === 'string' ? v.slice(0, 30) : JSON.stringify(v)}`).join(', ')})`}
                                                                </span>
                                                            )}
                                                        </summary>
                                                        {tResult && (
                                                            <div style={{ padding: '4px 10px 8px' }}>
                                                                <div style={{ color: 'var(--text-secondary)', fontSize: '11px', fontFamily: 'var(--font-mono)', whiteSpace: 'pre-wrap', wordBreak: 'break-word', maxHeight: '240px', overflow: 'auto', background: 'rgba(0,0,0,0.15)', borderRadius: '4px', padding: '4px 6px' }}>
                                                                    {tResult}
                                                                </div>
                                                            </div>
                                                        )}
                                                    </details>
                                                </div>
                                            );
                                        }
                                        if (m.role === 'assistant' && !m.content?.trim()) { if (m.thinking) { return (<div key={i} style={{ paddingLeft: '36px', marginBottom: '6px' }}><details style={{ fontSize: '12px', background: 'rgba(147, 130, 220, 0.08)', borderRadius: '6px', border: '1px solid rgba(147, 130, 220, 0.15)' }}><summary style={{ padding: '6px 10px', cursor: 'pointer', color: 'rgba(147, 130, 220, 0.9)', fontWeight: 500, userSelect: 'none', display: 'flex', alignItems: 'center', gap: '4px' }}>{t('chat.thinking', '思考中')}</summary><div style={{ padding: '4px 10px 8px', fontSize: '12px', lineHeight: '1.6', color: 'var(--text-secondary)', whiteSpace: 'pre-wrap', wordBreak: 'break-word', maxHeight: '300px', overflow: 'auto' }}>{m.thinking}</div></details></div>); } return null; }
                                        return <ChatMessageItem key={i} msg={m} i={i} isLeft={isLeft} t={t} />;
                                    });
                                })()}
                            </div>
                            {showHistoryScrollBtn && (<button onClick={scrollHistoryToBottom} style={{ position: 'absolute', bottom: '20px', right: '20px', width: '32px', height: '32px', borderRadius: '50%', background: 'var(--bg-elevated)', border: '1px solid var(--border-default)', color: 'var(--text-secondary)', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '16px', boxShadow: '0 2px 8px rgba(0,0,0,0.3)', zIndex: 10 }} title={t('chat.scrollToBottom', '滚动到底部')}>↓</button>)}
                        </>
                    ) : (
                        /* Live WebSocket chat (own session) */
                        <>
                            <div ref={chatContainerRef} onScroll={handleChatScroll} style={{ flex: 1, overflowY: 'auto', padding: '12px 16px' }}>
                                {chatMessages.length === 0 && (<div style={{ textAlign: 'center', padding: '60px 20px', color: 'var(--text-tertiary)' }}><div style={{ fontSize: '13px', marginBottom: '4px' }}>{activeSession?.title || t('agent.chat.startChat')}</div><div style={{ fontSize: '12px' }}>{t('agent.chat.startConversation', { name: agent.name })}</div><div style={{ fontSize: '11px', marginTop: '4px', opacity: 0.7 }}>{t('agent.chat.fileSupport')}</div></div>)}
                                {chatMessages.map((msg, i) => {
                                    if (msg.role === 'tool_call') {
                                        return (
                                            <div key={i} style={{ display: 'flex', gap: '8px', marginBottom: '6px', paddingLeft: '36px', minWidth: 0 }}>
                                                <details style={{ flex: 1, minWidth: 0, borderRadius: '8px', background: 'var(--accent-subtle)', border: '1px solid var(--accent-subtle)', fontSize: '12px', overflow: 'hidden' }}>
                                                    <summary style={{ padding: '6px 10px', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: '6px', userSelect: 'none', listStyle: 'none', overflow: 'hidden' }}>
                                                        <span style={{ fontSize: '13px' }}>{msg.toolStatus === 'running' ? '⏳' : '⚡'}</span>
                                                        <span style={{ fontWeight: 600, color: 'var(--accent-text)' }}>{msg.toolName}</span>
                                                        {msg.toolArgs && Object.keys(msg.toolArgs).length > 0 && (
                                                            <span style={{ color: 'var(--text-tertiary)', fontSize: '11px', fontFamily: 'var(--font-mono)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', flex: 1 }}>
                                                                {`(${Object.entries(msg.toolArgs).map(([k, v]) => `${k}: ${typeof v === 'string' ? v.slice(0, 30) : JSON.stringify(v)}`).join(', ')})`}
                                                            </span>
                                                        )}
                                                        {msg.toolStatus === 'running' && <span style={{ color: 'var(--text-tertiary)', fontSize: '11px', marginLeft: 'auto' }}>{t('common.loading')}</span>}
                                                    </summary>
                                                    {msg.toolResult && (
                                                        <div style={{ padding: '4px 10px 8px' }}>
                                                            <div style={{ color: 'var(--text-secondary)', fontSize: '11px', fontFamily: 'var(--font-mono)', whiteSpace: 'pre-wrap', wordBreak: 'break-word', maxHeight: '240px', overflow: 'auto', background: 'rgba(0,0,0,0.15)', borderRadius: '4px', padding: '4px 6px' }}>
                                                                {msg.toolResult}
                                                            </div>
                                                        </div>
                                                    )}
                                                </details>
                                            </div>
                                        );
                                    }
                                    if (msg.role === 'assistant' && !msg.content?.trim()) { if (msg.thinking) { return (<div key={i} style={{ paddingLeft: '36px', marginBottom: '6px' }}><details style={{ fontSize: '12px', background: 'rgba(147, 130, 220, 0.08)', borderRadius: '6px', border: '1px solid rgba(147, 130, 220, 0.15)' }}><summary style={{ padding: '6px 10px', cursor: 'pointer', color: 'rgba(147, 130, 220, 0.9)', fontWeight: 500, userSelect: 'none', display: 'flex', alignItems: 'center', gap: '4px' }}>{t('chat.thinking', '思考中')}</summary><div style={{ padding: '4px 10px 8px', fontSize: '12px', lineHeight: '1.6', color: 'var(--text-secondary)', whiteSpace: 'pre-wrap', wordBreak: 'break-word', maxHeight: '300px', overflow: 'auto' }}>{msg.thinking}</div></details></div>); } return null; }
                                    return <ChatMessageItem key={i} msg={msg} i={i} isLeft={msg.role === 'assistant'} t={t} />;
                                })}
                                {isWaiting && (<div style={{ display: 'flex', gap: '8px', marginBottom: '8px', animation: 'fadeIn .2s ease' }}><div style={{ width: '28px', height: '28px', borderRadius: '50%', background: 'var(--bg-elevated)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '11px', flexShrink: 0, color: 'var(--text-secondary)', fontWeight: 600 }}>A</div><div style={{ padding: '8px 12px', borderRadius: '12px', background: 'var(--bg-secondary)', fontSize: '13px' }}><div className="thinking-indicator"><div className="thinking-dots"><span /><span /><span /></div><span style={{ color: 'var(--text-tertiary)', fontSize: '13px' }}>{t('agent.chat.thinking', 'Thinking...')}</span></div></div></div>)}
                                <div ref={chatEndRef} />
                            </div>
                            {showScrollBtn && (<button onClick={scrollToBottom} style={{ position: 'absolute', bottom: '70px', right: '20px', width: '32px', height: '32px', borderRadius: '50%', background: 'var(--bg-elevated)', border: '1px solid var(--border-default)', color: 'var(--text-secondary)', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '16px', boxShadow: '0 2px 8px rgba(0,0,0,0.3)', zIndex: 10 }} title={t('chat.scrollToBottom', '滚动到底部')}>↓</button>)}
                            {agentExpired ? (<div style={{ padding: '7px 16px', borderTop: '1px solid rgba(245,158,11,0.3)', background: 'rgba(245,158,11,0.08)', display: 'flex', alignItems: 'center', gap: '8px', fontSize: '12px', color: 'rgb(180,100,0)' }}><span>⏸</span><span>{t('chat.agentExpired', '该 Agent 已<strong>过期</strong>，当前不在线。')}</span></div>) : !wsConnected && (!activeSession?.user_id || !currentUser || activeSession.user_id === String(currentUser?.id)) ? (<div style={{ padding: '3px 16px', display: 'flex', alignItems: 'center', gap: '6px', fontSize: '11px', color: 'var(--text-tertiary)' }}><span style={{ display: 'inline-block', width: '5px', height: '5px', borderRadius: '50%', background: 'var(--accent-primary)', opacity: 0.8, animation: 'pulse 1.2s ease-in-out infinite' }} />{t('chat.connecting', '连接中...')}</div>) : null}
                            {attachedFiles.length > 0 && (<div style={{ padding: '6px 16px', background: 'var(--bg-elevated)', borderTop: '1px solid var(--border-subtle)', display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>{attachedFiles.map((file, idx) => (<div key={idx} style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '11px', background: 'var(--bg-secondary)', padding: '4px 6px', borderRadius: '4px', border: '1px solid var(--border-subtle)', maxWidth: '200px' }}>{file.imageUrl ? (<img src={file.imageUrl} alt={file.name} style={{ width: '20px', height: '20px', borderRadius: '4px', objectFit: 'cover' }} />) : (<span>📎</span>)}<span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{file.name}</span><button onClick={() => setAttachedFiles(prev => prev.filter((_, i) => i !== idx))} style={{ background: 'none', border: 'none', color: 'var(--text-tertiary)', cursor: 'pointer', fontSize: '14px', padding: '0 2px' }} title={t('chat.removeFile', '移除文件')}>✕</button></div>))}</div>)}
                            <div style={{ display: 'flex', gap: '8px', padding: '6px 12px', borderTop: '1px solid var(--border-subtle)' }}>
                                <input type="file" multiple ref={fileInputRef} onChange={handleChatFile} style={{ display: 'none' }} />
                                <button className="btn btn-secondary" onClick={() => fileInputRef.current?.click()} disabled={!wsConnected || uploading || isWaiting || isStreaming || attachedFiles.length >= 10} style={{ padding: '6px 10px', fontSize: '14px', minWidth: 'auto', ...((!wsConnected || uploading || isWaiting || isStreaming) ? { cursor: 'not-allowed', opacity: 0.4 } : {}) }}>{uploading ? '⏳' : '📎'}</button>
                                {uploading && uploadProgress >= 0 && (<div style={{ display: 'flex', alignItems: 'center', gap: '6px', flex: '0 0 140px' }}>{uploadProgress <= 100 ? (<><div style={{ flex: 1, height: '4px', borderRadius: '2px', background: 'var(--bg-tertiary)', overflow: 'hidden' }}><div style={{ height: '100%', borderRadius: '2px', background: 'var(--accent-primary)', width: `${uploadProgress}%`, transition: 'width 0.15s ease' }} /></div><span style={{ fontSize: '11px', color: 'var(--text-tertiary)', fontVariantNumeric: 'tabular-nums', whiteSpace: 'nowrap' }}>{uploadProgress}%</span></>) : (<div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}><span style={{ display: 'inline-block', width: '5px', height: '5px', borderRadius: '50%', background: 'var(--accent-primary)', animation: 'pulse 1.2s ease-in-out infinite' }} /><span style={{ fontSize: '11px', color: 'var(--text-tertiary)', whiteSpace: 'nowrap' }}>Processing...</span></div>)}<button onClick={() => { uploadAbortRef.current?.(); }} style={{ background: 'none', border: 'none', color: 'var(--text-tertiary)', cursor: 'pointer', fontSize: '12px', padding: '0 2px', lineHeight: 1 }} title={t('chat.cancelUpload', '取消上传')}>✕</button></div>)}
                                <textarea ref={chatInputRef} className="chat-input" value={chatInput} onChange={e => { setChatInput(e.target.value); const MAX_H = 130; requestAnimationFrame(() => { const el = chatInputRef.current; if (!el) return; el.style.height = 'auto'; const natural = el.scrollHeight; el.style.height = Math.min(natural, MAX_H) + 'px'; el.style.overflowY = natural > MAX_H ? 'auto' : 'hidden'; }); }} onKeyDown={e => { if (e.key === 'Enter' && (e.ctrlKey || e.metaKey) && !e.nativeEvent.isComposing && !isWaiting && !isStreaming) { e.preventDefault(); sendChatMsg(); } }} onPaste={handlePaste} placeholder={!wsConnected && (!activeSession?.user_id || !currentUser || activeSession.user_id === String(currentUser?.id)) ? t('chat.connecting', '连接中...') : attachedFiles.length > 0 ? t('agent.chat.askAboutFile', { name: attachedFiles.length === 1 ? attachedFiles[0].name : `${attachedFiles.length} files` }) : t('chat.placeholder')} disabled={!wsConnected} rows={1} style={{ flex: 1, resize: 'none', overflowY: 'hidden', lineHeight: '22px', paddingTop: '7px', paddingBottom: '7px' }} autoFocus />
                                {(isStreaming || isWaiting) ? (
                                    <button className="btn btn-stop-generation" onClick={() => { if (!agentId || !activeSession?.id) return; const activeRuntimeKey = buildSessionRuntimeKey(agentId, String(activeSession.id)); const activeSocket = wsMapRef.current[activeRuntimeKey]; if (activeSocket?.readyState === WebSocket.OPEN) { activeSocket.send(JSON.stringify({ type: 'abort' })); setIsStreaming(false); setIsWaiting(false); setSessionUiState(activeRuntimeKey, { isWaiting: false, isStreaming: false }); } }} style={{ padding: '6px 16px' }} title={t('chat.stop', '停止')}><span className="stop-icon" /></button>
                                ) : (
                                    <button className="btn btn-primary" onClick={sendChatMsg} disabled={!wsConnected || (!chatInput.trim() && attachedFiles.length === 0)} style={{ padding: '6px 16px' }}>{t('chat.send')}</button>
                                )}
                            </div>
                        </>
                    )}
                </div>
                {!!(liveState.desktop || liveState.browser || liveState.code) && (<AgentBayLivePanel liveState={liveState} visible={livePanelVisible} onToggle={() => setLivePanelVisible(v => !v)} />)}
            </div>
        </div>
    );
}
