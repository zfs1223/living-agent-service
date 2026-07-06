import { request } from './apiBase';

// ==================== Types ====================

export interface Opportunity {
    opportunityId: string;
    title: string;
    description: string;
    type: 'GITHUB_BOUNTY' | 'GITHUB_ISSUE' | 'FREELANCE_PROJECT' | 'BUG_BOUNTY' | 'INTERNAL_TASK';
    sourceType: string;
    sourceId: string;
    url: string;
    payoutCents: number;
    currency: string;
    deadline: string | null;
    riskLevel: string;
    metadata: Record<string, unknown> | null;
}

export interface ActiveHunt {
    huntId: string;
    opportunity: Opportunity;
    status: 'IN_PROGRESS' | 'COMPLETED' | 'FAILED' | 'REJECTED';
    startedAt: string;
}

export interface ROIResult {
    opportunityId: string;
    decision: 'HUNT' | 'PASS' | 'CONSULT';
    estimatedCostCents: number;
    expectedPayoutCents: number;
    profitMargin: number;
    complexity: number;
    recommendedDeployment: string;
    estimatedTimeSeconds: number;
}

export interface PayoutAccount {
    accountId: string;
    accountName: string;
    accountType: string;
    provider: string;
    accountIdentifier: string;
    ownerId: string;
    ownerType: string;
    isDefault: boolean;
    isActive: boolean;
    verified: boolean;
}

export interface PayoutRecord {
    payoutId: string;
    externalId: string;
    sourceType: string;
    sourceReference: string;
    accountId: string;
    ownerId: string;
    amount: number;
    currency: string;
    status: string;
    fee: number;
    netAmount: number;
    createdAt: string;
}

export interface PayoutSummary {
    totalCollected: number;
    pendingAmount: number;
    totalPayouts: number;
    successfulPayouts: number;
    pendingPayouts: number;
    failedPayouts: number;
    thisMonthCollected: number;
    lastMonthCollected: number;
}

export interface IncomeRecord {
    incomeId: string;
    employeeId: string;
    sourceType: string;
    sourceId: string;
    amountCents: number;
    status: string;
    createdAt: string;
    receivedAt: string | null;
}

export interface EvolutionTierInfo {
    employeeId: string;
    tier: 'EVOLVING' | 'NORMAL' | 'SAVING' | 'MINIMAL';
    tierName: string;
    description: string;
    accumulatedFunds: number;
    queriedAt: string;
}

export interface AutonomousOverview {
    creditBalance: number;
    totalEarned: number;
    performanceScore: number;
    ledgerBalance: number;
    tier: string;
    tierName: string;
    accumulatedFunds: number;
    activeHunts: number;
    discoveredOpportunities: number;
    pendingPayout: number;
    totalCollected: number;
    successfulPayouts: number;
    generatedAt: string;
}

export interface DiscoverRequest {
    scanGitHub?: boolean;
    scanFreelance?: boolean;
    scanBugBounty?: boolean;
}

// ==================== API ====================

export const autonomousApi = {
    // Overview
    getOverview: () => request<AutonomousOverview>('/autonomous/overview'),

    // Bounty
    getOpportunities: () => request<Opportunity[]>('/autonomous/bounty/opportunities'),
    discoverOpportunities: (config?: DiscoverRequest) =>
        request<Opportunity[]>('/autonomous/bounty/discover', {
            method: 'POST',
            body: config ? JSON.stringify(config) : undefined,
        }),
    getActiveHunts: () => request<ActiveHunt[]>('/autonomous/bounty/active-hunts'),
    evaluateROI: (opportunityId: string) =>
        request<ROIResult>(`/autonomous/bounty/evaluate/${encodeURIComponent(opportunityId)}`, { method: 'POST' }),

    // Payout
    getPayoutAccounts: () => request<PayoutAccount[]>('/autonomous/payout/accounts'),
    createPayoutAccount: (data: {
        accountName: string;
        accountType: string;
        provider: string;
        accountIdentifier: string;
        ownerId: string;
        ownerType: string;
        isDefault: boolean;
    }) => request<PayoutAccount>('/autonomous/payout/accounts', {
        method: 'POST',
        body: JSON.stringify(data),
    }),
    getPayoutHistory: (from?: number, to?: number) => {
        const params = new URLSearchParams();
        if (from) params.set('from', String(from));
        if (to) params.set('to', String(to));
        const qs = params.toString();
        return request<PayoutRecord[]>(`/autonomous/payout/history${qs ? `?${qs}` : ''}`);
    },
    getPayoutSummary: () => request<PayoutSummary>('/autonomous/payout/summary'),
    getPendingPayoutAmount: () => request<number>('/autonomous/payout/pending'),

    // Ledger
    getLedgerBalance: () => request<{ employeeId: string; balance: number; totalEarned: number; queriedAt: string }>('/autonomous/ledger/balance'),
    getLedgerHistory: (limit?: number) =>
        request<IncomeRecord[]>(`/autonomous/ledger/history${limit ? `?limit=${limit}` : ''}`),

    // Evolution
    getEvolutionTier: () => request<EvolutionTierInfo>('/autonomous/evolution/tier'),
};
