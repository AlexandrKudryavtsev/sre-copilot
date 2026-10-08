import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  initialIncidents,
  metrics,
  series,
  PRIMARY_INCIDENT_ID,
  ANALYZING_INCIDENT_ID,
  SNAPSHOT,
  currentValues,
  compareIncidents,
} from '../lib/mock-data.ts';

test('every incident has a unique ID and a breached detection value', () => {
  assert.equal(
    new Set(initialIncidents.map((i) => i.id)).size,
    initialIncidents.length,
  );
  for (const incident of initialIncidents)
    assert.ok(incident.values[incident.metric] > incident.threshold);
});
test('chart values at incident creation match all four captured metrics', () => {
  for (const incident of initialIncidents) {
    for (let metric = 0; metric < metrics.length; metric++) {
      const data = series(metric, incident);
      assert.ok(data.length >= 30);
      assert.equal(
        data.find((p) => p.timestamp === Date.parse(incident.at)).value,
        incident.values[metric],
      );
      assert.ok(data.every((p) => Number.isFinite(p.value) && p.value >= 0));
    }
  }
});
test('the triggered signal stays above threshold for its entire rule window', () => {
  for (const incident of initialIncidents) {
    const window = series(incident.metric, incident).filter(
      (p) =>
        p.timestamp >=
          Date.parse(incident.at) - parseInt(incident.duration) * 60000 &&
        p.timestamp <= Date.parse(incident.at),
    );
    assert.ok(window.length > 1);
    assert.ok(
      window.every((p) => p.value > incident.threshold),
      incident.id,
    );
  }
});
test('dashboard ranges change the actual plotted time window', () => {
  for (const range of [60, 180, 360]) {
    const data = series(0, undefined, range);
    assert.equal((data.at(-1).timestamp - data[0].timestamp) / 60000, range);
  }
});

test('the same active critical incident leads both lists with completed analysis', () => {
  const table = [...initialIncidents].sort(compareIncidents);
  const overview = initialIncidents
    .filter((i) => i.status !== 'Resolved')
    .sort(compareIncidents);
  assert.equal(table[0].id, PRIMARY_INCIDENT_ID);
  assert.equal(overview[0].id, PRIMARY_INCIDENT_ID);
  assert.equal(table[0].severity, 'Critical');
  assert.equal(table[0].ai, 'Completed');
  assert.equal(
    initialIncidents.find((i) => i.id === ANALYZING_INCIDENT_ID).ai,
    'Analyzing',
  );
  const resolved = table
    .map((i) =>
      i.id === PRIMARY_INCIDENT_ID ? { ...i, status: 'Resolved' } : i,
    )
    .sort(compareIncidents);
  assert.equal(resolved[0].id, ANALYZING_INCIDENT_ID);
});
test('overview and active incident charts agree at every shared timestamp', () => {
  for (let metric = 0; metric < metrics.length; metric++) {
    const overview = series(metric);
    assert.equal(overview.at(-1).value, currentValues[metric]);
    for (const incident of initialIncidents.filter(
      (i) => i.status !== 'Resolved',
    )) {
      const context = series(metric, incident);
      for (const point of context) {
        const sameTime = overview.find((p) => p.timestamp === point.timestamp);
        if (sameTime) assert.equal(point.value, sameTime.value);
        assert.ok(point.timestamp <= Date.parse(SNAPSHOT));
      }
      assert.equal(context.at(-1).value, currentValues[metric]);
    }
  }
});

test('historical incidents agree with the six-hour overview and show recovery', () => {
  const historic = initialIncidents.find((i) => i.id === 'INC-1040');
  for (let metric = 0; metric < metrics.length; metric++) {
    const overview = series(metric, undefined, 360);
    assert.equal(
      overview.find((p) => p.timestamp === Date.parse(historic.at)).value,
      historic.values[metric],
    );
    const detail = series(metric, historic);
    for (const point of detail) {
      const sameTime = overview.find((p) => p.timestamp === point.timestamp);
      if (sameTime) assert.equal(point.value, sameTime.value);
    }
    assert.equal(detail.at(-1).value, metrics[metric].baseline);
  }
});
