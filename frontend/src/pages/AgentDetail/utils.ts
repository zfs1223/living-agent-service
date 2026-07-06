/**
 * Shared utility functions for AgentDetail page components.
 */

import { request } from '../../services/apiBase';

/** Format large token numbers with K/M suffixes */
export const formatTokens = (n: number): string => {
    if (!n) return '0';
    if (n >= 1000000) return `${(n / 1000000).toFixed(1)}M`;
    if (n >= 1000) return `${(n / 1000).toFixed(1)}K`;
    return String(n);
};

/** Fetch with authentication (alias of apiBase.request, auto拆包 ApiResponse) */
export const fetchAuth = request;
