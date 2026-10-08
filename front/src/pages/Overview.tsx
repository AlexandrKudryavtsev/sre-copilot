import { useCallback, useState } from 'react';
import {
  Activity,
  ArrowRight,
  Clock3,
  Cpu,
  Radio,
  ShieldCheck,
  TriangleAlert,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { api } from '../lib/api';
import { dateTime, formatValue, matchingRules, signals } from '../lib/signals';
import { useResource } from '../hooks/useResource';
import { Badge, ErrorNotice, Loading } from '../components/Feedback';
import { ProjectActions } from '../components/ProjectActions';
import { SignalChart } from '../components/SignalChart';
import { IncidentTable } from '../components/IncidentTable';

const icons = [Clock3, Activity, TriangleAlert, Cpu];
export function OverviewPage({
  projectId,
  onChanged,
}: {
  projectId: number;
  onChanged: () => void;
}) {
  const [range, setRange] = useState(60);
  const loadProject = useCallback(
    (signal: AbortSignal) => api.project(projectId, signal),
    [projectId],
  );
  const loadCurrent = useCallback(
    (signal: AbortSignal) => api.current(projectId, signal),
    [projectId],
  );
  const loadHistory = useCallback(
    (signal: AbortSignal) => {
      const to = new Date();
      return api.history(
        projectId,
        new Date(to.getTime() - range * 60_000).toISOString(),
        to.toISOString(),
        signal,
      );
    },
    [projectId, range],
  );
  const loadRules = useCallback(
    (signal: AbortSignal) => api.rules(projectId, signal),
    [projectId],
  );
  const loadIncidents = useCallback(
    (signal: AbortSignal) => api.incidents(projectId, signal),
    [projectId],
  );
  const project = useResource(loadProject, 15_000);
  const current = useResource(loadCurrent, 15_000);
  const history = useResource(loadHistory, 15_000);
  const rules = useResource(loadRules, 30_000);
  const incidents = useResource(loadIncidents, 15_000);
  const reload = () => {
    project.reload();
    current.reload();
    history.reload();
    rules.reload();
    incidents.reload();
    onChanged();
  };
  if (project.loading) return <Loading text="Loading overview…" />;
  if (!project.data)
    return (
      <ErrorNotice
        message={project.error || 'Project not found.'}
        retry={project.reload}
      />
    );
  const value = project.data;
  const active =
    incidents.data?.filter((incident) => incident.status === 'OPEN') ?? [];
  const paused = !value.monitoringEnabled;
  const unavailable = value.connectionStatus === 'UNAVAILABLE';
  const snapshot = current.data;
  const stale =
    !!snapshot &&
    (paused ||
      unavailable ||
      !!project.error ||
      !!current.error ||
      (current.receivedAt ?? 0) - Date.parse(snapshot.timestamp) > 120_000);
  const heading = paused
    ? 'Monitoring is paused'
    : unavailable
      ? 'Metrics source is unavailable'
      : !snapshot
        ? 'Waiting for the first measurements'
        : active.length
          ? `${active.length} open incident${active.length === 1 ? '' : 's'}`
          : 'Monitoring active';
  const explanation = paused
    ? 'Enable monitoring to start collecting metrics. Previous measurements remain available.'
    : unavailable
      ? 'Check the endpoint connection. Displayed measurements are from the last successful collection.'
      : !snapshot
        ? 'Rates need two successful collections. The first collection establishes a counter baseline.'
        : 'Metrics are collected by the backend. This page refreshes every 15 seconds.';
  return (
    <>
      <div className="page-heading">
        <div>
          <div className="eyebrow">PROJECT OVERVIEW</div>
          <h1>
            {value.name}
            <span className="project-tag">#{value.id}</span>
          </h1>
          <p>{value.description || 'Your service health, at a glance.'}</p>
        </div>
        <label className="range-select">
          <Clock3 size={14} />
          <select
            aria-label="Chart time range"
            value={range}
            onChange={(event) => setRange(Number(event.target.value))}
          >
            <option value={60}>Last 1 hour</option>
            <option value={180}>Last 3 hours</option>
            <option value={360}>Last 6 hours</option>
            <option value={1440}>Last 24 hours</option>
          </select>
        </label>
      </div>
      <div className="overview-source">
        <span className="endpoint-text">{value.endpointUrl}</span>
        <Badge
          tone={
            unavailable
              ? 'red'
              : value.connectionStatus === 'CONNECTED'
                ? 'green'
                : 'neutral'
          }
        >
          {value.connectionStatus === 'UNKNOWN'
            ? 'Not checked'
            : value.connectionStatus === 'CONNECTED'
              ? 'Connected'
              : 'Unavailable'}
        </Badge>
      </div>
      <ProjectActions project={value} onChanged={reload} />
      {[project, current, history, rules, incidents].map(
        (resource, index) =>
          resource.error && (
            <ErrorNotice
              key={index}
              message={resource.error}
              retry={resource.reload}
            />
          ),
      )}
      <div
        className={`health-banner ${!paused && !unavailable && !active.length ? 'health-banner-green' : ''}`}
      >
        <div className="health-icon">
          {unavailable || active.length ? (
            <TriangleAlert size={21} />
          ) : (
            <Radio size={21} />
          )}
        </div>
        <div>
          <strong>{heading}</strong>
          <p>{explanation}</p>
        </div>
        <Button variant="outline" onClick={reload}>
          Refresh
        </Button>
      </div>
      <div className="section-heading">
        <h2>
          Golden signals{' '}
          <span className="muted-label">The four essentials</span>
        </h2>
        <span className="subtle">
          {snapshot
            ? `Latest snapshot · ${dateTime(snapshot.timestamp)}`
            : 'No snapshots yet'}
        </span>
      </div>
      {stale && (
        <output className="stale-note">
          Historical values — the latest snapshot is not a live measurement.
        </output>
      )}
      <div className="signal-grid">
        {signals.map((signal, index) => {
          const Icon = icons[index];
          const matched = snapshot
            ? matchingRules(snapshot, signal.metric, rules.data ?? [])
            : [];
          const noObservations =
            !!snapshot &&
            snapshot.traffic === 0 &&
            (signal.metric === 'LATENCY' || signal.metric === 'ERRORS');
          return (
            <article className="signal-card" key={signal.key}>
              <div className="signal-top">
                <span>
                  <Icon size={16} />
                  {signal.name}
                </span>
                <Badge
                  tone={
                    stale ? 'neutral' : matched.length ? 'amber' : 'neutral'
                  }
                >
                  {!snapshot
                    ? current.loading
                      ? 'Loading'
                      : 'No data'
                    : stale
                      ? 'Historical'
                      : noObservations
                        ? 'No traffic'
                        : matched.length
                          ? 'Rule matched'
                          : 'Observed'}
                </Badge>
              </div>
              <div className="signal-value">
                {current.loading
                  ? '…'
                  : !snapshot || noObservations
                    ? '—'
                    : formatValue(snapshot[signal.key])}
                <span>{signal.unit}</span>
              </div>
              <div className="signal-description">{signal.description}</div>
              <SignalChart
                signal={signal}
                snapshots={history.data ?? []}
                small
              />
              <div className="signal-foot">
                <span>
                  {!snapshot
                    ? 'Waiting for measurements'
                    : noObservations
                      ? 'No requests in the latest interval'
                      : matched.length
                        ? `${matched.length} rule condition${matched.length > 1 ? 's' : ''} matched`
                        : 'Latest collected value'}
                </span>
              </div>
            </article>
          );
        })}
      </div>
      <section className="panel overview-trends">
        <div className="panel-heading">
          <div>
            <h2>Signal trends</h2>
            <p>Measurements over the selected period · UTC</p>
          </div>
          <span className="period-chip">{range / 60}h window</span>
        </div>
        {history.loading ? (
          <Loading text="Loading metric history…" />
        ) : (
          <div className="trend-grid">
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
                  rules={rules.data ?? []}
                />
              </div>
            ))}
          </div>
        )}
      </section>
      <section className="panel">
        <div className="panel-heading">
          <div className="title-with-count">
            <h2>Active incidents</h2>
            <span className="number-pill">{active.length}</span>
          </div>
          <a className="text-link" href={`#/projects/${projectId}/incidents`}>
            View all incidents
            <ArrowRight size={15} />
          </a>
        </div>
        {incidents.loading ? (
          <Loading text="Loading incidents…" />
        ) : (
          incidents.data && <IncidentTable incidents={active} />
        )}
      </section>
      <div className="dashboard-note">
        <ShieldCheck size={15} />
        <span>
          Detected by rules. Resolved after consecutive recovery checks.
        </span>
      </div>
    </>
  );
}
