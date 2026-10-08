export type IncidentStatus = 'Open' | 'Acknowledged' | 'Resolved';
export type AIStatus = 'Queued' | 'Analyzing' | 'Completed';
export type Incident = {
  id: string;
  title: string;
  status: IncidentStatus;
  severity: 'Critical' | 'Warning';
  at: string;
  rule: string;
  metric: number;
  threshold: number;
  duration: string;
  values: number[];
  ai: AIStatus;
  cause: string;
  recommendations: string[];
};
export const PRIMARY_INCIDENT_ID = 'INC-1042';
export const ANALYZING_INCIDENT_ID = 'INC-1041';
export const SNAPSHOT = '2026-09-26T14:32:00Z';
export const metrics = [
  {
    name: 'Latency',
    description: '95th percentile',
    unit: 'ms',
    color: '#df7741',
    icon: 'latency',
    baseline: 185,
    threshold: 500,
    max: 1200,
  },
  {
    name: 'Traffic',
    description: 'Requests per second',
    unit: 'req/s',
    color: '#6685d9',
    icon: 'traffic',
    baseline: 1050,
    threshold: null,
    max: 2000,
  },
  {
    name: 'Errors',
    description: 'HTTP 5xx error rate',
    unit: '%',
    color: '#dc646b',
    icon: 'errors',
    baseline: 0.2,
    threshold: 2,
    max: 6,
  },
  {
    name: 'Saturation',
    description: 'CPU utilization',
    unit: '%',
    color: '#7e73bc',
    icon: 'saturation',
    baseline: 43,
    threshold: 85,
    max: 100,
  },
];
export const initialIncidents: Incident[] = [
  {
    id: 'INC-1042',
    title: 'Elevated response latency',
    status: 'Open',
    severity: 'Critical',
    at: '2026-09-26T14:23:00Z',
    rule: 'P95 latency > 500 ms for 5 minutes',
    metric: 0,
    threshold: 500,
    duration: '5 minutes',
    values: [842, 1248, 3.24, 78],
    ai: 'Completed',
    cause:
      'Database connection pool exhaustion is the likely cause. Latency and HTTP 5xx errors rose together without a comparable traffic spike. CPU usage also increased, consistent with waiting requests and retries. Validate with connection pool and query telemetry.',
    recommendations: [
      'Inspect active and waiting database connections; compare pool usage with the configured maximum.',
      'Review slow queries and database locks around 14:18 UTC. Check the latest deployment for changes to connection handling.',
      'If pool exhaustion is confirmed, mitigate by reducing expensive queries or cautiously increasing pool capacity within database limits.',
      'Verify that P95 latency stays below 500 ms and the error rate below 2% before resolving the incident.',
    ],
  },
  {
    id: 'INC-1041',
    title: 'Increased HTTP error rate',
    status: 'Acknowledged',
    severity: 'Warning',
    at: '2026-09-26T14:25:00Z',
    rule: 'HTTP 5xx errors > 2% for 3 minutes',
    metric: 2,
    threshold: 2,
    duration: '3 minutes',
    values: [810, 1220, 3.12, 76],
    ai: 'Analyzing',
    cause:
      'Upstream database timeouts may be causing HTTP 503 responses. The concurrent latency incident suggests a shared dependency issue rather than a traffic spike. This is a hypothesis based on the available metrics.',
    recommendations: [
      'Correlate 503 responses with database timeout logs.',
      'Check dependency health and retry volume before changing retry settings.',
      'Confirm the error rate remains below 2% after mitigation.',
    ],
  },
  {
    id: 'INC-1040',
    title: 'High CPU utilization',
    status: 'Resolved',
    severity: 'Warning',
    at: '2026-09-26T10:42:00Z',
    rule: 'CPU utilization > 85% for 10 minutes',
    metric: 3,
    threshold: 85,
    duration: '10 minutes',
    values: [310, 1820, 0.45, 92],
    ai: 'Completed',
    cause:
      'A sustained increase in request volume likely saturated the application workers. Traffic and CPU utilization rose together while the error rate stayed within its threshold.',
    recommendations: [
      'Inspect worker CPU profiles for expensive request handlers.',
      'Review autoscaling thresholds and replica capacity.',
      'Validate CPU utilization after the request surge subsides.',
    ],
  },
  {
    id: 'INC-1039',
    title: 'Elevated response latency',
    status: 'Resolved',
    severity: 'Critical',
    at: '2026-09-25T18:16:00Z',
    rule: 'P95 latency > 500 ms for 5 minutes',
    metric: 0,
    threshold: 500,
    duration: '5 minutes',
    values: [721, 980, 1.2, 61],
    ai: 'Completed',
    cause:
      'A slow downstream dependency is a possible cause. Latency increased without a matching rise in traffic or CPU utilization.',
    recommendations: [
      'Inspect distributed traces for slow downstream calls.',
      'Review dependency timeouts and connection reuse.',
      'Monitor latency during the next comparable traffic period.',
    ],
  },
  {
    id: 'INC-1038',
    title: 'Increased HTTP error rate',
    status: 'Resolved',
    severity: 'Warning',
    at: '2026-09-25T09:08:00Z',
    rule: 'HTTP 5xx errors > 2% for 3 minutes',
    metric: 2,
    threshold: 2,
    duration: '3 minutes',
    values: [420, 1120, 2.8, 56],
    ai: 'Completed',
    cause:
      'A transient downstream failure is consistent with the observed error spike. Additional logs are needed to identify the failing dependency.',
    recommendations: [
      'Review application error logs around the incident.',
      'Check upstream service availability.',
      'Confirm recovery across all application replicas.',
    ],
  },
  {
    id: 'INC-1037',
    title: 'High CPU utilization',
    status: 'Resolved',
    severity: 'Warning',
    at: '2026-09-24T16:34:00Z',
    rule: 'CPU utilization > 85% for 10 minutes',
    metric: 3,
    threshold: 85,
    duration: '10 minutes',
    values: [350, 1650, 0.6, 89],
    ai: 'Completed',
    cause:
      'A background workload may have competed with request processing for CPU. Check scheduled jobs to validate this hypothesis.',
    recommendations: [
      'Compare scheduled job execution with the CPU spike.',
      'Consider isolating background work from request processing.',
      'Review resource requests and limits.',
    ],
  },
];
export function time(value: string) {
  return new Date(value).toLocaleTimeString('en-GB', {
    timeZone: 'UTC',
    hour: '2-digit',
    minute: '2-digit',
  });
}
export function date(value: string) {
  return new Date(value).toLocaleDateString('en-GB', {
    timeZone: 'UTC',
    day: '2-digit',
    month: 'short',
  });
}
export const primaryIncident = initialIncidents.find(
  (i) => i.id === PRIMARY_INCIDENT_ID,
)!;
export const currentValues = [...primaryIncident.values];
export function compareIncidents(a: Incident, b: Incident) {
  const priority = (i: Incident) =>
    i.status === 'Resolved' ? 2 : i.severity === 'Critical' ? 0 : 1;
  return priority(a) - priority(b) || b.at.localeCompare(a.at);
}

// Every view samples the same history, including recovered historical incidents.
function metricValue(metric: number, timestamp: number) {
  const shared = timestamp >= Date.parse(primaryIncident.at) - 15 * 60000;
  const source = shared
    ? primaryIncident
    : initialIncidents.find(
        (i) =>
          i.id !== PRIMARY_INCIDENT_ID &&
          i.id !== ANALYZING_INCIDENT_ID &&
          Math.abs(timestamp - Date.parse(i.at)) <= 15 * 60000,
      );
  if (!source) return metrics[metric].baseline;
  const anchor = Date.parse(source.at);
  const relative = (timestamp - anchor) / 60000;
  const m = metrics[metric];
  const duration = parseInt(source.duration);
  const progress = Math.max(0, Math.min(1, (relative + duration + 4) / 4));
  const oscillation =
    Math.sin((timestamp / 30000) * 1.7) * 0.025 +
    Math.cos((timestamp / 30000) * 0.7) * 0.015;
  let value =
    (m.baseline + (source.values[metric] - m.baseline) * progress) *
    (1 + oscillation);
  if (
    metric === source.metric &&
    relative >= -duration &&
    (shared || relative <= 0)
  ) {
    value = Math.max(source.threshold + (metric === 2 ? 0.05 : 1), value);
  }
  if (shared && timestamp >= anchor) {
    const anchors = [
      { at: anchor, value: primaryIncident.values[metric] },
      {
        at: Date.parse(
          initialIncidents.find((i) => i.id === ANALYZING_INCIDENT_ID)!.at,
        ),
        value: initialIncidents.find((i) => i.id === ANALYZING_INCIDENT_ID)!
          .values[metric],
      },
      { at: Date.parse(SNAPSHOT), value: currentValues[metric] },
    ];
    const rightIndex = anchors.findIndex((point) => point.at >= timestamp);
    const right = anchors[rightIndex < 0 ? anchors.length - 1 : rightIndex];
    const left = anchors[Math.max(0, rightIndex - 1)];
    value =
      left.at === right.at
        ? right.value
        : left.value +
          ((right.value - left.value) * (timestamp - left.at)) /
            (right.at - left.at);
  }
  if (!shared && relative > 0) {
    const recovery = Math.max(0, 1 - relative / 15);
    value = m.baseline + (source.values[metric] - m.baseline) * recovery;
  }
  if (timestamp === anchor) value = source.values[metric];
  return Number(value.toFixed(metric === 2 ? 2 : 0));
}
export function series(metric: number, incident?: Incident, range = 60) {
  const anchor = Date.parse(incident?.at ?? primaryIncident.at);
  const start = incident
    ? anchor - 15 * 60000
    : Date.parse(SNAPSHOT) - range * 60000;
  const end = incident
    ? Math.min(anchor + 15 * 60000, Date.parse(SNAPSHOT))
    : Date.parse(SNAPSHOT);
  const step = incident ? 30000 : range * 1000;
  const timestamps = new Set<number>();
  for (let timestamp = start; timestamp <= end; timestamp += step)
    timestamps.add(timestamp);
  // Include exact detection points even in the coarser overview windows.
  for (const timestamp of [
    end,
    anchor,
    ...initialIncidents.map((i) => Date.parse(i.at)),
  ]) {
    if (timestamp >= start && timestamp <= end) timestamps.add(timestamp);
  }
  return [...timestamps]
    .sort((a, b) => a - b)
    .map((timestamp) => ({
      timestamp,
      label: time(new Date(timestamp).toISOString()),
      value: metricValue(metric, timestamp),
    }));
}
