import { useEffect, useRef, useState } from 'react';
import { api } from '../lib/api';
import {
  createAiAnalysisSession,
  type AiAnalysisState,
} from '../lib/aiAnalysis';

export function useAiAnalysis(incidentId: number) {
  const session = useRef<ReturnType<typeof createAiAnalysisSession>>(null);
  const [state, setState] = useState<AiAnalysisState & { incidentId: number }>({
    incidentId,
    loading: true,
    retrying: false,
  });
  useEffect(() => {
    const current = createAiAnalysisSession({
      load: (signal) => api.aiAnalysis(incidentId, signal),
      retry: (signal) => api.retryAiAnalysis(incidentId, signal),
      onChange: (next) => setState({ ...next, incidentId }),
    });
    session.current = current;
    const syncVisibility = () => {
      if (document.hidden) current.pause();
      else current.resume();
    };
    document.addEventListener('visibilitychange', syncVisibility);
    syncVisibility();
    current.refresh();
    return () => {
      document.removeEventListener('visibilitychange', syncVisibility);
      current.dispose();
      session.current = null;
    };
  }, [incidentId]);
  const currentState: AiAnalysisState =
    state.incidentId === incidentId
      ? state
      : { loading: true, retrying: false };
  return {
    ...currentState,
    refresh: () => session.current?.refresh(),
    retry: () => session.current?.retry(),
  };
}
