import assert from 'node:assert/strict';
import { test } from 'node:test';
import { ruleNumbers } from '../src/lib/rules.ts';

test('rule fields preserve decimal thresholds, zero and integer counts', () => {
  assert.deepEqual(ruleNumbers('0', '30', '3'), {
    threshold: 0,
    checkIntervalSeconds: 30,
    consecutiveViolations: 3,
  });
  assert.equal(ruleNumbers('0.25', '1', '1').threshold, 0.25);
});

test('blank, negative and non-finite thresholds cannot become valid rules', () => {
  for (const value of ['', ' ', '-0.1', 'Infinity', 'NaN', '1e999'])
    assert.throws(() => ruleNumbers(value, '30', '3'), /Threshold/);
});

test('intervals and counts reject fractions and values beyond backend integer range', () => {
  for (const value of ['', '0', '-1', '1.5', '2147483648', 'Infinity']) {
    assert.throws(() => ruleNumbers('1', value, '3'), /Check interval/);
    assert.throws(
      () => ruleNumbers('1', '30', value),
      /Consecutive violations/,
    );
  }
  assert.equal(
    ruleNumbers('1', '2147483647', '1').checkIntervalSeconds,
    2147483647,
  );
});
