import type {
  AiAnalysis,
  ConnectionStatus,
  Incident,
  Project,
  ProjectRequest,
  Rule,
  RuleRequest,
  Snapshot,
} from './types';

const base = (import.meta.env?.VITE_API_BASE_URL ?? '').replace(/\/$/, '');

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

export async function request<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  let response: Response;
  const headers = new Headers(options.headers);
  if (options.body && !headers.has('Content-Type'))
    headers.set('Content-Type', 'application/json');
  try {
    response = await fetch(`${base}/api${path}`, {
      ...options,
      signal: options.signal
        ? AbortSignal.any([options.signal, AbortSignal.timeout(20_000)])
        : AbortSignal.timeout(20_000),
      headers,
    });
  } catch (error) {
    if (options.signal?.aborted) throw error;
    throw new ApiError(
      0,
      'Cannot reach the backend. Check the connection and try again.',
    );
  }
  if (!response.ok) {
    const problem = (await response.json().catch(() => ({}))) as {
      detail?: string;
      title?: string;
      errors?: { field: string; message: string }[];
    };
    const details = problem.errors
      ?.map((error) => `${error.field}: ${error.message}`)
      .join('; ');
    throw new ApiError(
      response.status,
      details ||
        problem.detail ||
        problem.title ||
        (response.status >= 500
          ? 'The backend is unavailable or could not complete the request. Try again.'
          : `Request failed (${response.status}).`),
    );
  }
  if (response.status === 204) return null as T;
  return response.json() as Promise<T>;
}

const body = (value: unknown) => JSON.stringify(value);
export const api = {
  projects: (signal?: AbortSignal) =>
    request<Project[]>('/projects', { signal }),
  project: (id: number, signal?: AbortSignal) =>
    request<Project>(`/projects/${id}`, { signal }),
  create: (value: ProjectRequest) =>
    request<Project>('/projects', { method: 'POST', body: body(value) }),
  update: (id: number, value: ProjectRequest) =>
    request<Project>(`/projects/${id}`, { method: 'PUT', body: body(value) }),
  delete: (id: number) =>
    request<null>(`/projects/${id}`, { method: 'DELETE' }),
  testConnection: (id: number) =>
    request<{ connectionStatus: ConnectionStatus; message: string }>(
      `/projects/${id}/test-connection`,
      { method: 'POST' },
    ),
  monitoring: (id: number, enabled: boolean) =>
    request<Project>(
      `/projects/${id}/monitoring/${enabled ? 'enable' : 'disable'}`,
      { method: 'POST' },
    ),
  current: (id: number, signal?: AbortSignal) =>
    request<Snapshot | null>(`/projects/${id}/metrics/current`, { signal }),
  history: (id: number, from: string, to: string, signal?: AbortSignal) =>
    request<Snapshot[]>(
      `/projects/${id}/metrics/history?${new URLSearchParams({ from, to })}`,
      { signal },
    ),
  rules: (id: number, signal?: AbortSignal) =>
    request<Rule[]>(`/projects/${id}/rules`, { signal }),
  rule: (projectId: number, ruleId: number, signal?: AbortSignal) =>
    request<Rule>(`/projects/${projectId}/rules/${ruleId}`, { signal }),
  createRule: (projectId: number, value: RuleRequest) =>
    request<Rule>(`/projects/${projectId}/rules`, {
      method: 'POST',
      body: body(value),
    }),
  updateRule: (projectId: number, ruleId: number, value: RuleRequest) =>
    request<Rule>(`/projects/${projectId}/rules/${ruleId}`, {
      method: 'PUT',
      body: body(value),
    }),
  deleteRule: (projectId: number, ruleId: number) =>
    request<null>(`/projects/${projectId}/rules/${ruleId}`, {
      method: 'DELETE',
    }),
  ruleEnabled: (projectId: number, ruleId: number, enabled: boolean) =>
    request<Rule>(
      `/projects/${projectId}/rules/${ruleId}/${enabled ? 'enable' : 'disable'}`,
      { method: 'POST' },
    ),
  incidents: (id: number, signal?: AbortSignal) =>
    request<Incident[]>(`/projects/${id}/incidents`, { signal }),
  aiAnalysis: async (id: number, signal?: AbortSignal) => {
    try {
      return await request<AiAnalysis>(`/incidents/${id}/ai-analysis`, {
        signal,
      });
    } catch (error) {
      // IncidentDetails already loaded the incident; older incidents can have no analysis.
      if (error instanceof ApiError && error.status === 404) return null;
      throw error;
    }
  },
  retryAiAnalysis: (id: number, signal?: AbortSignal) =>
    request<AiAnalysis>(`/incidents/${id}/ai-analysis/retry`, {
      method: 'POST',
      signal,
    }),
  incident: (id: number, signal?: AbortSignal) =>
    request<Incident>(`/incidents/${id}`, { signal }),
};
