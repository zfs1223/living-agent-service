import { fetchJson } from './api';
import type { LlmModel, BrainModelAssignment, BrainModelRequest } from '../types/modelPool';

export const brainModelApi = {
  list: () => fetchJson<BrainModelAssignment[]>('/brain-models'),

  get: (brainId: string) => fetchJson<BrainModelAssignment>(`/brain-models/${encodeURIComponent(brainId)}`),

  assign: (brainId: string, data: BrainModelRequest) =>
    fetchJson<BrainModelAssignment>(`/brain-models?brainId=${encodeURIComponent(brainId)}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),

  clear: (brainId: string) => fetchJson<void>(`/brain-models?brainId=${encodeURIComponent(brainId)}`, { method: 'DELETE' }),

  available: () => fetchJson<LlmModel[]>('/brain-models/available'),
};
