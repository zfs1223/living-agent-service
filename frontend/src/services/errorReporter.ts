import { API_BASE } from './apiBase';

interface ErrorReport {
  type: string;
  message: string;
  stack?: string;
  url?: string;
  line?: number;
  column?: number;
  timestamp: number;
  userAgent?: string;
}

const QUEUE: ErrorReport[] = [];
let flushTimer: ReturnType<typeof setTimeout> | null = null;
const FLUSH_INTERVAL = 5000;
const MAX_QUEUE_SIZE = 20;

export function reportError(error: Error | string, context?: Record<string, unknown>): void {
  const report: ErrorReport = {
    type: context?.type as string ?? 'unknown',
    message: typeof error === 'string' ? error : error.message,
    stack: typeof error === 'string' ? undefined : error.stack,
    url: window.location.href,
    timestamp: Date.now(),
    userAgent: navigator.userAgent,
    ...context,
  };

  QUEUE.push(report);
  if (QUEUE.length > MAX_QUEUE_SIZE) QUEUE.shift();

  if (!flushTimer) {
    flushTimer = setTimeout(flush, FLUSH_INTERVAL);
  }
}

async function flush(): Promise<void> {
  flushTimer = null;
  if (QUEUE.length === 0) return;

  const batch = QUEUE.splice(0, QUEUE.length);
  try {
    await fetch(`${API_BASE}/error-reports`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ errors: batch }),
      credentials: 'include',
    });
  } catch {
    // Silently fail - error reporting should not cause further errors
  }
}

// Global unhandled error handler
window.addEventListener('error', (event) => {
  reportError(event.error || event.message, {
    type: 'unhandled_error',
    line: event.lineno,
    column: event.colno,
    url: event.filename,
  });
});

window.addEventListener('unhandledrejection', (event) => {
  reportError(event.reason instanceof Error ? event.reason : String(event.reason), {
    type: 'unhandled_promise_rejection',
  });
});
