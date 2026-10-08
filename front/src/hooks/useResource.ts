import { useEffect, useState } from 'react';

export function useResource<T>(
  load: (signal: AbortSignal) => Promise<T>,
  interval = 0,
) {
  const [revision, setRevision] = useState(0);
  const [state, setState] = useState<{
    load: typeof load;
    data?: T;
    error?: string;
    loading: boolean;
    receivedAt?: number;
  }>({ load, loading: true });
  useEffect(() => {
    const controller = new AbortController();
    let timer: ReturnType<typeof setTimeout> | undefined;
    async function refresh() {
      try {
        const data = await load(controller.signal);
        if (!controller.signal.aborted)
          setState({ load, data, loading: false, receivedAt: Date.now() });
      } catch (error) {
        if (!controller.signal.aborted)
          setState((previous) => ({
            load,
            data: previous.load === load ? previous.data : undefined,
            loading: false,
            receivedAt:
              previous.load === load ? previous.receivedAt : undefined,
            error: error instanceof Error ? error.message : 'Request failed.',
          }));
      } finally {
        if (interval && !controller.signal.aborted)
          timer = setTimeout(refresh, interval);
      }
    }
    void refresh();
    return () => {
      controller.abort();
      clearTimeout(timer);
    };
  }, [load, interval, revision]);
  const sameRequest = state.load === load;
  return {
    data: sameRequest ? state.data : undefined,
    error: sameRequest ? state.error : undefined,
    loading: !sameRequest || state.loading,
    receivedAt: sameRequest ? state.receivedAt : undefined,
    reload: () => setRevision((value) => value + 1),
  };
}
