import type { Incident, Metric, Operator, Rule, Snapshot } from './types';

export const signals = [
  {
    metric: 'LATENCY',
    key: 'latency',
    name: 'Latency',
    unit: 'ms',
    description: 'Mean request latency',
    color: '#df7741',
  },
  {
    metric: 'TRAFFIC',
    key: 'traffic',
    name: 'Traffic',
    unit: 'req/s',
    description: 'Requests per second',
    color: '#6685d9',
  },
  {
    metric: 'ERRORS',
    key: 'errors',
    name: 'Errors',
    unit: '%',
    description: 'HTTP 5xx error rate',
    color: '#dc646b',
  },
  {
    metric: 'SATURATION',
    key: 'saturation',
    name: 'Saturation',
    unit: '%',
    description: 'CPU utilization',
    color: '#7e73bc',
  },
] as const;
export type Signal = (typeof signals)[number];
export const operators: Record<Operator, string> = {
  GREATER_THAN: '>',
  GREATER_OR_EQUAL: '≥',
  LESS_THAN: '<',
  LESS_OR_EQUAL: '≤',
};
export function matches(value: number, operator: Operator, threshold: number) {
  switch (operator) {
    case 'GREATER_THAN':
      return value > threshold;
    case 'GREATER_OR_EQUAL':
      return value >= threshold;
    case 'LESS_THAN':
      return value < threshold;
    case 'LESS_OR_EQUAL':
      return value <= threshold;
  }
}
export function matchingRules(
  snapshot: Snapshot,
  metric: Metric,
  rules: Rule[],
) {
  const signal = signals.find((item) => item.metric === metric)!;
  if (snapshot.traffic === 0 && (metric === 'LATENCY' || metric === 'ERRORS'))
    return [];
  return rules.filter(
    (rule) =>
      rule.enabled &&
      rule.metric === metric &&
      matches(snapshot[signal.key], rule.operator, rule.threshold),
  );
}
export function condition(
  metric: Metric,
  operator: Operator,
  threshold: number,
  checks: number,
) {
  const signal = signals.find((item) => item.metric === metric)!;
  return `${signal.name} ${operators[operator]} ${formatValue(threshold)} ${signal.unit} × ${checks} ${checks === 1 ? 'check' : 'checks'}`;
}
export function incidentCondition(incident: Incident) {
  return condition(
    incident.ruleMetric,
    incident.ruleOperator,
    incident.ruleThreshold,
    incident.requiredConsecutiveViolations,
  );
}
export const formatValue = (value: number) =>
  value.toLocaleString('en-US', { maximumFractionDigits: 2 });
export function dateTime(value: string) {
  return (
    new Date(value).toLocaleString('en-GB', {
      timeZone: 'UTC',
      day: '2-digit',
      month: 'short',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    }) + ' UTC'
  );
}

export function incidentDuration(
  startedAt: string,
  resolvedAt: string | null,
  now: number,
) {
  const start = Date.parse(startedAt);
  const end = resolvedAt ? Date.parse(resolvedAt) : now;
  if (!Number.isFinite(start) || !Number.isFinite(end)) return '—';
  const seconds = Math.max(0, Math.floor((end - start) / 1000));
  const days = Math.floor(seconds / 86400);
  const hours = Math.floor((seconds % 86400) / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const remainder = seconds % 60;
  if (days) return `${days}d ${hours}h ${minutes}m`;
  if (hours) return `${hours}h ${minutes}m ${remainder}s`;
  if (minutes) return `${minutes}m ${remainder}s`;
  return `${remainder}s`;
}
