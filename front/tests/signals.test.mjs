import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  condition,
  matchingRules,
  matches,
  signals,
  incidentDuration,
} from '../src/lib/signals.ts';

test('open incident duration advances with time, resolved duration stays frozen', () => {
  const started = '2026-10-08T12:00:00Z';
  const resolved = '2026-10-08T12:18:45Z';
  assert.equal(
    incidentDuration(started, null, Date.parse('2026-10-08T12:00:00Z')),
    '0s',
  );
  assert.equal(
    incidentDuration(started, null, Date.parse(resolved)),
    '18m 45s',
  );
  assert.equal(
    incidentDuration(started, resolved, Date.parse('2026-10-09T12:00:00Z')),
    '18m 45s',
  );
});

test('incident duration handles long intervals and cannot be negative on clock skew', () => {
  const start = '2026-10-08T12:00:00Z';
  assert.equal(incidentDuration(start, '2026-10-08T13:01:02Z', 0), '1h 1m 2s');
  assert.equal(incidentDuration(start, '2026-10-10T15:04:00Z', 0), '2d 3h 4m');
  assert.equal(incidentDuration(start, null, Date.parse(start) - 1_000), '0s');
  assert.equal(incidentDuration('invalid', null, 0), '—');
});

test('rule boundaries follow all four backend comparison operators', () => {
  assert.equal(matches(80, 'GREATER_THAN', 80), false);
  assert.equal(matches(80, 'GREATER_OR_EQUAL', 80), true);
  assert.equal(matches(80, 'LESS_THAN', 80), false);
  assert.equal(matches(80, 'LESS_OR_EQUAL', 80), true);
  assert.equal(matches(79, 'LESS_THAN', 80), true);
});

test('disabled rules do not mark a signal as matching', () => {
  const snapshot = { saturation: 90, traffic: 10 };
  const rule = {
    enabled: false,
    metric: 'SATURATION',
    operator: 'GREATER_OR_EQUAL',
    threshold: 80,
  };
  assert.deepEqual(matchingRules(snapshot, 'SATURATION', [rule]), []);
  assert.equal(
    matchingRules(snapshot, 'SATURATION', [{ ...rule, enabled: true }]).length,
    1,
  );
});

test('no requests are not treated as healthy latency or error observations', () => {
  const snapshot = { latency: 0, errors: 0, traffic: 0 };
  for (const metric of ['LATENCY', 'ERRORS']) {
    const rule = {
      enabled: true,
      metric,
      operator: 'LESS_OR_EQUAL',
      threshold: 1,
    };
    assert.deepEqual(matchingRules(snapshot, metric, [rule]), []);
  }
});

test('rule labels use consecutive checks and latency is labelled as a mean', () => {
  assert.equal(
    condition('LATENCY', 'GREATER_OR_EQUAL', 800, 3),
    'Latency ≥ 800 ms × 3 checks',
  );
  assert.equal(signals[0].description, 'Mean request latency');
});
