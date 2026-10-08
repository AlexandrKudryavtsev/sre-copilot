import { useCallback, useEffect, useState } from 'react';
import { ArrowLeft, Gauge, ShieldCheck, Zap } from 'lucide-react';
import { Input } from '@/components/ui/input';
import { api } from '../lib/api';
import {
  dateTime,
  formatValue,
  incidentCondition,
  incidentDuration,
  signals,
} from '../lib/signals';
import { useResource } from '../hooks/useResource';
import { Badge, ErrorNotice, Loading } from '../components/Feedback';
import { AiAnalysisPanel } from '../components/AiAnalysisPanel';
import { IncidentTable } from '../components/IncidentTable';
import { SignalChart } from '../components/SignalChart';
import type { Rule } from '../lib/types';

export function IncidentsPage({ projectId }: { projectId: number }) {
  const load = useCallback(
    (signal: AbortSignal) => api.incidents(projectId, signal),
    [projectId],
  );
  const incidents = useResource(load, 15_000);
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState('ALL');
  const [severity, setSeverity] = useState('ALL');
  const filtered = incidents.data?.filter(
    (incident) =>
      (status === 'ALL' || status === incident.status) &&
      (severity === 'ALL' || severity === incident.severity) &&
      `INC-${incident.id} ${incident.title} ${incidentCondition(incident)}`
        .toLowerCase()
        .includes(query.toLowerCase()),
  );
  return (
    <>
      <div className="page-heading">
        <div>
          <div className="eyebrow">INCIDENT MANAGEMENT</div>
          <h1>Incidents</h1>
          <p>Rule-triggered incidents and their recovery history.</p>
        </div>
        <a className="secondary-link" href={`#/projects/${projectId}`}>
          Back to overview
        </a>
      </div>
      {incidents.error && (
        <ErrorNotice message={incidents.error} retry={incidents.reload} />
      )}
      <section className="panel">
        <div className="incident-filters">
          <Input
            aria-label="Search incidents"
            placeholder="Search by ID, title or rule…"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
          <select
            aria-label="Incident status"
            value={status}
            onChange={(event) => setStatus(event.target.value)}
          >
            <option value="ALL">All statuses</option>
            <option value="OPEN">Open</option>
            <option value="RESOLVED">Resolved</option>
          </select>
          <select
            aria-label="Incident severity"
            value={severity}
            onChange={(event) => setSeverity(event.target.value)}
          >
            <option value="ALL">All severities</option>
            <option value="CRITICAL">Critical</option>
            <option value="WARNING">Warning</option>
          </select>
        </div>
        {incidents.loading ? (
          <Loading />
        ) : (
          filtered && <IncidentTable incidents={filtered} />
        )}
      </section>
    </>
  );
}

export function IncidentDetails({ incidentId }: { incidentId: number }) {
  const load = useCallback(
    (signal: AbortSignal) => api.incident(incidentId, signal),
    [incidentId],
  );
  const incident = useResource(load, 15_000);
  const value = incident.data;
  const [now, setNow] = useState(() => Date.now());
  const live = value?.status === 'OPEN';
  useEffect(() => {
    if (!live) return;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [live]);
  const historyProjectId = value?.projectId;
  const startedAt = value?.startedAt;
  const loadHistory = useCallback(
    (signal: AbortSignal) => {
      if (!historyProjectId || !startedAt) return Promise.resolve([]);
      const at = Date.parse(startedAt);
      return api.history(
        historyProjectId,
        new Date(at - 15 * 60_000).toISOString(),
        new Date(Math.min(at + 15 * 60_000, Date.now())).toISOString(),
        signal,
      );
    },
    [historyProjectId, startedAt],
  );
  const history = useResource(loadHistory, 15_000);
  if (incident.loading) return <Loading text="Loading incident…" />;
  if (!value)
    return (
      <ErrorNotice
        message={incident.error || 'Incident not found.'}
        retry={incident.reload}
      />
    );
  const captured = [
    value.latencyAtDetection,
    value.trafficAtDetection,
    value.errorsAtDetection,
    value.saturationAtDetection,
  ];
  const rule: Pick<
    Rule,
    'id' | 'metric' | 'operator' | 'threshold' | 'enabled'
  > = {
    id: value.ruleId ?? 0,
    metric: value.ruleMetric,
    operator: value.ruleOperator,
    threshold: value.ruleThreshold,
    enabled: true,
  };
  return (
    <>
      <a href={`#/projects/${value.projectId}/incidents`} className="back-link">
        <ArrowLeft size={14} />
        Back to incidents
      </a>
      <div className="page-heading">
        <div>
          <div className="detail-id">
            <span>INC-{value.id}</span>
            <Badge tone={value.status === 'OPEN' ? 'red' : 'green'}>
              {value.status === 'OPEN' ? 'Open' : 'Resolved'}
            </Badge>
            <span className={`severity ${value.severity.toLowerCase()}`}>
              {value.severity === 'CRITICAL' ? 'Critical' : 'Warning'}
            </span>
          </div>
          <h1>{value.title}</h1>
          <p>
            Started {dateTime(value.startedAt)}
            {value.resolvedAt && ` · Resolved ${dateTime(value.resolvedAt)}`}
          </p>
          <p className="incident-duration">
            Duration:{' '}
            <strong>
              {incidentDuration(value.startedAt, value.resolvedAt, now)}
            </strong>
            {value.status === 'OPEN' && <span> · ongoing</span>}
          </p>
        </div>
        <a className="secondary-link" href={`#/projects/${value.projectId}`}>
          Project overview
        </a>
      </div>
      {incident.error && (
        <ErrorNotice message={incident.error} retry={incident.reload} />
      )}
      <div className="rule-banner">
        <span className="rule-icon">
          <Zap size={20} />
        </span>
        <div>
          <div className="eyebrow">TRIGGERED MONITORING RULE</div>
          <strong>{incidentCondition(value)}</strong>
          <p>
            Trigger value: {formatValue(value.triggerValue)}{' '}
            {
              signals.find((signal) => signal.metric === value.triggerMetric)
                ?.unit
            }
            . Incident created by the rule engine.
          </p>
        </div>
        <span className="rule-engine">
          <ShieldCheck size={15} />
          Rule-based detection
        </span>
      </div>
      <AiAnalysisPanel key={value.id} incidentId={value.id} />
      <section className="panel">
        <div className="panel-heading">
          <div>
            <h2>Metrics at detection</h2>
            <p>Snapshot captured at {dateTime(value.startedAt)}</p>
          </div>
          <Gauge size={18} />
        </div>
        <div className="detection-metrics">
          {signals.map((signal, index) => (
            <div key={signal.key}>
              <span>{signal.name}</span>
              <strong
                className={
                  value.triggerMetric === signal.metric ? 'text-red' : ''
                }
              >
                {formatValue(captured[index])}
                <small>{signal.unit}</small>
              </strong>
              <small>{signal.description}</small>
            </div>
          ))}
        </div>
        <div className="panel-heading chart-section-heading">
          <div>
            <h2>Metrics around the incident</h2>
            <p>15 minutes before and after detection · UTC</p>
          </div>
        </div>
        {history.error && (
          <ErrorNotice message={history.error} retry={history.reload} />
        )}
        {history.loading ? (
          <Loading text="Loading context metrics…" />
        ) : (
          <div className="trend-grid detail-trends">
            {signals.map((signal) => (
              <div className="trend-item" key={signal.key}>
                <div className="trend-title">
                  <span>
                    <i style={{ background: signal.color }} />
                    {signal.name}
                  </span>
                  <small>{signal.unit}</small>
                </div>
                <SignalChart
                  signal={signal}
                  snapshots={history.data ?? []}
                  rules={[rule]}
                  detectedAt={value.startedAt}
                />
              </div>
            ))}
          </div>
        )}
      </section>
      <div className="dashboard-note">
        <ShieldCheck size={15} />
        <span>
          The original rule condition and detection values remain frozen.
          Recovery is evaluated automatically by the backend.
        </span>
      </div>
    </>
  );
}
