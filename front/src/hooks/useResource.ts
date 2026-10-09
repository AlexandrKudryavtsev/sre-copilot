import { useEffect, useState } from 'react';

export function useResource<T>(
  load: (signal: AbortSignal) => Promise<T>,
  interval = 0,
  options: {
    refreshKey?: unknown;
    pollWhile?: (data: T) => boolean;
  } = {},
) {
  const { refreshKey, pollWhile } = options;
  const [revision, setRevision] = useState(0);
  const [state, setState] = useState<{
    load: typeof load;
    data?: T;
    error?: string;
    loading: boolean;
    receivedAt?: number;
  }>({ load, loading: true });
  useEffect(() => {
    let controller: AbortController | undefined;
    let disposed = false;
    let continuePolling = true;
    let timer: ReturnType<typeof setTimeout> | undefined;
    async function refresh() {
      if (disposed || (interval && document.hidden)) return;
      controller?.abort();
      const request = new AbortController();
      controller = request;
      try {
        const data = await load(request.signal);
        if (!request.signal.aborted) {
          continuePolling = pollWhile?.(data) ?? true;
          setState({ load, data, loading: false, receivedAt: Date.now() });
        }
      } catch (error) {
        if (!request.signal.aborted)
          setState((previous) => ({
            load,
            data: previous.load === load ? previous.data : undefined,
            loading: false,
            receivedAt:
              previous.load === load ? previous.receivedAt : undefined,
            error: error instanceof Error ? error.message : 'Request failed.',
          }));
      } finally {
        if (
          interval &&
          continuePolling &&
          !request.signal.aborted &&
          !document.hidden
        )
          timer = setTimeout(refresh, interval);
      }
    }
    function syncVisibility() {
      clearTimeout(timer);
      if (document.hidden) controller?.abort();
      else void refresh();
    }
    if (interval) document.addEventListener('visibilitychange', syncVisibility);
    void refresh();
    return () => {
      disposed = true;
      controller?.abort();
      clearTimeout(timer);
      document.removeEventListener('visibilitychange', syncVisibility);
    };
  }, [load, interval, revision, refreshKey, pollWhile]);
  const sameRequest = state.load === load;
  return {
    data: sameRequest ? state.data : undefined,
    error: sameRequest ? state.error : undefined,
    loading: !sameRequest || state.loading,
    receivedAt: sameRequest ? state.receivedAt : undefined,
    reload: () => setRevision((value) => value + 1),
  };
}
