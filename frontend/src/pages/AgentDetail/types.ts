/**
 * Shared type definitions for AgentDetail page components.
 */

/** Chat message structure used in the agent chat interface */
export interface ChatMsg {
    role: 'user' | 'assistant' | 'tool_call';
    content: string;
    fileName?: string;
    toolName?: string;
    toolArgs?: any;
    toolStatus?: 'running' | 'done';
    toolResult?: string;
    thinking?: string;
    imageUrl?: string;
    timestamp?: string;
}

/** Tab identifiers for the agent detail page */
export const TABS = ['status', 'aware', 'mind', 'tools', 'skills', 'relationships', 'workspace', 'chat', 'activityLog', 'approvals', 'settings'] as const;

export type AgentTab = typeof TABS[number];
