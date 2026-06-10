import { useEffect, useRef, useCallback } from 'react';

/**
 * 页面可见时执行轮询，不可见时暂停
 *
 * @param callback  轮询回调（每次间隔到期时调用）
 * @param intervalMs 轮询间隔（毫秒）
 * @param enabled   是否启用轮询（默认 true）
 */
export function usePolling(
    callback: () => void,
    intervalMs: number,
    enabled: boolean = true,
) {
    const savedCallback = useRef(callback);

    // 始终保持 ref 指向最新回调，避免闭包过期
    useEffect(() => {
        savedCallback.current = callback;
    }, [callback]);

    const tick = useCallback(() => {
        savedCallback.current();
    }, []);

    useEffect(() => {
        if (!enabled) return;

        let timerId: number | null = null;

        const start = () => {
            if (timerId !== null) return; // 已在运行
            timerId = window.setInterval(tick, intervalMs);
        };

        const stop = () => {
            if (timerId !== null) {
                window.clearInterval(timerId);
                timerId = null;
            }
        };

        const onVisibilityChange = () => {
            if (document.hidden) {
                stop();
            } else {
                start();
            }
        };

        // 页面可见时立即启动
        if (!document.hidden) {
            start();
        }

        document.addEventListener('visibilitychange', onVisibilityChange);

        return () => {
            stop();
            document.removeEventListener('visibilitychange', onVisibilityChange);
        };
    }, [intervalMs, enabled, tick]);
}
