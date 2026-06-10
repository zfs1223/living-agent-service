/**
 * Shared utility functions for AgentDetail page components.
 */

import { getToken } from '../../stores';

/** Format large token numbers with K/M suffixes */
export const formatTokens = (n: number): string => {
    if (!n) return '0';
    if (n >= 1000000) return `${(n / 1000000).toFixed(1)}M`;
    if (n >= 1000) return `${(n / 1000).toFixed(1)}K`;
    return String(n);
};

/** Fetch with authentication header, auto-prefixes /api */
export function fetchAuth<T>(url: string, options?: RequestInit): Promise<T> {
    const token = getToken();
    return fetch(`/api${url}`, {
        ...options,
        headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    }).then(r => r.json());
}
