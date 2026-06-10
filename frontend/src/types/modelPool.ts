export interface ProviderConfig {
  id: string;
  displayName: string;
  protocol: 'OPENAI_COMPATIBLE' | 'ANTHROPIC' | 'GEMINI' | 'OPENAI_RESPONSES';
  baseUrl: string;
  apiKeyEncrypted?: string;
  enabled: boolean;
  supportsToolChoice: boolean;
  defaultMaxTokens: number;
  autoDiscoverModels: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface LlmModel {
  id: string;
  providerId: string;
  modelName: string;
  displayName: string;
  contextWindow: number;
  maxOutputTokens: number;
  supportsVision: boolean;
  supportsReasoning: boolean;
  temperature: number | null;
  enabled: boolean;
  recommended: boolean;
  bestFor: string | null;
  inputTypes: string;
  createdAt?: string;
}

export interface BrainModelAssignment {
  id: string;
  brainId: string;
  brainName: string;
  brainType: string;
  modelId: string;
  modelName: string;
  displayName: string;
  assignedBy: string;
  assignedAt: string;
  updatedAt: string;
}

export interface ProviderTestResult {
  success: boolean;
  latencyMs: number;
  response: string;
  message: string;
  error: string | null;
}

export interface BrainModelRequest {
  modelId: string;
}

export interface ProviderRequest {
  id: string;
  displayName: string;
  protocol: 'OPENAI_COMPATIBLE' | 'ANTHROPIC' | 'GEMINI' | 'OPENAI_RESPONSES';
  baseUrl: string;
  apiKeyEncrypted: string;
  enabled: boolean;
  supportsToolChoice: boolean;
  defaultMaxTokens: number;
  autoDiscoverModels: boolean;
}

export interface ModelRequest {
  providerId: string;
  modelName: string;
  displayName: string;
  contextWindow: number;
  maxOutputTokens: number;
  supportsVision: boolean;
  supportsReasoning: boolean;
  temperature: number | null;
  enabled: boolean;
  recommended: boolean;
  bestFor: string;
  inputTypes: string;
}
