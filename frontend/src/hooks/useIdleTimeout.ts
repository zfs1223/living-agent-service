import { useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '../stores';

const ACTIVITY_EVENTS: (keyof WindowEventMap)[] = [
    'mousedown',
    'mousemove',
    'keypress',
    'keydown',
    'scroll',
    'touchstart',
    'click',
];

export function useIdleTimeout() {
    const navigate = useNavigate();
    const logout = useAuthStore((s) => s.logout);
    const user = useAuthStore((s) => s.user);
    const updateLastActivity = useAuthStore((s) => s.updateLastActivity);
    const lastActivity = useAuthStore((s) => s.lastActivity);
    const checkTimerRef = useRef<number | null>(null);

    const handleActivity = useCallback(() => {
        updateLastActivity();
    }, [updateLastActivity]);

    useEffect(() => {
        if (!user) return;

        ACTIVITY_EVENTS.forEach((event) => {
            window.addEventListener(event, handleActivity, { passive: true });
        });

        return () => {
            ACTIVITY_EVENTS.forEach((event) => {
                window.removeEventListener(event, handleActivity);
            });
        };
    }, [user, handleActivity]);

    useEffect(() => {
        if (!user) return;

        const stored = localStorage.getItem('idle_timeout_minutes');
        let timeoutMs: number;
        if (stored) {
            const minutes = parseInt(stored, 10);
            if (!isNaN(minutes) && minutes > 0) {
                timeoutMs = minutes * 60 * 1000;
            } else {
                timeoutMs = 30 * 60 * 1000;
            }
        } else {
            timeoutMs = 30 * 60 * 1000;
        }

        const checkIdle = () => {
            const now = Date.now();
            const elapsed = now - lastActivity;
            if (elapsed >= timeoutMs) {
                logout();
                navigate('/login');
            }
        };

        if (checkTimerRef.current) {
            clearInterval(checkTimerRef.current);
        }

        checkTimerRef.current = window.setInterval(checkIdle, 60 * 1000);

        return () => {
            if (checkTimerRef.current) {
                clearInterval(checkTimerRef.current);
            }
        };
    }, [user, lastActivity, logout, navigate]);
}
