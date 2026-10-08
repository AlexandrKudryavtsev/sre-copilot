import assert from 'node:assert/strict';
import { test } from 'node:test';
import { createAiAnalysisSession } from '../src/lib/aiAnalysis.ts';

const result = (status = 'PENDING') => ({
  id: 1,
  incidentId: 12,
  status,
  probableCause: null,
  evidence: [],
  recommendations: [],
  errorMessage: null,
  createdAt: '2026-10-08T12:00:00Z',
  completedAt: null,
});
const flush = async () => {
  for (let i = 0; i < 5; i++) await Promise.resolve();
};
const deferred = () => {
  let resolve, reject;
  const promise = new Promise((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
};

for (const terminal of ['COMPLETED', 'FAILED', 'INSUFFICIENT_DATA']) {
  test(`polls PENDING every 3 seconds and stops on ${terminal}`, async (t) => {
    t.mock.timers.enable({ apis: ['setTimeout'] });
    let reads = 0;
    const changes = [];
    const session = createAiAnalysisSession({
      load: async () => result(++reads === 1 ? 'PENDING' : terminal),
      retry: async () => result(),
      onChange: (state) => changes.push(state),
    });
    t.after(() => session.dispose());
    session.refresh();
    await flush();
    t.mock.timers.tick(2999);
    await flush();
    assert.equal(reads, 1);
    t.mock.timers.tick(1);
    await flush();
    assert.equal(reads, 2);
    assert.equal(changes.at(-1).analysis.status, terminal);
    t.mock.timers.tick(30_000);
    await flush();
    assert.equal(reads, 2);
  });
}

test('never overlaps polling requests when a read takes longer than the interval', async (t) => {
  t.mock.timers.enable({ apis: ['setTimeout'] });
  const pending = deferred();
  let reads = 0;
  const session = createAiAnalysisSession({
    load: () => {
      reads++;
      return pending.promise;
    },
    retry: async () => result(),
    onChange: () => {},
  });
  t.after(() => session.dispose());
  session.refresh();
  t.mock.timers.tick(30_000);
  await flush();
  assert.equal(reads, 1);
  pending.resolve(result());
  await flush();
  t.mock.timers.tick(2999);
  await flush();
  assert.equal(reads, 1);
  t.mock.timers.tick(1);
  await flush();
  assert.equal(reads, 2);
});

test('a missing legacy analysis is an empty state and does not keep polling', async (t) => {
  t.mock.timers.enable({ apis: ['setTimeout'] });
  let reads = 0;
  let latest;
  const session = createAiAnalysisSession({
    load: async () => {
      reads++;
      return null;
    },
    retry: async () => result(),
    onChange: (state) => {
      latest = state;
    },
  });
  t.after(() => session.dispose());
  session.refresh();
  await flush();
  assert.equal(latest.analysis, null);
  assert.equal(latest.loading, false);
  t.mock.timers.tick(9000);
  await flush();
  assert.equal(reads, 1);
});

test('retry clears the old result, coalesces duplicate clicks and resumes polling', async (t) => {
  t.mock.timers.enable({ apis: ['setTimeout'] });
  let reads = 0,
    retries = 0,
    latest;
  const pending = deferred();
  const session = createAiAnalysisSession({
    load: async () => {
      reads++;
      return result('COMPLETED');
    },
    retry: () => {
      retries++;
      return pending.promise;
    },
    onChange: (state) => {
      latest = state;
    },
  });
  t.after(() => session.dispose());
  session.refresh();
  await flush();
  session.retry();
  session.retry();
  session.refresh();
  assert.equal(retries, 1);
  assert.equal(reads, 1);
  assert.equal(latest.retrying, true);
  pending.resolve(result());
  await flush();
  assert.equal(latest.analysis.status, 'PENDING');
  assert.equal(latest.retrying, false);
  t.mock.timers.tick(3000);
  await flush();
  assert.equal(latest.analysis.status, 'COMPLETED');
});

test('retry failure retains the previous analysis and reports an actionable request error', async (t) => {
  let latest;
  const session = createAiAnalysisSession({
    load: async () => result('FAILED'),
    retry: async () => {
      throw new Error('Backend unavailable');
    },
    onChange: (state) => {
      latest = state;
    },
  });
  t.after(() => session.dispose());
  session.refresh();
  await flush();
  session.retry();
  await flush();
  assert.equal(latest.analysis.status, 'FAILED');
  assert.equal(latest.error, 'Backend unavailable');
  assert.equal(latest.retrying, false);
});

test('temporary polling errors retain PENDING and recover on the next poll', async (t) => {
  t.mock.timers.enable({ apis: ['setTimeout'] });
  let reads = 0,
    latest;
  const session = createAiAnalysisSession({
    load: async () => {
      reads++;
      if (reads === 2) throw new Error('Network interrupted');
      return result(reads === 1 ? 'PENDING' : 'COMPLETED');
    },
    retry: async () => result(),
    onChange: (state) => {
      latest = state;
    },
  });
  t.after(() => session.dispose());
  session.refresh();
  await flush();
  t.mock.timers.tick(3000);
  await flush();
  assert.equal(latest.analysis.status, 'PENDING');
  assert.equal(latest.error, 'Network interrupted');
  t.mock.timers.tick(3000);
  await flush();
  assert.equal(latest.analysis.status, 'COMPLETED');
  assert.equal(latest.error, undefined);
});

test('navigation cancels a request and ignores a late result even if transport ignores abort', async () => {
  const pending = deferred();
  const changes = [];
  let signal;
  const session = createAiAnalysisSession({
    load: (value) => {
      signal = value;
      return pending.promise;
    },
    retry: async () => result(),
    onChange: (state) => changes.push(state),
  });
  session.refresh();
  session.dispose();
  assert.equal(signal.aborted, true);
  pending.resolve(result('COMPLETED'));
  await flush();
  assert.equal(changes.length, 0);
});

test('retry invalidates an older GET so it cannot overwrite the new pending analysis', async (t) => {
  const oldRead = deferred();
  let signal, latest;
  const session = createAiAnalysisSession({
    load: (value) => {
      signal = value;
      return oldRead.promise;
    },
    retry: async () => result(),
    onChange: (state) => {
      latest = state;
    },
  });
  t.after(() => session.dispose());
  session.refresh();
  session.retry();
  await flush();
  assert.equal(signal.aborted, true);
  oldRead.resolve(result('COMPLETED'));
  await flush();
  assert.equal(latest.analysis.status, 'PENDING');
});

test('dispose clears pending polling timers', async (t) => {
  t.mock.timers.enable({ apis: ['setTimeout'] });
  let reads = 0;
  const session = createAiAnalysisSession({
    load: async () => {
      reads++;
      return result();
    },
    retry: async () => result(),
    onChange: () => {},
  });
  session.refresh();
  await flush();
  session.dispose();
  t.mock.timers.tick(9000);
  await flush();
  assert.equal(reads, 1);
});
