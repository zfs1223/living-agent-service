import { fetchJson } from './api';
import type {
  ProviderConfig,
  LlmModel,
  BrainModelAssignment,
  ProviderTestResult,
  ProviderRequest,
  ModelRequest,
  BrainModelRequest,
} from '../types/modelPool';

export interface ProviderEntry {
  id: string;
  displayName: string;
  protocol: string;
  defaultBaseUrl: string | null;
  supportsToolChoice: boolean;
  defaultMaxTokens: number;
}

export const modelPoolApi = {
  providers: {
    list: () =>
      fetchJson<ProviderConfig[]>('/model-pool/providers'),

    get: (id: string) =>
      fetchJson<ProviderConfig>(`/model-pool/providers/${id}`),

    add: (data: ProviderRequest) =>
      fetchJson<ProviderConfig>('/model-pool/providers', {
        method: 'POST',
        body: JSON.stringify(data),
      }),

    update: (id: string, data: ProviderRequest) =>
      fetchJson<ProviderConfig>(`/model-pool/providers/${id}`, {
        method: 'PUT',
        body: JSON.stringify(data),
      }),

    delete: (id: string) =>
      fetchJson<void>(`/model-pool/providers/${id}`, {
        method: 'DELETE',
      }),

    test: (id: string, testModel: string, baseUrl?: string, apiKeyEncrypted?: string) =>
      fetchJson<ProviderTestResult>(`/model-pool/providers/${id}/test`, {
        method: 'POST',
        body: JSON.stringify({ testModel, baseUrl, apiKeyEncrypted }),
      }),

    discover: (id: string, tempBaseUrl?: string, tempApiKey?: string) =>
      fetchJson<string[]>(`/model-pool/providers/${id}/discover`, {
        method: 'POST',
        body: JSON.stringify({ baseUrl: tempBaseUrl, apiKeyEncrypted: tempApiKey }),
      }),

    manifest: () =>
      fetchJson<ProviderEntry[]>('/model-pool/providers/manifest'),

    getDefaultBaseUrl: (id: string) =>
      fetchJson<string | null>(`/model-pool/providers/${id}/default-base-url`),
  },

  models: {
    list: () =>
      fetchJson<LlmModel[]>('/model-pool/models'),

    get: (id: string) =>
      fetchJson<LlmModel>(`/model-pool/models/${id}`),

    getByProvider: (providerId: string) =>
      fetchJson<LlmModel[]>(`/model-pool/models/provider/${providerId}`),

    add: (data: ModelRequest) =>
      fetchJson<LlmModel>('/model-pool/models', {
        method: 'POST',
        body: JSON.stringify(data),
      }),

    update: (id: string, data: ModelRequest) =>
      fetchJson<LlmModel>(`/model-pool/models/${id}`, {
        method: 'PUT',
        body: JSON.stringify(data),
      }),

    delete: (id: string) =>
      fetchJson<void>(`/model-pool/models/${id}`, {
        method: 'DELETE',
      }),

    batchDelete: (ids: string[]) =>
      fetchJson<{ deleted: number; total: number }>('/model-pool/models/batch-delete', {
        method: 'POST',
        body: JSON.stringify({ ids }),
      }),
  },

  assignments: {
    list: () =>
      fetchJson<BrainModelAssignment[]>('/model-pool/assignments'),

    get: (brainId: string) =>
      fetchJson<BrainModelAssignment>(`/model-pool/assignments/${brainId}`),

    assign: (brainId: string, data: { modelId: string; assignedBy?: string }) =>
      fetchJson<BrainModelAssignment>(`/model-pool/assignments/${brainId}`, {
        method: 'POST',
        body: JSON.stringify(data),
      }),

    clear: (brainId: string) =>
      fetchJson<void>(`/model-pool/assignments/${brainId}`, {
        method: 'DELETE',
      }),

    available: () =>
      fetchJson<LlmModel[]>('/model-pool/models/available'),
  },
};
