import { useEffect, useRef, useCallback } from 'react';

/**
 * 页面可见时执行轮询，不可见时降频（不完全暂停）
 *
 * @param callback  轮询回调（每次间隔到期时调用）
 * @param intervalMs 轮询间隔（毫秒）
 * @param enabled   是否启用轮询（默认 true）
 * @param hiddenIntervalMs 页面隐藏时的轮询间隔（默认为 intervalMs 的 3 倍）
 *                         设为 0 则隐藏时完全暂停（旧行为）
 */
export function usePolling(
    callback: () => void,
    intervalMs: number,
    enabled: boolean = true,
    hiddenIntervalMs?: number,
) {
    const savedCallback = useRef(callback);

    // 始终保持 ref 指向最新回调，避免闭包过期
    useEffect(() => {
        savedCallback.current = callback;
    }, [callback]);

    const tick = useCallback(() => {
        savedCallback.current();
    }, []);

    // 页面隐藏时的间隔：默认 3 倍（保活但不频繁），设为 0 则完全暂停
    const resolvedHiddenInterval = hiddenIntervalMs ?? intervalMs * 3;

    useEffect(() => {
        if (!enabled) return;

        let timerId: number | null = null;

        const start = (interval: number) => {
            if (timerId !== null) return; // 已在运行
            timerId = window.setInterval(tick, interval);
        };

        const stop = () => {
            if (timerId !== null) {
                window.clearInterval(timerId);
                timerId = null;
            }
        };

        const restart = (interval: number) => {
            stop();
            if (interval > 0) {
                start(interval);
            }
        };

        const onVisibilityChange = () => {
            if (document.hidden) {
                // 页面隐藏：降频而非暂停（保持 NAT 穿透流量）
                restart(resolvedHiddenInterval);
            } else {
                // 页面恢复：切回正常频率，并立即执行一次
                restart(intervalMs);
                tick();
            }
        };

        // 根据当前可见性启动
        if (!document.hidden) {
            start(intervalMs);
        } else if (resolvedHiddenInterval > 0) {
            start(resolvedHiddenInterval);
        }

        document.addEventListener('visibilitychange', onVisibilityChange);

        return () => {
            stop();
            document.removeEventListener('visibilitychange', onVisibilityChange);
        };
    }, [intervalMs, resolvedHiddenInterval, enabled, tick]);
}
