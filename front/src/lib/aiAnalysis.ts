import type { AiAnalysis } from './types';

export interface AiAnalysisState {
  analysis?: AiAnalysis | null;
  loading: boolean;
  retrying: boolean;
  error?: string;
}

// Each incident owns one session. Cancelling requests also invalidates late responses.
export function createAiAnalysisSession({
  load,
  retry,
  onChange,
}: {
  load: (signal: AbortSignal) => Promise<AiAnalysis | null>;
  retry: (signal: AbortSignal) => Promise<AiAnalysis>;
  onChange: (state: AiAnalysisState) => void;
}) {
  let state: AiAnalysisState = { loading: true, retrying: false };
  let disposed = false;
  let paused = false;
  let version = 0;
  let controller: AbortController | undefined;
  let timer: ReturnType<typeof setTimeout> | undefined;

  async function update(isRetry = false) {
    if (disposed || paused || state.retrying) return;
    const current = ++version;
    controller?.abort();
    clearTimeout(timer);
    const request = new AbortController();
    controller = request;
    if (isRetry) {
      state = { ...state, retrying: true, error: undefined };
      onChange(state);
    }
    try {
      const analysis = await (isRetry ? retry : load)(request.signal);
      if (disposed || current !== version) return;
      state = { analysis, loading: false, retrying: false };
    } catch (error) {
      if (disposed || current !== version || request.signal.aborted) return;
      state = {
        ...state,
        loading: false,
        retrying: false,
        error: error instanceof Error ? error.message : 'Request failed.',
      };
    }
    onChange(state);
    // Sequential polling only while PENDING, including recovery from a temporary read error.
    if (!paused && state.analysis?.status === 'PENDING')
      timer = setTimeout(() => void update(), 3_000);
  }

  return {
    refresh: () => void update(),
    retry: () => void update(true),
    pause: () => {
      paused = true;
      clearTimeout(timer);
    },
    resume: () => {
      if (disposed || !paused) return;
      paused = false;
      if (state.loading || state.error || state.analysis?.status === 'PENDING')
        void update();
    },
    dispose: () => {
      disposed = true;
      version++;
      controller?.abort();
      clearTimeout(timer);
    },
  };
}
