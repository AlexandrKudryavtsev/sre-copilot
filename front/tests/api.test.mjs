import assert from 'node:assert/strict';
import { afterEach, mock, test } from 'node:test';
import { api, ApiError } from '../src/lib/api.ts';

afterEach(() => mock.restoreAll());

test('rule creation and editing send all trigger fields without runtime state', async () => {
  const calls = [];
  mock.method(globalThis, 'fetch', async (url, options) => {
    calls.push({
      url,
      method: options.method,
      value: JSON.parse(options.body),
    });
    return Response.json({ id: 8 });
  });
  const value = {
    name: 'High errors',
    metric: 'ERRORS',
    operator: 'GREATER_OR_EQUAL',
    threshold: 0.25,
    severity: 'CRITICAL',
    checkIntervalSeconds: 30,
    consecutiveViolations: 3,
    enabled: true,
  };
  await api.createRule(12, value);
  await api.updateRule(12, 8, { ...value, enabled: false });
  assert.deepEqual(calls, [
    { url: '/api/projects/12/rules', method: 'POST', value },
    {
      url: '/api/projects/12/rules/8',
      method: 'PUT',
      value: { ...value, enabled: false },
    },
  ]);
});

test('rule toggles use dedicated endpoints and deletion accepts 204', async () => {
  const calls = [];
  mock.method(globalThis, 'fetch', async (url, options) => {
    calls.push([url, options.method]);
    return options.method === 'DELETE'
      ? new Response(null, { status: 204 })
      : Response.json({ id: 8 });
  });
  await api.ruleEnabled(12, 8, false);
  await api.ruleEnabled(12, 8, true);
  assert.equal(await api.deleteRule(12, 8), null);
  assert.deepEqual(calls, [
    ['/api/projects/12/rules/8/disable', 'POST'],
    ['/api/projects/12/rules/8/enable', 'POST'],
    ['/api/projects/12/rules/8', 'DELETE'],
  ]);
});

test('deletion of a rule with an open incident surfaces the backend conflict', async () => {
  mock.method(globalThis, 'fetch', async () =>
    Response.json({ detail: 'Rule has an open incident.' }, { status: 409 }),
  );
  await assert.rejects(
    api.deleteRule(12, 8),
    (error) => error.status === 409 && error.message.includes('open incident'),
  );
});

test('a 204 current response represents no snapshots yet', async () => {
  mock.method(
    globalThis,
    'fetch',
    async () => new Response(null, { status: 204 }),
  );
  assert.equal(await api.current(12), null);
});

test('history sends the selected UTC interval without losing timezone characters', async () => {
  let url;
  mock.method(globalThis, 'fetch', async (value) => {
    url = value;
    return Response.json([]);
  });
  await api.history(12, '2026-10-08T12:00:00Z', '2026-10-08T13:00:00Z');
  const parsed = new URL(url, 'http://localhost');
  assert.equal(parsed.pathname, '/api/projects/12/metrics/history');
  assert.equal(parsed.searchParams.get('from'), '2026-10-08T12:00:00Z');
  assert.equal(parsed.searchParams.get('to'), '2026-10-08T13:00:00Z');
});

test('backend ProblemDetail and field errors are surfaced to the form', async () => {
  mock.method(globalThis, 'fetch', async () =>
    Response.json(
      {
        detail: 'Validation failed',
        errors: [{ field: 'endpointUrl', message: 'Invalid HTTP URL' }],
      },
      { status: 400 },
    ),
  );
  await assert.rejects(
    api.create({}),
    (error) =>
      error instanceof ApiError &&
      error.status === 400 &&
      error.message.includes('endpointUrl: Invalid HTTP URL'),
  );
});

test('a stale connection check returns a retryable conflict, not Connected', async () => {
  mock.method(globalThis, 'fetch', async () =>
    Response.json(
      { detail: 'Project monitoring configuration changed; retry.' },
      { status: 409 },
    ),
  );
  await assert.rejects(
    api.testConnection(12),
    (error) => error.status === 409 && error.message.includes('retry'),
  );
});

test('delete accepts an empty success body and uses the DELETE method', async () => {
  let request;
  mock.method(globalThis, 'fetch', async (url, options) => {
    request = { url, options };
    return new Response(null, { status: 204 });
  });
  assert.equal(await api.delete(12), null);
  assert.equal(request.url, '/api/projects/12');
  assert.equal(request.options.method, 'DELETE');
});

test('connection refusal gives a readable error rather than exposing fetch internals', async () => {
  mock.method(globalThis, 'fetch', async () => {
    throw new TypeError('fetch failed');
  });
  await assert.rejects(
    api.projects(),
    (error) =>
      error.status === 0 && error.message.includes('Cannot reach the backend'),
  );
});

test('navigation cancellation is preserved rather than shown as a backend failure', async () => {
  const controller = new AbortController();
  controller.abort();
  mock.method(globalThis, 'fetch', async () => {
    throw controller.signal.reason;
  });
  await assert.rejects(
    api.projects(controller.signal),
    (error) => error.name === 'AbortError',
  );
});
